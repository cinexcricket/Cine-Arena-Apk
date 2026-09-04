package com.example.update

import android.net.Uri

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 1
)

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(
        val progress: Float, // 0.0f to 1.0f
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L
    ) : UpdateDownloadState()
    data class ReadyToInstall(val apkUri: Uri, val localFile: java.io.File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}
