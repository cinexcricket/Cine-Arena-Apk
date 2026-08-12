package com.example.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import android.os.Build
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.DrmConfig
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
data class VideoQualityOption(
    val label: String,
    val height: Int,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val isAuto: Boolean = false
)

@OptIn(UnstableApi::class)
data class AudioTrackOption(
    val label: String,
    val language: String,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val isAuto: Boolean = false
)

@OptIn(UnstableApi::class)
data class SubtitleTrackOption(
    val label: String,
    val language: String,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val isOff: Boolean = false
)

enum class VideoResizeMode {
    FIT, CROP, FILL
}

@OptIn(UnstableApi::class)
@Composable
fun CinePlayerView(
    streamUrl: String,
    streamType: String, // hls, dash, mp4, iframe
    drmConfig: DrmConfig? = null,
    cookie: String? = null,
    referer: String? = null,
    origin: String? = null,
    title: String = "",
    subtitle: String = "",
    isMiniPlayer: Boolean = false,
    isFullscreen: Boolean = false,
    isInPipMode: Boolean = false,
    onBackClick: () -> Unit = {},
    onFullscreenToggle: (Boolean) -> Unit = {},
    onMiniPlayerToggle: (Boolean) -> Unit = {},
    onCloseClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (streamType.lowercase() == "iframe" || streamUrl.contains("pages.dev") || streamUrl.contains("<iframe")) {
        IframeStreamPlayer(
            url = streamUrl,
            title = title,
            subtitle = subtitle,
            isMiniPlayer = isMiniPlayer,
            isFullscreen = isFullscreen,
            isInPipMode = isInPipMode,
            onBackClick = onBackClick,
            onFullscreenToggle = onFullscreenToggle,
            onMiniPlayerToggle = onMiniPlayerToggle,
            onCloseClick = onCloseClick,
            modifier = modifier
        )
    } else {
        ExoStreamPlayer(
            streamUrl = streamUrl,
            streamType = streamType,
            drmConfig = drmConfig,
            cookie = cookie,
            referer = referer,
            origin = origin,
            title = title,
            subtitle = subtitle,
            isMiniPlayer = isMiniPlayer,
            isFullscreen = isFullscreen,
            isInPipMode = isInPipMode,
            onBackClick = onBackClick,
            onFullscreenToggle = onFullscreenToggle,
            onMiniPlayerToggle = onMiniPlayerToggle,
            onCloseClick = onCloseClick,
            modifier = modifier
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoStreamPlayer(
    streamUrl: String,
    streamType: String,
    drmConfig: DrmConfig?,
    cookie: String?,
    referer: String?,
    origin: String?,
    title: String,
    subtitle: String,
    isMiniPlayer: Boolean = false,
    isFullscreen: Boolean = false,
    isInPipMode: Boolean = false,
    onBackClick: () -> Unit = {},
    onFullscreenToggle: (Boolean) -> Unit = {},
    onMiniPlayerToggle: (Boolean) -> Unit = {},
    onCloseClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Keep mobile screen awake during video playback (prevent screen lock / display sleep timeout)
    DisposableEffect(isPlaying) {
        val window = activity?.window
        if (isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto hide controls after 2.5 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            kotlinx.coroutines.delay(2500L)
            showControls = false
        }
    }

    var qualityOptions by remember { mutableStateOf<List<VideoQualityOption>>(emptyList()) }
    var selectedQualityLabel by remember { mutableStateOf("Auto") }
    var showQualityDialog by remember { mutableStateOf(false) }

    var audioTrackOptions by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var selectedAudioLabel by remember { mutableStateOf("Default / Auto") }
    var showLanguageDialog by remember { mutableStateOf(false) }

    var subtitleTrackOptions by remember { mutableStateOf<List<SubtitleTrackOption>>(emptyList()) }
    var selectedSubtitleLabel by remember { mutableStateOf("Off") }
    var showCaptionDialog by remember { mutableStateOf(false) }

    val isDash = remember(streamUrl, streamType) {
        streamType.equals("dash", ignoreCase = true) ||
        streamType.equals("mpd", ignoreCase = true) ||
        streamUrl.contains(".mpd", ignoreCase = true)
    }

    val isHls = remember(streamUrl, streamType) {
        streamType.equals("hls", ignoreCase = true) ||
        streamType.equals("m3u8", ignoreCase = true) ||
        streamUrl.contains(".m3u8", ignoreCase = true)
    }

    val exoPlayer = remember(streamUrl, streamType, drmConfig, cookie, referer, origin) {
        try {
            val parsed = StreamUrlParser.parse(
                rawUrl = streamUrl,
                inputCookie = cookie,
                inputReferer = referer,
                inputOrigin = origin,
                inputDrmLicense = drmConfig?.licenseUrl ?: if (!drmConfig?.keyId.isNullOrBlank() && !drmConfig?.key.isNullOrBlank()) "${drmConfig.keyId}:${drmConfig.key}" else null,
                inputDrmType = drmConfig?.type
            )

            val activeUrl = parsed.cleanUrl
            if (activeUrl.isBlank()) return@remember null

            val activeType = parsed.streamType
            val activeCookie = parsed.cookie
            val activeReferer = parsed.referer
            val activeOrigin = parsed.origin
            val activeDrm = parsed.drmConfig ?: drmConfig

            if (!activeCookie.isNullOrBlank()) {
                try {
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setCookie(activeUrl, activeCookie.trim())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val dataSourceFactory = ClearKeyUtil.createDataSourceFactory(context, activeCookie, activeReferer, activeOrigin, activeUrl)

            val isMp4Stream = activeType.equals("mp4", ignoreCase = true) ||
                    activeType.equals("video/mp4", ignoreCase = true) ||
                    activeUrl.endsWith(".mp4", ignoreCase = true) ||
                    activeUrl.contains(".mp4?", ignoreCase = true) ||
                    activeUrl.contains(".mp4", ignoreCase = true) ||
                    activeUrl.endsWith(".mkv", ignoreCase = true) ||
                    activeUrl.contains(".mkv", ignoreCase = true) ||
                    activeUrl.endsWith(".webm", ignoreCase = true) ||
                    activeUrl.contains(".webm", ignoreCase = true) ||
                    activeUrl.endsWith(".mov", ignoreCase = true) ||
                    activeUrl.contains(".mov", ignoreCase = true) ||
                    activeUrl.endsWith(".avi", ignoreCase = true) ||
                    activeUrl.contains(".avi", ignoreCase = true)

            val isDashStream = !isMp4Stream && (isDash || activeType.equals("dash", ignoreCase = true) || activeType.equals("mpd", ignoreCase = true) || activeUrl.contains(".mpd", ignoreCase = true))
            val isHlsStream = !isMp4Stream && !isDashStream && (isHls || activeType.equals("hls", ignoreCase = true) || activeType.equals("m3u8", ignoreCase = true) || activeUrl.contains(".m3u8", ignoreCase = true))

            val mediaItemBuilder = MediaItem.Builder().setUri(activeUrl)

            if (isDashStream) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            } else if (isHlsStream) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (isMp4Stream) {
                mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
            }

            val localDrmCallback = ClearKeyUtil.buildClearKeyDrmCallback(activeDrm)

            if (localDrmCallback != null) {
                mediaItemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                        .setForceSessionsForAudioAndVideoTracks(true)
                        .build()
                )
            } else if (activeDrm?.type?.equals("widevine", ignoreCase = true) == true && !activeDrm.licenseUrl.isNullOrEmpty()) {
                val drmBuilder = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(activeDrm.licenseUrl)
                    .setForceSessionsForAudioAndVideoTracks(true)

                val requestHeaders = mutableMapOf<String, String>()
                if (!activeCookie.isNullOrBlank()) requestHeaders["Cookie"] = activeCookie.trim()
                if (!activeReferer.isNullOrBlank()) requestHeaders["Referer"] = activeReferer.trim()
                if (!activeOrigin.isNullOrBlank()) requestHeaders["Origin"] = activeOrigin.trim()
                if (requestHeaders.isNotEmpty()) {
                    drmBuilder.setLicenseRequestHeaders(requestHeaders)
                }
                mediaItemBuilder.setDrmConfiguration(drmBuilder.build())
            }

            val mediaItem = mediaItemBuilder.build()

            val drmSessionManager = try {
                if (localDrmCallback != null) {
                    DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(true)
                        .build(localDrmCallback)
                } else if (activeDrm?.type?.equals("widevine", ignoreCase = true) == true && !activeDrm.licenseUrl.isNullOrEmpty()) {
                    val httpDrmCallback = HttpMediaDrmCallback(activeDrm.licenseUrl, dataSourceFactory)
                    DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(true)
                        .build(httpDrmCallback)
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            val extractorsFactory = DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

            val mediaSource = when {
                isDashStream -> {
                    val dashFactory = DashMediaSource.Factory(dataSourceFactory)
                    if (drmSessionManager != null) {
                        dashFactory.setDrmSessionManagerProvider { drmSessionManager }
                    }
                    dashFactory.createMediaSource(mediaItem)
                }
                isHlsStream -> {
                    val hlsFactory = HlsMediaSource.Factory(dataSourceFactory)
                    if (drmSessionManager != null) {
                        hlsFactory.setDrmSessionManagerProvider { drmSessionManager }
                    }
                    hlsFactory.createMediaSource(mediaItem)
                }
                else -> {
                    val progFactory = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    if (drmSessionManager != null) {
                        progFactory.setDrmSessionManagerProvider { drmSessionManager }
                    }
                    progFactory.createMediaSource(mediaItem)
                }
            }

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    2500,   // minBufferMs (2.5s)
                    50000,  // maxBufferMs (50s)
                    1000,   // bufferForPlaybackMs (1s - start playback immediately)
                    2000    // bufferForPlaybackAfterRebufferMs (2s)
                )
                .setBackBuffer(
                    30000,  // backBufferDurationMs (30s)
                    true
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)

            val audioAttrs = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttrs, true)
                .build().apply {
                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = true
                }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    val rawDur = player.duration
                    duration = if (rawDur != C.TIME_UNSET && rawDur > 0) rawDur else 0L
                    errorMessage = null
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isBuffering = false
                errorMessage = "Playback Error: ${error.localizedMessage ?: "Stream unavailable or network issue"}"
            }

            override fun onTracksChanged(tracks: Tracks) {
                val options = mutableListOf<VideoQualityOption>()
                options.add(VideoQualityOption(label = "Auto", height = 0, group = null, trackIndex = -1, isAuto = true))

                val aOptions = mutableListOf<AudioTrackOption>()
                aOptions.add(AudioTrackOption(label = "Default / Auto", language = "auto", group = null, trackIndex = -1, isAuto = true))

                val sOptions = mutableListOf<SubtitleTrackOption>()
                sOptions.add(SubtitleTrackOption(label = "Off", language = "off", group = null, trackIndex = -1, isOff = true))

                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        val mediaTrackGroup = group.mediaTrackGroup
                        for (i in 0 until mediaTrackGroup.length) {
                            val format = mediaTrackGroup.getFormat(i)
                            val h = format.height
                            val w = format.width
                            if (h > 0) {
                                val label = if (w > 0) "${h}p (${w}x${h})" else "${h}p"
                                options.add(
                                    VideoQualityOption(
                                        label = label,
                                        height = h,
                                        group = group,
                                        trackIndex = i,
                                        isAuto = false
                                    )
                                )
                            }
                        }
                    } else if (group.type == C.TRACK_TYPE_AUDIO) {
                        val mediaTrackGroup = group.mediaTrackGroup
                        for (i in 0 until mediaTrackGroup.length) {
                            val format = mediaTrackGroup.getFormat(i)
                            val lang = format.language?.takeIf { it.isNotBlank() && it != "und" }?.uppercase() ?: "AUDIO ${aOptions.size}"
                            val label = if (format.label.isNullOrBlank()) "Audio Stream ($lang)" else "${format.label} ($lang)"
                            aOptions.add(
                                AudioTrackOption(
                                    label = label,
                                    language = lang,
                                    group = group,
                                    trackIndex = i,
                                    isAuto = false
                                )
                            )
                        }
                    } else if (group.type == C.TRACK_TYPE_TEXT) {
                        val mediaTrackGroup = group.mediaTrackGroup
                        for (i in 0 until mediaTrackGroup.length) {
                            val format = mediaTrackGroup.getFormat(i)
                            val lang = format.language?.takeIf { it.isNotBlank() && it != "und" }?.uppercase() ?: "SUBTITLE ${sOptions.size}"
                            val label = if (format.label.isNullOrBlank()) "Subtitle ($lang)" else "${format.label} ($lang)"
                            sOptions.add(
                                SubtitleTrackOption(
                                    label = label,
                                    language = lang,
                                    group = group,
                                    trackIndex = i,
                                    isOff = false
                                )
                            )
                        }
                    }
                }
                qualityOptions = options.distinctBy { it.label }.sortedByDescending { it.height }
                audioTrackOptions = aOptions.distinctBy { it.label }
                subtitleTrackOptions = sOptions.distinctBy { it.label }
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Controls timeout auto-hide
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    // Position tracker coroutine
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            val rawDur = player.duration
            val validDur = if (rawDur != C.TIME_UNSET && rawDur > 0) rawDur else 0L
            val p = player.currentPosition.coerceAtLeast(0L)

            duration = validDur
            currentPosition = if (duration > 0) p.coerceIn(0L, duration) else p
            delay(500)
        }
    }

    if (isMiniPlayer) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            border = BorderStroke(1.dp, Color(0xFF2B62F6)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = modifier
                .clickable { onMiniPlayerToggle(true) }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            player = exoPlayer
                            keepScreenOn = true
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                        playerView.keepScreenOn = isPlaying
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Mini Player Overlay Controls
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title.ifEmpty { "Live Match" },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        )
                        IconButton(
                            onClick = onCloseClick,
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer?.pause()
                                isPlaying = false
                            } else {
                                exoPlayer?.play()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .background(Color(0xFF2B62F6), CircleShape)
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onMiniPlayerToggle(true) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Expand",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .then(if (isFullscreen) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black)
                .pointerInput(exoPlayer) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onDoubleTap = { offset ->
                            val w = size.width
                            val x = offset.x
                            val player = exoPlayer
                            if (player != null) {
                                if (x < w * 0.35f) {
                                    val target = (player.currentPosition - 10000).coerceAtLeast(0L)
                                    player.seekTo(target)
                                    currentPosition = target
                                    showControls = true
                                } else if (x > w * 0.65f) {
                                    val rawDur = player.duration
                                    val validDur = if (rawDur != C.TIME_UNSET && rawDur > 0) rawDur else 0L
                                    val target = if (validDur > 0) {
                                        (player.currentPosition + 10000).coerceAtMost(validDur)
                                    } else {
                                        player.currentPosition + 10000
                                    }
                                    player.seekTo(target)
                                    currentPosition = target
                                    showControls = true
                                } else {
                                    showControls = !showControls
                                }
                            } else {
                                showControls = !showControls
                            }
                        }
                    )
                }
        ) {
            // ExoPlayer Canvas
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                        keepScreenOn = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.resizeMode = when (resizeMode) {
                            VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            VideoResizeMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.keepScreenOn = isPlaying
                    playerView.resizeMode = when (resizeMode) {
                        VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VideoResizeMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Loading spinner
            if (isBuffering) {
                CircularProgressIndicator(
                    color = Color(0xFF2B62F6),
                    strokeWidth = 3.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            val isPip = isInPipMode || (activity?.isInPictureInPictureMode == true)

            // Custom Overlay Controls
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                if (isPip) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showControls = false
                            }
                    ) {
                        // Close Button at Top-Right
                        IconButton(
                            onClick = { onCloseClick() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(30.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Video",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Center Expand / Open Full Screen Button
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(context, activity?.javaClass ?: context.javaClass).apply {
                                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        action = Intent.ACTION_MAIN
                                        addCategory(Intent.CATEGORY_LAUNCHER)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                onMiniPlayerToggle(false)
                                onFullscreenToggle(true)
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(46.dp)
                                .background(Color(0xFF2B62F6), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Expand Full Screen",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Center Bottom Play / Pause Button
                        IconButton(
                            onClick = {
                                val player = exoPlayer
                                if (player != null) {
                                    if (isPlaying) {
                                        player.pause()
                                        isPlaying = false
                                    } else {
                                        player.play()
                                        isPlaying = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.75f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showControls = false
                            }
                            .padding(12.dp)
                    ) {
                        // Top Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title.ifEmpty { "Live Broadcast" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // LIVE Pill
                            Surface(
                                color = Color(0xFFEF4444),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LIVE",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { showLanguageDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = "Language",
                                    tint = if (selectedAudioLabel != "Default / Auto") Color(0xFF00E5FF) else Color.White
                                )
                            }

                            IconButton(
                                onClick = { showQualityDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Tune",
                                    tint = if (selectedQualityLabel != "Auto") Color(0xFF00E5FF) else Color.White
                                )
                            }

                            IconButton(
                                onClick = { showCaptionDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaption,
                                    contentDescription = "CC",
                                    tint = if (selectedSubtitleLabel != "Off") Color(0xFF00E5FF) else Color.White
                                )
                            }
                        }

                        // Middle Action Controls (Replay 10, Play/Pause, Forward 10)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = {
                                    val player = exoPlayer
                                    if (player != null) {
                                        val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                                        player.seekTo(target)
                                        currentPosition = target
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Replay10,
                                    contentDescription = "-10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(28.dp))

                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        exoPlayer?.pause()
                                        isPlaying = false
                                    } else {
                                        exoPlayer?.play()
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(Color(0xFF2B62F6), CircleShape)
                            ) {
                                if (isBuffering) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(28.dp))

                            IconButton(
                                onClick = {
                                    val player = exoPlayer
                                    if (player != null) {
                                        val rawDur = player.duration
                                        val validDur = if (rawDur != C.TIME_UNSET && rawDur > 0) rawDur else duration
                                        val target = if (validDur > 0) {
                                            (player.currentPosition + 10000L).coerceAtMost(validDur)
                                        } else {
                                            player.currentPosition + 10000L
                                        }
                                        player.seekTo(target)
                                        currentPosition = target
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Forward10,
                                    contentDescription = "+10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Bottom Control Bar
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        ) {
                            if (duration > 0) {
                                ThinVideoSeekbar(
                                    positionMs = currentPosition,
                                    durationMs = duration,
                                    onSeek = { seekMs ->
                                        val player = exoPlayer
                                        if (player != null) {
                                            val target = seekMs.coerceIn(0L, duration)
                                            player.seekTo(target)
                                            currentPosition = target
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            isMuted = !isMuted
                                            exoPlayer?.volume = if (isMuted) 0f else 1f
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "Mute",
                                            tint = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = if (duration > 0) {
                                            "${formatTime(currentPosition)} / ${formatTime(duration)}"
                                        } else {
                                            formatTime(currentPosition)
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable { showQualityDialog = true }
                                    ) {
                                        Text(
                                            text = selectedQualityLabel,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(
                                        onClick = {
                                            resizeMode = when (resizeMode) {
                                                VideoResizeMode.FIT -> VideoResizeMode.CROP
                                                VideoResizeMode.CROP -> VideoResizeMode.FILL
                                                VideoResizeMode.FILL -> VideoResizeMode.FIT
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = "Aspect Ratio",
                                            tint = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { onFullscreenToggle(!isFullscreen) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = "Fullscreen",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = {
                Text(
                    text = "Select Stream Quality",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (qualityOptions.isEmpty()) {
                        Text(
                            text = "Auto (Fetching stream qualities...)",
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        qualityOptions.forEach { option ->
                            val isSelected = (option.isAuto && selectedQualityLabel == "Auto") ||
                                    (!option.isAuto && selectedQualityLabel.startsWith("${option.height}"))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedQualityLabel = if (option.isAuto) "Auto" else "${option.height}p"
                                        showQualityDialog = false

                                        val player = exoPlayer
                                        if (player != null) {
                                            if (option.isAuto || option.group == null) {
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                    .build()
                                            } else {
                                                val override = TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex)
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(override)
                                                    .build()
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close", color = Color(0xFF2B62F6))
                }
            },
            containerColor = Color(0xFF131B2E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Audio Track / Language",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val languagesToShow = if (audioTrackOptions.size > 1) {
                        audioTrackOptions
                    } else {
                        listOf(
                            AudioTrackOption("Default Stream Audio", "auto", null, -1, isAuto = true),
                            AudioTrackOption("Hindi Commentary (HD)", "hi", null, -1, isAuto = false),
                            AudioTrackOption("English Commentary (HD)", "en", null, -1, isAuto = false),
                            AudioTrackOption("Tamil / Telugu Commentary", "ta", null, -1, isAuto = false)
                        )
                    }

                    languagesToShow.forEach { option ->
                        val isSelected = selectedAudioLabel == option.label
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAudioLabel = option.label
                                    showLanguageDialog = false

                                    val player = exoPlayer
                                    if (player != null) {
                                        if (option.isAuto || option.group == null) {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                .build()
                                        } else {
                                            val override = TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex)
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(override)
                                                .build()
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option.label,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close", color = Color(0xFF2B62F6))
                }
            },
            containerColor = Color(0xFF131B2E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    if (showCaptionDialog) {
        AlertDialog(
            onDismissRequest = { showCaptionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Subtitles & Closed Captions",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val captionsToShow = if (subtitleTrackOptions.size > 1) {
                        subtitleTrackOptions
                    } else {
                        listOf(
                            SubtitleTrackOption("Off", "off", null, -1, isOff = true),
                            SubtitleTrackOption("English Captions", "en", null, -1, isOff = false),
                            SubtitleTrackOption("Hindi Captions", "hi", null, -1, isOff = false)
                        )
                    }

                    captionsToShow.forEach { option ->
                        val isSelected = selectedSubtitleLabel == option.label
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSubtitleLabel = option.label
                                    showCaptionDialog = false

                                    val player = exoPlayer
                                    if (player != null) {
                                        if (option.isOff) {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                        } else {
                                            if (option.group != null) {
                                                val override = TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex)
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setOverrideForType(override)
                                                    .build()
                                            } else {
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .build()
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option.label,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCaptionDialog = false }) {
                    Text("Close", color = Color(0xFF2B62F6))
                }
            },
            containerColor = Color(0xFF131B2E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IframeStreamPlayer(
    url: String,
    title: String,
    subtitle: String,
    isMiniPlayer: Boolean = false,
    isFullscreen: Boolean = false,
    isInPipMode: Boolean = false,
    onBackClick: () -> Unit = {},
    onFullscreenToggle: (Boolean) -> Unit = {},
    onMiniPlayerToggle: (Boolean) -> Unit = {},
    onCloseClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isPip = isInPipMode || (activity?.isInPictureInPictureMode == true)
    var showIframeControls by remember { mutableStateOf(true) }

    LaunchedEffect(showIframeControls, isPip) {
        if (isPip && showIframeControls) {
            delay(3000)
            showIframeControls = false
        }
    }

    // Keep screen awake while iframe video stream is loaded
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val activeCustomView = customView
    if (activeCustomView != null) {
        Dialog(
            onDismissRequest = {
                try {
                    customViewCallback?.onCustomViewHidden()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                customView = null
                customViewCallback = null
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { activeCustomView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (isMiniPlayer) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            border = BorderStroke(1.dp, Color(0xFF2B62F6)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = modifier.clickable { onMiniPlayerToggle(true) }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            keepScreenOn = true
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webViewClient = WebViewClient()
                            webChromeClient = object : WebChromeClient() {
                                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                    customView = view
                                    customViewCallback = callback
                                }

                                override fun onHideCustomView() {
                                    try {
                                        customViewCallback?.onCustomViewHidden()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    customView = null
                                    customViewCallback = null
                                }
                            }
                            if (url.startsWith("http")) loadUrl(url)
                            else if (url.contains("<iframe")) loadDataWithBaseURL("https://cinexcricket.com", url, "text/html", "UTF-8", null)
                            else loadUrl(url)
                        }
                    },
                    onRelease = { webView ->
                        try {
                            webView.stopLoading()
                            webView.loadUrl("about:blank")
                            webView.onPause()
                            webView.removeAllViews()
                            webView.destroy()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.ifEmpty { "Iframe Stream" },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCloseClick, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    } else {
        Box(
            modifier = if (isFullscreen) {
                modifier
                    .fillMaxSize()
                    .background(Color.Black)
            } else {
                modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            }
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        keepScreenOn = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                        webViewClient = WebViewClient()
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                customView = view
                                customViewCallback = callback
                                onFullscreenToggle(true)
                            }

                            override fun onHideCustomView() {
                                try {
                                    customViewCallback?.onCustomViewHidden()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                customView = null
                                customViewCallback = null
                                onFullscreenToggle(false)
                            }
                        }

                        if (url.startsWith("http")) {
                            loadUrl(url)
                        } else if (url.contains("<iframe")) {
                            loadDataWithBaseURL("https://cinexcricket.com", url, "text/html", "UTF-8", null)
                        } else {
                            loadUrl(url)
                        }
                    }
                },
                onRelease = { webView ->
                    try {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.onPause()
                        webView.removeAllViews()
                        webView.destroy()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isPip) {
                AnimatedVisibility(
                    visible = showIframeControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showIframeControls = false
                            }
                    ) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(context, activity?.javaClass ?: context.javaClass).apply {
                                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                onMiniPlayerToggle(false)
                                onFullscreenToggle(true)
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                                .background(Color(0xFF2B62F6), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Expand Full Screen",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        IconButton(
                            onClick = { onCloseClick() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Video",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                // Overlay Header for Back & Fullscreen Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.ifEmpty { "Iframe Stream" },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = { onFullscreenToggle(!isFullscreen) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { onMiniPlayerToggle(true) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun ThinVideoSeekbar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (durationMs <= 0) return

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (isDragging) dragFraction else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((frac * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek((dragFraction * durationMs).toLong())
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbPx = currentFraction * widthPx

        // Background track (Thin 3dp height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White.copy(alpha = 0.35f))
        )
        // Active progress track (Thin 3dp height)
        Box(
            modifier = Modifier
                .fillMaxWidth(currentFraction)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0xFF2B62F6))
        )
        // Small thumb handle (10dp dot)
        val density = LocalDensity.current
        val thumbOffsetDp = with(density) { thumbPx.toDp() }
        Box(
            modifier = Modifier
                .offset(x = (thumbOffsetDp - 5.dp).coerceAtLeast(0.dp))
                .size(10.dp)
                .background(Color(0xFF2B62F6), CircleShape)
        )
    }
}
