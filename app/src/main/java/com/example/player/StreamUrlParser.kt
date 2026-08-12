package com.example.player

import com.example.model.DrmConfig
import java.net.URLDecoder

data class ParsedStreamInfo(
    val cleanUrl: String,
    val streamType: String, // "dash", "hls", "mp4", "iframe"
    val cookie: String? = null,
    val referer: String? = null,
    val origin: String? = null,
    val drmConfig: DrmConfig? = null
)

object StreamUrlParser {

    fun parse(
        rawUrl: String,
        inputCookie: String? = null,
        inputReferer: String? = null,
        inputOrigin: String? = null,
        inputDrmLicense: String? = null,
        inputDrmType: String? = null
    ): ParsedStreamInfo {
        var url = rawUrl.trim()
        if (url.isBlank()) {
            return ParsedStreamInfo("", "hls")
        }

        // Try decoding URL if encoded parameters exist (%7C, %3A, %3D, etc.)
        var urlToExamine = url
        try {
            if (url.contains("%7C", ignoreCase = true) ||
                url.contains("%3A", ignoreCase = true) ||
                url.contains("%3D", ignoreCase = true) ||
                url.contains("%2F", ignoreCase = true)
            ) {
                urlToExamine = URLDecoder.decode(url, "UTF-8")
            }
        } catch (e: Exception) {
            urlToExamine = url
        }

        var extractedCookie: String? = inputCookie?.trim()?.takeIf { it.isNotBlank() }
        var extractedReferer: String? = inputReferer?.trim()?.takeIf { it.isNotBlank() }
        var extractedOrigin: String? = inputOrigin?.trim()?.takeIf { it.isNotBlank() }
        var extractedDrmLicense: String? = inputDrmLicense?.trim()?.takeIf { it.isNotBlank() }
        var extractedDrmScheme: String? = inputDrmType?.trim()?.takeIf { it.isNotBlank() }

        // Check if params like drmScheme=..., drmLicense=..., Cookie=..., Referer=..., Origin=... are embedded in the URL
        if (urlToExamine.contains("drmScheme=", ignoreCase = true) ||
            urlToExamine.contains("drmLicense=", ignoreCase = true) ||
            urlToExamine.contains("Cookie=", ignoreCase = true) ||
            urlToExamine.contains("Referer=", ignoreCase = true) ||
            urlToExamine.contains("Origin=", ignoreCase = true)
        ) {
            val paramRegex = Regex("""(?i)(?:[?&|]+)(drmScheme|drmLicense|Cookie|Referer|Origin)=([^&]+)""")
            val matches = paramRegex.findAll(urlToExamine)
            for (match in matches) {
                val key = match.groupValues[1]
                val value = match.groupValues[2]
                when (key.lowercase()) {
                    "drmscheme" -> if (extractedDrmScheme.isNullOrBlank()) extractedDrmScheme = value
                    "drmlicense" -> if (extractedDrmLicense.isNullOrBlank()) extractedDrmLicense = value
                    "cookie" -> if (extractedCookie.isNullOrBlank()) extractedCookie = value
                    "referer" -> if (extractedReferer.isNullOrBlank()) extractedReferer = value
                    "origin" -> if (extractedOrigin.isNullOrBlank()) extractedOrigin = value
                }
            }

            // Strip out non-media query params (drmScheme, drmLicense, Cookie) from the media stream URL
            var clean = urlToExamine
            clean = clean.replace(Regex("""(?i)[?&|]+(drmScheme|drmLicense|Cookie|Referer|Origin)=[^&]*"""), "")
            clean = clean.replace(Regex("""\?[|&]+$"""), "").replace(Regex("""[|&]+$"""), "")
            if (!clean.contains("?") && clean.contains("&")) {
                val idx = clean.indexOf("&")
                if (idx != -1) {
                    clean = clean.substring(0, idx) + "?" + clean.substring(idx + 1)
                }
            }
            url = clean
        }

        // Determine stream type
        val lowerUrl = url.lowercase()
        val detectedType = when {
            lowerUrl.endsWith(".mp4") || lowerUrl.contains(".mp4?") || lowerUrl.endsWith(".mkv") || lowerUrl.contains(".mkv?") || lowerUrl.contains("/videos/") -> "mp4"
            lowerUrl.endsWith(".mpd") || lowerUrl.contains(".mpd?") || lowerUrl.contains("/dash/") || lowerUrl.contains("cenc.mpd") -> "dash"
            lowerUrl.endsWith(".m3u8") || lowerUrl.contains(".m3u8?") || lowerUrl.contains("/hls/") || lowerUrl.contains("playlist.m3u8") -> "hls"
            lowerUrl.contains("<iframe") || lowerUrl.contains("pages.dev") -> "iframe"
            !extractedDrmLicense.isNullOrBlank() -> "dash"
            else -> "hls"
        }

        // Build DrmConfig if DRM license is found
        val drmConfig = if (!extractedDrmLicense.isNullOrBlank()) {
            val scheme = extractedDrmScheme ?: "clearkey"
            if (scheme.equals("clearkey", ignoreCase = true) || extractedDrmLicense.contains(":")) {
                val parts = extractedDrmLicense.split(":", ";", " ", "=").filter { it.isNotBlank() }
                if (parts.size >= 2) {
                    DrmConfig(type = "clearkey", keyId = parts[0].trim(), key = parts[1].trim(), licenseUrl = extractedDrmLicense)
                } else {
                    DrmConfig(type = "clearkey", keyId = extractedDrmLicense, key = extractedDrmLicense, licenseUrl = extractedDrmLicense)
                }
            } else {
                DrmConfig(type = "widevine", licenseUrl = extractedDrmLicense)
            }
        } else null

        return ParsedStreamInfo(
            cleanUrl = url,
            streamType = detectedType,
            cookie = extractedCookie,
            referer = extractedReferer,
            origin = extractedOrigin,
            drmConfig = drmConfig
        )
    }
}
