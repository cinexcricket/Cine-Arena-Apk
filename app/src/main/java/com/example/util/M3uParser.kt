package com.example.util

import com.example.model.ChannelItem
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uParser {

    private val TVG_ID_REGEX = Regex("""tvg-id=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val TVG_NAME_REGEX = Regex("""tvg-name=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val TVG_LOGO_REGEX = Regex("""tvg-logo=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val GROUP_TITLE_REGEX = Regex("""group-title=["']([^"']*)["']""", RegexOption.IGNORE_CASE)

    // Parse from an InputStream (streamed line-by-line)
    fun parseStream(
        inputStream: InputStream,
        idPrefix: String = "m3u",
        defaultReferer: String? = null
    ): M3uParseResult {
        val allChannels = mutableListOf<ChannelItem>()
        val categoriesSet = linkedSetOf<String>()
        val channelsByCategory = LinkedHashMap<String, MutableList<ChannelItem>>()

        var currentTvgId: String? = null
        var currentTvgName: String? = null
        var currentTvgLogo: String? = null
        var currentGroupTitle: String? = null
        var currentChannelName: String? = null
        var currentReferer: String? = defaultReferer

        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 32768)
        var line: String? = reader.readLine()

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                line = reader.readLine()
                continue
            }

            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                // Parse attributes
                currentTvgId = TVG_ID_REGEX.find(trimmed)?.groupValues?.get(1)?.trim()
                currentTvgName = TVG_NAME_REGEX.find(trimmed)?.groupValues?.get(1)?.trim()
                currentTvgLogo = TVG_LOGO_REGEX.find(trimmed)?.groupValues?.get(1)?.trim()
                currentGroupTitle = GROUP_TITLE_REGEX.find(trimmed)?.groupValues?.get(1)?.trim()

                // Channel name after the last comma
                val commaIndex = trimmed.lastIndexOf(',')
                currentChannelName = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    currentTvgName ?: currentTvgId ?: "Live Channel"
                }

                if (currentChannelName.isBlank()) {
                    currentChannelName = currentTvgName ?: currentTvgId ?: "Live Channel"
                }
            } else if (trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val opt = trimmed.substring(11).trim()
                if (opt.startsWith("http-referrer=", ignoreCase = true)) {
                    currentReferer = opt.substring(14).trim()
                } else if (opt.startsWith("http-user-agent=", ignoreCase = true)) {
                    // ignore or store if needed
                }
            } else if (!trimmed.startsWith("#")) {
                // This is the stream URL
                val streamUrl = trimmed
                if (streamUrl.startsWith("http://", ignoreCase = true) ||
                    streamUrl.startsWith("https://", ignoreCase = true) ||
                    streamUrl.startsWith("rtmp://", ignoreCase = true) ||
                    streamUrl.startsWith("rtsp://", ignoreCase = true)
                ) {
                    val cat = if (!currentGroupTitle.isNullOrBlank()) currentGroupTitle.trim() else "General"
                    val channelId = currentTvgId?.takeIf { it.isNotBlank() } ?: "${idPrefix}_${allChannels.size + 1}"
                    val name = currentChannelName?.takeIf { it.isNotBlank() } ?: "Channel ${allChannels.size + 1}"

                    val lowerStreamUrl = streamUrl.lowercase()
                    val streamType = when {
                        lowerStreamUrl.contains(".mpd") || lowerStreamUrl.contains("/dash/") -> "dash"
                        lowerStreamUrl.contains(".mp4") || lowerStreamUrl.contains(".mkv") -> "mp4"
                        lowerStreamUrl.endsWith(".ts") || lowerStreamUrl.contains(".ts?") || Regex("""^https?://[^/]+/[A-Za-z0-9_.\-]+/[A-Za-z0-9_.\-]+/\d+(\?.*)?$""").matches(streamUrl) -> "ts"
                        else -> "hls"
                    }

                    val item = ChannelItem(
                        id = "${idPrefix}_${channelId}_${allChannels.size}",
                        name = name,
                        category = cat,
                        logo = currentTvgLogo?.takeIf { it.isNotBlank() },
                        status = "LIVE",
                        streamType = streamType,
                        streamUrl = streamUrl,
                        referer = currentReferer
                    )

                    allChannels.add(item)
                    categoriesSet.add(cat)
                    channelsByCategory.getOrPut(cat) { mutableListOf() }.add(item)
                }

                // Reset per-channel temp variables (keep defaultReferer if applicable)
                currentTvgId = null
                currentTvgName = null
                currentTvgLogo = null
                currentGroupTitle = null
                currentChannelName = null
                currentReferer = defaultReferer
            }

            line = reader.readLine()
        }

        val categoriesList = listOf("All Channels") + categoriesSet.toList()
        return M3uParseResult(
            categories = categoriesList,
            allChannels = allChannels,
            channelsByCategory = channelsByCategory
        )
    }

    // Parse from raw String text
    fun parseText(
        text: String,
        idPrefix: String = "iptv",
        defaultReferer: String? = null
    ): M3uParseResult {
        return parseStream(text.byteInputStream(Charsets.UTF_8), idPrefix, defaultReferer)
    }
}

data class M3uParseResult(
    val categories: List<String>,
    val allChannels: List<ChannelItem>,
    val channelsByCategory: Map<String, List<ChannelItem>>
)
