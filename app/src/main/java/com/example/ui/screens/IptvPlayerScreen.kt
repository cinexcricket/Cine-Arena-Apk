package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FavoriteEntity
import com.example.model.ChannelItem
import com.example.ui.UiState
import com.example.ui.components.dpadFocusable
import com.example.ui.theme.*

@Composable
fun IptvPlayerScreen(
    channelsState: UiState<List<ChannelItem>>,
    favorites: List<FavoriteEntity>,
    selectedCategory: String,
    searchQuery: String,
    categories: List<String>,
    totalChannelsCount: Int,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    activeChannel: ChannelItem?,
    playerContent: (@Composable () -> Unit)?,
    currentPlaylistInput: String,
    isLoading: Boolean,
    onLoadPlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (ChannelItem) -> Unit,
    onToggleFavorite: (ChannelItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputUrlOrContent by remember(currentPlaylistInput) { mutableStateOf(currentPlaylistInput) }
    var isInputExpanded by remember { mutableStateOf(currentPlaylistInput.isBlank()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = CineLiveRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Playlist?",
                        fontWeight = FontWeight.Bold,
                        color = CineTextPrimary
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to remove this IPTV playlist and all its channels? You can add another playlist anytime.",
                    color = CineTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        inputUrlOrContent = ""
                        onDeletePlaylist()
                        Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CineLiveRed)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = false },
                    border = BorderStroke(1.dp, CineOutline)
                ) {
                    Text("Cancel", color = CineTextPrimary)
                }
            },
            containerColor = CineSurfaceVariant,
            shape = RoundedCornerShape(16.dp)
        )
    }

    fun pasteClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        if (!clip.isNullOrBlank()) {
            inputUrlOrContent = clip
            Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    TvScreen(
        tvChannelsState = channelsState,
        favorites = favorites,
        selectedCategory = selectedCategory,
        searchQuery = searchQuery,
        screenTitle = "IPTV Player",
        screenIcon = Icons.Default.PlaylistPlay,
        providedCategories = categories,
        totalChannelsCount = if (totalChannelsCount > 0) totalChannelsCount else null,
        hasMore = hasMore,
        onLoadMore = onLoadMore,
        activeChannel = activeChannel,
        playerContent = playerContent,
        onCategorySelected = onCategorySelected,
        onSearchQueryChange = onSearchQueryChange,
        onChannelClick = onChannelClick,
        onToggleFavorite = onToggleFavorite,
        onRefresh = {
            if (inputUrlOrContent.isNotBlank()) {
                onLoadPlaylist(inputUrlOrContent)
            }
        },
        isRefreshing = isLoading,
        topContentComposable = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Surface(
                    color = CineSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CineOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isInputExpanded = !isInputExpanded }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = CinePrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlaylistAdd,
                                            contentDescription = null,
                                            tint = CinePrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Custom M3U Playlist URL or Raw Text",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = CineTextPrimary
                                    )
                                    Text(
                                        text = if (inputUrlOrContent.isNotBlank()) "Playlist configured (${totalChannelsCount} channels)" else "Paste playlist URL or raw M3U text",
                                        fontSize = 11.sp,
                                        color = CineTextSecondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (currentPlaylistInput.isNotBlank() || totalChannelsCount > 0) {
                                    IconButton(
                                        onClick = { showDeleteConfirmDialog = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Playlist",
                                            tint = CineLiveRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                IconButton(
                                    onClick = { isInputExpanded = !isInputExpanded },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isInputExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Toggle Input",
                                        tint = CineTextSecondary
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = isInputExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                OutlinedTextField(
                                    value = inputUrlOrContent,
                                    onValueChange = { inputUrlOrContent = it },
                                    placeholder = {
                                        Text(
                                            "https://example.com/playlist.m3u or paste raw #EXTM3U text",
                                            fontSize = 12.sp,
                                            color = CineTextSecondary
                                        )
                                    },
                                    maxLines = 4,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = CineTextPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CinePrimary,
                                        unfocusedBorderColor = CineOutline,
                                        focusedContainerColor = CineSurface,
                                        unfocusedContainerColor = CineSurface
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { pasteClipboard() },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CinePrimary),
                                        border = BorderStroke(1.dp, CinePrimary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .dpadFocusable(RoundedCornerShape(10.dp), Color.White)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Paste", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (currentPlaylistInput.isNotBlank() || totalChannelsCount > 0) {
                                        OutlinedButton(
                                            onClick = { showDeleteConfirmDialog = true },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CineLiveRed),
                                            border = BorderStroke(1.dp, CineLiveRed),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.dpadFocusable(RoundedCornerShape(10.dp), Color.White)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else if (inputUrlOrContent.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { inputUrlOrContent = "" },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CineTextSecondary),
                                            border = BorderStroke(1.dp, CineOutline),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.dpadFocusable(RoundedCornerShape(10.dp), Color.White)
                                        ) {
                                            Text("Clear", fontSize = 12.sp)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (inputUrlOrContent.isNotBlank()) {
                                                onLoadPlaylist(inputUrlOrContent)
                                                isInputExpanded = false
                                            } else {
                                                Toast.makeText(context, "Please enter a URL or paste M3U content", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CinePrimary),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .dpadFocusable(RoundedCornerShape(10.dp), Color.White)
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text("Fetch Channels", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        emptyStateContent = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                Surface(
                    color = CineSurfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = CinePrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (currentPlaylistInput.isBlank()) "No IPTV Playlist Added" else "No Channels Available",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = CineTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (currentPlaylistInput.isBlank())
                        "Enter or paste an M3U / M3U8 playlist URL or content above to load custom TV channels."
                    else
                        "The added playlist didn't return any active channels. Check your link or delete the playlist to add a new one.",
                    fontSize = 13.sp,
                    color = CineTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPlaylistInput.isNotBlank() || totalChannelsCount > 0) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CineLiveRed),
                            border = BorderStroke(1.dp, CineLiveRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Playlist", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = { isInputExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CinePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentPlaylistInput.isBlank()) "Add Playlist" else "Edit Playlist",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        modifier = modifier
    )
}
