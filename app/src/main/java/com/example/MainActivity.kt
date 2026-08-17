@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.example

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ads.StartAppHelper
import com.example.player.CinePlayerView
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.CineArenaTheme
import com.example.ui.theme.CineBackground
import com.example.ui.theme.CineSurface
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val isInPipModeState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Initialize Start.io SDK with App ID: 207109422
        StartAppHelper.initialize(this)
        StartAppHelper.preloadExitAd(this)

        // Initialize Android notification channel for broadcast alerts from cinexcricket.com
        com.example.notification.HostingerNotificationManager.createNotificationChannel(this)

        // Handle runtime notification permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Initialize background notification receiver
        com.example.notification.NotificationAlarmReceiver.scheduleAlarm(this)

        // Check if opened via notification click with target stream url
        handleNotificationIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
            LaunchedEffect(systemInDark) {
                viewModel.isDarkMode.value = systemInDark
            }
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            CineArenaTheme(isDarkMode = isDarkMode) {
                CineArenaApp(viewModel = viewModel, isInPipMode = isInPipModeState.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.notification.HostingerNotificationManager.setAppForegroundState(true)
    }

    override fun onPause() {
        super.onPause()
        com.example.notification.HostingerNotificationManager.setAppForegroundState(false)
        com.example.notification.NotificationAlarmReceiver.scheduleAlarm(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.closePlayer()
        com.example.notification.HostingerNotificationManager.setAppForegroundState(false)
        com.example.notification.NotificationAlarmReceiver.scheduleAlarm(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val targetStreamUrl = intent?.getStringExtra("target_stream_url") ?: return
        if (targetStreamUrl.isBlank()) return

        val targetTitle = intent.getStringExtra("target_stream_title") ?: "Live Broadcast Stream"
        val targetImage = intent.getStringExtra("target_stream_image") ?: "https://cinexcricket.com/favicon.ico"

        val parsed = com.example.player.StreamUrlParser.parse(targetStreamUrl)
        val notifChannel = com.example.model.ChannelItem(
            id = "notif_stream_${System.currentTimeMillis()}",
            name = targetTitle,
            streamUrl = parsed.cleanUrl,
            streamType = parsed.streamType,
            cookie = parsed.cookie,
            referer = parsed.referer,
            origin = parsed.origin,
            drm = parsed.drmConfig,
            category = "Live Match",
            quality = "HD",
            logo = if (targetImage.isNotBlank()) targetImage else "https://cinexcricket.com/favicon.ico",
            status = "LIVE"
        )
        viewModel.playChannel(notifChannel)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val wasInPip = isInPipModeState.value
        isInPipModeState.value = isInPictureInPictureMode

        if (wasInPip && !isInPictureInPictureMode) {
            // If exiting PiP and not returning to active foreground app, user closed PiP window
            window.decorView.post {
                if (lifecycle.currentState != androidx.lifecycle.Lifecycle.State.RESUMED) {
                    viewModel.closePlayer()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            viewModel.closePlayer()
        }
    }

    fun updatePipParams(hasActiveChannel: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(hasActiveChannel)
                }
                setPictureInPictureParams(builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                // Return super so MediaSession receives and executes the playback action seamlessly
                return super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.activeChannel.value != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun CineArenaApp(viewModel: MainViewModel, isInPipMode: Boolean = false) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(CineTab.HOME) }
    var lastSourceTab by remember { mutableStateOf<CineTab?>(null) }
    var showNetworkStreamDialog by remember { mutableStateOf(false) }
    var showSplashScreen by remember { mutableStateOf(true) }
    var showExitDialog by remember { mutableStateOf(false) }

    var isPlayerExpanded by remember { mutableStateOf(true) }
    var isPlayerFullscreen by remember { mutableStateOf(false) }

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val activeMatch by viewModel.activeMatch.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val liveLikesCount by viewModel.liveMatchLikes.collectAsState()
    val showProfileDialog by viewModel.showProfileDialog.collectAsState()

    // Orientation Event Listener to unlock rotation ONLY when phone auto-rotation is ON
    DisposableEffect(context, activeChannel) {
        val orientationEventListener = object : android.view.OrientationEventListener(context, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN || activity == null) return

                // Check if system auto-rotate is enabled on the device
                val isSystemAutoRotateOn = try {
                    android.provider.Settings.System.getInt(
                        context.contentResolver,
                        android.provider.Settings.System.ACCELEROMETER_ROTATION,
                        0
                    ) == 1
                } catch (e: Exception) {
                    false
                }

                // If mobile orientation is locked, NEVER rotate app based on sensor
                if (!isSystemAutoRotateOn) return

                // Check rotation angles: Landscape ~ 90° or 270°; Portrait ~ 0° or 180°
                val isLandscapeAngle = (orientation in 65..115) || (orientation in 245..295)
                val isPortraitAngle = (orientation in 335..359) || (orientation in 0..25) || (orientation in 155..205)

                if (activeChannel != null) {
                    if (isLandscapeAngle && !isPlayerFullscreen) {
                        isPlayerFullscreen = true
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else if (isPortraitAngle && isPlayerFullscreen) {
                        isPlayerFullscreen = false
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }
        }
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
        onDispose {
            orientationEventListener.disable()
        }
    }

    val homeMatchesState by viewModel.homeMatches.collectAsState()
    val sportsChannelsState by viewModel.sportsChannels.collectAsState()
    val tvChannelsState by viewModel.tvChannels.collectAsState()
    val moviesState by viewModel.movies.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    val isRefreshingHome by viewModel.isRefreshingHome.collectAsState()
    val isRefreshingSports by viewModel.isRefreshingSports.collectAsState()
    val isRefreshingTv by viewModel.isRefreshingTv.collectAsState()
    val isRefreshingMovies by viewModel.isRefreshingMovies.collectAsState()

    val selectedHomeCategory by viewModel.selectedHomeCategory.collectAsState()
    val selectedSportsCategory by viewModel.selectedSportsCategory.collectAsState()
    val selectedTvCategory by viewModel.selectedTvCategory.collectAsState()
    val selectedMovieCategory by viewModel.selectedMovieCategory.collectAsState()
    val searchQueryTv by viewModel.searchQueryTv.collectAsState()
    val searchQuerySports by viewModel.searchQuerySports.collectAsState()
    val searchQueryMovies by viewModel.searchQueryMovies.collectAsState()
    val continueWatchingList by viewModel.continueWatchingList.collectAsState()
    val activePlaybackInitialPosition by viewModel.activePlaybackInitialPosition.collectAsState()

    val closePlayerAndReturn = {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        isPlayerFullscreen = false
        isPlayerExpanded = false
        viewModel.closePlayer()
        if (lastSourceTab != null) {
            selectedTab = lastSourceTab!!
            lastSourceTab = null
        }
    }

    BackHandler(enabled = true) {
        when {
            showSplashScreen -> { /* Wait or let splash finish */ }
            drawerState.isOpen -> {
                scope.launch { drawerState.close() }
            }
            isPlayerFullscreen -> {
                isPlayerFullscreen = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            activeChannel != null -> {
                closePlayerAndReturn()
            }
            selectedTab != CineTab.HOME -> {
                selectedTab = CineTab.HOME
            }
            showExitDialog -> {
                showExitDialog = false
            }
            else -> {
                activity?.let { StartAppHelper.preloadExitAd(it) }
                showExitDialog = true
            }
        }
    }

    // Automatically expand player whenever a new channel/match starts playing and update PIP params
    LaunchedEffect(activeChannel) {
        if (activeChannel != null) {
            showSplashScreen = false
            isPlayerExpanded = true
        }
        (activity as? MainActivity)?.updatePipParams(activeChannel != null)
    }

    val enterPipMode = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity.enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Automatically expand player and restore source tab/fullscreen state when exiting System PiP mode
    var wasFullscreenBeforePip by remember { mutableStateOf(false) }

    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            wasFullscreenBeforePip = isPlayerFullscreen
        } else if (activeChannel != null) {
            isPlayerExpanded = true
            if (wasFullscreenBeforePip) {
                isPlayerFullscreen = true
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                isPlayerFullscreen = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                if (lastSourceTab != null) {
                    selectedTab = lastSourceTab!!
                }
            }
        }
    }

    val playerContent = remember(activeChannel, activeMatch, activePlaybackInitialPosition) {
        if (activeChannel == null) null
        else movableContentOf { isFs: Boolean, isMini: Boolean, isPip: Boolean, playerModifier: Modifier ->
            activeChannel?.let { ch ->
                CinePlayerView(
                    streamUrl = ch.streamUrl,
                    streamType = ch.streamType,
                    drmConfig = ch.drm,
                    cookie = ch.cookie,
                    referer = ch.referer,
                    origin = ch.origin,
                    title = activeMatch?.title ?: ch.name,
                    subtitle = activeMatch?.subtitle ?: ch.category,
                    initialPositionMs = activePlaybackInitialPosition,
                    isFullscreen = isFs,
                    isMiniPlayer = isMini,
                    isInPipMode = isPip,
                    onBackClick = {
                        if (isFs) {
                            isPlayerFullscreen = false
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            closePlayerAndReturn()
                        }
                    },
                    onFullscreenToggle = { fs ->
                        if (!isPlayerExpanded) {
                            if (lastSourceTab != null) {
                                selectedTab = lastSourceTab!!
                            }
                            isPlayerExpanded = true
                        }
                        isPlayerFullscreen = fs
                        activity?.requestedOrientation = if (fs) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    },
                    onMiniPlayerToggle = {
                        if (!isPlayerExpanded) {
                            if (lastSourceTab != null) {
                                selectedTab = lastSourceTab!!
                            }
                            isPlayerExpanded = true
                        } else {
                            isPlayerFullscreen = false
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            isPlayerExpanded = false
                        }
                    },
                    onEnterPipClick = {
                        if (isPlayerExpanded) {
                            if (isPlayerFullscreen) {
                                isPlayerFullscreen = false
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                            isPlayerExpanded = false
                        } else {
                            enterPipMode()
                        }
                    },
                    onCloseClick = { closePlayerAndReturn() },
                    onPlaybackProgress = { pos, dur ->
                        viewModel.updatePlaybackProgress(pos, dur)
                    },
                    modifier = playerModifier
                )
            }
        }
    }

    if (isInPipMode && activeChannel != null && playerContent != null) {
        playerContent(true, false, true, Modifier.fillMaxSize())
        return
    }

    val isEffectivelyFullscreen = isPlayerFullscreen || isLandscape

    // Toggle Immersive System Bars (Hide Status/Nav Bars in Landscape/Fullscreen Mode)
    DisposableEffect(isEffectivelyFullscreen) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isEffectivelyFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Crossfade(
        targetState = showSplashScreen,
        label = "splashTransition"
    ) { isSplash ->
        if (isSplash) {
            SplashScreen(
                onSplashFinished = { showSplashScreen = false }
            )
        } else {
            ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isEffectivelyFullscreen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CineSurface
            ) {
                CineDrawerContent(
                    onOpenNetworkStream = { showNetworkStreamDialog = true },
                    onOpenMovies = { selectedTab = CineTab.MOVIES },
                    onOpenFavorites = { selectedTab = CineTab.FAVORITES },
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (!isEffectivelyFullscreen) {
                    CineTopAppBar(
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        },
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = { viewModel.toggleDarkMode() }
                    )
                }
            },
            bottomBar = {
                if (!isEffectivelyFullscreen) {
                    CineBottomNavBar(
                        selectedTab = selectedTab,
                        onTabSelected = { tab ->
                            if (tab != selectedTab) {
                                viewModel.closePlayer()
                                isPlayerExpanded = false
                                isPlayerFullscreen = false
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                            selectedTab = tab
                        }
                    )
                }
            },
            containerColor = CineBackground
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isEffectivelyFullscreen) PaddingValues(0.dp) else innerPadding)
            ) {
                if (isEffectivelyFullscreen && activeChannel != null && playerContent != null) {
                    // LANDSCAPE / FULLSCREEN MODE (Captures 100% display, no header, no bottom nav)
                    playerContent(true, false, false, Modifier.fillMaxSize())
                } else if (activeChannel != null && isPlayerExpanded && playerContent != null) {
                    if (selectedTab == CineTab.SPORTS) {
                        SportsScreen(
                            sportsChannelsState = sportsChannelsState,
                            favorites = favorites,
                            selectedCategory = selectedSportsCategory,
                            searchQuery = searchQuerySports,
                            activeChannel = activeChannel,
                            playerContent = {
                                playerContent(
                                    false,
                                    false,
                                    false,
                                    Modifier.fillMaxSize()
                                )
                            },
                            onCategorySelected = { viewModel.selectedSportsCategory.value = it },
                            onSearchQueryChange = { viewModel.searchQuerySports.value = it },
                            onChannelClick = { channel ->
                                lastSourceTab = CineTab.SPORTS
                                isPlayerExpanded = true
                                isPlayerFullscreen = false
                                viewModel.playChannel(channel)
                            },
                            onToggleFavorite = { channel -> viewModel.toggleFavoriteChannel(channel) },
                            onRefresh = { viewModel.fetchSportsChannels(isPullRefresh = true) },
                            isRefreshing = isRefreshingSports
                        )
                    } else if (selectedTab == CineTab.TV) {
                        TvScreen(
                            tvChannelsState = tvChannelsState,
                            favorites = favorites,
                            selectedCategory = selectedTvCategory,
                            searchQuery = searchQueryTv,
                            activeChannel = activeChannel,
                            playerContent = {
                                playerContent(
                                    false,
                                    false,
                                    false,
                                    Modifier.fillMaxSize()
                                )
                            },
                            onCategorySelected = { viewModel.selectedTvCategory.value = it },
                            onSearchQueryChange = { viewModel.searchQueryTv.value = it },
                            onChannelClick = { channel ->
                                lastSourceTab = CineTab.TV
                                isPlayerExpanded = true
                                isPlayerFullscreen = false
                                viewModel.playChannel(channel)
                            },
                            onToggleFavorite = { channel -> viewModel.toggleFavoriteChannel(channel) },
                            onRefresh = { viewModel.fetchTvChannels(isPullRefresh = true) },
                            isRefreshing = isRefreshingTv
                        )
                    } else {
                        val currentItemId = activeMatch?.id ?: activeChannel?.id ?: ""
                        // EXPANDED VIDEO PLAYER SCREEN FOR MATCHES / OTHER CONTENT
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CineBackground)
                        ) {
                            // Top Video Player Area with Shared Bounds
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .background(Color.Black)
                                    .cineSharedBounds("card_$currentItemId")
                            ) {
                                playerContent(
                                    false,
                                    false,
                                    false,
                                    Modifier
                                        .fillMaxSize()
                                        .cineSharedElement("poster_$currentItemId")
                                )
                            }

                            // Detail Overlay Area below Player
                            Box(modifier = Modifier.weight(1f)) {
                                PlayerDetailOverlay(
                                    activeMatch = activeMatch,
                                    activeChannel = activeChannel,
                                    chatMessages = chatMessages,
                                    userProfile = userProfile,
                                    liveLikesCount = liveLikesCount,
                                    showProfileDialog = showProfileDialog,
                                    onChannelSelect = { selectedCh ->
                                        viewModel.selectChannelForActiveMatch(selectedCh)
                                    },
                                    onSendMessage = { msg ->
                                        viewModel.sendChatMessage(msg)
                                    },
                                    onLikeClick = {
                                        viewModel.addHeartReaction()
                                    },
                                    onSaveProfile = { name, phone, hostingerUrl ->
                                        viewModel.saveUserProfile(name, phone, hostingerUrl)
                                    },
                                    onDismissProfileDialog = {
                                        viewModel.showProfileDialog.value = false
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                } else {
                    // BROWSE SCREENS
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CineBackground)
                    ) {
                        when (selectedTab) {
                            CineTab.HOME -> {
                                HomeScreen(
                                    homeMatchesState = homeMatchesState,
                                    favorites = favorites,
                                    selectedCategory = selectedHomeCategory,
                                    onCategorySelected = { viewModel.selectedHomeCategory.value = it },
                                    onMatchClick = { match ->
                                        lastSourceTab = CineTab.HOME
                                        isPlayerExpanded = true
                                        isPlayerFullscreen = false
                                        viewModel.playMatch(match)
                                        selectedTab = CineTab.HOME
                                    },
                                    onToggleFavorite = { match -> viewModel.toggleFavoriteMatch(match) },
                                    onRefresh = { viewModel.fetchHomeMatches(isPullRefresh = true) },
                                    isRefreshing = isRefreshingHome
                                )
                            }
                            CineTab.SPORTS -> {
                                SportsScreen(
                                    sportsChannelsState = sportsChannelsState,
                                    favorites = favorites,
                                    selectedCategory = selectedSportsCategory,
                                    searchQuery = searchQuerySports,
                                    activeChannel = activeChannel,
                                    playerContent = null,
                                    onCategorySelected = { viewModel.selectedSportsCategory.value = it },
                                    onSearchQueryChange = { viewModel.searchQuerySports.value = it },
                                    onChannelClick = { channel ->
                                        lastSourceTab = CineTab.SPORTS
                                        isPlayerExpanded = true
                                        isPlayerFullscreen = false
                                        viewModel.playChannel(channel)
                                    },
                                    onToggleFavorite = { channel -> viewModel.toggleFavoriteChannel(channel) },
                                    onRefresh = { viewModel.fetchSportsChannels(isPullRefresh = true) },
                                    isRefreshing = isRefreshingSports
                                )
                            }
                            CineTab.TV -> {
                                TvScreen(
                                    tvChannelsState = tvChannelsState,
                                    favorites = favorites,
                                    selectedCategory = selectedTvCategory,
                                    searchQuery = searchQueryTv,
                                    activeChannel = activeChannel,
                                    playerContent = null,
                                    onCategorySelected = { viewModel.selectedTvCategory.value = it },
                                    onSearchQueryChange = { viewModel.searchQueryTv.value = it },
                                    onChannelClick = { channel ->
                                        lastSourceTab = CineTab.TV
                                        isPlayerExpanded = true
                                        isPlayerFullscreen = false
                                        viewModel.playChannel(channel)
                                    },
                                    onToggleFavorite = { channel -> viewModel.toggleFavoriteChannel(channel) },
                                    onRefresh = { viewModel.fetchTvChannels(isPullRefresh = true) },
                                    isRefreshing = isRefreshingTv
                                )
                            }
                            CineTab.HISTORY -> {
                                HistoryScreen(
                                    historyList = continueWatchingList,
                                    onPlayItem = { item ->
                                        lastSourceTab = CineTab.HISTORY
                                        isPlayerExpanded = true
                                        isPlayerFullscreen = false
                                        viewModel.playContinueWatchingItem(item)
                                    },
                                    onRemoveItem = { id ->
                                        viewModel.removeContinueWatchingItem(id)
                                    },
                                    onClearAll = {
                                        viewModel.clearAllHistory()
                                    }
                                )
                            }
                            CineTab.FAVORITES -> {
                                FavoritesScreen(
                                    favorites = favorites,
                                    onPlayFavorite = { fav ->
                                        lastSourceTab = CineTab.FAVORITES
                                        isPlayerExpanded = true
                                        isPlayerFullscreen = false
                                        val favChannel = com.example.model.ChannelItem(
                                            id = fav.id,
                                            name = fav.title,
                                            category = fav.category,
                                            logo = fav.logoUrl,
                                            background = fav.backgroundUrl,
                                            poster = fav.backgroundUrl,
                                            streamType = fav.streamType,
                                            streamUrl = fav.streamUrl
                                        )
                                        viewModel.playChannel(favChannel)
                                        selectedTab = CineTab.FAVORITES
                                    },
                                    onRemoveFavorite = { fav ->
                                        val favChannel = com.example.model.ChannelItem(
                                            id = fav.id,
                                            name = fav.title,
                                            category = fav.category,
                                            logo = fav.logoUrl,
                                            background = fav.backgroundUrl,
                                            poster = fav.backgroundUrl,
                                            streamType = fav.streamType,
                                            streamUrl = fav.streamUrl
                                        )
                                        viewModel.toggleFavoriteChannel(favChannel)
                                    }
                                )
                            }
                            CineTab.MOVIES -> {
                                MoviesScreen(
                                    moviesState = moviesState,
                                    favorites = favorites,
                                    selectedCategory = selectedMovieCategory,
                                    searchQuery = searchQueryMovies,
                                    activeMatchId = activeMatch?.id ?: activeChannel?.id,
                                    onCategorySelected = { viewModel.selectedMovieCategory.value = it },
                                    onSearchQueryChange = { viewModel.searchQueryMovies.value = it },
                                    onMovieClick = { movie ->
                                        lastSourceTab = CineTab.MOVIES
                                        isPlayerExpanded = true
                                        isPlayerFullscreen = false
                                        viewModel.playMatch(movie)
                                    },
                                    onToggleFavorite = { movie -> viewModel.toggleFavoriteMatch(movie) },
                                    onRefresh = { viewModel.fetchMovies(isPullRefresh = true) },
                                    isRefreshing = isRefreshingMovies
                                )
                            }
                        }

                                        // FLOATING IN-APP PIP MINI-PLAYER OVERLAY
                                        if (activeChannel != null && !isPlayerExpanded && playerContent != null) {
                                            var offsetX by remember { mutableFloatStateOf(0f) }
                                            var offsetY by remember { mutableFloatStateOf(0f) }
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(bottom = 12.dp, end = 12.dp)
                                                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                                    .clickable {
                                                        if (lastSourceTab != null) {
                                                            selectedTab = lastSourceTab!!
                                                        }
                                                        isPlayerExpanded = true
                                                    }
                                                    .pointerInput(Unit) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            offsetX += dragAmount.x
                                                            offsetY += dragAmount.y
                                                        }
                                                    }
                                            ) {
                                                playerContent(
                                                    false,
                                                    true,
                                                    false,
                                                    Modifier
                                                        .width(220.dp)
                                                        .height(124.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Custom Network Stream Dialog Modal
                                if (showNetworkStreamDialog) {
                                    NetworkStreamDialog(
                                        onDismiss = { showNetworkStreamDialog = false },
                                        onPlayStream = { url, cookie, referer, origin, drmLicense, drmType ->
                                            viewModel.playCustomStream(url, cookie, referer, origin, drmLicense, drmType)
                                            isPlayerFullscreen = true
                                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                    )
                                }

                                // Exit Confirmation Dialog with Start.io Exit Interstitial Ad
                                if (showExitDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showExitDialog = false },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                                        containerColor = if (isDarkMode) CineSurface else Color.White,
                                        title = {
                                            Text(
                                                text = "Exit Cine Arena?",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = "Are you sure you want to exit? We hope to see you back for more live streams!",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                                            )
                                        },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    showExitDialog = false
                                                    if (activity != null) {
                                                        StartAppHelper.showExitInterstitial(activity) {
                                                            activity.finish()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                ),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = "Exit",
                                                    color = Color.White,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                )
                                            }
                                        },
                                        dismissButton = {
                                            OutlinedButton(
                                                onClick = { showExitDialog = false },
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = "Cancel",
                                                    color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

