package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.player.VideoResizeMode
import com.example.ui.theme.*

@Composable
fun SettingsDialog(
    selectedAspectRatio: VideoResizeMode,
    isAlwaysLandscape: Boolean,
    isDarkMode: Boolean,
    onSelectAspectRatio: (VideoResizeMode) -> Unit,
    onToggleAlwaysLandscape: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkMode) CineSurface else Color.White
            ),
            border = BorderStroke(
                1.dp,
                if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE2E8F0)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .background(CinePrimary.copy(alpha = 0.15f), CircleShape)
                                .border(1.5.dp, CinePrimary.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = CinePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                            )
                            Text(
                                text = "Player & Video Preferences",
                                fontSize = 12.sp,
                                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color(0xFFF1F5F9),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isDarkMode) Color.White else Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 1: Default Video Aspect Ratio
                Text(
                    text = "Default Video Aspect Ratio",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CinePrimary
                )
                Text(
                    text = "Applied automatically whenever any match, channel, or video opens:",
                    fontSize = 12.sp,
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                val aspectOptions = listOf(
                    AspectRatioOption(
                        mode = VideoResizeMode.FIT,
                        title = "Fit to Screen",
                        tag = "Fit",
                        description = "Fits video inside display, preserving original aspect ratio with black bars",
                        icon = Icons.Default.FitScreen
                    ),
                    AspectRatioOption(
                        mode = VideoResizeMode.FIXED_WIDTH,
                        title = "16:9 Standard",
                        tag = "16:9",
                        description = "Standard 16:9 widescreen format commonly used for TV broadcasts",
                        icon = Icons.Default.Tv
                    ),
                    AspectRatioOption(
                        mode = VideoResizeMode.FILL,
                        title = "Fill Screen",
                        tag = "Fill",
                        description = "Stretches the video to fill the whole display without black bars",
                        icon = Icons.Default.Fullscreen
                    ),
                    AspectRatioOption(
                        mode = VideoResizeMode.ZOOM,
                        title = "Zoom / Crop",
                        tag = "Zoom",
                        description = "Zooms in to fill the entire screen while cropping outer margins",
                        icon = Icons.Default.Crop
                    )
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    aspectOptions.forEach { option ->
                        val isSelected = selectedAspectRatio == option.mode
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                CinePrimary.copy(alpha = 0.15f)
                            } else {
                                if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color(0xFFF8FAFC)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) CinePrimary else (if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color(0xFFE2E8F0))
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectAspectRatio(option.mode) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (isSelected) CinePrimary.copy(alpha = 0.2f) else (if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.title,
                                        tint = if (isSelected) CinePrimary else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = option.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = if (isSelected) CinePrimary else (if (isDarkMode) Color.White else Color(0xFF0F172A))
                                        )
                                        Surface(
                                            color = if (isSelected) CinePrimary else (if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color(0xFFE2E8F0)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = option.tag,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = option.description,
                                        fontSize = 11.sp,
                                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectAspectRatio(option.mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = CinePrimary,
                                        unselectedColor = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(
                    color = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color(0xFFE2E8F0),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Always Play in Landscape Mode
                Text(
                    text = "Playback Orientation",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CinePrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isAlwaysLandscape) {
                        CinePrimary.copy(alpha = 0.12f)
                    } else {
                        if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color(0xFFF8FAFC)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isAlwaysLandscape) CinePrimary else (if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color(0xFFE2E8F0))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggleAlwaysLandscape(!isAlwaysLandscape) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isAlwaysLandscape) CinePrimary.copy(alpha = 0.2f) else (if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Always Landscape",
                                tint = if (isAlwaysLandscape) CinePrimary else (if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Always play in Landscape mode",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                                )
                            }
                            Text(
                                text = "Automatically open all match channels and videos in full landscape orientation (default is Off).",
                                fontSize = 11.sp,
                                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Switch(
                            checked = isAlwaysLandscape,
                            onCheckedChange = onToggleAlwaysLandscape,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CinePrimary,
                                uncheckedThumbColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                uncheckedTrackColor = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color(0xFFE2E8F0)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Done Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CinePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Done",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private data class AspectRatioOption(
    val mode: VideoResizeMode,
    val title: String,
    val tag: String,
    val description: String,
    val icon: ImageVector
)
