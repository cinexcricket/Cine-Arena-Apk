package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
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
import com.example.data.FavoriteEntity
import com.example.model.MatchItem
import com.example.ui.UiState
import com.example.ui.components.cineSharedBounds
import com.example.ui.components.cineSharedElement
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

private fun formatShortCode(code: String?, fallbackName: String): String {
    val text = code?.takeIf { it.isNotBlank() } ?: fallbackName
    return if (text.length > 5) {
        text.take(5) + "..."
    } else {
        text
    }
}

private fun formatToIST(startTime: String?): String {
    if (startTime.isNullOrBlank()) return "Scheduled IST"
    val trimmed = startTime.trim()
    if (trimmed.contains("IST", ignoreCase = true)) return trimmed

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val istZone = java.time.ZoneId.of("Asia/Kolkata")
            val outputFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a 'IST'", java.util.Locale.ENGLISH)

            try {
                val instant = java.time.Instant.parse(trimmed)
                return instant.atZone(istZone).format(outputFormatter)
            } catch (e: Exception) { /* ignore */ }

            try {
                val ldt = java.time.LocalDateTime.parse(trimmed)
                return ldt.atZone(java.time.ZoneId.systemDefault()).withZoneSameInstant(istZone).format(outputFormatter)
            } catch (e: Exception) { /* ignore */ }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return "$trimmed IST"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeMatchesState: UiState<List<MatchItem>>,
    favorites: List<FavoriteEntity>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onMatchClick: (MatchItem) -> Unit,
    onToggleFavorite: (MatchItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val columns = if (screenWidth >= 800) 4 else if (screenWidth >= 500) 2 else 1

    val categories = remember(homeMatchesState) {
        if (homeMatchesState is UiState.Success) {
            val fetchedCategories = homeMatchesState.data
                .flatMap { it.parsedCategories }
                .filter { it.isNotBlank() }
                .distinct()
            listOf("All Sports") + fetchedCategories
        } else {
            listOf("All Sports")
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(CineBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            when (homeMatchesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CinePrimary)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = homeMatchesState.message,
                                color = CineLiveRed,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = CinePrimary)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    val matches = remember(homeMatchesState.data, selectedCategory) {
                        homeMatchesState.data.filter { match ->
                            if (selectedCategory == "All Sports" || selectedCategory == "All") true
                            else match.parsedCategories.any { it.equals(selectedCategory, ignoreCase = true) }
                        }
                    }

                    val matchRows = remember(matches, columns) { matches.chunked(columns) }

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "featured_matches_title") {
                            // Featured Live Matches Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = CinePrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Featured Live Matches",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = CineTextPrimary
                                    )
                                }

                                Surface(
                                    color = CineLiveRedBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CineLiveRed)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(CineLiveRed, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "LIVE",
                                            color = CineLiveRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "category_chips_row") {
                            // Horizontal Category Pills
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                items(
                                    items = categories,
                                    key = { it },
                                    contentType = { "category_pill" }
                                ) { cat ->
                                    val isSelected = (cat == selectedCategory)
                                    Surface(
                                        onClick = { onCategorySelected(cat) },
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) CinePrimary else CineSurfaceVariant,
                                        border = if (!isSelected) BorderStroke(1.dp, CineOutline) else null,
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        ) {
                                            Text(
                                                text = cat,
                                                color = if (isSelected) Color.White else CineTextSecondary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (matches.isEmpty()) {
                            item(key = "empty_matches_view") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No live matches available", color = CineTextSecondary)
                                }
                            }
                        } else {
                            items(
                                items = matchRows,
                                key = { row -> row.joinToString(separator = "_") { it.id } },
                                contentType = { "match_row" }
                            ) { matchRow ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    for (match in matchRow) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            val isFavorite = favorites.any { it.id == match.id }
                                            MatchCard(
                                                match = match,
                                                isFavorite = isFavorite,
                                                onWatchClick = {
                                                    val rawStatus = match.status.trim().uppercase()
                                                    val statusText = if (rawStatus.isEmpty()) "LIVE" else rawStatus
                                                    val isLiveOrStreaming = statusText == "LIVE" || statusText == "FC LIVE" || statusText == "STREAMING" || statusText.contains("LIVE") || statusText.contains("STREAM")
                                                    if (isLiveOrStreaming) {
                                                        onMatchClick(match)
                                                    }
                                                },
                                                onToggleFavorite = { onToggleFavorite(match) }
                                            )
                                        }
                                    }
                                    if (matchRow.size < columns) {
                                        repeat(columns - matchRow.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchCard(
    match: MatchItem,
    isFavorite: Boolean,
    onWatchClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val rawStatus = match.status.trim().uppercase()
    val statusText = if (rawStatus.isEmpty()) "LIVE" else rawStatus
    val isLiveOrStreaming = statusText == "LIVE" || statusText == "FC LIVE" || statusText == "STREAMING" || statusText.contains("LIVE") || statusText.contains("STREAM")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CineSurface),
        border = BorderStroke(1.dp, CineOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .cineSharedBounds("card_${match.id}")
            .then(
                if (isLiveOrStreaming) {
                    Modifier.clickable(onClick = onWatchClick)
                } else {
                    Modifier
                }
            )
    ) {
        Column {
            // Visual Banner Image / Poster with Team A vs Team B Overlay on Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .cineSharedElement("poster_${match.id}")
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E1B4B), Color(0xFF312E81))
                        )
                    )
            ) {
                if (!match.poster.isNullOrEmpty()) {
                    AsyncImage(
                        model = match.poster,
                        contentDescription = match.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Gradient dim overlay for image text legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Sport Tag Top Left
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = match.firstCategory.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Status Badge Top Right (LIVE / STREAMING -> Red background with white text; UPCOMING -> same background as sport)
                Surface(
                    color = if (isLiveOrStreaming) CineLiveRed else Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    border = if (isLiveOrStreaming) null else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (isLiveOrStreaming) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = statusText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Team A vs Team B Display on Bottom Side of Background Image
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Team A (Logo in circle with thin theme border + Name)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.5.dp, CinePrimary), // thin border matching app theme
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                if (!match.teamA?.logo.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = match.teamA?.logo,
                                        contentDescription = match.teamA?.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = match.teamA?.shortCode?.take(3)?.uppercase() ?: match.teamA?.name?.take(3)?.uppercase() ?: "T1",
                                        color = CinePrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatShortCode(match.teamA?.shortCode, match.teamA?.name ?: "Team A"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    // VS Pill Badge
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, CinePrimary),
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "VS",
                                color = CinePrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Team B (Name + Logo in circle with thin theme border)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = formatShortCode(match.teamB?.shortCode, match.teamB?.name ?: "Team B"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.5.dp, CinePrimary), // thin border matching app theme
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                if (!match.teamB?.logo.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = match.teamB?.logo,
                                        contentDescription = match.teamB?.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = match.teamB?.shortCode?.take(3)?.uppercase() ?: match.teamB?.name?.take(3)?.uppercase() ?: "T2",
                                        color = CinePrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Side of Card: Title, Subtitle, and Watch Button in same Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = match.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CineTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${match.subtitle.ifEmpty { "Live Stream" }} • ${match.channels.size} Streams",
                        fontSize = 12.sp,
                        color = CineTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) CineLiveRed else CineTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                if (isLiveOrStreaming) {
                    Button(
                        onClick = onWatchClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CinePrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Watch",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Watch Stream",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                } else {
                    Surface(
                        color = CinePrimaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = "Scheduled",
                                modifier = Modifier.size(16.dp),
                                tint = CinePrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatToIST(match.startTime),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = CineOnPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
