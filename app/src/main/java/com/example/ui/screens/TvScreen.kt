package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.FavoriteEntity
import com.example.model.ChannelItem
import com.example.ui.UiState
import com.example.ui.components.cineSharedBounds
import com.example.ui.components.cineSharedElement
import com.example.ui.components.dpadFocusable
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvScreen(
    tvChannelsState: UiState<List<ChannelItem>>,
    favorites: List<FavoriteEntity>,
    selectedCategory: String,
    searchQuery: String,
    activeChannel: ChannelItem? = null,
    playerContent: (@Composable () -> Unit)? = null,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (ChannelItem) -> Unit,
    onToggleFavorite: (ChannelItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val categories = remember(tvChannelsState) {
        if (tvChannelsState is UiState.Success) {
            val fetchedCategories = tvChannelsState.data
                .flatMap { it.parsedCategories }
                .filter { it.isNotBlank() }
                .distinct()
            listOf("All Channels") + fetchedCategories
        } else {
            listOf("All Channels")
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
            // Video Player Section at Top of Screen (if video active)
            if (playerContent != null) {
                val currentItemId = activeChannel?.id ?: "active_tv_stream"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .cineSharedBounds("card_$currentItemId")
                ) {
                    playerContent()
                }
            }

            // TV Channels Title Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        tint = CinePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (playerContent != null) "More TV Channels" else "TV Channels",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
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

            // Search Input Bar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CineSurface,
                border = BorderStroke(1.dp, CineOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = CineTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search TV channels...",
                                color = CineTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 13.sp,
                                color = CineTextPrimary
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(CinePrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = CineTextSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onSearchQueryChange("") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
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
                        modifier = Modifier
                            .height(36.dp)
                            .dpadFocusable(
                                shape = RoundedCornerShape(20.dp),
                                focusedBorderColor = Color.White,
                                scaleOnFocus = 1.06f
                            )
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

        // TV Channel 2-Column Grid
        when (tvChannelsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CinePrimary)
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = tvChannelsState.message, color = CineLiveRed)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = CinePrimary)
                        ) { Text("Retry") }
                    }
                }
            }
            is UiState.Success -> {
                val filteredChannels = remember(tvChannelsState.data, selectedCategory, searchQuery) {
                    tvChannelsState.data.filter { channel ->
                        val matchesCategory = if (selectedCategory == "All Channels" || selectedCategory == "All") true
                        else channel.parsedCategories.any { it.equals(selectedCategory, ignoreCase = true) }

                        val matchesSearch = if (searchQuery.isBlank()) true
                        else channel.name.contains(searchQuery, ignoreCase = true) || channel.parsedCategories.any { it.contains(searchQuery, ignoreCase = true) }

                        matchesCategory && matchesSearch
                    }
                }

                if (filteredChannels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No TV channels found", color = CineTextSecondary)
                    }
                } else {
                    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                    val gridColumns = if (screenWidth >= 800) 6 else if (screenWidth >= 500) 4 else 2

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredChannels,
                            key = { it.id },
                            contentType = { "tv_channel" }
                        ) { channel ->
                            val isFavorite = favorites.any { it.id == channel.id }
                            TvChannelCard(
                                channel = channel,
                                isFavorite = isFavorite,
                                onChannelClick = { onChannelClick(channel) },
                                onToggleFavorite = { onToggleFavorite(channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun TvChannelCard(
    channel: ChannelItem,
    isFavorite: Boolean,
    onChannelClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val initialLetters = remember(channel.name) {
        channel.name.take(2).uppercase()
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CineSurface),
        border = BorderStroke(1.dp, CineOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                shape = RoundedCornerShape(20.dp),
                focusedBorderColor = CinePrimary,
                focusedBorderWidth = 3.dp,
                scaleOnFocus = 1.05f,
                elevationOnFocus = 8.dp
            )
            .cineSharedBounds("card_${channel.id}")
            .clickable(onClick = onChannelClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Top Logo & Favorite Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Channel Logo Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, CineOutline),
                    modifier = Modifier
                        .size(56.dp)
                        .cineSharedElement("poster_${channel.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!channel.logo.isNullOrEmpty()) {
                            AsyncImage(
                                model = channel.logo,
                                contentDescription = channel.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )
                        } else {
                            Text(
                                text = initialLetters,
                                fontWeight = FontWeight.Bold,
                                color = CinePrimary,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // Heart Favorite Icon Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(32.dp)
                        .background(CineSurfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) CineLiveRed else CineTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Channel Title
            Text(
                text = channel.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CineTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Category & LIVE Status Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = CineSurfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = channel.firstCategory,
                        color = CineTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // ● LIVE tag
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CineLiveRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "LIVE",
                        color = CineLiveRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
