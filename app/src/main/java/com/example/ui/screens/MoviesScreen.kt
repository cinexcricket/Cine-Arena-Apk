package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import com.example.data.FavoriteEntity
import com.example.model.MatchItem
import com.example.ui.UiState
import com.example.ui.components.cineSharedBounds
import com.example.ui.components.cineSharedElement
import com.example.ui.components.dpadFocusable
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    moviesState: UiState<List<MatchItem>>,
    favorites: List<FavoriteEntity>,
    selectedCategory: String,
    searchQuery: String,
    activeMatchId: String? = null,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMovieClick: (MatchItem) -> Unit,
    onToggleFavorite: (MatchItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val movieGridColumns = when {
        screenWidth >= 1000 -> 8
        screenWidth >= 600 -> 4
        else -> 2
    }

    val categories = remember(moviesState) {
        if (moviesState is UiState.Success) {
            val fetchedCategories = moviesState.data
                .flatMap { it.parsedCategories }
                .filter { it.isNotBlank() }
                .distinct()
            listOf("All Movies") + fetchedCategories
        } else {
            listOf("All Movies")
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
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = CinePrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Movies (Requested)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = CineTextPrimary
                )
            }

            Surface(
                color = CinePrimaryContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CinePrimary.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CinePrimary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "VOD",
                        color = CinePrimary,
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
                            text = "Search movies & web series...",
                            color = CineTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = CineTextPrimary,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = CineTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (moviesState) {
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
                            text = moviesState.message,
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
                val filteredMovies = remember(moviesState.data, selectedCategory, searchQuery) {
                    moviesState.data.filter { movie ->
                        val matchesCategory = if (selectedCategory == "All Movies" || selectedCategory == "All") {
                            true
                        } else {
                            movie.parsedCategories.any { it.equals(selectedCategory, ignoreCase = true) }
                        }

                        val matchesSearch = if (searchQuery.isBlank()) {
                            true
                        } else {
                            movie.title.contains(searchQuery, ignoreCase = true) ||
                                    movie.parsedCategories.any { it.contains(searchQuery, ignoreCase = true) }
                        }

                        matchesCategory && matchesSearch
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(movieGridColumns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Category Chips Span Full Width
                    item(span = { GridItemSpan(movieGridColumns) }, key = "movie_category_chips") {
                        LazyRow(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
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
                    }

                    if (filteredMovies.isEmpty()) {
                        item(span = { GridItemSpan(movieGridColumns) }, key = "empty_movies_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No movies or requested content found", color = CineTextSecondary)
                            }
                        }
                    } else {
                        items(
                            items = filteredMovies,
                            key = { it.id },
                            contentType = { "movie_card" }
                        ) { movie ->
                            val isFavorite = favorites.any { it.id == movie.id }
                            val isCurrentPlaying = activeMatchId == movie.id

                            MovieCard(
                                movie = movie,
                                isFavorite = isFavorite,
                                isPlaying = isCurrentPlaying,
                                onClick = { onMovieClick(movie) },
                                onToggleFavorite = { onToggleFavorite(movie) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

private val MovieCardBackgroundGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
)
private val MovieCardOverlayGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.45f),
        Color.Transparent,
        Color.Black.copy(alpha = 0.7f)
    )
)

@Composable
fun MovieCard(
    movie: MatchItem,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val imageUrl = remember(movie.poster, movie.teamA?.logo) {
        movie.poster ?: movie.teamA?.logo
    }
    val categoryUpper = remember(movie.firstCategory) {
        movie.firstCategory.uppercase()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) CinePrimaryContainer.copy(alpha = 0.4f) else CineSurface
        ),
        border = BorderStroke(
            width = if (isPlaying) 1.5.dp else 1.dp,
            color = if (isPlaying) CinePrimary else CineOutline
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .dpadFocusable(
                shape = RoundedCornerShape(16.dp),
                focusedBorderColor = CinePrimary,
                focusedBorderWidth = 3.dp,
                scaleOnFocus = 1.05f,
                elevationOnFocus = 8.dp
            )
            .cineSharedBounds("card_${movie.id}")
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Poster as Background Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f) // Standard movie/series poster aspect ratio
                    .cineSharedElement("poster_${movie.id}")
                    .background(MovieCardBackgroundGradient)
            ) {
                if (!imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Gradient overlays for text/badge readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MovieCardOverlayGradient)
                )

                // Category Badge on Top Left on Background Image
                Surface(
                    color = CinePrimary,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = categoryUpper,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Favorite Option Badge on Top Right
                Surface(
                    onClick = onToggleFavorite,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(
                        0.5.dp,
                        if (isFavorite) CineLiveRed.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .padding(8.dp)
                        .size(30.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) CineLiveRed else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Center Play Button Overlay
                Surface(
                    shape = CircleShape,
                    color = if (isPlaying) CinePrimary else Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, if (isPlaying) Color.White else CinePrimary),
                    modifier = Modifier
                        .size(38.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = if (isPlaying) Color.White else CinePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Below Image: Movie Title (Full Title)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(
                    text = movie.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CineTextPrimary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
