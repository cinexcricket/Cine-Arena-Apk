package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.ui.theme.*
import com.example.update.AppUpdateInfo
import com.example.update.UpdateDownloadState
import java.util.Locale

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    downloadState: UpdateDownloadState,
    isDarkMode: Boolean,
    onStartDownload: () -> Unit,
    onInstallDownloadedApk: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentVersion = BuildConfig.VERSION_NAME
    val isForced = updateInfo.forceUpdate

    Dialog(
        onDismissRequest = {
            if (!isForced && downloadState !is UpdateDownloadState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isForced && downloadState !is UpdateDownloadState.Downloading,
            dismissOnClickOutside = !isForced && downloadState !is UpdateDownloadState.Downloading
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkMode) CineSurface else Color.White
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE2E8F0)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .background(CinePrimary.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, CinePrimary.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (downloadState is UpdateDownloadState.ReadyToInstall) {
                            Icons.Default.CloudDownload
                        } else {
                            Icons.Default.SystemUpdate
                        },
                        contentDescription = "Update Icon",
                        tint = CinePrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = if (isForced) "Mandatory Update Required" else "New Update Available!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Version Comparison Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Current: v$currentVersion",
                            fontSize = 12.sp,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = " ➜ ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CinePrimary
                    )

                    Surface(
                        color = CinePrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "v${updateInfo.latestVersionName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CinePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Release Notes
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = "What's New:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )

                    Surface(
                        color = if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Download Progress / Status State
                when (downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (downloadState.progress >= 0f) {
                                LinearProgressIndicator(
                                    progress = { downloadState.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = CinePrimary,
                                    trackColor = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color(0xFFE2E8F0)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val percent = (downloadState.progress * 100).toInt()
                                val mbDownloaded = downloadState.downloadedBytes / (1024f * 1024f)
                                val mbTotal = downloadState.totalBytes / (1024f * 1024f)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Downloading...",
                                        fontSize = 12.sp,
                                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                                    )
                                    Text(
                                        text = if (mbTotal > 0) {
                                            String.format(Locale.US, "%d%% (%.1f / %.1f MB)", percent, mbDownloaded, mbTotal)
                                        } else {
                                            "$percent%"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CinePrimary
                                    )
                                }
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = CinePrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Downloading update package...",
                                    fontSize = 12.sp,
                                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                    is UpdateDownloadState.ReadyToInstall -> {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✓ Update downloaded! Tap Install Now to complete update.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is UpdateDownloadState.Error -> {
                        Surface(
                            color = CineLiveRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CineLiveRed.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = CineLiveRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = downloadState.message,
                                    fontSize = 12.sp,
                                    color = CineLiveRed,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    is UpdateDownloadState.Idle -> {
                        // Empty / default state
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (downloadState) {
                        is UpdateDownloadState.ReadyToInstall -> {
                            Button(
                                onClick = onInstallDownloadedApk,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Install Now",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        }
                        is UpdateDownloadState.Downloading -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Downloading...", fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            Button(
                                onClick = onStartDownload,
                                colors = ButtonDefaults.buttonColors(containerColor = CinePrimary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (downloadState is UpdateDownloadState.Error) "Retry Download" else "Update Now",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Fallback / Browser Download Button
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Download via Browser",
                            fontSize = 13.sp,
                            color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                        )
                    }

                    // Dismiss / Later button (only if NOT force update and not actively downloading)
                    if (!isForced && downloadState !is UpdateDownloadState.Downloading) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Text(
                                text = "Later",
                                fontSize = 14.sp,
                                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }
    }
}
