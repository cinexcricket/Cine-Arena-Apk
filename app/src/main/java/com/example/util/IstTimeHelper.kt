package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * IstTimeHelper formats and converts all live chat message timestamps to Indian Standard Time (IST).
 */
object IstTimeHelper {
    private val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")

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
     * Converts raw date/timestamp (epoch millis, ISO string, or formatted time) into IST string with "IST".
     */
    fun formatToIst(rawTime: Any?): String {
        if (rawTime == null) return currentIstFormatted()
        val outSdf = SimpleDateFormat("hh:mm a 'IST'", Locale.ENGLISH).apply {
            timeZone = istTimeZone
        }

        return when (rawTime) {
            is Long -> outSdf.format(Date(rawTime))
            is Date -> outSdf.format(rawTime)
            is String -> {
                val str = rawTime.trim()
                if (str.isBlank() || str.equals("Just now", ignoreCase = true)) {
                    return currentIstFormatted()
                }
                if (str.endsWith("IST", ignoreCase = true)) {
                    return str
                }
                val numeric = str.toLongOrNull()
                if (numeric != null) {
                    val millis = if (numeric < 100000000000L) numeric * 1000L else numeric
                    return outSdf.format(Date(millis))
                }

                val possibleFormats = listOf(
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd",
                    "hh:mm a",
                    "h:mm a",
                    "HH:mm:ss",
                    "HH:mm"
                )

                for (fmt in possibleFormats) {
                    try {
                        val parser = SimpleDateFormat(fmt, Locale.ENGLISH).apply {
                            timeZone = if (fmt.contains("Z")) TimeZone.getTimeZone("UTC") else TimeZone.getTimeZone("Asia/Kolkata")
                        }
                        val parsed = parser.parse(str)
                        if (parsed != null) {
                            return outSdf.format(parsed)
                        }
                    } catch (_: Exception) {}
                }

                if (str.contains("AM", ignoreCase = true) || str.contains("PM", ignoreCase = true)) {
                    return "$str IST"
                }
                return "$str IST"
            }
            else -> currentIstFormatted()
        }
    }
}
