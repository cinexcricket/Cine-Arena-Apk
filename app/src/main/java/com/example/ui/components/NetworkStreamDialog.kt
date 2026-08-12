package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.player.StreamUrlParser
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onPlayStream: (url: String, cookie: String, referer: String, origin: String, drmLicense: String, drmType: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    var streamUrl by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var drmLicense by remember { mutableStateOf("") }

    var drmType1 by remember { mutableStateOf("Default") }
    var drmType2 by remember { mutableStateOf("ClearKey") }

    var expandedType1 by remember { mutableStateOf(false) }
    var expandedType2 by remember { mutableStateOf(false) }

    fun getClipboardText(): String {
        val clipData = clipboardManager.primaryClip
        return if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).text?.toString() ?: ""
        } else ""
    }

    fun applyUrlAutoParse(rawUrl: String) {
        val parsed = StreamUrlParser.parse(rawUrl)
        streamUrl = parsed.cleanUrl
        if (!parsed.cookie.isNullOrBlank()) cookie = parsed.cookie
        if (!parsed.referer.isNullOrBlank()) referer = parsed.referer
        if (!parsed.origin.isNullOrBlank()) origin = parsed.origin
        if (parsed.drmConfig != null) {
            val keyOrLicense = parsed.drmConfig.licenseUrl.takeIf { !it.isNullOrBlank() }
                ?: "${parsed.drmConfig.keyId ?: ""}:${parsed.drmConfig.key ?: ""}"
            if (keyOrLicense.isNotBlank()) {
                drmLicense = keyOrLicense
            }
            drmType2 = if (parsed.drmConfig.type.equals("clearkey", ignoreCase = true)) "ClearKey" else "Widevine"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CineBackground
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = CineTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Network Stream",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = CineTextPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Star",
                            tint = CinePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Input Field: Stream Url
                    CustomStreamInputField(
                        label = "Stream Url",
                        value = streamUrl,
                        onValueChange = { input ->
                            if (input.contains("drmScheme=") || input.contains("drmLicense=") || input.contains("Cookie=") || input.contains("%7C") || input.contains("|")) {
                                applyUrlAutoParse(input)
                            } else {
                                streamUrl = input
                            }
                        },
                        onPaste = {
                            val pasted = getClipboardText()
                            if (pasted.isNotBlank()) {
                                applyUrlAutoParse(pasted)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input Field: Cookie
                    CustomStreamInputField(
                        label = "Cookie",
                        value = cookie,
                        onValueChange = { cookie = it },
                        onPaste = { cookie = getClipboardText() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input Field: Referer
                    CustomStreamInputField(
                        label = "Referer",
                        value = referer,
                        onValueChange = { referer = it },
                        onPaste = { referer = getClipboardText() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input Field: Origin
                    CustomStreamInputField(
                        label = "Origin",
                        value = origin,
                        onValueChange = { origin = it },
                        onPaste = { origin = getClipboardText() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input Field: Drm License
                    CustomStreamInputField(
                        label = "Drm License",
                        value = drmLicense,
                        onValueChange = { drmLicense = it },
                        onPaste = { drmLicense = getClipboardText() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dropdowns Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Dropdown 1
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expandedType1 = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CinePrimary),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = CineSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = drmType1, color = CineTextPrimary, fontWeight = FontWeight.SemiBold)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = CineTextPrimary)
                                }
                            }

                            DropdownMenu(
                                expanded = expandedType1,
                                onDismissRequest = { expandedType1 = false },
                                modifier = Modifier.background(CineSurface)
                            ) {
                                listOf("Default", "Widevine", "ClearKey").forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item, color = CineTextPrimary) },
                                        onClick = {
                                            drmType1 = item
                                            expandedType1 = false
                                        }
                                    )
                                }
                            }
                        }

                        // Dropdown 2
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expandedType2 = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CinePrimary),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = CineSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = drmType2, color = CineTextPrimary, fontWeight = FontWeight.SemiBold)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = CineTextPrimary)
                                }
                            }

                            DropdownMenu(
                                expanded = expandedType2,
                                onDismissRequest = { expandedType2 = false },
                                modifier = Modifier.background(CineSurface)
                            ) {
                                listOf("ClearKey", "Widevine").forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item, color = CineTextPrimary) },
                                        onClick = {
                                            drmType2 = item
                                            expandedType2 = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }

                // Floating Action Play Button
                FloatingActionButton(
                    onClick = {
                        if (streamUrl.isBlank()) {
                            Toast.makeText(context, "Please enter a stream URL", Toast.LENGTH_SHORT).show()
                        } else {
                            onPlayStream(
                                streamUrl.trim(),
                                cookie.trim(),
                                referer.trim(),
                                origin.trim(),
                                drmLicense.trim(),
                                drmType2
                            )
                            onDismiss()
                        }
                    },
                    containerColor = CinePrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                        .size(60.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play Stream",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CustomStreamInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = CineTextSecondary) },
        trailingIcon = {
            IconButton(onClick = onPaste) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = "Paste",
                    tint = CinePrimary
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CineSurface,
            unfocusedContainerColor = CineSurface,
            focusedBorderColor = CinePrimary,
            unfocusedBorderColor = CineOutline,
            focusedTextColor = CineTextPrimary,
            unfocusedTextColor = CineTextPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
