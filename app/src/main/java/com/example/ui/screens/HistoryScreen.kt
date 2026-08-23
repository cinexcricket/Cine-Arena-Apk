package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ContinueWatchingEntity
import com.example.ui.components.cineSharedBounds
import com.example.ui.components.cineSharedElement
import com.example.ui.components.dpadFocusable
import com.example.ui.theme.*

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Recently"
    val diff = System.currentTimeMillis() - timestampMs
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> {
            val date = java.util.Date(timestampMs)
            java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(date)
        }
    }
}

@Composable
fun HistoryScreen(
    historyList: List<ContinueWatchingEntity>,
    onPlayItem: (ContinueWatchingEntity) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val continueWatchingItems = remember(historyList) {
        historyList.filter { it.positionMs > 0L }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = CineLiveRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Watch History?")
                }
            },
            text = {
                Text(
                    "Are you sure you want to remove all watched items and continue watching progress?",
                    color = CineTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAll()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CineLiveRed)
                ) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = CineTextSecondary)
                }
            },
            containerColor = CineSurface,
            tonalElevation = 6.dp
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CineBackground)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = CinePrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Watch History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = CineTextPrimary
                )
            }

            if (historyList.isNotEmpty()) {
                IconButton(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(CineSurfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear All History",
                        tint = CineLiveRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = CineSurfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = CinePrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Watch History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = CineTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Channels, matches, and movies you watch will appear here with your saved playback progress.",
                        color = CineTextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Section 1: Top Continue Watching Carousel
                if (continueWatchingItems.isNotEmpty()) {
                    item(key = "header_continue_watching") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleOutline,
                                        contentDescription = null,
                                        tint = CinePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Continue Watching",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = CineTextPrimary
                                    )
                                }

                                Surface(
                                    color = CineSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CineOutline.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "${continueWatchingItems.size}",
                                        color = CineTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    items = continueWatchingItems,
                                    key = { "cw_${it.id}" },
                                    contentType = { "continue_watching_card" }
                                ) { item ->
                                    HistoryContinueWatchingCard(
                                        item = item,
                                        onClick = { onPlayItem(item) },
                                        onRemove = { onRemoveItem(item.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: History List Header
                item(key = "header_history_list") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "History (${historyList.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CineTextPrimary
                        )

                        Text(
                            text = "Tap to resume",
                            color = CineTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Section 3: History List Items (Vertical List)
                items(
                    items = historyList,
                    key = { "hist_${it.id}_${it.lastWatchedTimestamp}" },
                    contentType = { "history_item" }
                ) { item ->
                    HistoryListItem(
                        item = item,
                        onClick = { onPlayItem(item) },
                        onRemove = { onRemoveItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryContinueWatchingCard(
    item: ContinueWatchingEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFraction = remember(item.positionMs, item.durationMs) {
        if (item.durationMs > 0L) {
            (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0.05f, 1f)
        } else {
            0.5f
        }
    }

    val progressText = remember(item.positionMs, item.durationMs) {
        if (item.durationMs > 0L) {
            val remainingMs = (item.durationMs - item.positionMs).coerceAtLeast(0L)
            "${formatPlaybackTime(remainingMs)} left"
        } else {
            formatPlaybackTime(item.positionMs)
        }
    }

    val imageUrl = remember(item.poster, item.background) {
        item.poster.ifBlank { item.background }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = CineSurface,
        border = BorderStroke(1.dp, CineOutline),
        tonalElevation = 2.dp,
        modifier = modifier
            .width(200.dp)
            .height(140.dp)
            .dpadFocusable(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = CinePrimary,
                focusedBorderWidth = 2.5.dp,
                scaleOnFocus = 1.05f
            )
            .cineSharedBounds("card_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .background(Color.Black)
            ) {
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .cineSharedElement("poster_${item.id}")
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(CinePrimaryContainer, CineSurfaceVariant)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = CineTextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Dark overlay gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                // Category pill
                if (item.category.isNotBlank()) {
                    Surface(
                        color = CinePrimary.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            text = item.category.uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Remove X button
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Center Play Button
                Surface(
                    shape = CircleShape,
                    color = CinePrimary.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Progress Bar at Bottom of Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .background(CinePrimary)
                    )
                }
            }

            // Info section below image
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    color = CineTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = progressText,
                    color = CinePrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun HistoryListItem(
    item: ContinueWatchingEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFraction = remember(item.positionMs, item.durationMs) {
        if (item.durationMs > 0L) {
            (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    val progressInfo = remember(item.positionMs, item.durationMs) {
        if (item.durationMs > 0L) {
            "${formatPlaybackTime(item.positionMs)} / ${formatPlaybackTime(item.durationMs)}"
        } else if (item.positionMs > 0L) {
            formatPlaybackTime(item.positionMs)
        } else {
            "Stream"
        }
    }

    val relativeTime = remember(item.lastWatchedTimestamp) {
        formatRelativeTime(item.lastWatchedTimestamp)
    }

    val imageUrl = remember(item.poster, item.background) {
        item.poster.ifBlank { item.background }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = CineSurface,
        border = BorderStroke(1.dp, CineOutline),
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .dpadFocusable(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = CinePrimary,
                focusedBorderWidth = 2.5.dp,
                scaleOnFocus = 1.03f
            )
            .cineSharedBounds("card_hist_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with progress
            Box(
                modifier = Modifier
                    .width(105.dp)
                    .height(65.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .cineSharedElement("poster_hist_${item.id}")
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(CinePrimaryContainer, CineSurfaceVariant)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = CineTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Semi-dark gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )

                // Play icon overlay
                Surface(
                    shape = CircleShape,
                    color = CinePrimary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(26.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Bottom progress line if watched part of it
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .background(CinePrimary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    color = CineTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.category.isNotBlank()) {
                        Surface(
                            color = CinePrimaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = item.category.uppercase(),
                                color = CineOnPrimaryContainer,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = "•  $relativeTime",
                        color = CineTextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = progressInfo,
                    color = CinePrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Delete item button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete from history",
                    tint = CineTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
