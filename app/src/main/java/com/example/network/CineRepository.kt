package com.example.network

import com.example.data.ChatDao
import com.example.data.ChatMessageEntity
import com.example.data.ContinueWatchingDao
import com.example.data.ContinueWatchingEntity
import com.example.data.FavoriteDao
import com.example.data.FavoriteEntity
import com.example.data.UserProfileEntity
import com.example.model.ChannelItem
import com.example.model.ChannelsData
import com.example.model.MatchData
import com.example.model.MovieResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

interface CineApiService {
    @GET("api/cineapi/match.json")
    suspend fun getHomeMatches(): MatchData

    @GET("api/sports/data.json")
    suspend fun getSportsChannels(): ChannelsData

    @GET("api/tv/data.json")
    suspend fun getTvChannels(): ChannelsData

    @GET("api/movies/data.json")
    suspend fun getMovies(): MovieResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://cinexcricket.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Android; CineArena/5.0)")
                .build()
            chain.proceed(request)
        }
        .build()

    fun getOkHttpClient(): OkHttpClient = okHttpClient

    val apiService: CineApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CineApiService::class.java)
    }
}

class CineRepository(
    private val apiService: CineApiService = RetrofitClient.apiService,
    private val favoriteDao: FavoriteDao,
    private val chatDao: ChatDao? = null,
    private val continueWatchingDao: ContinueWatchingDao? = null
) {
    suspend fun fetchHomeMatches(): Result<MatchData> {
        return runCatching { apiService.getHomeMatches() }
    }

    suspend fun fetchSportsChannels(): Result<ChannelsData> {
        return runCatching { apiService.getSportsChannels() }
    }

    suspend fun fetchTvChannels(): Result<ChannelsData> {
        return runCatching { apiService.getTvChannels() }
    }

    suspend fun fetchMovies(): Result<MovieResponse> {
        return runCatching { apiService.getMovies() }
    }

    fun getAllFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()

    fun isFavorite(id: String): Flow<Boolean> = favoriteDao.isFavorite(id)

    suspend fun toggleFavorite(favorite: FavoriteEntity, currentlyFav: Boolean) {
        if (currentlyFav) {
            favoriteDao.deleteFavoriteById(favorite.id)
        } else {
            favoriteDao.insertFavorite(favorite)
        }
    }

    fun getContinueWatching(): Flow<List<ContinueWatchingEntity>>? = continueWatchingDao?.getAllContinueWatching()

    suspend fun saveContinueWatching(item: ContinueWatchingEntity) {
        continueWatchingDao?.saveProgress(item)
    }

    suspend fun deleteContinueWatching(id: String) {
        continueWatchingDao?.deleteById(id)
    }

    suspend fun clearContinueWatching() {
        continueWatchingDao?.clearAll()
    }

    fun getUserProfile(): Flow<UserProfileEntity?>? = chatDao?.getUserProfile()

    suspend fun saveUserProfile(name: String, phone: String, hostingerApiUrl: String = "") {
        chatDao?.saveUserProfile(
            UserProfileEntity(
                name = name,
                phoneNumber = phone,
                hostingerApiUrl = hostingerApiUrl
            )
        )
    }

    fun getChatMessages(matchId: String = "global_live"): Flow<List<ChatMessageEntity>>? {
        return chatDao?.getMessagesForMatch(matchId)
    }

    suspend fun saveChatMessage(
        matchId: String = "global_live",
        senderName: String,
        senderPhone: String,
        text: String,
        timestamp: Long = System.currentTimeMillis(),
        formattedTime: String = com.example.util.IstTimeHelper.currentIstFormatted(),
        isMe: Boolean = true
    ) {
        val entity = ChatMessageEntity(
            id = System.currentTimeMillis().toString() + "_" + (100..999).random(),
            matchId = matchId,
            senderName = senderName,
            senderPhone = senderPhone,
            text = text,
            timestamp = timestamp,
            formattedTime = formattedTime,
            isMe = isMe
        )
        chatDao?.insertChatMessage(entity)
    }

    suspend fun saveRemoteChatMessage(
        id: String,
        matchId: String = "global_live",
        senderName: String,
        senderPhone: String,
        text: String,
        timestamp: Long = System.currentTimeMillis(),
        formattedTime: String = com.example.util.IstTimeHelper.currentIstFormatted()
    ) {
        val entity = ChatMessageEntity(
            id = id,
            matchId = matchId,
            senderName = senderName,
            senderPhone = senderPhone,
            text = text,
            timestamp = timestamp,
            formattedTime = formattedTime,
            isMe = false
        )
        chatDao?.insertChatMessage(entity)
    }

    suspend fun fetchHostingerChats(apiUrl: String, matchId: String = "global_live"): List<com.example.model.ChatMessage> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (apiUrl.isBlank() || !apiUrl.startsWith("http")) return@withContext emptyList()
        try {
            val url = if (apiUrl.contains("?")) "$apiUrl&match_id=$matchId" else "$apiUrl?match_id=$matchId"
            val request = okhttp3.Request.Builder()
                .url(url)
                .get()
                .build()
            val client = RetrofitClient.apiService // use same okHttpClient underneath
            val response = RetrofitClient.getOkHttpClient().newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            
            val jsonObj = org.json.JSONObject(responseBody)
            val messagesArray = jsonObj.optJSONArray("messages") ?: org.json.JSONArray()
            val resultList = mutableListOf<com.example.model.ChatMessage>()
            
            for (i in 0 until messagesArray.length()) {
                val item = messagesArray.getJSONObject(i)
                val rawTime = item.optString("timestamp", "")
                val istTime = com.example.util.IstTimeHelper.formatToIst(rawTime)
                resultList.add(
                    com.example.model.ChatMessage(
                        id = item.optString("id", System.currentTimeMillis().toString()),
                        senderName = item.optString("senderName", "User"),
                        senderPhone = item.optString("senderPhone", ""),
                        text = item.optString("text", ""),
                        timestamp = istTime,
                        isMe = false
                    )
                )
            }
            resultList
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun postHostingerChat(
        apiUrl: String,
        matchId: String = "global_live",
        senderName: String,
        senderPhone: String,
        text: String
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (apiUrl.isBlank() || !apiUrl.startsWith("http")) return@withContext false
        try {
            val jsonPayload = org.json.JSONObject().apply {
                put("matchId", matchId)
                put("senderName", senderName)
                put("senderPhone", senderPhone)
                put("text", text)
            }.toString()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = okhttp3.RequestBody.create(mediaType, jsonPayload)
            val request = okhttp3.Request.Builder()
                .url(apiUrl)
                .post(body)
                .build()

            val response = RetrofitClient.getOkHttpClient().newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun sendPresenceHeartbeat(
        apiUrl: String,
        matchId: String = "global_live",
        deviceId: String
    ): Int? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (apiUrl.isBlank() || !apiUrl.startsWith("http")) return@withContext null
        try {
            val presenceUrl = when {
                apiUrl.contains("chat.php") -> apiUrl.replace("chat.php", "presence.php")
                apiUrl.endsWith("/") -> "${apiUrl}presence.php"
                else -> apiUrl
            }
            val separator = if (presenceUrl.contains("?")) "&" else "?"
            val fullUrl = "${presenceUrl}${separator}action=heartbeat&match_id=$matchId&device_id=$deviceId&t=${System.currentTimeMillis()}"

            val request = okhttp3.Request.Builder()
                .url(fullUrl)
                .get()
                .build()

            val response = RetrofitClient.getOkHttpClient().newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null
            
            val jsonObj = org.json.JSONObject(responseBody)
            val count = when {
                jsonObj.has("count") -> jsonObj.optInt("count", 1)
                jsonObj.has("active_devices") -> jsonObj.optInt("active_devices", 1)
                jsonObj.has("viewers") -> jsonObj.optInt("viewers", 1)
                jsonObj.has("devices") -> jsonObj.optJSONArray("devices")?.length() ?: 1
                else -> 1
            }
            count.coerceAtLeast(1)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun sendPresenceLeave(
        apiUrl: String,
        matchId: String = "global_live",
        deviceId: String
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (apiUrl.isBlank() || !apiUrl.startsWith("http")) return@withContext
        try {
            val presenceUrl = when {
                apiUrl.contains("chat.php") -> apiUrl.replace("chat.php", "presence.php")
                apiUrl.endsWith("/") -> "${apiUrl}presence.php"
                else -> apiUrl
            }
            val separator = if (presenceUrl.contains("?")) "&" else "?"
            val fullUrl = "${presenceUrl}${separator}action=leave&match_id=$matchId&device_id=$deviceId"

            val request = okhttp3.Request.Builder()
                .url(fullUrl)
                .get()
                .build()

            RetrofitClient.getOkHttpClient().newCall(request).execute()
        } catch (_: Exception) {}
    }

    suspend fun fetchAirtelTvChannels(
        context: android.content.Context,
        isPullRefresh: Boolean = false
    ): Result<AirtelTvResult> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val cacheFile = java.io.File(context.cacheDir, "airteltv_cache.json")
            val tempFile = java.io.File(context.cacheDir, "airteltv_cache.json.tmp")
            val url = "https://playlist-september26.pages.dev/airteltv.json"
            val maxCacheAgeMs = 12 * 3600 * 1000L // 12 hours cache valid

            val needsDownload = isPullRefresh ||
                    !cacheFile.exists() ||
                    cacheFile.length() < 1000L ||
                    (System.currentTimeMillis() - cacheFile.lastModified() > maxCacheAgeMs)

            if (needsDownload) {
                try {
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Android; CineArena/5.0)")
                        .build()
                    val response = RetrofitClient.getOkHttpClient().newCall(request).execute()
                    if (!response.isSuccessful) {
                        if (!cacheFile.exists() || cacheFile.length() < 1000L) {
                            throw java.io.IOException("HTTP error ${response.code}")
                        }
                    } else {
                        val body = response.body ?: throw java.io.IOException("Empty response")
                        body.byteStream().use { input ->
                            tempFile.outputStream().use { output ->
                                val buffer = ByteArray(32768)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 1000L) {
                            if (cacheFile.exists()) cacheFile.delete()
                            tempFile.renameTo(cacheFile)
                        }
                    }
                } catch (e: Exception) {
                    if (!cacheFile.exists() || cacheFile.length() < 1000L) {
                        throw e
                    }
                }
            }

            if (!cacheFile.exists() || cacheFile.length() < 1000L) {
                throw java.io.IOException("Airtel TV playlist could not be retrieved")
            }

            val categoriesList = mutableListOf<String>()
            val categoriesMap = LinkedHashMap<String, MutableList<ChannelItem>>()
            val allChannelsList = mutableListOf<ChannelItem>()

            java.io.FileInputStream(cacheFile).use { fileInput ->
                java.io.BufferedReader(java.io.InputStreamReader(fileInput, Charsets.UTF_8), 32768).use { bufferedReader ->
                    val reader = android.util.JsonReader(bufferedReader)
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val topKey = reader.nextName()
                        if (topKey == "categories") {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val categoryName = reader.nextName()
                                categoriesList.add(categoryName)
                                val catChannels = mutableListOf<ChannelItem>()
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    reader.beginObject()
                                    var id = ""
                                    var name = ""
                                    var logo: String? = null
                                    var streamUrl = ""
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "id" -> id = reader.nextString()
                                            "name" -> name = reader.nextString()
                                            "logo" -> {
                                                if (reader.peek() == android.util.JsonToken.NULL) {
                                                    reader.nextNull()
                                                } else {
                                                    logo = reader.nextString()
                                                }
                                            }
                                            "url" -> streamUrl = reader.nextString()
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    if (streamUrl.isNotBlank() && name.isNotBlank()) {
                                        val item = ChannelItem(
                                            id = if (id.isNotBlank()) "airtel_$id" else "airtel_${System.identityHashCode(streamUrl)}",
                                            name = name,
                                            category = categoryName,
                                            logo = logo?.takeIf { it.isNotBlank() },
                                            status = "LIVE",
                                            streamType = "hls",
                                            streamUrl = streamUrl
                                        )
                                        catChannels.add(item)
                                        allChannelsList.add(item)
                                    }
                                }
                                reader.endArray()
                                categoriesMap[categoryName] = catChannels
                            }
                            reader.endObject()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }

            AirtelTvResult(
                categories = listOf("All Channels") + categoriesList,
                allChannels = allChannelsList,
                channelsByCategory = categoriesMap
            )
        }
    }

    suspend fun fetchJioWwChannels(
        context: android.content.Context,
        isPullRefresh: Boolean = false
    ): Result<com.example.util.M3uParseResult> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val cacheFile = java.io.File(context.cacheDir, "jioww_cache.m3u")
            val tempFile = java.io.File(context.cacheDir, "jioww_cache.m3u.tmp")
            val url = "https://playlist-september26.pages.dev/jio-ww-lookme.m3u"
            val maxCacheAgeMs = 12 * 3600 * 1000L

            val needsDownload = isPullRefresh ||
                    !cacheFile.exists() ||
                    cacheFile.length() < 100L ||
                    (System.currentTimeMillis() - cacheFile.lastModified() > maxCacheAgeMs)

            if (needsDownload) {
                try {
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Android; CineArena/5.0)")
                        .build()
                    val response = RetrofitClient.getOkHttpClient().newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body ?: throw java.io.IOException("Empty body")
                        body.byteStream().use { input ->
                            tempFile.outputStream().use { output ->
                                val buffer = ByteArray(32768)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 100L) {
                            if (cacheFile.exists()) cacheFile.delete()
                            tempFile.renameTo(cacheFile)
                        }
                    }
                } catch (e: Exception) {
                    if (!cacheFile.exists() || cacheFile.length() < 100L) {
                        throw e
                    }
                }
            }

            if (!cacheFile.exists() || cacheFile.length() < 100L) {
                throw java.io.IOException("Failed to retrieve Jio-Tv Worldwide playlist")
            }

            java.io.FileInputStream(cacheFile).use { input ->
                com.example.util.M3uParser.parseStream(
                    inputStream = input,
                    idPrefix = "jioww",
                    defaultReferer = "https://hey-lookme.shop/"
                )
            }
        }
    }

    suspend fun fetchCustomPlaylist(
        urlOrContent: String
    ): Result<com.example.util.M3uParseResult> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val trimmed = urlOrContent.trim()
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val request = okhttp3.Request.Builder()
                    .url(trimmed)
                    .header("User-Agent", "Mozilla/5.0 (Android; CineArena/5.0)")
                    .build()
                val response = RetrofitClient.getOkHttpClient().newCall(request).execute()
                if (!response.isSuccessful) {
                    throw java.io.IOException("Failed to download playlist: HTTP ${response.code}")
                }
                val body = response.body ?: throw java.io.IOException("Empty response body")
                body.byteStream().use { input ->
                    com.example.util.M3uParser.parseStream(input, idPrefix = "custom_iptv")
                }
            } else {
                // Parse directly as M3U text
                com.example.util.M3uParser.parseText(trimmed, idPrefix = "custom_iptv")
            }
        }
    }
}

data class AirtelTvResult(
    val categories: List<String>,
    val allChannels: List<ChannelItem>,
    val channelsByCategory: Map<String, List<ChannelItem>>
)
