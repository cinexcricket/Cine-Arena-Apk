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
import com.example.network.CineRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        loadData()
        observeDbChatMessages()
        startHostingerChatSync()
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
