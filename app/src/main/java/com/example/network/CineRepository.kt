package com.example.network

import com.example.data.ChatDao
import com.example.data.ChatMessageEntity
import com.example.data.ContinueWatchingDao
import com.example.data.ContinueWatchingEntity
import com.example.data.FavoriteDao
import com.example.data.FavoriteEntity
import com.example.data.UserProfileEntity
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
}
