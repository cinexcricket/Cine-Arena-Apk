package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    const val DEFAULT_VERSION_ENDPOINT = "https://cinexcricket.com/api/version.php"

    /**
     * Queries the remote PHP version endpoint to check if an updated APK is available.
     */
    suspend fun checkForUpdate(
        context: Context,
        customApiUrl: String? = null
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val baseEndpoint = when {
                !customApiUrl.isNullOrBlank() && customApiUrl.startsWith("http") -> {
                    when {
                        customApiUrl.contains("chat.php") -> customApiUrl.replace("chat.php", "version.php")
                        customApiUrl.contains("notifications.php") -> customApiUrl.replace("notifications.php", "version.php")
                        else -> customApiUrl
                    }
                }
                else -> DEFAULT_VERSION_ENDPOINT
            }

            val currentVersionCode = BuildConfig.VERSION_CODE
            val separator = if (baseEndpoint.contains("?")) "&" else "?"
            val fullUrl = "$baseEndpoint${separator}current_code=$currentVersionCode&pkg=${context.packageName}&_t=${System.currentTimeMillis()}"

            Log.d(TAG, "Checking for updates from: $fullUrl (Current code: $currentVersionCode, Name: ${BuildConfig.VERSION_NAME})")

            val url = URL(fullUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; CineArena/${BuildConfig.VERSION_NAME})")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Update response: $responseText")
                if (responseText.isNotBlank()) {
                    val json = JSONObject(responseText)
                    val latestVersionCode = json.optInt("latest_version_code", 0)
                    val latestVersionName = json.optString("latest_version_name", "")
                    val downloadUrl = json.optString("download_url", "")
                    val releaseNotes = json.optString(
                        "release_notes",
                        "• Bug fixes & performance enhancements\n• Player and live stream updates"
                    )
                    val forceUpdate = json.optBoolean("force_update", false)
                    val minSupportedCode = json.optInt("min_version_code", 1)

                    val isUpdateNeeded = latestVersionCode > currentVersionCode ||
                            (forceUpdate && latestVersionCode > currentVersionCode) ||
                            (currentVersionCode < minSupportedCode)

                    if (isUpdateNeeded && downloadUrl.isNotBlank()) {
                        Log.i(TAG, "New update found! Remote v$latestVersionName (code $latestVersionCode) > Local v${BuildConfig.VERSION_NAME} (code $currentVersionCode)")
                        return@withContext AppUpdateInfo(
                            latestVersionCode = latestVersionCode,
                            latestVersionName = latestVersionName.ifBlank { "v$latestVersionCode" },
                            downloadUrl = downloadUrl,
                            releaseNotes = releaseNotes,
                            forceUpdate = forceUpdate || (currentVersionCode < minSupportedCode),
                            minSupportedVersionCode = minSupportedCode
                        )
                    } else {
                        Log.d(TAG, "App is up to date (current $currentVersionCode >= remote $latestVersionCode)")
                    }
                }
            } else {
                Log.w(TAG, "Update check failed with HTTP response code: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for app update: ${e.message}")
        }
        null
    }

    /**
     * Downloads the updated APK file from the remote URL with progress callbacks.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting APK download from: $downloadUrl")

            // Store in app-specific external files dir or internal cache dir
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.cacheDir, "updates").apply { mkdirs() }
            if (!targetDir.exists()) targetDir.mkdirs()

            val apkFile = File(targetDir, "CineArena_Update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val url = URL(downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; CineArena/${BuildConfig.VERSION_NAME})")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Server returned HTTP $responseCode while downloading APK"))
            }

            val totalLength = connection.contentLength.toLong()
            var downloadedLength = 0L

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedLength += bytesRead

                        val progress = if (totalLength > 0) {
                            (downloadedLength.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                        } else {
                            -1f // indeterminate
                        }
                        onProgress(progress, downloadedLength, totalLength)
                    }
                    output.flush()
                }
            }

            Log.i(TAG, "APK download completed successfully: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Prompts the Android system to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Log.e(TAG, "Cannot install: APK file does not exist or is empty")
                return false
            }

            // Check if unknown sources install permission is granted on Android 8+ (Oreo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.w(TAG, "Package installs permission not yet granted. Directing user to Settings...")
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permissionIntent)
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            Log.d(TAG, "Triggering install Intent with FileProvider Uri: $apkUri")

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch APK installation intent: ${e.message}", e)
            false
        }
    }
}
