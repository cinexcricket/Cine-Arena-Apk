package com.example.player

import android.content.Context
import android.util.Base64
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import com.example.model.DrmConfig

object ClearKeyUtil {

    private fun isHex(s: String): Boolean {
        val clean = s.replace(" ", "").replace("-", "").replace(":", "").trim()
        return clean.isNotEmpty() && clean.length % 2 == 0 && clean.all { it in "0123456789abcdefABCDEF" }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("-", "").replace(":", "").trim()
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) + Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.encodeToString(
            bytes,
            Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE
        )
    }

    fun buildClearKeyDrmCallback(drmConfig: DrmConfig?): LocalMediaDrmCallback? {
        if (drmConfig == null) return null

        var keyIdStr: String? = drmConfig.keyId
        var keyStr: String? = drmConfig.key

        // Parse keyId:key if combined in keyId
        if (keyIdStr != null && keyIdStr.contains(":") && keyStr.isNullOrBlank()) {
            val parts = keyIdStr.split(":")
            if (parts.size >= 2) {
                keyIdStr = parts[0]
                keyStr = parts[1]
            }
        }

        // Parse keyId:key if passed in licenseUrl
        if ((keyIdStr.isNullOrBlank() || keyStr.isNullOrBlank()) && !drmConfig.licenseUrl.isNullOrBlank()) {
            val licenseStr = drmConfig.licenseUrl.trim()
            if (!licenseStr.startsWith("http", ignoreCase = true) && licenseStr.contains(":")) {
                val parts = licenseStr.split(":")
                if (parts.size >= 2) {
                    keyIdStr = parts[0]
                    keyStr = parts[1]
                }
            }
        }

        if (keyIdStr.isNullOrBlank() || keyStr.isNullOrBlank()) return null

        return try {
            val kidB64 = if (isHex(keyIdStr)) base64UrlEncode(hexToBytes(keyIdStr)) else keyIdStr.trim()
            val kB64 = if (isHex(keyStr)) base64UrlEncode(hexToBytes(keyStr)) else keyStr.trim()

            val jsonKey = """{"keys":[{"kty":"oct","kid":"$kidB64","k":"$kB64"}],"type":"temporary"}"""
            LocalMediaDrmCallback(jsonKey.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createDataSourceFactory(
        context: Context? = null,
        cookie: String? = null,
        referer: String? = null,
        origin: String? = null,
        mediaUrl: String? = null
    ): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(4000)
            .setReadTimeoutMs(6000)
            .setKeepPostFor302Redirects(true)

        val headers = mutableMapOf<String, String>()
        headers["Accept"] = "*/*"
        headers["Accept-Language"] = "en-US,en;q=0.9"

        if (!cookie.isNullOrBlank()) headers["Cookie"] = cookie.trim()

        val effectiveReferer = if (!referer.isNullOrBlank()) referer.trim() else {
            mediaUrl?.let { url ->
                try {
                    val uri = android.net.Uri.parse(url)
                    if (!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
                        "${uri.scheme}://${uri.host}/"
                    } else null
                } catch (e: Exception) { null }
            }
        }
        if (!effectiveReferer.isNullOrBlank()) headers["Referer"] = effectiveReferer

        val effectiveOrigin = if (!origin.isNullOrBlank()) origin.trim() else {
            mediaUrl?.let { url ->
                try {
                    val uri = android.net.Uri.parse(url)
                    if (!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
                        "${uri.scheme}://${uri.host}"
                    } else null
                } catch (e: Exception) { null }
            }
        }
        if (!effectiveOrigin.isNullOrBlank()) headers["Origin"] = effectiveOrigin

        if (headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(headers)
        }

        return if (context != null) {
            DefaultDataSource.Factory(context, httpFactory)
        } else {
            httpFactory
        }
    }
}
