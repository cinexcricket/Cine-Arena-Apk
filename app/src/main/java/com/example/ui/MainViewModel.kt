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
    private val repository = CineRepository(favoriteDao = db.favoriteDao(), chatDao = db.chatDao())

    val favorites: StateFlow<List<FavoriteEntity>> = repository.getAllFavorites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _homeMatches = MutableStateFlow<UiState<List<MatchItem>>>(UiState.Loading)
    val homeMatches: StateFlow<UiState<List<MatchItem>>> = _homeMatches.asStateFlow()

    private val _sportsChannels = MutableStateFlow<UiState<List<ChannelItem>>>(UiState.Loading)
    val sportsChannels: StateFlow<UiState<List<ChannelItem>>> = _sportsChannels.asStateFlow()

    private val _tvChannels = MutableStateFlow<UiState<List<ChannelItem>>>(UiState.Loading)
    val tvChannels: StateFlow<UiState<List<ChannelItem>>> = _tvChannels.asStateFlow()

    // Filter states
    val isDarkMode = MutableStateFlow(true)
    val selectedHomeCategory = MutableStateFlow("All Sports")
    val selectedSportsCategory = MutableStateFlow("All Sports")
    val selectedTvCategory = MutableStateFlow("All Channels")
    val searchQueryTv = MutableStateFlow("")
    val searchQuerySports = MutableStateFlow("")

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

    // Reaction Likes Counter
    val liveMatchLikes = MutableStateFlow(1240)

    // Chat messages
    private val defaultInitialChats = listOf(
        ChatMessage("1", "CricketFan99", "Who is winning today?", "10:12 AM"),
        ChatMessage("2", "SportsLover", "IND batting looks solid!", "10:14 AM"),
        ChatMessage("3", "Admin", "Welcome to Cine Arena live stream!", "10:15 AM")
    )
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(defaultInitialChats)
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    init {
        loadData()
        observeDbChatMessages()
        startHostingerChatSync()
    }

    private fun observeDbChatMessages() {
        viewModelScope.launch {
            repository.getChatMessages("global_live")?.collect { entities ->
                val dbMsgs = entities.map { entity ->
                    val date = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                        .format(java.util.Date(entity.timestamp))
                    ChatMessage(
                        id = entity.id,
                        senderName = entity.senderName,
                        text = entity.text,
                        timestamp = date,
                        senderPhone = entity.senderPhone,
                        isMe = true
                    )
                }
                _chatMessages.value = defaultInitialChats + dbMsgs
            }
        }
    }

    private fun startHostingerChatSync() {
        viewModelScope.launch {
            while (true) {
                val apiUrl = userProfile.value?.hostingerApiUrl?.ifBlank { "https://cinexcricket.com/api/chat.php" } ?: "https://cinexcricket.com/api/chat.php"
                if (apiUrl.isNotBlank() && apiUrl.startsWith("http")) {
                    val remoteMsgs = repository.fetchHostingerChats(apiUrl, "global_live")
                    if (remoteMsgs.isNotEmpty()) {
                        val currentMsgs = _chatMessages.value
                        val newMsgs = remoteMsgs.filter { remote -> currentMsgs.none { it.id == remote.id } }
                        if (newMsgs.isNotEmpty()) {
                            _chatMessages.value = currentMsgs + newMsgs
                        }
                    }
                }
                kotlinx.coroutines.delay(5000)
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
    }

    fun fetchHomeMatches() {
        viewModelScope.launch {
            _homeMatches.value = UiState.Loading
            val result = repository.fetchHomeMatches()
            result.onSuccess { data ->
                val processedMatches = data.matches.map { match -> ensureMatchHasChannels(match) }
                if (processedMatches.isNotEmpty()) {
                    _homeMatches.value = UiState.Success(processedMatches)
                } else {
                    _homeMatches.value = UiState.Error("Network issue. Check your internet connection.")
                }
            }.onFailure { err ->
                _homeMatches.value = UiState.Error("Network issue. Check your internet connection.")
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

    fun fetchSportsChannels() {
        viewModelScope.launch {
            _sportsChannels.value = UiState.Loading
            val result = repository.fetchSportsChannels()
            result.onSuccess { data ->
                _sportsChannels.value = UiState.Success(data.channels)
            }.onFailure { err ->
                _sportsChannels.value = UiState.Error("Network issue. Check your internet connection.")
            }
        }
    }

    fun fetchTvChannels() {
        viewModelScope.launch {
            _tvChannels.value = UiState.Loading
            val result = repository.fetchTvChannels()
            result.onSuccess { data ->
                _tvChannels.value = UiState.Success(data.channels)
            }.onFailure { err ->
                _tvChannels.value = UiState.Error("Network issue. Check your internet connection.")
            }
        }
    }

    fun playMatch(match: MatchItem, channel: ChannelItem? = null) {
        val filledMatch = ensureMatchHasChannels(match)
        activeMatch.value = filledMatch
        activeChannel.value = channel ?: filledMatch.channels.firstOrNull()
    }

    fun playChannel(channel: ChannelItem) {
        activeMatch.value = null
        activeChannel.value = channel
    }

    fun selectChannelForActiveMatch(channel: ChannelItem) {
        activeChannel.value = channel
    }

    fun closePlayer() {
        activeMatch.value = null
        activeChannel.value = null
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
                logoUrl = channel.logo ?: "",
                backgroundUrl = channel.background ?: "",
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
        if (text.isBlank()) return
        val profile = userProfile.value
        if (profile == null || profile.name.isBlank()) {
            pendingChatText.value = text
            showProfileDialog.value = true
            return
        }

        viewModelScope.launch {
            repository.saveChatMessage(
                matchId = "global_live",
                senderName = profile.name,
                senderPhone = profile.phoneNumber,
                text = text
            )
            val apiUrl = profile.hostingerApiUrl.ifBlank { "https://cinexcricket.com/api/chat.php" }
            if (apiUrl.isNotBlank() && apiUrl.startsWith("http")) {
                repository.postHostingerChat(
                    apiUrl = apiUrl,
                    matchId = "global_live",
                    senderName = profile.name,
                    senderPhone = profile.phoneNumber,
                    text = text
                )
            }
            val currentTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            val msg = ChatMessage(
                id = System.currentTimeMillis().toString(),
                senderName = profile.name,
                senderPhone = profile.phoneNumber,
                text = text,
                timestamp = currentTime,
                isMe = true
            )
            _chatMessages.value = _chatMessages.value + msg
        }
    }
}
