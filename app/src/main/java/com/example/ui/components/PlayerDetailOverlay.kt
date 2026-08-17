package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileEntity
import kotlin.random.Random
import com.example.model.ChannelItem
import com.example.model.ChatMessage
import com.example.model.MatchItem
import com.example.util.IstTimeHelper
import com.example.ads.StartAppHorizontalBannerAd
import com.example.ui.theme.*

@Composable
fun PlayerDetailOverlay(
    activeMatch: MatchItem?,
    activeChannel: ChannelItem?,
    chatMessages: List<ChatMessage>,
    userProfile: UserProfileEntity? = null,
    liveLikesCount: Int = 1240,
    showProfileDialog: Boolean = false,
    onChannelSelect: (ChannelItem) -> Unit,
    onSendMessage: (String) -> Unit,
    onLikeClick: () -> Unit = {},
    onSaveProfile: (name: String, phone: String, hostingerUrl: String) -> Unit = { _, _, _ -> },
    onDismissProfileDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var commentText by remember { mutableStateOf("") }
    var showEditProfileModal by remember { mutableStateOf(false) }
    var showDatabaseGuideModal by remember { mutableStateOf(false) }

    val channelsList = remember(activeMatch, activeChannel) {
        if (activeMatch != null && activeMatch.channels.isNotEmpty()) {
            activeMatch.channels
        } else if (activeChannel != null) {
            listOf(
                activeChannel,
                activeChannel.copy(id = "${activeChannel.id}_srv2", name = "Server 2 (Backup SD)"),
                activeChannel.copy(id = "${activeChannel.id}_srv3", name = "Server 3 (HD Alternate)")
            )
        } else {
            emptyList()
        }
    }

    // Floating heart balloons state (JioHotstar style)
    var floatingHearts by remember { mutableStateOf(listOf<FloatingHeart>()) }

    val chatScrollState = rememberScrollState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            chatScrollState.animateScrollTo(chatScrollState.maxValue)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CineBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Section 1: Quality Availables Card
            if (channelsList.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = CineSurface),
                    border = BorderStroke(1.dp, CineOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        val headerTitle = remember(activeMatch) {
                            if (!activeMatch?.heading.isNullOrBlank()) {
                                activeMatch?.heading!!.trim()
                            } else {
                                val cat = (activeMatch?.displayCategory ?: "").lowercase()
                                val isMovieOrSeries = cat.contains("movie") ||
                                        cat.contains("webseries") ||
                                        cat.contains("series") ||
                                        cat.contains("vod") ||
                                        cat.contains("film")
                                if (isMovieOrSeries) {
                                    "Quality Availables"
                                } else {
                                    "Availables Channels & Server"
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 5.dp)
                        ) {
                            Icon(
                                Icons.Default.HighQuality,
                                contentDescription = null,
                                tint = CinePrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (headerTitle.endsWith(":")) headerTitle else "$headerTitle :",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = CineTextPrimary
                            )
                        }

                        val qualityList = remember(channelsList, activeChannel) {
                            channelsList.map { ch ->
                                val name = ch.name.trim()
                                val label = when {
                                    name.equals("1080", ignoreCase = true) || name.equals("1080p", ignoreCase = true) -> "1080p"
                                    name.equals("720", ignoreCase = true) || name.equals("720p", ignoreCase = true) -> "720p"
                                    name.equals("480", ignoreCase = true) || name.equals("480p", ignoreCase = true) -> "480p"
                                    name.equals("360", ignoreCase = true) || name.equals("360p", ignoreCase = true) -> "360p"
                                    name.isNotBlank() -> name
                                    !ch.quality.isNullOrBlank() -> ch.quality
                                    else -> "Server ${ch.id}"
                                }
                                label to ch
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = qualityList,
                                key = { it.first + it.second.id },
                                contentType = { "quality_chip" }
                            ) { (qualityLabel, targetChannel) ->
                                val isSelected = (activeChannel?.id == targetChannel.id) || (activeChannel == null && targetChannel == channelsList.first())
                                Surface(
                                    onClick = { onChannelSelect(targetChannel) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) CinePrimary.copy(alpha = 0.22f) else CineSurfaceVariant,
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) CinePrimary else CineOutline
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    if (isSelected) CinePrimary else CineTextSecondary.copy(alpha = 0.6f),
                                                    CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = qualityLabel,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else CineTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Horizontal Banner Ad below Channel Switching
            StartAppHorizontalBannerAd(
                modifier = Modifier.fillMaxWidth()
            )

            // Section 2: Live Match Chat Card (Full width, fills remaining height)
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = CineSurface),
                border = BorderStroke(1.dp, CineOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Header Chat Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ChatBubble,
                                contentDescription = "Chat",
                                tint = CinePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Match Chat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CineTextPrimary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            IconButton(
                                onClick = { showDatabaseGuideModal = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Database Info",
                                    tint = CinePrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // User Profile Icon (Surface with CircleShape to prevent radius clipping)
                        Surface(
                            onClick = { showEditProfileModal = true },
                            shape = CircleShape,
                            color = CineSurfaceVariant,
                            border = BorderStroke(1.dp, CinePrimary),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User Profile (${userProfile?.name ?: "Guest"})",
                                    tint = CinePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Chat Messages Feed Box (Scrollable feed filling available area)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(CineSurfaceVariant, RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        if (chatMessages.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.ChatBubble,
                                    contentDescription = null,
                                    tint = CinePrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Be the first to join the live match discussion!",
                                    color = CineTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(chatScrollState),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                chatMessages.forEach { msg ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (msg.isMe) CinePrimary else CineSurface,
                                            border = BorderStroke(1.dp, CineOutline),
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = msg.senderName.take(1).uppercase(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (msg.isMe) Color.White else CineTextPrimary
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = msg.senderName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = if (msg.isMe) CinePrimary else CineTextPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = IstTimeHelper.formatToIst(msg.timestamp),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = CineTextSecondary
                                                )
                                            }
                                            Text(
                                                text = msg.text,
                                                fontSize = 12.sp,
                                                color = CineTextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Fixed Input Field Bar at bottom of card (Unclipped BasicTextField + Heart icon + Send icon)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = CineTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(CinePrimary),
                            keyboardOptions = KeyboardOptions.Default,
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (commentText.isNotBlank()) {
                                        onSendMessage(commentText.trim())
                                        commentText = ""
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .background(CineSurfaceVariant, CircleShape)
                                .border(1.dp, CineOutline, CircleShape)
                                .padding(horizontal = 14.dp),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (commentText.isEmpty()) {
                                        Text(
                                            text = "Enter comment...",
                                            color = CineTextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Heart Reaction Button (Increased icon size to 18dp)
                        Surface(
                            onClick = {
                                onLikeClick()
                                val newHearts = (0..Random.nextInt(2, 4)).map { generateRandomHeart() }
                                floatingHearts = floatingHearts + newHearts
                            },
                            shape = CircleShape,
                            color = CineSurfaceVariant,
                            border = BorderStroke(1.dp, CineLiveRed.copy(alpha = 0.4f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = "Heart Like Reaction",
                                    tint = CineLiveRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Send Icon Button with right margin padding
                        Surface(
                            onClick = {
                                if (commentText.isNotBlank()) {
                                    onSendMessage(commentText.trim())
                                    commentText = ""
                                }
                            },
                            shape = CircleShape,
                            color = CinePrimary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }

        // Floating Hearts Balloons Layer
        FloatingHeartsOverlay(
            activeHearts = floatingHearts,
            onHeartFinished = { finishedId ->
                floatingHearts = floatingHearts.filter { it.id != finishedId }
            },
            modifier = Modifier
                .matchParentSize()
                .padding(8.dp)
        )
    }

    // User Profile Setup / Edit Dialog
    if (showProfileDialog || showEditProfileModal) {
        ProfileSetupDialog(
            initialName = userProfile?.name ?: "",
            initialPhone = userProfile?.phoneNumber ?: "",
            initialHostingerUrl = userProfile?.hostingerApiUrl ?: "",
            onSave = { name, phone, hostingerUrl ->
                onSaveProfile(name, phone, hostingerUrl)
                showEditProfileModal = false
            },
            onDismiss = {
                onDismissProfileDialog()
                showEditProfileModal = false
            }
        )
    }

    // Database Integration Guide Modal
    if (showDatabaseGuideModal) {
        AlertDialog(
            onDismissRequest = { showDatabaseGuideModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CinePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Database & Live Chat Guide", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "1. Local Storage (Room DB):",
                        fontWeight = FontWeight.Bold,
                        color = CinePrimary,
                        fontSize = 13.sp
                    )
                    Text(
                        "Your display name, mobile number, and match comments are stored directly in your app's Room Database (`AppDatabase`).",
                        fontSize = 12.sp,
                        color = CineTextPrimary
                    )
                    Divider(color = CineOutline, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "2. Real-Time Syncing to Other Users:",
                        fontWeight = FontWeight.Bold,
                        color = CinePrimary,
                        fontSize = 13.sp
                    )
                    Text(
                        "To broadcast chats to users on other phones in real time, you can connect your own Firebase Firestore, Supabase, or REST API database in `CineRepository.kt`.",
                        fontSize = 12.sp,
                        color = CineTextPrimary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDatabaseGuideModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CinePrimary)
                ) {
                    Text("Got It", color = Color.White)
                }
            },
            containerColor = CineSurface
        )
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

@Composable
private fun ProfileSetupDialog(
    initialName: String,
    initialPhone: String,
    initialHostingerUrl: String = "",
    onSave: (name: String, phone: String, hostingerUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var hostingerUrl by remember { mutableStateOf(if (initialHostingerUrl.isNotBlank()) initialHostingerUrl else "https://cinexcricket.com/api/chat.php") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = CinePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Live Chat Profile Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Set your display name and mobile number. Optionally connect your Hostinger API URL for live cross-device syncing.",
                    fontSize = 12.sp,
                    color = CineTextSecondary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = "" },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Rahul Sharma") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = CinePrimary) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = "" },
                    label = { Text("Mobile Number") },
                    placeholder = { Text("e.g. +91 9876543210") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = CinePrimary) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hostingerUrl,
                    onValueChange = { hostingerUrl = it },
                    label = { Text("Hostinger API URL (Optional)") },
                    placeholder = { Text("https://yourdomain.com/api/chat.php") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Sensors, contentDescription = null, tint = CinePrimary) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = CineLiveRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || phone.isBlank()) {
                        errorMsg = "Please fill both name and mobile number"
                    } else {
                        onSave(name.trim(), phone.trim(), hostingerUrl.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CinePrimary)
            ) {
                Text("Save & Continue", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CineTextSecondary)
            }
        },
        containerColor = CineSurface
    )
}
