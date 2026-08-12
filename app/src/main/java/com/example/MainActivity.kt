package com.example

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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

    override fun onDestroy() {
        super.onDestroy()
        viewModel.closePlayer()
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

    var isPlayerExpanded by remember { mutableStateOf(true) }
    var isPlayerFullscreen by remember { mutableStateOf(false) }

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val activeMatch by viewModel.activeMatch.collectAsState()
    val activeChannel by viewModel.activeChannel.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val liveLikesCount by viewModel.liveMatchLikes.collectAsState()
    val showProfileDialog by viewModel.showProfileDialog.collectAsState()

    val homeMatchesState by viewModel.homeMatches.collectAsState()
    val sportsChannelsState by viewModel.sportsChannels.collectAsState()
    val tvChannelsState by viewModel.tvChannels.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    val selectedHomeCategory by viewModel.selectedHomeCategory.collectAsState()
    val selectedSportsCategory by viewModel.selectedSportsCategory.collectAsState()
    val selectedTvCategory by viewModel.selectedTvCategory.collectAsState()
    val searchQueryTv by viewModel.searchQueryTv.collectAsState()
    val searchQuerySports by viewModel.searchQuerySports.collectAsState()

    val closePlayerAndReturn = {
        viewModel.closePlayer()
        if (lastSourceTab != null) {
            selectedTab = lastSourceTab!!
            lastSourceTab = null
        }
    }

    BackHandler(enabled = showSplashScreen || drawerState.isOpen || isPlayerFullscreen || activeChannel != null || selectedTab != CineTab.HOME) {
        when {
            showSplashScreen -> { /* Wait or let splash finish */ }
            drawerState.isOpen -> {
                scope.launch { drawerState.close() }
            }
            isPlayerFullscreen -> {
                isPlayerFullscreen = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            activeChannel != null -> {
                closePlayerAndReturn()
            }
            selectedTab != CineTab.HOME -> {
                selectedTab = CineTab.HOME
            }
        }
    }

    // Automatically expand player whenever a new channel/match starts playing and update PIP params
    LaunchedEffect(activeChannel) {
        if (activeChannel != null) {
            isPlayerExpanded = true
        }
        (activity as? MainActivity)?.updatePipParams(activeChannel != null)
    }

    // Automatically expand player and restore source tab when exiting System PiP mode
    LaunchedEffect(isInPipMode) {
        if (!isInPipMode && activeChannel != null) {
            isPlayerExpanded = true
            isPlayerFullscreen = false
            if (lastSourceTab != null) {
                selectedTab = lastSourceTab!!
            }
        }
    }

    val playerContent = remember(activeChannel, activeMatch) {
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
                    isFullscreen = isFs,
                    isMiniPlayer = isMini,
                    isInPipMode = isPip,
                    onBackClick = {
                        if (isFs) {
                            isPlayerFullscreen = false
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        } else {
                            closePlayerAndReturn()
                        }
                    },
                    onFullscreenToggle = { fs ->
                        isPlayerFullscreen = fs
                        activity?.requestedOrientation = if (fs) {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    },
                    onMiniPlayerToggle = {
                        isPlayerFullscreen = false
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        isPlayerExpanded = false
                    },
                    onCloseClick = { closePlayerAndReturn() },
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
                            if (activeChannel != null) {
                                viewModel.closePlayer()
                            }
                            selectedTab = tab
                            lastSourceTab = null
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
                } else {
                    // PORTRAIT MODE
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Show top player if active and expanded
                        if (activeChannel != null && isPlayerExpanded && playerContent != null) {
                            playerContent(false, false, false, Modifier.fillMaxWidth().height(210.dp))
                        }

                        // Main Screen Area
                        Box(modifier = Modifier.weight(1f)) {
                            if (activeChannel != null && isPlayerExpanded && activeMatch != null) {
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
                            } else {
                                when (selectedTab) {
                                    CineTab.HOME -> {
                                        HomeScreen(
                                            homeMatchesState = homeMatchesState,
                                            favorites = favorites,
                                            selectedCategory = selectedHomeCategory,
                                            onCategorySelected = { viewModel.selectedHomeCategory.value = it },
                                            onMatchClick = { match ->
                                                lastSourceTab = CineTab.HOME
                                                viewModel.playMatch(match)
                                                selectedTab = CineTab.HOME
                                            },
                                            onToggleFavorite = { match -> viewModel.toggleFavoriteMatch(match) },
                                            onRefresh = { viewModel.fetchHomeMatches() }
                                        )
                                    }
                                    CineTab.SPORTS -> {
                                    SportsScreen(
                                        sportsChannelsState = sportsChannelsState,
                                        favorites = favorites,
                                        selectedCategory = selectedSportsCategory,
                                        searchQuery = searchQuerySports,
                                        activeChannel = activeChannel,
                                        onCategorySelected = { viewModel.selectedSportsCategory.value = it },
                                        onSearchQueryChange = { viewModel.searchQuerySports.value = it },
                                        onChannelClick = { channel ->
                                            lastSourceTab = CineTab.SPORTS
                                            viewModel.playChannel(channel)
                                        },
                                        onToggleFavorite = { channel -> viewModel.toggleFavoriteChannel(channel) },
                                        onRefresh = { viewModel.fetchSportsChannels() }
                                    )
                                    }
                                    CineTab.TV -> {
                                        TvScreen(
                                            tvChannelsState = tvChannelsState,
                                            favorites = favorites,
                                            selectedCategory = selectedTvCategory,
                                            searchQuery = searchQueryTv,
                                            activeChannel = activeChannel,
                                            onCategorySelected = { viewModel.selectedTvCategory.value = it },
                                            onSearchQueryChange = { viewModel.searchQueryTv.value = it },
                                            onChannelClick = { channel ->
                                                lastSourceTab = CineTab.TV
                                                viewModel.playChannel(channel)
                                            },
                                            onToggleFavorite = { channel -> viewModel.toggleFavoriteChannel(channel) },
                                            onRefresh = { viewModel.fetchTvChannels() }
                                        )
                                    }
                                    CineTab.FAVORITES -> {
                                    FavoritesScreen(
                                        favorites = favorites,
                                        onPlayFavorite = { fav ->
                                            lastSourceTab = CineTab.FAVORITES
                                            val favChannel = com.example.model.ChannelItem(
                                                id = fav.id,
                                                name = fav.title,
                                                category = fav.category,
                                                logo = fav.logoUrl,
                                                background = fav.backgroundUrl,
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
                                                streamType = fav.streamType,
                                                streamUrl = fav.streamUrl
                                            )
                                            viewModel.toggleFavoriteChannel(favChannel)
                                        }
                                    )
                                }
                            }
                        }

                    // FLOATING IN-APP PIP MINI-PLAYER OVERLAY
                    if (activeChannel != null && !isPlayerExpanded) {
                        activeChannel?.let { ch ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 12.dp, end = 12.dp)
                            ) {
                                CinePlayerView(
                                    streamUrl = ch.streamUrl,
                                    streamType = ch.streamType,
                                    drmConfig = ch.drm,
                                    cookie = ch.cookie,
                                    referer = ch.referer,
                                    origin = ch.origin,
                                    title = activeMatch?.title ?: ch.name,
                                    subtitle = activeMatch?.subtitle ?: ch.category,
                                    isFullscreen = false,
                                    isMiniPlayer = true,
                                    onBackClick = {},
                                    onFullscreenToggle = { fs ->
                                        if (lastSourceTab != null) {
                                            selectedTab = lastSourceTab!!
                                        }
                                        isPlayerExpanded = true
                                        isPlayerFullscreen = fs
                                        activity?.requestedOrientation = if (fs) {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        }
                                    },
                                    onMiniPlayerToggle = {
                                        if (lastSourceTab != null) {
                                            selectedTab = lastSourceTab!!
                                        }
                                        isPlayerExpanded = true
                                    },
                                    onCloseClick = { closePlayerAndReturn() },
                                    modifier = Modifier
                                        .width(220.dp)
                                        .height(124.dp)
                                )
                            }
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
        }
    }
}
}
}
}
}

