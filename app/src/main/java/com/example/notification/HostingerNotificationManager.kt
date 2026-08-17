package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * HostingerNotificationManager handles fetching, polling, and presenting notifications
 * broadcasted directly from the Hostinger PHP backend (cinexcricket.com).
 */
object HostingerNotificationManager {

    private const val TAG = "HostingerNotify"
    const val CHANNEL_ID = "cine_arena_broadcast_alerts"
    const val CHANNEL_NAME = "Live Match & Announcements"
    private const val PREFS_NAME = "cine_arena_notifications"
    private const val KEY_LAST_NOTIF_ID = "last_received_notification_id"

    @Volatile
    var isAppInForeground: Boolean = false

    fun setAppForegroundState(inForeground: Boolean) {
        isAppInForeground = inForeground
        Log.d(TAG, "App foreground state updated: $inForeground")
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Live match start alerts, score updates, and breaking announcements"
                enableLights(true)
                lightColor = 0xFF00E5FF.toInt()
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Poll the Hostinger backend (cinexcricket.com/api/notifications.php) for any new notifications
     */
    suspend fun checkAndDisplayNewNotifications(context: Context, customApiUrl: String? = null) = withContext(Dispatchers.IO) {
        try {
            createNotificationChannel(context)

            val baseEndpoint = if (!customApiUrl.isNullOrBlank() && customApiUrl.startsWith("http")) {
                if (customApiUrl.contains("chat.php")) {
                    customApiUrl.replace("chat.php", "notifications.php")
                } else {
                    customApiUrl
                }
            } else {
                "https://cinexcricket.com/api/notifications.php"
            }

            val separator = if (baseEndpoint.contains("?")) "&" else "?"
            val endpointWithCacheBuster = "$baseEndpoint${separator}_t=${System.currentTimeMillis()}"

            val url = URL(endpointWithCacheBuster)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; CineArena/5.0)")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                if (responseText.isNotBlank()) {
                    parseAndShowNotification(context, responseText)
                }
            } else {
                Log.d(TAG, "Notification check returned HTTP status: ${conn.responseCode}")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.d(TAG, "Notification check skipped or unreachable: ${e.message}")
        }
    }

    private suspend fun parseAndShowNotification(context: Context, jsonStr: String) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(jsonStr)
            
            // Support both single object or array of notifications
            val notifObj = if (json.has("notification")) {
                json.getJSONObject("notification")
            } else if (json.has("notifications")) {
                val array = json.getJSONArray("notifications")
                if (array.length() > 0) array.getJSONObject(array.length() - 1) else null
            } else {
                json
            } ?: return@withContext

            val idRaw = notifObj.optString("id", notifObj.optString("notif_id", notifObj.optString("notificationId", "")))
            val title = notifObj.optString("title", notifObj.optString("header", notifObj.optString("subject", "Live Cricket Stream Alert")))
            val message = notifObj.optString("message", notifObj.optString("body", notifObj.optString("text", notifObj.optString("msg", "Match is live now! Tap to watch."))))
            val imageUrl = notifObj.optString("imageUrl", notifObj.optString("image_url", notifObj.optString("image", notifObj.optString("banner", ""))))
            val streamUrl = notifObj.optString("streamUrl", notifObj.optString("stream_url", notifObj.optString("url", notifObj.optString("link", ""))))

            val id = if (idRaw.isNotBlank()) idRaw else "${title.hashCode()}_${message.hashCode()}"

            if (title.isBlank() && message.isBlank()) return@withContext

            // Check if already displayed
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastId = prefs.getString(KEY_LAST_NOTIF_ID, "")
            if (id.isNotBlank() && id == lastId) {
                // Already shown or acknowledged
                return@withContext
            }

            // If the app is currently OPEN and active in the foreground, do NOT show status bar notification
            if (isAppInForeground) {
                Log.d(TAG, "App is open in foreground, saving notification ID $id without showing status bar notification")
                if (id.isNotBlank()) {
                    prefs.edit().putString(KEY_LAST_NOTIF_ID, id).apply()
                }
                return@withContext
            }

            // Download big picture bitmap if provided
            var bigPicture: Bitmap? = null
            if (imageUrl.isNotBlank() && imageUrl.startsWith("http")) {
                try {
                    val imgUrl = URL(imageUrl)
                    val imgConn = imgUrl.openConnection() as HttpURLConnection
                    imgConn.connectTimeout = 7000
                    imgConn.readTimeout = 7000
                    imgConn.doInput = true
                    imgConn.connect()
                    bigPicture = BitmapFactory.decodeStream(imgConn.inputStream)
                    imgConn.disconnect()
                } catch (e: Exception) {
                    Log.d(TAG, "Could not download notification image: ${e.message}")
                }
            }

            // Create open intent with video stream details
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (streamUrl.isNotBlank()) {
                    putExtra("target_stream_url", streamUrl)
                    putExtra("target_stream_title", title)
                    putExtra("target_stream_image", imageUrl)
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .setLights(0xFF00E5FF.toInt(), 1000, 1000)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            if (bigPicture != null) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bigPicture)
                        .setBigContentTitle(title)
                        .setSummaryText(message)
                )
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
            }

            try {
                val notificationManagerCompat = NotificationManagerCompat.from(context)
                val notifIntId = if (id.isNotBlank()) id.hashCode() else System.currentTimeMillis().toInt()
                notificationManagerCompat.notify(notifIntId, builder.build())
                
                if (id.isNotBlank()) {
                    prefs.edit().putString(KEY_LAST_NOTIF_ID, id).apply()
                }
                Log.d(TAG, "Successfully displayed notification: $title")
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission not granted by user: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing notification JSON", e)
        }
    }
}
