package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * IstTimeHelper formats and converts all live chat message timestamps to Indian Standard Time (IST).
 */
object IstTimeHelper {
    val istTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    /**
     * Formats current time into IST representation (e.g. "08:45 PM IST")
     */
    fun currentIstFormatted(): String {
        val sdf = SimpleDateFormat("hh:mm a 'IST'", Locale.ENGLISH).apply {
            timeZone = istTimeZone
        }
        return sdf.format(Date())
    }

    /**
     * Formats current time into IST representation without suffix (e.g. "08:45 PM")
     */
    fun currentIstTimeOnly(): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
            timeZone = istTimeZone
        }
        return sdf.format(Date())
    }

    /**
     * Converts raw date/timestamp (epoch millis, ISO string, or formatted time) into IST string with "IST".
     */
    fun formatToIst(rawTime: Any?): String {
        if (rawTime == null) return currentIstFormatted()
        val outSdf = SimpleDateFormat("hh:mm a 'IST'", Locale.ENGLISH).apply {
            timeZone = istTimeZone
        }

        return when (rawTime) {
            is Long -> {
                val millis = if (rawTime < 100000000000L) rawTime * 1000L else rawTime
                outSdf.format(Date(millis))
            }
            is Number -> {
                val num = rawTime.toLong()
                val millis = if (num < 100000000000L) num * 1000L else num
                outSdf.format(Date(millis))
            }
            is Date -> outSdf.format(rawTime)
            is String -> {
                val str = rawTime.trim()
                if (str.isBlank() || str.equals("Just now", ignoreCase = true) || str.equals("now", ignoreCase = true)) {
                    return currentIstFormatted()
                }

                // If already ends with IST, check if it has valid time format
                if (str.endsWith("IST", ignoreCase = true)) {
                    val withoutIst = str.substringBeforeLast("IST").trim()
                    if (withoutIst.contains("AM", ignoreCase = true) || withoutIst.contains("PM", ignoreCase = true)) {
                        return "$withoutIst IST"
                    }
                }

                val numeric = str.toLongOrNull()
                if (numeric != null) {
                    val millis = if (numeric < 100000000000L) numeric * 1000L else numeric
                    return outSdf.format(Date(millis))
                }

                val possibleFormats = listOf(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd",
                    "hh:mm:ss a",
                    "hh:mm a",
                    "h:mm a",
                    "HH:mm:ss",
                    "HH:mm"
                )

                for (fmt in possibleFormats) {
                    try {
                        val parser = SimpleDateFormat(fmt, Locale.ENGLISH).apply {
                            timeZone = if (fmt.contains("Z") || fmt.contains("X")) TimeZone.getTimeZone("UTC") else istTimeZone
                        }
                        val parsed = parser.parse(str)
                        if (parsed != null) {
                            return outSdf.format(parsed)
                        }
                    } catch (_: Exception) {}
                }

                if (str.contains("AM", ignoreCase = true) || str.contains("PM", ignoreCase = true)) {
                    val clean = str.replace(Regex("(?i)\\s*ist"), "").trim()
                    return "$clean IST"
                }

                // Fallback: return current IST formatted
                return currentIstFormatted()
            }
            else -> currentIstFormatted()
        }
    }
}
