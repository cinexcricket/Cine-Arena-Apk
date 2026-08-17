package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MatchData(
    @Json(name = "matches") val matches: List<MatchItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MatchItem(
    @Json(name = "id") val id: String,
    @Json(name = "sport") val sport: String = "Sports",
    @Json(name = "title") val title: String = "",
    @Json(name = "subtitle") val subtitle: String = "",
    @Json(name = "teamA") val teamA: TeamInfo? = null,
    @Json(name = "teamB") val teamB: TeamInfo? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "status") val status: String = "LIVE",
    @Json(name = "startTime") val startTime: String? = null,
    @Json(name = "channels") val channels: List<ChannelItem> = emptyList(),
    @Json(name = "category") val category: String? = null,
    @Json(name = "categories") val categories: String? = null,
    @Json(name = "heading") val heading: String? = null
) {
    val displayCategory: String
        get() = categories?.takeIf { it.isNotBlank() }
            ?: category?.takeIf { it.isNotBlank() }
            ?: sport.takeIf { it.isNotBlank() }
            ?: "Webseries"

    val parsedCategories: List<String>
        get() {
            val list = mutableListOf<String>()
            if (!categories.isNullOrBlank()) {
                list.addAll(splitCategoryString(categories))
            }
            if (!category.isNullOrBlank()) {
                list.addAll(splitCategoryString(category))
            }
            if (sport.isNotBlank() && !sport.equals("Sports", ignoreCase = true)) {
                list.addAll(splitCategoryString(sport))
            }
            if (list.isEmpty() && sport.isNotBlank()) {
                list.addAll(splitCategoryString(sport))
            }
            return list.distinct()
        }

    val firstCategory: String
        get() = parsedCategories.firstOrNull() ?: displayCategory.split(Regex("[,/|;]+")).firstOrNull()?.trim() ?: "Sports"
}

fun splitCategoryString(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(Regex("[,/|;]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class MovieResponse(
    @Json(name = "matches") val matches: List<MatchItem> = emptyList(),
    @Json(name = "channels") val channels: List<ChannelItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TeamInfo(
    @Json(name = "name") val name: String = "",
    @Json(name = "shortCode") val shortCode: String = "",
    @Json(name = "logo") val logo: String? = null
)

@JsonClass(generateAdapter = true)
data class ChannelsData(
    @Json(name = "channels") val channels: List<ChannelItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChannelItem(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String = "",
    @Json(name = "category") val category: String = "General",
    @Json(name = "logo") val logo: String? = null,
    @Json(name = "background") val background: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "status") val status: String = "LIVE",
    @Json(name = "streamType") val streamType: String = "hls", // hls, dash, mp4, iframe
    @Json(name = "streamUrl") val streamUrl: String = "",
    @Json(name = "drm") val drm: DrmConfig? = null,
    @Json(name = "cookie") val cookie: String? = null,
    @Json(name = "referer") val referer: String? = null,
    @Json(name = "origin") val origin: String? = null
) {
    val parsedCategories: List<String>
        get() = splitCategoryString(category).ifEmpty { listOf("General") }

    val firstCategory: String
        get() = parsedCategories.firstOrNull() ?: category.split(Regex("[,/|;]+")).firstOrNull()?.trim() ?: "General"
}

@JsonClass(generateAdapter = true)
data class DrmConfig(
    @Json(name = "type") val type: String = "clearkey", // clearkey, widevine
    @Json(name = "keyId") val keyId: String? = null,
    @Json(name = "key") val key: String? = null,
    @Json(name = "licenseUrl") val licenseUrl: String? = null
)

data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val timestamp: String,
    val senderPhone: String? = null,
    val avatarUrl: String? = null,
    val isMe: Boolean = false
)
