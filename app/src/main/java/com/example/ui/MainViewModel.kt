package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FavoriteEntity
import com.example.model.ChannelItem
import com.example.model.ChatMessage
import com.example.model.DrmConfig
import com.example.model.MatchItem
import com.example.network.AirtelTvResult
import com.example.network.CineRepository
import com.example.util.M3uParseResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.player.StreamUrlParser

sealed interface UiState<out T> {
    object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = CineRepository(
        favoriteDao = db.favoriteDao(),
        chatDao = db.chatDao(),
        continueWatchingDao = db.continueWatchingDao()
    )

    val favorites: StateFlow<List<FavoriteEntity>> = repository.getAllFavorites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val continueWatchingList: StateFlow<List<com.example.data.ContinueWatchingEntity>> =
        (repository.getContinueWatching() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val activePlaybackInitialPosition = MutableStateFlow<Long>(0L)

    private val _homeMatches = MutableStateFlow<UiState<List<MatchItem>>>(UiState.Loading)
    val homeMatches: StateFlow<UiState<List<MatchItem>>> = _homeMatches.asStateFlow()

    private val _sportsChannels = MutableStateFlow<UiState<List<ChannelItem>>>(UiState.Loading)
    val sportsChannels: StateFlow<UiState<List<ChannelItem>>> = _sportsChannels.asStateFlow()

    private val _tvChannels = MutableStateFlow<UiState<List<ChannelItem>>>(UiState.Loading)
    val tvChannels: StateFlow<UiState<List<ChannelItem>>> = _tvChannels.asStateFlow()

    private val _movies = MutableStateFlow<UiState<List<MatchItem>>>(UiState.Loading)
    val movies: StateFlow<UiState<List<MatchItem>>> = _movies.asStateFlow()

    // Airtel TV Lazy Loading State (Only fetched when screen opened)
    private val _airtelTvResult = MutableStateFlow<AirtelTvResult?>(null)
    private val _airtelUiState = MutableStateFlow<UiState<List<ChannelItem>>>(UiState.Loading)
    val isRefreshingAirtel = MutableStateFlow(false)
    val selectedAirtelCategory = MutableStateFlow("All Channels")
    val searchQueryAirtel = MutableStateFlow("")
    val airtelDisplayLimit = MutableStateFlow(80)

    private var isAirtelLoaded = false
    private var isAirtelLoading = false

    val airtelChannelsState: StateFlow<UiState<List<ChannelItem>>> = combine(
        _airtelUiState,
        _airtelTvResult,
        selectedAirtelCategory,
        searchQueryAirtel,
        airtelDisplayLimit
    ) { uiState, result, category, query, limit ->
        if (uiState !is UiState.Success || result == null) {
            return@combine uiState
        }

        val trimmedQuery = query.trim()
        val channels = if (trimmedQuery.isNotEmpty()) {
            val pool = if (category == "All Channels") result.allChannels else (result.channelsByCategory[category] ?: emptyList())
            pool.filter {
                it.name.contains(trimmedQuery, ignoreCase = true) ||
                it.category.contains(trimmedQuery, ignoreCase = true)
            }.take(150)
        } else if (category != "All Channels") {
            result.channelsByCategory[category] ?: emptyList()
        } else {
            result.allChannels.take(limit)
        }

        UiState.Success(channels)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState.Loading
    )

    val airtelCategories: StateFlow<List<String>> = _airtelTvResult.map { result ->
        result?.categories ?: listOf("All Channels")
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("All Channels")
    )

    val airtelTotalCount: StateFlow<Int> = _airtelTvResult.map { result ->
        result?.allChannels?.size ?: 0
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val airtelHasMore: StateFlow<Boolean> = combine(
        _airtelTvResult,
        selectedAirtelCategory,
        searchQueryAirtel,
        airtelDisplayLimit
    ) { result, category, query, limit ->
        if (result == null || query.isNotBlank() || category != "All Channels") false
        else limit < result.allChannels.size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    // Jio-TV World Wide Lazy Loading State
    private val _jioWwResult = MutableStateFlow<M3uParseResult?>(null)
    private val _jioWwUiState = MutableStateFlow<UiState<List<ChannelItem>>>(UiState.Loading)
    val isRefreshingJioWw = MutableStateFlow(false)
    val selectedJioWwCategory = MutableStateFlow("All Channels")
    val searchQueryJioWw = MutableStateFlow("")
    val jioWwDisplayLimit = MutableStateFlow(80)

    private var isJioWwLoaded = false
    private var isJioWwLoading = false

    val jioWwChannelsState: StateFlow<UiState<List<ChannelItem>>> = combine(
        _jioWwUiState,
        _jioWwResult,
        selectedJioWwCategory,
        searchQueryJioWw,
        jioWwDisplayLimit
    ) { uiState, result, category, query, limit ->
        if (uiState !is UiState.Success || result == null) {
            return@combine uiState
        }

        val trimmedQuery = query.trim()
        val channels = if (trimmedQuery.isNotEmpty()) {
            val pool = if (category == "All Channels") result.allChannels else (result.channelsByCategory[category] ?: emptyList())
            pool.filter {
                it.name.contains(trimmedQuery, ignoreCase = true) ||
                it.category.contains(trimmedQuery, ignoreCase = true)
            }.take(150)
        } else if (category != "All Channels") {
            result.channelsByCategory[category] ?: emptyList()
        } else {
            result.allChannels.take(limit)
        }

        UiState.Success(channels)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState.Loading
    )

    val jioWwCategories: StateFlow<List<String>> = _jioWwResult.map { result ->
        result?.categories ?: listOf("All Channels")
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("All Channels")
    )

    val jioWwTotalCount: StateFlow<Int> = _jioWwResult.map { result ->
        result?.allChannels?.size ?: 0
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val jioWwHasMore: StateFlow<Boolean> = combine(
        _jioWwResult,
        selectedJioWwCategory,
        searchQueryJioWw,
        jioWwDisplayLimit
    ) { result, category, query, limit ->
        if (result == null || query.isNotBlank() || category != "All Channels") false
        else limit < result.allChannels.size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    // IPTV Player (Custom User Playlist) State
    private val iptvPrefs = getApplication<Application>().getSharedPreferences("cine_iptv_prefs", android.content.Context.MODE_PRIVATE)
    val customIptvInput = MutableStateFlow(iptvPrefs.getString("saved_playlist_input", "") ?: "")
    private val _customIptvResult = MutableStateFlow<M3uParseResult?>(null)
    private val _customIptvUiState = MutableStateFlow<UiState<List<ChannelItem>>>(
        if ((iptvPrefs.getString("saved_playlist_input", "") ?: "").isBlank()) UiState.Success(emptyList()) else UiState.Loading
    )
    val isCustomIptvLoading = MutableStateFlow(false)
    val selectedIptvCategory = MutableStateFlow("All Channels")
    val searchQueryIptv = MutableStateFlow("")
    val iptvDisplayLimit = MutableStateFlow(80)

    val iptvChannelsState: StateFlow<UiState<List<ChannelItem>>> = combine(
        _customIptvUiState,
        _customIptvResult,
        selectedIptvCategory,
        searchQueryIptv,
        iptvDisplayLimit
    ) { uiState, result, category, query, limit ->
        if (uiState !is UiState.Success || result == null) {
            return@combine uiState
        }

        val trimmedQuery = query.trim()
        val channels = if (trimmedQuery.isNotEmpty()) {
            val pool = if (category == "All Channels") result.allChannels else (result.channelsByCategory[category] ?: emptyList())
            pool.filter {
                it.name.contains(trimmedQuery, ignoreCase = true) ||
                it.category.contains(trimmedQuery, ignoreCase = true)
            }.take(150)
        } else if (category != "All Channels") {
            result.channelsByCategory[category] ?: emptyList()
        } else {
            result.allChannels.take(limit)
        }

        UiState.Success(channels)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState.Success(emptyList())
    )

    val iptvCategories: StateFlow<List<String>> = _customIptvResult.map { result ->
        result?.categories ?: listOf("All Channels")
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("All Channels")
    )

    val iptvTotalCount: StateFlow<Int> = _customIptvResult.map { result ->
        result?.allChannels?.size ?: 0
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val iptvHasMore: StateFlow<Boolean> = combine(
        _customIptvResult,
        selectedIptvCategory,
        searchQueryIptv,
        iptvDisplayLimit
    ) { result, category, query, limit ->
        if (result == null || query.isNotBlank() || category != "All Channels") false
        else limit < result.allChannels.size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    // Filter states
    val isDarkMode = MutableStateFlow(true)
    val selectedHomeCategory = MutableStateFlow("All Sports")
    val selectedSportsCategory = MutableStateFlow("All Sports")
    val selectedTvCategory = MutableStateFlow("All Channels")
    val selectedMovieCategory = MutableStateFlow("All Movies")
    val searchQueryTv = MutableStateFlow("")
    val searchQuerySports = MutableStateFlow("")
    val searchQueryMovies = MutableStateFlow("")

    // Active playing items
    val activeMatch = MutableStateFlow<MatchItem?>(null)
    val activeChannel = MutableStateFlow<ChannelItem?>(null)

    // User Profile & Dialog State
    val userProfile: StateFlow<com.example.data.UserProfileEntity?> = repository.getUserProfile()
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        ) ?: MutableStateFlow(null)

    val showProfileDialog = MutableStateFlow(false)
    val pendingChatText = MutableStateFlow("")

    // Pull-to-refresh states
    val isRefreshingHome = MutableStateFlow(false)
    val isRefreshingSports = MutableStateFlow(false)
    val isRefreshingTv = MutableStateFlow(false)
    val isRefreshingMovies = MutableStateFlow(false)

    // Unique installation device identifier to count exact active devices
    val deviceId: String by lazy {
        val prefs = getApplication<Application>().getSharedPreferences("cine_device_prefs", android.content.Context.MODE_PRIVATE)
        var id = prefs.getString("unique_device_uuid", null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("unique_device_uuid", id).apply()
        }
        id
    }

    // Reaction Likes Counter
    val liveMatchLikes = MutableStateFlow(12)

    // Exact Active Stream Viewers in Video Section (starts with 1 representing this active device)
    private val _activeStreamViewers = MutableStateFlow(1)
    val activeStreamViewers: StateFlow<Int> = _activeStreamViewers.asStateFlow()

    private var presenceHeartbeatJob: kotlinx.coroutines.Job? = null

    // Chat messages (Pure database chat loaded from local Room DB)
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // App Settings (Aspect Ratio, Always Landscape)
    private val appSettingsManager = com.example.data.AppSettingsManager.getInstance(application)
    val defaultAspectRatio: StateFlow<com.example.player.VideoResizeMode> = appSettingsManager.defaultAspectRatio
    val alwaysLandscape: StateFlow<Boolean> = appSettingsManager.alwaysLandscape

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    fun openSettingsDialog() {
        _showSettingsDialog.value = true
    }

    fun closeSettingsDialog() {
        _showSettingsDialog.value = false
    }

    fun setDefaultAspectRatio(mode: com.example.player.VideoResizeMode) {
        appSettingsManager.setDefaultAspectRatio(mode)
    }

    fun setAlwaysLandscape(enabled: Boolean) {
        appSettingsManager.setAlwaysLandscape(enabled)
    }

    // Remote Version Endpoint App Update State
    private val _appUpdateInfo = MutableStateFlow<com.example.update.AppUpdateInfo?>(null)
    val appUpdateInfo: StateFlow<com.example.update.AppUpdateInfo?> = _appUpdateInfo.asStateFlow()

    private val _updateDownloadState = MutableStateFlow<com.example.update.UpdateDownloadState>(com.example.update.UpdateDownloadState.Idle)
    val updateDownloadState: StateFlow<com.example.update.UpdateDownloadState> = _updateDownloadState.asStateFlow()

    private var downloadedApkFile: java.io.File? = null

    init {
        loadData()
        observeDbChatMessages()
        startHostingerChatSync()
        checkForAppUpdate()
    }

    fun checkForAppUpdate() {
        viewModelScope.launch {
            try {
                val customUrl = userProfile.value?.hostingerApiUrl
                val update = com.example.update.AppUpdateManager.checkForUpdate(
                    context = getApplication(),
                    customApiUrl = customUrl
                )
                if (update != null) {
                    _appUpdateInfo.value = update
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissUpdateDialog() {
        _appUpdateInfo.value = null
        _updateDownloadState.value = com.example.update.UpdateDownloadState.Idle
    }

    fun startUpdateDownload() {
        val update = _appUpdateInfo.value ?: return
        viewModelScope.launch {
            _updateDownloadState.value = com.example.update.UpdateDownloadState.Downloading(0f)
            val result = com.example.update.AppUpdateManager.downloadApk(
                context = getApplication(),
                downloadUrl = update.downloadUrl,
                onProgress = { progress, downloaded, total ->
                    _updateDownloadState.value = com.example.update.UpdateDownloadState.Downloading(
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                }
            )

            result.fold(
                onSuccess = { file ->
                    downloadedApkFile = file
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        getApplication(),
                        "${getApplication<Application>().packageName}.fileprovider",
                        file
                    )
                    _updateDownloadState.value = com.example.update.UpdateDownloadState.ReadyToInstall(
                        apkUri = uri,
                        localFile = file
                    )
                    // Trigger installation automatically once downloaded
                    installDownloadedUpdate()
                },
                onFailure = { error ->
                    _updateDownloadState.value = com.example.update.UpdateDownloadState.Error(
                        message = error.localizedMessage ?: "Failed to download update APK. Please check your connection."
                    )
                }
            )
        }
    }

    fun installDownloadedUpdate() {
        val file = downloadedApkFile ?: return
        com.example.update.AppUpdateManager.installApk(getApplication(), file)
    }

    fun startPresenceSyncForActiveStream() {
        presenceHeartbeatJob?.cancel()
        _activeStreamViewers.value = 1
        presenceHeartbeatJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val currentMatchId = activeMatch.value?.id ?: activeChannel.value?.id ?: "global_live"
                    val apiUrl = userProfile.value?.hostingerApiUrl?.ifBlank { "https://cinexcricket.com/api/chat.php" } ?: "https://cinexcricket.com/api/chat.php"
                    
                    val realActiveCount = repository.sendPresenceHeartbeat(
                        apiUrl = apiUrl,
                        matchId = currentMatchId,
                        deviceId = deviceId
                    )

                    if (realActiveCount != null && realActiveCount >= 1) {
                        _activeStreamViewers.value = realActiveCount
                    }
                } catch (_: Exception) {}
                
                // Real-time heartbeat interval every 6 seconds
                kotlinx.coroutines.delay(6000L)
            }
        }
    }

    fun stopPresenceSyncForActiveStream() {
        val currentMatchId = activeMatch.value?.id ?: activeChannel.value?.id ?: "global_live"
        val apiUrl = userProfile.value?.hostingerApiUrl?.ifBlank { "https://cinexcricket.com/api/chat.php" } ?: "https://cinexcricket.com/api/chat.php"
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null
        _activeStreamViewers.value = 1
        viewModelScope.launch {
            try {
                repository.sendPresenceLeave(
                    apiUrl = apiUrl,
                    matchId = currentMatchId,
                    deviceId = deviceId
                )
            } catch (_: Exception) {}
        }
    }

    private fun observeDbChatMessages() {
        viewModelScope.launch {
            repository.getChatMessages("global_live")?.collect { entities ->
                val dbMsgs = entities.map { entity ->
                    val istTime = if (entity.formattedTime.isNotBlank()) {
                        entity.formattedTime
                    } else {
                        com.example.util.IstTimeHelper.formatToIst(entity.timestamp)
                    }
                    ChatMessage(
                        id = entity.id,
                        senderName = entity.senderName,
                        text = entity.text,
                        timestamp = istTime,
                        senderPhone = entity.senderPhone,
                        isMe = entity.isMe
                    )
                }
                _chatMessages.value = dbMsgs
            }
        }
    }

    private fun startHostingerChatSync() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val apiUrl = userProfile.value?.hostingerApiUrl?.ifBlank { "https://cinexcricket.com/api/chat.php" } ?: "https://cinexcricket.com/api/chat.php"
                    if (apiUrl.isNotBlank() && apiUrl.startsWith("http")) {
                        val remoteMsgs = repository.fetchHostingerChats(apiUrl, "global_live")
                        if (remoteMsgs.isNotEmpty()) {
                            val currentIds = _chatMessages.value.map { it.id }.toSet()
                            remoteMsgs.forEach { remote ->
                                if (!currentIds.contains(remote.id)) {
                                    repository.saveRemoteChatMessage(
                                        id = remote.id,
                                        matchId = "global_live",
                                        senderName = remote.senderName,
                                        senderPhone = remote.senderPhone ?: "",
                                        text = remote.text,
                                        timestamp = System.currentTimeMillis(),
                                        formattedTime = remote.timestamp
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(10000L)
            }
        }
    }

    fun addHeartReaction() {
        liveMatchLikes.value += 1
    }

    fun saveUserProfile(name: String, phone: String, hostingerApiUrl: String = "") {
        viewModelScope.launch {
            repository.saveUserProfile(name.trim(), phone.trim(), hostingerApiUrl.trim())
            showProfileDialog.value = false
            if (pendingChatText.value.isNotBlank()) {
                sendChatMessage(pendingChatText.value)
                pendingChatText.value = ""
            }
        }
    }

    fun toggleDarkMode() {
        isDarkMode.value = !isDarkMode.value
    }

    fun loadData() {
        fetchHomeMatches()
        fetchSportsChannels()
        fetchTvChannels()
        fetchMovies()
    }

    fun fetchHomeMatches(isPullRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isPullRefresh) {
                isRefreshingHome.value = true
            } else if (_homeMatches.value !is UiState.Success) {
                _homeMatches.value = UiState.Loading
            }
            val result = repository.fetchHomeMatches()
            result.onSuccess { data ->
                val processedMatches = data.matches.map { match -> ensureMatchHasChannels(match) }
                if (processedMatches.isNotEmpty()) {
                    _homeMatches.value = UiState.Success(processedMatches)
                } else if (_homeMatches.value !is UiState.Success) {
                    _homeMatches.value = UiState.Error("Network issue. Check your internet connection.")
                }
            }.onFailure { err ->
                if (_homeMatches.value !is UiState.Success) {
                    _homeMatches.value = UiState.Error("Network issue. Check your internet connection.")
                }
            }
            if (isPullRefresh) {
                isRefreshingHome.value = false
            }
        }
    }

    private fun ensureMatchHasChannels(match: MatchItem): MatchItem {
        if (match.channels.isNotEmpty()) return match
        val defaultChannels = listOf(
            ChannelItem(
                id = "${match.id}_server1",
                name = "Server 1 (HD English)",
                category = match.sport,
                logo = match.poster ?: match.teamA?.logo,
                status = "LIVE",
                streamType = "hls",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
            ),
            ChannelItem(
                id = "${match.id}_server2",
                name = "Server 2 (Hindi SD)",
                category = match.sport,
                logo = match.poster ?: match.teamB?.logo,
                status = "LIVE",
                streamType = "hls",
                streamUrl = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
            ),
            ChannelItem(
                id = "${match.id}_server3",
                name = "Server 3 (Backup HD)",
                category = match.sport,
                logo = match.poster,
                status = "LIVE",
                streamType = "hls",
                streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_f4vhls/master.m3u8"
            )
        )
        return match.copy(channels = defaultChannels)
    }

    private fun getDefaultMatches(): List<MatchItem> {
        return listOf(
            MatchItem(
                id = "m1",
                sport = "Cricket",
                title = "India vs Srilanka Test",
                subtitle = "Warm-Up Match • Sony Ten Hindi S2",
                status = "LIVE",
                poster = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=800",
                teamA = com.example.model.TeamInfo("India", "IND", "https://i.imgur.com/5Xf8Z7K.png"),
                teamB = com.example.model.TeamInfo("Sri Lanka", "SL", "https://i.imgur.com/3Z8QZ8K.png"),
                channels = listOf(
                    ChannelItem(
                        id = "m1_c1",
                        name = "Sony Ten Hindi S2 (HD)",
                        category = "Cricket",
                        logo = "https://i.imgur.com/5Xf8Z7K.png",
                        status = "LIVE",
                        streamType = "hls",
                        streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                    ),
                    ChannelItem(
                        id = "m1_c2",
                        name = "Sony Ten ENG S1 (HD)",
                        category = "Cricket",
                        logo = "https://i.imgur.com/5Xf8Z7K.png",
                        status = "LIVE",
                        streamType = "hls",
                        streamUrl = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
                    ),
                    ChannelItem(
                        id = "m1_c3",
                        name = "Sony Ten Hindi (SD)",
                        category = "Cricket",
                        logo = "https://i.imgur.com/3Z8QZ8K.png",
                        status = "LIVE",
                        streamType = "hls",
                        streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_f4vhls/master.m3u8"
                    ),
                    ChannelItem(
                        id = "m1_c4",
                        name = "Sony Ten 3 (HD)",
                        category = "Cricket",
                        logo = "https://i.imgur.com/3Z8QZ8K.png",
                        status = "LIVE",
                        streamType = "hls",
                        streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                    )
                )
            ),
            MatchItem(
                id = "m2",
                sport = "Football",
                title = "UEFA Champions League - El Clásico",
                subtitle = "Real Madrid vs FC Barcelona",
                status = "LIVE",
                poster = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800",
                teamA = com.example.model.TeamInfo("Real Madrid", "RMA", "https://i.imgur.com/9Xf8Z7K.png"),
                teamB = com.example.model.TeamInfo("FC Barcelona", "BAR", "https://i.imgur.com/8Z8QZ8K.png"),
                channels = listOf(
                    ChannelItem(
                        id = "m2_c1",
                        name = "Sony Sports Ten 2 (HD)",
                        category = "Football",
                        logo = "https://i.imgur.com/9Xf8Z7K.png",
                        status = "LIVE",
                        streamType = "hls",
                        streamUrl = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
                    ),
                    ChannelItem(
                        id = "m2_c2",
                        name = "SuperSport Football",
                        category = "Football",
                        logo = "https://i.imgur.com/8Z8QZ8K.png",
                        status = "LIVE",
                        streamType = "hls",
                        streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                    )
                )
            ),
            MatchItem(
                id = "m3",
                sport = "Badminton",
                title = "BWF World Championships",
                subtitle = "Men's Singles Finals",
                status = "LIVE",
                poster = "https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?w=800",
                teamA = com.example.model.TeamInfo("Viktor Axelsen", "DEN", null),
                teamB = com.example.model.TeamInfo("Lakshya Sen", "IND", null),
                channels = listOf(
                    ChannelItem(
                        id = "m3_c1",
                        name = "BWF TV Live HD",
                        category = "Badminton",
                        status = "LIVE",
                        streamType = "hls",
                        streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_f4vhls/master.m3u8"
                    )
                )
            )
        )
    }

    fun fetchSportsChannels(isPullRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isPullRefresh) {
                isRefreshingSports.value = true
            } else if (_sportsChannels.value !is UiState.Success) {
                _sportsChannels.value = UiState.Loading
            }
            val result = repository.fetchSportsChannels()
            result.onSuccess { data ->
                _sportsChannels.value = UiState.Success(data.channels)
            }.onFailure { err ->
                if (_sportsChannels.value !is UiState.Success) {
                    _sportsChannels.value = UiState.Error("Network issue. Check your internet connection.")
                }
            }
            if (isPullRefresh) {
                isRefreshingSports.value = false
            }
        }
    }

    fun fetchTvChannels(isPullRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isPullRefresh) {
                isRefreshingTv.value = true
            } else if (_tvChannels.value !is UiState.Success) {
                _tvChannels.value = UiState.Loading
            }
            val result = repository.fetchTvChannels()
            result.onSuccess { data ->
                _tvChannels.value = UiState.Success(data.channels)
            }.onFailure { err ->
                if (_tvChannels.value !is UiState.Success) {
                    _tvChannels.value = UiState.Error("Network issue. Check your internet connection.")
                }
            }
            if (isPullRefresh) {
                isRefreshingTv.value = false
            }
        }
    }

    fun fetchMovies(isPullRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isPullRefresh) {
                isRefreshingMovies.value = true
            } else if (_movies.value !is UiState.Success) {
                _movies.value = UiState.Loading
            }
            val result = repository.fetchMovies()
            result.onSuccess { data ->
                val list = if (data.matches.isNotEmpty()) {
                    data.matches
                } else if (data.channels.isNotEmpty()) {
                    data.channels.map { ch ->
                        MatchItem(
                            id = ch.id,
                            sport = ch.category,
                            title = ch.name,
                            poster = ch.poster ?: ch.background ?: ch.logo,
                            categories = ch.category,
                            channels = listOf(ch)
                        )
                    }
                } else {
                    emptyList()
                }

                if (list.isNotEmpty()) {
                    _movies.value = UiState.Success(list)
                } else if (_movies.value !is UiState.Success) {
                    _movies.value = UiState.Error("No movies or requested content found.")
                }
            }.onFailure { err ->
                if (_movies.value !is UiState.Success) {
                    _movies.value = UiState.Error("Network issue. Check your internet connection.")
                }
            }
            if (isPullRefresh) {
                isRefreshingMovies.value = false
            }
        }
    }

    fun loadMoreAirtelChannels() {
        val currentLimit = airtelDisplayLimit.value
        val total = _airtelTvResult.value?.allChannels?.size ?: 0
        if (currentLimit < total) {
            airtelDisplayLimit.value = (currentLimit + 80).coerceAtMost(total)
        }
    }

    fun loadAirtelTvIfNeeded(isPullRefresh: Boolean = false) {
        if (isAirtelLoading && !isPullRefresh) return
        if (isAirtelLoaded && !isPullRefresh) return

        viewModelScope.launch {
            isAirtelLoading = true
            if (isPullRefresh) {
                isRefreshingAirtel.value = true
            } else if (_airtelTvResult.value == null) {
                _airtelUiState.value = UiState.Loading
            }

            val result = repository.fetchAirtelTvChannels(getApplication(), isPullRefresh)
            result.onSuccess { data ->
                _airtelTvResult.value = data
                _airtelUiState.value = UiState.Success(data.allChannels)
                isAirtelLoaded = true
            }.onFailure { err ->
                if (_airtelTvResult.value == null) {
                    _airtelUiState.value = UiState.Error(err.message ?: "Failed to load Airtel TV channels. Please check internet connection.")
                }
            }

            isAirtelLoading = false
            if (isPullRefresh) {
                isRefreshingAirtel.value = false
            }
        }
    }

    fun loadMoreJioWwChannels() {
        val currentLimit = jioWwDisplayLimit.value
        val total = _jioWwResult.value?.allChannels?.size ?: 0
        if (currentLimit < total) {
            jioWwDisplayLimit.value = (currentLimit + 80).coerceAtMost(total)
        }
    }

    fun loadJioWwIfNeeded(isPullRefresh: Boolean = false) {
        if (isJioWwLoading && !isPullRefresh) return
        if (isJioWwLoaded && !isPullRefresh) return

        viewModelScope.launch {
            isJioWwLoading = true
            if (isPullRefresh) {
                isRefreshingJioWw.value = true
            } else if (_jioWwResult.value == null) {
                _jioWwUiState.value = UiState.Loading
            }

            val result = repository.fetchJioWwChannels(getApplication(), isPullRefresh)
            result.onSuccess { data ->
                _jioWwResult.value = data
                _jioWwUiState.value = UiState.Success(data.allChannels)
                isJioWwLoaded = true
            }.onFailure { err ->
                if (_jioWwResult.value == null) {
                    _jioWwUiState.value = UiState.Error(err.message ?: "Failed to load Jio-Tv (World Wide) channels.")
                }
            }

            isJioWwLoading = false
            if (isPullRefresh) {
                isRefreshingJioWw.value = false
            }
        }
    }

    fun loadMoreIptvChannels() {
        val currentLimit = iptvDisplayLimit.value
        val total = _customIptvResult.value?.allChannels?.size ?: 0
        if (currentLimit < total) {
            iptvDisplayLimit.value = (currentLimit + 80).coerceAtMost(total)
        }
    }

    fun loadIptvPlaylist(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        iptvPrefs.edit().putString("saved_playlist_input", trimmed).apply()
        customIptvInput.value = trimmed

        viewModelScope.launch {
            isCustomIptvLoading.value = true
            _customIptvUiState.value = UiState.Loading
            iptvDisplayLimit.value = 80
            selectedIptvCategory.value = "All Channels"

            val result = repository.fetchCustomPlaylist(trimmed)
            result.onSuccess { data ->
                _customIptvResult.value = data
                _customIptvUiState.value = UiState.Success(data.allChannels)
            }.onFailure { err ->
                _customIptvUiState.value = UiState.Error(err.message ?: "Failed to parse playlist. Please verify the URL or M3U content.")
            }

            isCustomIptvLoading.value = false
        }
    }

    fun deleteIptvPlaylist() {
        iptvPrefs.edit().remove("saved_playlist_input").apply()
        customIptvInput.value = ""
        _customIptvResult.value = null
        _customIptvUiState.value = UiState.Success(emptyList())
        selectedIptvCategory.value = "All Channels"
        searchQueryIptv.value = ""
        iptvDisplayLimit.value = 80
    }

    fun loadSavedIptvIfNeeded() {
        val saved = customIptvInput.value.trim()
        if (saved.isNotEmpty() && _customIptvResult.value == null && !isCustomIptvLoading.value) {
            loadIptvPlaylist(saved)
        }
    }

    fun playMatch(match: MatchItem, channel: ChannelItem? = null, initialPositionMs: Long = 0L) {
        val filledMatch = ensureMatchHasChannels(match)
        activePlaybackInitialPosition.value = initialPositionMs
        activeMatch.value = filledMatch
        activeChannel.value = channel ?: filledMatch.channels.firstOrNull()
        startPresenceSyncForActiveStream()
    }

    fun playChannel(channel: ChannelItem, initialPositionMs: Long = 0L) {
        activePlaybackInitialPosition.value = initialPositionMs
        activeMatch.value = null
        activeChannel.value = channel
        startPresenceSyncForActiveStream()
    }

    fun playContinueWatchingItem(item: com.example.data.ContinueWatchingEntity) {
        activePlaybackInitialPosition.value = item.positionMs
        val channel = ChannelItem(
            id = item.id,
            name = item.title,
            category = item.category,
            logo = item.poster.ifBlank { item.background },
            background = item.background.ifBlank { item.poster },
            poster = item.poster.ifBlank { item.background },
            streamType = item.streamType,
            streamUrl = item.streamUrl
        )
        val match = MatchItem(
            id = item.id,
            sport = item.category.ifBlank { "Movie" },
            title = item.title,
            subtitle = item.subtitle,
            status = "LIVE",
            poster = item.poster.ifBlank { item.background },
            channels = listOf(channel)
        )
        activeMatch.value = match
        activeChannel.value = channel
        startPresenceSyncForActiveStream()
    }

    fun removeContinueWatchingItem(id: String) {
        viewModelScope.launch {
            repository.deleteContinueWatching(id)
        }
    }

    fun clearAllContinueWatching() {
        viewModelScope.launch {
            repository.clearContinueWatching()
        }
    }

    fun clearAllHistory() {
        clearAllContinueWatching()
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
        if (positionMs < 1000L) return
        val currentMatch = activeMatch.value
        val currentChannel = activeChannel.value ?: return

        val id = currentMatch?.id ?: currentChannel.id
        val title = currentMatch?.title ?: currentChannel.name
        val subtitle = currentMatch?.subtitle ?: currentChannel.category
        val category = currentMatch?.sport ?: currentChannel.category
        val poster = currentMatch?.poster ?: currentChannel.poster ?: currentChannel.logo ?: ""
        val background = currentMatch?.poster ?: currentChannel.background ?: currentChannel.poster ?: ""
        val streamType = currentChannel.streamType
        val streamUrl = currentChannel.streamUrl

        if (id.isBlank() || streamUrl.isBlank()) return

        val entity = com.example.data.ContinueWatchingEntity(
            id = id,
            title = title,
            subtitle = subtitle,
            category = category,
            poster = poster,
            background = background,
            streamType = streamType,
            streamUrl = streamUrl,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedTimestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repository.saveContinueWatching(entity)
        }
    }

    fun selectChannelForActiveMatch(channel: ChannelItem) {
        activeChannel.value = channel
    }

    fun closePlayer() {
        stopPresenceSyncForActiveStream()
        activeMatch.value = null
        activeChannel.value = null
        activePlaybackInitialPosition.value = 0L
    }

    fun toggleFavoriteMatch(match: MatchItem) {
        viewModelScope.launch {
            val isFav = favorites.value.any { it.id == match.id }
            val entity = FavoriteEntity(
                id = match.id,
                itemType = "match",
                title = match.title,
                subtitle = match.subtitle,
                category = match.sport,
                logoUrl = match.poster ?: match.teamA?.logo ?: "",
                backgroundUrl = match.poster ?: "",
                streamType = match.channels.firstOrNull()?.streamType ?: "hls",
                streamUrl = match.channels.firstOrNull()?.streamUrl ?: ""
            )
            repository.toggleFavorite(entity, isFav)
        }
    }

    fun toggleFavoriteChannel(channel: ChannelItem) {
        viewModelScope.launch {
            val isFav = favorites.value.any { it.id == channel.id }
            val entity = FavoriteEntity(
                id = channel.id,
                itemType = "channel",
                title = channel.name,
                subtitle = channel.category,
                category = channel.category,
                logoUrl = channel.poster ?: channel.logo ?: "",
                backgroundUrl = channel.poster ?: channel.background ?: "",
                streamType = channel.streamType,
                streamUrl = channel.streamUrl
            )
            repository.toggleFavorite(entity, isFav)
        }
    }

    fun playCustomStream(
        url: String,
        cookie: String,
        referer: String,
        origin: String,
        drmLicense: String,
        drmType: String
    ) {
        val parsed = StreamUrlParser.parse(
            rawUrl = url,
            inputCookie = cookie,
            inputReferer = referer,
            inputOrigin = origin,
            inputDrmLicense = drmLicense,
            inputDrmType = drmType
        )

        val customChannel = ChannelItem(
            id = "custom_" + System.currentTimeMillis(),
            name = "Custom Network Stream",
            category = "Network Stream",
            logo = null,
            background = null,
            status = "LIVE",
            streamType = parsed.streamType,
            streamUrl = parsed.cleanUrl,
            drm = parsed.drmConfig,
            cookie = parsed.cookie,
            referer = parsed.referer,
            origin = parsed.origin
        )

        playChannel(customChannel)
    }

    fun sendChatMessage(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val profile = userProfile.value
        if (profile == null || profile.name.isBlank()) {
            pendingChatText.value = cleanText
            showProfileDialog.value = true
            return
        }

        viewModelScope.launch {
            val currentIstTime = com.example.util.IstTimeHelper.currentIstFormatted()
            val timestamp = System.currentTimeMillis()
            
            // Save directly into local database with IST timestamp
            repository.saveChatMessage(
                matchId = "global_live",
                senderName = profile.name,
                senderPhone = profile.phoneNumber,
                text = cleanText,
                timestamp = timestamp,
                formattedTime = currentIstTime,
                isMe = true
            )

            // Asynchronously sync with Hostinger chat server if configured
            val apiUrl = profile.hostingerApiUrl.ifBlank { "https://cinexcricket.com/api/chat.php" }
            if (apiUrl.isNotBlank() && apiUrl.startsWith("http")) {
                try {
                    repository.postHostingerChat(
                        apiUrl = apiUrl,
                        matchId = "global_live",
                        senderName = profile.name,
                        senderPhone = profile.phoneNumber,
                        text = cleanText
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
