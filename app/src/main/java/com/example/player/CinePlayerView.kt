package com.example.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import androidx.media3.common.PlaybackParameters
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.example.ui.components.dpadFocusable
import com.example.model.DrmConfig
import com.example.model.SubtitleTrackItem
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
    val isOff: Boolean = false,
    val isSelected: Boolean = false
)

enum class VideoResizeMode {
    FIT, FIXED_WIDTH, FILL, ZOOM
}

@OptIn(UnstableApi::class)
private fun applyExoPlayerAspectRatio(
    playerView: PlayerView,
    exoPlayer: ExoPlayer?,
    resizeMode: VideoResizeMode,
    currentVideoSize: androidx.media3.common.VideoSize?
) {
    val contentFrame = playerView.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
        ?: (0 until playerView.childCount).map { playerView.getChildAt(it) }.filterIsInstance<AspectRatioFrameLayout>().firstOrNull()

    val vs = exoPlayer?.videoSize ?: currentVideoSize
    val rawStreamRatio = if (vs != null && vs.height > 0 && vs.width > 0) {
        val pr = if (vs.pixelWidthHeightRatio > 0f) vs.pixelWidthHeightRatio else 1f
        (vs.width.toFloat() * pr) / vs.height.toFloat()
    } else {
        16f / 9f
    }

    when (resizeMode) {
        VideoResizeMode.FIT -> {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            contentFrame?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            contentFrame?.setAspectRatio(rawStreamRatio)
            exoPlayer?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
        VideoResizeMode.FIXED_WIDTH -> {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            contentFrame?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            contentFrame?.setAspectRatio(16f / 9f)
            exoPlayer?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
        VideoResizeMode.FILL -> {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            contentFrame?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            contentFrame?.setAspectRatio(0f)
            exoPlayer?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
        VideoResizeMode.ZOOM -> {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            contentFrame?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            contentFrame?.setAspectRatio(rawStreamRatio)
            exoPlayer?.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }
    }

    contentFrame?.requestLayout()
    contentFrame?.invalidate()
    playerView.videoSurfaceView?.requestLayout()
    playerView.videoSurfaceView?.invalidate()
    playerView.requestLayout()
    playerView.invalidate()
    playerView.post {
        contentFrame?.requestLayout()
        playerView.requestLayout()
    }
}

private fun getIframeScalingJs(resizeMode: VideoResizeMode): String {
    return when (resizeMode) {
        VideoResizeMode.FIT -> """
            (function() {
                var els = document.querySelectorAll('video, iframe');
                els.forEach(function(el) {
                    el.style.objectFit = 'contain';
                    el.style.aspectRatio = 'unset';
                    el.style.width = '100vw';
                    el.style.height = '100vh';
                    el.style.transform = 'none';
                });
            })();
        """.trimIndent()
        VideoResizeMode.FIXED_WIDTH -> """
            (function() {
                var els = document.querySelectorAll('video, iframe');
                els.forEach(function(el) {
                    el.style.objectFit = 'contain';
                    el.style.aspectRatio = '16/9';
                    el.style.width = '100vw';
                    el.style.height = '100vh';
                    el.style.transform = 'none';
                });
            })();
        """.trimIndent()
        VideoResizeMode.FILL -> """
            (function() {
                var els = document.querySelectorAll('video, iframe');
                els.forEach(function(el) {
                    el.style.objectFit = 'fill';
                    el.style.aspectRatio = 'unset';
                    el.style.width = '100vw';
                    el.style.height = '100vh';
                    el.style.transform = 'none';
                });
            })();
        """.trimIndent()
        VideoResizeMode.ZOOM -> """
            (function() {
                var els = document.querySelectorAll('video, iframe');
                els.forEach(function(el) {
                    el.style.objectFit = 'cover';
                    el.style.aspectRatio = 'unset';
                    el.style.width = '100vw';
                    el.style.height = '100vh';
                    el.style.transform = 'scale(1.2)';
                });
            })();
        """.trimIndent()
    }
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
    subtitleUrl: String? = null,
    subtitles: List<SubtitleTrackItem>? = null,
    title: String = "",
    subtitle: String = "",
    initialPositionMs: Long = 0L,
    isMiniPlayer: Boolean = false,
    isFullscreen: Boolean = false,
    isInPipMode: Boolean = false,
    onBackClick: () -> Unit = {},
    onFullscreenToggle: (Boolean) -> Unit = {},
    onMiniPlayerToggle: (Boolean) -> Unit = {},
    onEnterPipClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    onPlaybackProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
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
            onEnterPipClick = onEnterPipClick,
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
            subtitleUrl = subtitleUrl,
            subtitles = subtitles,
            title = title,
            subtitle = subtitle,
            initialPositionMs = initialPositionMs,
            isMiniPlayer = isMiniPlayer,
            isFullscreen = isFullscreen,
            isInPipMode = isInPipMode,
            onBackClick = onBackClick,
            onFullscreenToggle = onFullscreenToggle,
            onMiniPlayerToggle = onMiniPlayerToggle,
            onEnterPipClick = onEnterPipClick,
            onCloseClick = onCloseClick,
            onPlaybackProgress = onPlaybackProgress,
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
    subtitleUrl: String? = null,
    subtitles: List<SubtitleTrackItem>? = null,
    title: String,
    subtitle: String,
    initialPositionMs: Long = 0L,
    isMiniPlayer: Boolean = false,
    isFullscreen: Boolean = false,
    isInPipMode: Boolean = false,
    onBackClick: () -> Unit = {},
    onFullscreenToggle: (Boolean) -> Unit = {},
    onMiniPlayerToggle: (Boolean) -> Unit = {},
    onEnterPipClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    onPlaybackProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    var aspectToastText by remember { mutableStateOf<String?>(null) }
    var currentVideoSize by remember { mutableStateOf<androidx.media3.common.VideoSize?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showLockOverlay by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val playerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    var controlsInteractionCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            playerFocusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(50L)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        } else {
            try {
                playerFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var brightnessLevel by remember {
        val b = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (b >= 0f) b else 0.5f)
    }
    var volumeLevel by remember {
        val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 8
        val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
        mutableFloatStateOf(curVol.toFloat() / maxVol.toFloat())
    }
    var showBrightnessHUD by remember { mutableStateOf(false) }
    var showVolumeHUD by remember { mutableStateOf(false) }
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var isDraggingVolume by remember { mutableStateOf(false) }

    LaunchedEffect(showBrightnessHUD, isDraggingBrightness) {
        if (showBrightnessHUD && !isDraggingBrightness) {
            delay(1200L)
            showBrightnessHUD = false
        }
    }

    LaunchedEffect(showVolumeHUD, isDraggingVolume) {
        if (showVolumeHUD && !isDraggingVolume) {
            delay(1200L)
            showVolumeHUD = false
        }
    }

    LaunchedEffect(aspectToastText) {
        if (aspectToastText != null) {
            kotlinx.coroutines.delay(1500L)
            aspectToastText = null
        }
    }

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

    var qualityOptions by remember { mutableStateOf<List<VideoQualityOption>>(emptyList()) }
    var selectedQualityLabel by remember { mutableStateOf("Auto") }
    var showQualityDialog by remember { mutableStateOf(false) }

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    var audioTrackOptions by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var selectedAudioLabel by remember { mutableStateOf("Default / Auto") }
    var showLanguageDialog by remember { mutableStateOf(false) }

    var subtitleTrackOptions by remember { mutableStateOf<List<SubtitleTrackOption>>(emptyList()) }
    var selectedSubtitleLabel by remember {
        mutableStateOf(
            if (!subtitleUrl.isNullOrBlank() || !subtitles.isNullOrEmpty()) "English Subtitles" else "Off"
        )
    }
    var showCaptionDialog by remember { mutableStateOf(false) }
    var currentCues by remember { mutableStateOf<List<Cue>>(emptyList()) }

    // Auto hide controls after 5 seconds of inactivity on TV/mobile
    LaunchedEffect(showControls, isPlaying, controlsInteractionCount, showQualityDialog, showSpeedDialog, showLanguageDialog, showCaptionDialog) {
        val anyDialogOpen = showQualityDialog || showSpeedDialog || showLanguageDialog || showCaptionDialog
        if (showControls && isPlaying && !anyDialogOpen) {
            kotlinx.coroutines.delay(5000L)
            showControls = false
        }
    }

    // Auto hide lock overlay when screen is locked after 2.5 seconds
    LaunchedEffect(showLockOverlay, isScreenLocked) {
        if (showLockOverlay && isScreenLocked) {
            kotlinx.coroutines.delay(2500L)
            showLockOverlay = false
        }
    }

    // Auto unlock if exiting landscape / fullscreen mode
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            isScreenLocked = false
            showLockOverlay = false
        }
    }

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

    val exoPlayer = remember(streamUrl, streamType, drmConfig, cookie, referer, origin, retryCount) {
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

            val subConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
            if (!subtitleUrl.isNullOrBlank() && (subtitleUrl.startsWith("http://") || subtitleUrl.startsWith("https://"))) {
                val mime = if (subtitleUrl.endsWith(".srt", ignoreCase = true)) MimeTypes.APPLICATION_SUBRIP
                           else if (subtitleUrl.endsWith(".ttml", ignoreCase = true)) MimeTypes.APPLICATION_TTML
                           else MimeTypes.TEXT_VTT
                subConfigs.add(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl.trim()))
                        .setMimeType(mime)
                        .setLanguage("en")
                        .setLabel("English Subtitles")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            }
            subtitles?.forEach { sub ->
                if (sub.url.isNotBlank() && (sub.url.startsWith("http://") || sub.url.startsWith("https://"))) {
                    val mime = sub.mimeType ?: if (sub.url.endsWith(".srt", ignoreCase = true)) MimeTypes.APPLICATION_SUBRIP
                               else if (sub.url.endsWith(".ttml", ignoreCase = true)) MimeTypes.APPLICATION_TTML
                               else MimeTypes.TEXT_VTT
                    subConfigs.add(
                        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url.trim()))
                            .setMimeType(mime)
                            .setLanguage(sub.language.ifBlank { "en" })
                            .setLabel(sub.label ?: "Subtitle (${sub.language.uppercase()})")
                            .build()
                    )
                }
            }
            if (subConfigs.isNotEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(subConfigs)
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

            val baseMediaSource = when {
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

            val subtitleSources = subConfigs.map { config ->
                SingleSampleMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(config, C.TIME_UNSET)
            }

            val mediaSource = if (subtitleSources.isNotEmpty()) {
                MergingMediaSource(baseMediaSource, *subtitleSources.toTypedArray())
            } else {
                baseMediaSource
            }

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    500,    // minBufferMs (0.5s)
                    20000,  // maxBufferMs (20s)
                    100,    // bufferForPlaybackMs (0.1s - start playback instantly)
                    250     // bufferForPlaybackAfterRebufferMs (0.25s)
                )
                .setBackBuffer(
                    10000,  // backBufferDurationMs (10s)
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

            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setAllowMultipleAdaptiveSelections(true)
                    .setIgnoredTextSelectionFlags(0)
                    .build()
            }

            ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttrs, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                    setMediaSource(mediaSource)
                    if (initialPositionMs > 0L) {
                        seekTo(initialPositionMs)
                    }
                    prepare()
                    playWhenReady = true
                }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // MediaSession enables OS-level Media Button handling (Bluetooth earbuds tap to play/pause, headset controls)
    val mediaSession = remember(exoPlayer) {
        val player = exoPlayer ?: return@remember null
        try {
            MediaSession.Builder(context, player)
                .setId("CinePlayerSession_${player.hashCode()}")
                .build()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Lifecycle observer ensures video and audio release properly when app is destroyed, but keeps playing in background / lock screen
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (player.playWhenReady && !player.isPlaying && player.playbackState == Player.STATE_READY) {
                        player.play()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                    player.stop()
                    player.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(resizeMode, currentVideoSize, isFullscreen, exoPlayer) {
        playerViewRef?.let { pv ->
            applyExoPlayerAspectRatio(pv, exoPlayer, resizeMode, currentVideoSize)
        }
    }

    DisposableEffect(exoPlayer, mediaSession) {
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
                val cause = error.cause
                val msg = when {
                    cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException && cause.responseCode == 403 ->
                        "Stream server returned 403 (Forbidden). The stream link may require auth tokens or has expired."
                    cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException ->
                        "Network connection error while fetching stream."
                    else ->
                        error.localizedMessage ?: "Stream unavailable or temporarily offline"
                }
                errorMessage = msg
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                currentVideoSize = videoSize
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
                            val isSelected = group.isTrackSelected(i)
                            sOptions.add(
                                SubtitleTrackOption(
                                    label = label,
                                    language = lang,
                                    group = group,
                                    trackIndex = i,
                                    isOff = false,
                                    isSelected = isSelected
                                )
                            )
                        }
                    }
                }
                qualityOptions = options.distinctBy { it.label }.sortedByDescending { it.height }
                audioTrackOptions = aOptions.distinctBy { it.label }
                val distinctSubs = sOptions.distinctBy { it.label }
                subtitleTrackOptions = distinctSubs
                val activeSelected = distinctSubs.firstOrNull { it.isSelected && !it.isOff }
                if (activeSelected != null && selectedSubtitleLabel == "Off") {
                    selectedSubtitleLabel = activeSelected.label
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                currentCues = cueGroup.cues
            }

            @Deprecated("Deprecated in Java")
            override fun onCues(cues: List<Cue>) {
                currentCues = cues
            }
        }
        player.addListener(listener)

        onDispose {
            if (currentPosition > 1000L) {
                onPlaybackProgress(currentPosition, duration)
            }
            player.removeListener(listener)
            try {
                mediaSession?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                player.stop()
                player.clearMediaItems()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        var tickCounter = 0
        while (true) {
            val rawDur = player.duration
            val validDur = if (rawDur != C.TIME_UNSET && rawDur > 0) rawDur else 0L
            val p = player.currentPosition.coerceAtLeast(0L)

            duration = validDur
            currentPosition = if (duration > 0) p.coerceIn(0L, duration) else p

            tickCounter++
            if (tickCounter % 4 == 0 && currentPosition > 1000L) {
                onPlaybackProgress(currentPosition, duration)
            }
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
                    onRelease = { playerView ->
                        try {
                            playerView.player = null
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
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
                                .clickable { onMiniPlayerToggle(true) }
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onCloseClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2B62F6))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isPlaying) {
                                    exoPlayer?.pause()
                                    isPlaying = false
                                } else {
                                    exoPlayer?.play()
                                    isPlaying = true
                                }
                            },
                        contentAlignment = Alignment.Center
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
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onEnterPipClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "System PiP",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onMiniPlayerToggle(true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Expand",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = (if (isFullscreen) modifier.fillMaxSize() else modifier.fillMaxWidth())
                .background(Color.Black)
                .focusRequester(playerFocusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val player = exoPlayer
                    val keyCode = keyEvent.nativeKeyEvent.keyCode

                    // Hardware Media Buttons (Always operate playback directly)
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (isPlaying) {
                                player?.pause()
                                isPlaying = false
                            } else {
                                player?.play()
                                isPlaying = true
                            }
                            return@onKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            player?.play()
                            isPlaying = true
                            return@onKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player?.pause()
                            isPlaying = false
                            return@onKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
                        android.view.KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                            if (player != null) {
                                val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                                player.seekTo(target)
                                currentPosition = target
                                showControls = true
                                controlsInteractionCount++
                            }
                            return@onKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        android.view.KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
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
                                showControls = true
                                controlsInteractionCount++
                            }
                            return@onKeyEvent true
                        }
                    }

                    // When controls are HIDDEN: Any D-pad key shows controls and transfers focus to controls
                    if (!showControls) {
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                            android.view.KeyEvent.KEYCODE_DPAD_UP,
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                            android.view.KeyEvent.KEYCODE_MENU -> {
                                showControls = true
                                controlsInteractionCount++
                                return@onKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (player != null) {
                                    val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                                    player.seekTo(target)
                                    currentPosition = target
                                    showControls = true
                                    controlsInteractionCount++
                                }
                                return@onKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
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
                                    showControls = true
                                    controlsInteractionCount++
                                }
                                return@onKeyEvent true
                            }
                        }
                        return@onKeyEvent false
                    }

                    // When controls ARE VISIBLE:
                    // Allow Compose focus system to navigate DPAD_UP / DPAD_DOWN / DPAD_LEFT / DPAD_RIGHT
                    // across all control buttons!
                    if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                        showControls = false
                        try {
                            playerFocusRequester.requestFocus()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        return@onKeyEvent true
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                        showControls = false
                        return@onKeyEvent true
                    }

                    false
                }
                .pointerInput(isScreenLocked, isFullscreen) {
                    if (isScreenLocked) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val isLeft = offset.x < size.width * 0.5f
                            if (isLeft) {
                                isDraggingBrightness = true
                                isDraggingVolume = false
                                showBrightnessHUD = true
                                showVolumeHUD = false
                                val currentB = activity?.window?.attributes?.screenBrightness ?: -1f
                                brightnessLevel = if (currentB >= 0f) currentB else 0.5f
                            } else {
                                isDraggingBrightness = false
                                isDraggingVolume = true
                                showVolumeHUD = true
                                showBrightnessHUD = false
                                val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
                                volumeLevel = curVol.toFloat() / maxVol.toFloat()
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val totalHeight = size.height.toFloat().coerceAtLeast(100f)
                            val delta = -dragAmount / (totalHeight * 0.7f)

                            if (isDraggingBrightness) {
                                brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1.0f)
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    lp.screenBrightness = brightnessLevel
                                    act.window.attributes = lp
                                }
                                showBrightnessHUD = true
                            } else if (isDraggingVolume) {
                                volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                                val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
                                val targetVol = (volumeLevel * maxVol).roundToInt().coerceIn(0, maxVol)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                exoPlayer?.volume = if (targetVol == 0) 0f else 1f
                                isMuted = (targetVol == 0)
                                showVolumeHUD = true
                            }
                        },
                        onDragEnd = {
                            isDraggingBrightness = false
                            isDraggingVolume = false
                        },
                        onDragCancel = {
                            isDraggingBrightness = false
                            isDraggingVolume = false
                        }
                    )
                }
                .pointerInput(exoPlayer, isFullscreen, isScreenLocked) {
                    detectTapGestures(
                        onTap = {
                            if (isScreenLocked) {
                                showLockOverlay = !showLockOverlay
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
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        applyExoPlayerAspectRatio(this, exoPlayer, resizeMode, currentVideoSize)

                        subtitleView?.let { subView ->
                            subView.visibility = View.VISIBLE
                            subView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.25f)
                            subView.setBottomPaddingFraction(0.08f)
                            subView.setStyle(
                                CaptionStyleCompat(
                                    android.graphics.Color.WHITE,
                                    android.graphics.Color.argb(190, 0, 0, 0),
                                    android.graphics.Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    android.graphics.Typeface.DEFAULT_BOLD
                                )
                            )
                            subView.setApplyEmbeddedStyles(true)
                            subView.setApplyEmbeddedFontSizes(true)
                        }
                        playerViewRef = this
                    }
                },
                update = { playerView ->
                    playerViewRef = playerView
                    playerView.player = exoPlayer
                    playerView.keepScreenOn = isPlaying

                    applyExoPlayerAspectRatio(playerView, exoPlayer, resizeMode, currentVideoSize)

                    playerView.subtitleView?.let { subView ->
                        subView.visibility = View.VISIBLE
                        subView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.25f)
                        subView.setBottomPaddingFraction(0.08f)
                        subView.setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.argb(190, 0, 0, 0),
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                android.graphics.Typeface.DEFAULT_BOLD
                            )
                        )
                        subView.setApplyEmbeddedStyles(true)
                        subView.setApplyEmbeddedFontSizes(true)
                    }
                },
                onRelease = { playerView ->
                    try {
                        playerView.player = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Compose Subtitle Cues Overlay (Displays crisp high-visibility subtitles directly)
            if (selectedSubtitleLabel != "Off" && currentCues.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = if (isFullscreen) 52.dp else 28.dp,
                            start = 24.dp,
                            end = 24.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        currentCues.forEach { cue ->
                            val cueText = cue.text?.toString()
                            if (!cueText.isNullOrBlank()) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = cueText,
                                        color = Color.White,
                                        fontSize = if (isFullscreen) 18.sp else 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Loading spinner
            if (isBuffering) {
                CircularProgressIndicator(
                    color = Color(0xFF2B62F6),
                    strokeWidth = 3.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            val isSystemPip = isInPipMode || (activity?.isInPictureInPictureMode == true)

            // Brightness HUD Indicator Overlay (Left side)
            AnimatedVisibility(
                visible = showBrightnessHUD && !isSystemPip,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = if (isFullscreen) 48.dp else 24.dp)
            ) {
                GestureLevelIndicator(
                    icon = if (brightnessLevel > 0.65f) Icons.Default.BrightnessHigh
                           else if (brightnessLevel > 0.3f) Icons.Default.BrightnessMedium
                           else Icons.Default.BrightnessLow,
                    label = "Brightness",
                    level = brightnessLevel
                )
            }

            // Volume HUD Indicator Overlay (Right side)
            AnimatedVisibility(
                visible = showVolumeHUD && !isSystemPip,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = if (isFullscreen) 48.dp else 24.dp)
            ) {
                GestureLevelIndicator(
                    icon = if (volumeLevel <= 0.01f || isMuted) Icons.Default.VolumeOff
                           else if (volumeLevel > 0.5f) Icons.Default.VolumeUp
                           else Icons.Default.VolumeDown,
                    label = "Volume",
                    level = if (isMuted) 0f else volumeLevel
                )
            }

            // Custom Overlay Controls (Hidden during System Picture-in-Picture)
            if (!isSystemPip) {
                AnimatedVisibility(
                    visible = showControls && !isScreenLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
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
                        // Aspect Ratio Toast HUD
                        AnimatedVisibility(
                            visible = aspectToastText != null,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    val (badgeIcon, badgeColor) = when (resizeMode) {
                                        VideoResizeMode.FIT -> Pair(Icons.Default.FitScreen, Color(0xFF00E5FF))
                                        VideoResizeMode.FIXED_WIDTH -> Pair(Icons.Default.Tv, Color(0xFF2B62F6))
                                        VideoResizeMode.FILL -> Pair(Icons.Default.Fullscreen, Color(0xFF4CAF50))
                                        VideoResizeMode.ZOOM -> Pair(Icons.Default.Crop, Color(0xFFFF9800))
                                    }
                                    Icon(
                                        imageVector = badgeIcon,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = aspectToastText ?: "",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

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
                                    .dpadFocusable(shape = CircleShape, focusedBorderColor = Color.White, scaleOnFocus = 1.15f)
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
                                modifier = Modifier
                                    .size(32.dp)
                                    .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF), scaleOnFocus = 1.15f)
                            ) {
                                Icon(
                                    painter = painterResource(androidx.media3.ui.R.drawable.exo_styled_controls_audiotrack),
                                    contentDescription = "Language",
                                    tint = if (selectedAudioLabel != "Default / Auto") Color(0xFF00E5FF) else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = { showQualityDialog = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF), scaleOnFocus = 1.15f)
                            ) {
                                Icon(
                                    painter = painterResource(androidx.media3.ui.R.drawable.exo_styled_controls_settings),
                                    contentDescription = "Tune",
                                    tint = if (selectedQualityLabel != "Auto") Color(0xFF00E5FF) else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = { showCaptionDialog = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF), scaleOnFocus = 1.15f)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (selectedSubtitleLabel != "Off") androidx.media3.ui.R.drawable.exo_styled_controls_subtitle_on
                                        else androidx.media3.ui.R.drawable.exo_styled_controls_subtitle_off
                                    ),
                                    contentDescription = "CC",
                                    tint = if (selectedSubtitleLabel != "Off") Color(0xFF00E5FF) else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Lock screen toggle in Landscape mode (right of caption / CC button)
                            if (isFullscreen) {
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        isScreenLocked = true
                                        showControls = false
                                        showLockOverlay = true
                                        aspectToastText = "Screen Locked"
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF), scaleOnFocus = 1.15f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Lock Screen",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
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
                                    controlsInteractionCount++
                                    val player = exoPlayer
                                    if (player != null) {
                                        val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                                        player.seekTo(target)
                                        currentPosition = target
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF), scaleOnFocus = 1.15f)
                            ) {
                                Icon(
                                    painter = painterResource(androidx.media3.ui.R.drawable.exo_styled_controls_rewind),
                                    contentDescription = "-10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(28.dp))

                            IconButton(
                                onClick = {
                                    controlsInteractionCount++
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
                                    .focusRequester(playPauseFocusRequester)
                                    .dpadFocusable(shape = CircleShape, focusedBorderColor = Color.White, scaleOnFocus = 1.15f)
                            ) {
                                if (isBuffering) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(
                                            if (isPlaying) androidx.media3.ui.R.drawable.exo_styled_controls_pause
                                            else androidx.media3.ui.R.drawable.exo_styled_controls_play
                                        ),
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(28.dp))

                            IconButton(
                                onClick = {
                                    controlsInteractionCount++
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
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF), scaleOnFocus = 1.15f)
                            ) {
                                Icon(
                                    painter = painterResource(androidx.media3.ui.R.drawable.exo_styled_controls_fastforward),
                                    contentDescription = "+10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
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
                                        controlsInteractionCount++
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
                                            controlsInteractionCount++
                                            isMuted = !isMuted
                                            exoPlayer?.volume = if (isMuted) 0f else 1f
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF), scaleOnFocus = 1.15f)
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
                                        modifier = Modifier
                                            .dpadFocusable(
                                                shape = RoundedCornerShape(8.dp),
                                                focusedBorderColor = Color(0xFF00E5FF),
                                                scaleOnFocus = 1.1f
                                            )
                                            .clickable { showQualityDialog = true }
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

                                    // Playback Speed Button (between Quality and Aspect Ratio)
                                    Surface(
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .dpadFocusable(
                                                shape = RoundedCornerShape(8.dp),
                                                focusedBorderColor = Color(0xFF00E5FF),
                                                scaleOnFocus = 1.1f
                                            )
                                            .clickable { showSpeedDialog = true }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Speed,
                                                contentDescription = "Playback Speed",
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    val (aspectIcon, aspectLabel) = when (resizeMode) {
                                        VideoResizeMode.FIT -> Pair(Icons.Default.FitScreen, "Fit")
                                        VideoResizeMode.FIXED_WIDTH -> Pair(Icons.Default.Tv, "16:9")
                                        VideoResizeMode.FILL -> Pair(Icons.Default.Fullscreen, "Fill")
                                        VideoResizeMode.ZOOM -> Pair(Icons.Default.Crop, "Zoom")
                                    }

                                    IconButton(
                                        onClick = {
                                            resizeMode = when (resizeMode) {
                                                VideoResizeMode.FIT -> {
                                                    aspectToastText = "Aspect: 16:9"
                                                    VideoResizeMode.FIXED_WIDTH
                                                }
                                                VideoResizeMode.FIXED_WIDTH -> {
                                                    aspectToastText = "Aspect: Fill"
                                                    VideoResizeMode.FILL
                                                }
                                                VideoResizeMode.FILL -> {
                                                    aspectToastText = "Aspect: Zoom"
                                                    VideoResizeMode.ZOOM
                                                }
                                                VideoResizeMode.ZOOM -> {
                                                    aspectToastText = "Aspect: Fit"
                                                    VideoResizeMode.FIT
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF), scaleOnFocus = 1.1f)
                                    ) {
                                        Icon(
                                            imageVector = aspectIcon,
                                            contentDescription = "Aspect Ratio: $aspectLabel",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(
                                        onClick = {
                                            onEnterPipClick()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF), scaleOnFocus = 1.1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureInPictureAlt,
                                            contentDescription = "Picture in Picture",
                                            tint = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { onFullscreenToggle(!isFullscreen) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF), scaleOnFocus = 1.1f)
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                if (isFullscreen) androidx.media3.ui.R.drawable.exo_styled_controls_fullscreen_exit
                                                else androidx.media3.ui.R.drawable.exo_styled_controls_fullscreen_enter
                                            ),
                                            contentDescription = "Fullscreen",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Lock Screen UI Overlay (when screen is locked in landscape mode)
            AnimatedVisibility(
                visible = isScreenLocked && showLockOverlay,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showLockOverlay = false
                        }
                        .padding(16.dp)
                ) {
                    // Floating Unlock Button on the left side
                    Surface(
                        color = Color(0xFF131B2E).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .clickable {
                                isScreenLocked = false
                                showLockOverlay = false
                                showControls = true
                                aspectToastText = "Screen Unlocked"
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Unlock Screen",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Tap to Unlock",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Top notification badge indicating screen is locked
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Screen Locked — Accidental touches blocked",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // In-Player Error State Overlay with Retry Button
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Playback Issue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "Stream is currently unavailable",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.widthIn(max = 380.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    errorMessage = null
                                    isBuffering = true
                                    retryCount++
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2B62F6)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Retry Stream",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                                    .dpadFocusable(
                                        shape = RoundedCornerShape(8.dp),
                                        focusedBorderColor = Color(0xFF00E5FF),
                                        scaleOnFocus = 1.03f
                                    )
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
                TextButton(
                    onClick = { showQualityDialog = false },
                    modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), focusedBorderColor = Color(0xFF2B62F6))
                ) {
                    Text("Close", color = Color(0xFF2B62F6))
                }
            },
            containerColor = Color(0xFF131B2E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    if (showSpeedDialog) {
        val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Playback Speed",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    speedOptions.forEach { speed ->
                        val isSelected = playbackSpeed == speed
                        val label = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    scaleOnFocus = 1.03f
                                )
                                .clickable {
                                    playbackSpeed = speed
                                    exoPlayer?.setPlaybackSpeed(speed)
                                    aspectToastText = "Speed: ${speed}x"
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
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
                TextButton(
                    onClick = { showSpeedDialog = false },
                    modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), focusedBorderColor = Color(0xFF2B62F6))
                ) {
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
                                .dpadFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    scaleOnFocus = 1.03f
                                )
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
                TextButton(
                    onClick = { showLanguageDialog = false },
                    modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), focusedBorderColor = Color(0xFF2B62F6))
                ) {
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
                    val captionsToShow = subtitleTrackOptions

                    if (captionsToShow.size <= 1) {
                        Text(
                            text = "No additional captions/subtitles available in this stream.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }

                    captionsToShow.forEach { option ->
                        val isSelected = selectedSubtitleLabel == option.label
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    scaleOnFocus = 1.03f
                                )
                                .clickable {
                                    selectedSubtitleLabel = option.label
                                    showCaptionDialog = false

                                    val player = exoPlayer
                                    if (player != null) {
                                        val builder = player.trackSelectionParameters.buildUpon()
                                        if (option.isOff) {
                                            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                            selectedSubtitleLabel = "Off"
                                            currentCues = emptyList()
                                        } else {
                                            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                            if (option.group != null) {
                                                val override = TrackSelectionOverride(option.group.mediaTrackGroup, listOf(option.trackIndex))
                                                builder.setOverrideForType(override)
                                                selectedSubtitleLabel = option.label
                                            } else if (option.language == "auto") {
                                                selectedSubtitleLabel = "Auto"
                                            } else {
                                                builder.setPreferredTextLanguage(option.language.lowercase())
                                                selectedSubtitleLabel = option.label
                                            }
                                            builder.setIgnoredTextSelectionFlags(0)
                                        }
                                        player.trackSelectionParameters = builder.build()
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
                TextButton(
                    onClick = { showCaptionDialog = false },
                    modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), focusedBorderColor = Color(0xFF2B62F6))
                ) {
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
    onEnterPipClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isPip = isInPipMode || (activity?.isInPictureInPictureMode == true)
    var showIframeControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    var aspectToastText by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showLockOverlay by remember { mutableStateOf(false) }
    val iframeFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }
    var controlsInteractionCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            iframeFocusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(showIframeControls) {
        if (showIframeControls) {
            delay(50L)
            try {
                backFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        } else {
            try {
                iframeFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(resizeMode, isFullscreen) {
        val js = getIframeScalingJs(resizeMode)
        webViewRef?.evaluateJavascript(js, null)
    }

    LaunchedEffect(aspectToastText) {
        if (aspectToastText != null) {
            delay(1500L)
            aspectToastText = null
        }
    }

    LaunchedEffect(showIframeControls, isPip, controlsInteractionCount) {
        if (isPip && showIframeControls) {
            delay(3000)
            showIframeControls = false
        }
    }

    // Auto hide lock overlay when screen is locked after 2.5 seconds
    LaunchedEffect(showLockOverlay, isScreenLocked) {
        if (showLockOverlay && isScreenLocked) {
            delay(2500L)
            showLockOverlay = false
        }
    }

    // Auto unlock if exiting landscape / fullscreen mode
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            isScreenLocked = false
            showLockOverlay = false
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

    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var brightnessLevel by remember {
        val b = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (b >= 0f) b else 0.5f)
    }
    var volumeLevel by remember {
        val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 8
        val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
        mutableFloatStateOf(curVol.toFloat() / maxVol.toFloat())
    }
    var showBrightnessHUD by remember { mutableStateOf(false) }
    var showVolumeHUD by remember { mutableStateOf(false) }
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var isDraggingVolume by remember { mutableStateOf(false) }

    LaunchedEffect(showBrightnessHUD, isDraggingBrightness) {
        if (showBrightnessHUD && !isDraggingBrightness) {
            delay(1200L)
            showBrightnessHUD = false
        }
    }

    LaunchedEffect(showVolumeHUD, isDraggingVolume) {
        if (showVolumeHUD && !isDraggingVolume) {
            delay(1200L)
            showVolumeHUD = false
        }
    }

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    // Observe lifecycle to pause/resume/destroy WebView audio and timers
    val iframeLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(iframeLifecycleOwner, webViewRef, webViewInstance) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val inPip = isInPipMode || (activity?.isInPictureInPictureMode == true)
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    if (!inPip) {
                        try {
                            webViewRef?.onPause()
                            webViewRef?.pauseTimers()
                            webViewInstance?.onPause()
                            webViewInstance?.pauseTimers()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    try {
                        webViewRef?.onResume()
                        webViewRef?.resumeTimers()
                        webViewInstance?.onResume()
                        webViewInstance?.resumeTimers()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                    try {
                        webViewRef?.loadUrl("about:blank")
                        webViewRef?.stopLoading()
                        webViewRef?.destroy()
                        webViewInstance?.loadUrl("about:blank")
                        webViewInstance?.stopLoading()
                        webViewInstance?.destroy()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                else -> {}
            }
        }
        iframeLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            iframeLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                            settings.databaseEnabled = true
                            settings.setSupportMultipleWindows(false)
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString() ?: return false
                                    // Block external intents or market links attempting to hijack stream
                                    if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
                                        return true
                                    }
                                    if (targetUrl.contains("play.google.com", ignoreCase = true) || targetUrl.contains("market://", ignoreCase = true)) {
                                        return true
                                    }
                                    return false
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, targetUrl: String?): Boolean {
                                    if (targetUrl == null) return false
                                    if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
                                        return true
                                    }
                                    if (targetUrl.contains("play.google.com", ignoreCase = true) || targetUrl.contains("market://", ignoreCase = true)) {
                                        return true
                                    }
                                    return false
                                }

                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                    try {
                                        (view?.parent as? ViewGroup)?.removeView(view)
                                        view?.destroy()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    return true
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                    return false
                                }

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
                            webViewRef = this
                            webViewInstance = this
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                        webViewInstance = webView
                    },
                    onRelease = { webView ->
                        try {
                            webView.stopLoading()
                            webView.loadUrl("about:blank")
                            webView.onPause()
                            webView.pauseTimers()
                            webView.removeAllViews()
                            webView.destroy()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
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
                            text = title.ifEmpty { "Iframe Stream" },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                                .clickable { onMiniPlayerToggle(true) }
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onCloseClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onEnterPipClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "System PiP",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onMiniPlayerToggle(true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Expand",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = (if (isFullscreen) {
                modifier
                    .fillMaxSize()
                    .background(Color.Black)
            } else {
                modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            })
            .focusRequester(iframeFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                if (!showIframeControls) {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                        android.view.KeyEvent.KEYCODE_MENU -> {
                            showIframeControls = true
                            controlsInteractionCount++
                            return@onKeyEvent true
                        }
                    }
                    return@onKeyEvent false
                }

                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    showIframeControls = false
                    try {
                        iframeFocusRequester.requestFocus()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return@onKeyEvent true
                } else if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                    showIframeControls = false
                    return@onKeyEvent true
                }

                false
            }
            .pointerInput(isScreenLocked, isFullscreen) {
                if (isScreenLocked) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val isLeft = offset.x < size.width * 0.5f
                        if (isLeft) {
                            isDraggingBrightness = true
                            isDraggingVolume = false
                            showBrightnessHUD = true
                            showVolumeHUD = false
                            val currentB = activity?.window?.attributes?.screenBrightness ?: -1f
                            brightnessLevel = if (currentB >= 0f) currentB else 0.5f
                        } else {
                            isDraggingBrightness = false
                            isDraggingVolume = true
                            showVolumeHUD = true
                            showBrightnessHUD = false
                            val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                            val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
                            volumeLevel = curVol.toFloat() / maxVol.toFloat()
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val totalHeight = size.height.toFloat().coerceAtLeast(100f)
                        val delta = -dragAmount / (totalHeight * 0.7f)

                        if (isDraggingBrightness) {
                            brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1.0f)
                            activity?.let { act ->
                                val lp = act.window.attributes
                                lp.screenBrightness = brightnessLevel
                                act.window.attributes = lp
                            }
                            showBrightnessHUD = true
                        } else if (isDraggingVolume) {
                            volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                            val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
                            val targetVol = (volumeLevel * maxVol).roundToInt().coerceIn(0, maxVol)
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            showVolumeHUD = true
                        }
                    },
                    onDragEnd = {
                        isDraggingBrightness = false
                        isDraggingVolume = false
                    },
                    onDragCancel = {
                        isDraggingBrightness = false
                        isDraggingVolume = false
                    }
                )
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
                        settings.databaseEnabled = true
                        settings.setSupportMultipleWindows(false)
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val targetUrl = request?.url?.toString() ?: return false
                                // Block external intents or market links attempting to hijack stream
                                if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
                                    return true
                                }
                                if (targetUrl.contains("play.google.com", ignoreCase = true) || targetUrl.contains("market://", ignoreCase = true)) {
                                    return true
                                }
                                return false
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, targetUrl: String?): Boolean {
                                if (targetUrl == null) return false
                                if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
                                    return true
                                }
                                if (targetUrl.contains("play.google.com", ignoreCase = true) || targetUrl.contains("market://", ignoreCase = true)) {
                                    return true
                                }
                                return false
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                try {
                                    (view?.parent as? ViewGroup)?.removeView(view)
                                    view?.destroy()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                return false
                            }

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
                        webViewRef = this
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    webViewInstance = webView
                    val js = getIframeScalingJs(resizeMode)
                    webView.evaluateJavascript(js, null)
                },
                onRelease = { webView ->
                    try {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.onPause()
                        webView.pauseTimers()
                        webView.removeAllViews()
                        webView.destroy()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            val isSystemPip = isInPipMode || (activity?.isInPictureInPictureMode == true)

            // Brightness HUD Indicator Overlay (Left side)
            AnimatedVisibility(
                visible = showBrightnessHUD && !isSystemPip,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = if (isFullscreen) 48.dp else 24.dp)
            ) {
                GestureLevelIndicator(
                    icon = if (brightnessLevel > 0.65f) Icons.Default.BrightnessHigh
                           else if (brightnessLevel > 0.3f) Icons.Default.BrightnessMedium
                           else Icons.Default.BrightnessLow,
                    label = "Brightness",
                    level = brightnessLevel
                )
            }

            // Volume HUD Indicator Overlay (Right side)
            AnimatedVisibility(
                visible = showVolumeHUD && !isSystemPip,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = if (isFullscreen) 48.dp else 24.dp)
            ) {
                GestureLevelIndicator(
                    icon = if (volumeLevel <= 0.01f) Icons.Default.VolumeOff
                           else if (volumeLevel > 0.5f) Icons.Default.VolumeUp
                           else Icons.Default.VolumeDown,
                    label = "Volume",
                    level = volumeLevel
                )
            }

            if (!isSystemPip) {
                // Aspect Ratio Toast HUD
                AnimatedVisibility(
                    visible = aspectToastText != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            val (badgeIcon, badgeColor) = when (resizeMode) {
                                VideoResizeMode.FIT -> Pair(Icons.Default.FitScreen, Color(0xFF00E5FF))
                                VideoResizeMode.FIXED_WIDTH -> Pair(Icons.Default.Tv, Color(0xFF2B62F6))
                                VideoResizeMode.FILL -> Pair(Icons.Default.Fullscreen, Color(0xFF4CAF50))
                                VideoResizeMode.ZOOM -> Pair(Icons.Default.Crop, Color(0xFFFF9800))
                            }
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = aspectToastText ?: "",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (!isScreenLocked) {
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
                                .focusRequester(backFocusRequester)
                                .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF))
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

                        // Playback Speed Button
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .dpadFocusable(shape = RoundedCornerShape(8.dp), focusedBorderColor = Color(0xFF00E5FF))
                                .clickable { showSpeedDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        val (aspectIcon, aspectLabel) = when (resizeMode) {
                            VideoResizeMode.FIT -> Pair(Icons.Default.FitScreen, "Fit")
                            VideoResizeMode.FIXED_WIDTH -> Pair(Icons.Default.Tv, "16:9")
                            VideoResizeMode.FILL -> Pair(Icons.Default.Fullscreen, "Fill")
                            VideoResizeMode.ZOOM -> Pair(Icons.Default.Crop, "Zoom")
                        }

                        IconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    VideoResizeMode.FIT -> {
                                        aspectToastText = "Aspect: 16:9"
                                        VideoResizeMode.FIXED_WIDTH
                                    }
                                    VideoResizeMode.FIXED_WIDTH -> {
                                        aspectToastText = "Aspect: Fill"
                                        VideoResizeMode.FILL
                                    }
                                    VideoResizeMode.FILL -> {
                                        aspectToastText = "Aspect: Zoom"
                                        VideoResizeMode.ZOOM
                                    }
                                    VideoResizeMode.ZOOM -> {
                                        aspectToastText = "Aspect: Fit"
                                        VideoResizeMode.FIT
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF))
                        ) {
                            Icon(
                                imageVector = aspectIcon,
                                contentDescription = "Aspect Ratio: $aspectLabel",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        if (isFullscreen) {
                            IconButton(
                                onClick = {
                                    isScreenLocked = true
                                    showLockOverlay = true
                                    aspectToastText = "Screen Locked"
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Screen",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = { onFullscreenToggle(!isFullscreen) },
                            modifier = Modifier
                                .size(32.dp)
                                .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF))
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isFullscreen) androidx.media3.ui.R.drawable.exo_styled_controls_fullscreen_exit
                                    else androidx.media3.ui.R.drawable.exo_styled_controls_fullscreen_enter
                                ),
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { onEnterPipClick() },
                            modifier = Modifier
                                .size(32.dp)
                                .dpadFocusable(shape = CircleShape, focusedBorderColor = Color(0xFF00E5FF))
                        ) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                        }
                    }
                }

                // Lock Screen UI Overlay (when screen is locked in landscape mode)
                AnimatedVisibility(
                    visible = isScreenLocked && showLockOverlay,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showLockOverlay = false
                            }
                            .padding(16.dp)
                    ) {
                        Surface(
                            color = Color(0xFF131B2E).copy(alpha = 0.92f),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                                .dpadFocusable(shape = RoundedCornerShape(24.dp), focusedBorderColor = Color(0xFF00E5FF))
                                .clickable {
                                    isScreenLocked = false
                                    showLockOverlay = false
                                    aspectToastText = "Screen Unlocked"
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Unlock Screen",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Tap to Unlock",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.6f)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Screen Locked — Accidental touches blocked",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                if (showSpeedDialog) {
                    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                    AlertDialog(
                        onDismissRequest = { showSpeedDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Playback Speed",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                speedOptions.forEach { speed ->
                                    val isSelected = playbackSpeed == speed
                                    val label = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .dpadFocusable(
                                                shape = RoundedCornerShape(8.dp),
                                                focusedBorderColor = Color(0xFF00E5FF),
                                                scaleOnFocus = 1.03f
                                            )
                                            .clickable {
                                                playbackSpeed = speed
                                                webViewRef?.evaluateJavascript(
                                                    "document.querySelectorAll('video').forEach(function(v){v.playbackRate = $speed;});",
                                                    null
                                                )
                                                aspectToastText = "Speed: ${speed}x"
                                                showSpeedDialog = false
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
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
                            TextButton(
                                onClick = { showSpeedDialog = false },
                                modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), focusedBorderColor = Color(0xFF2B62F6))
                            ) {
                                Text("Close", color = Color(0xFF2B62F6))
                            }
                        },
                        containerColor = Color(0xFF131B2E),
                        titleContentColor = Color.White,
                        textContentColor = Color.White
                    )
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val currentFraction = if (isDragging) dragFraction else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .dpadFocusable(
                shape = RoundedCornerShape(14.dp),
                focusedBorderColor = Color(0xFF00E5FF),
                scaleOnFocus = 1.02f,
                interactionSource = interactionSource
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val target = (positionMs - 10000L).coerceAtLeast(0L)
                        onSeek(target)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val target = (positionMs + 10000L).coerceAtMost(durationMs)
                        onSeek(target)
                        true
                    }
                    else -> false
                }
            }
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
        val trackHeight = if (isFocused) 6.dp else 3.dp

        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(Color.White.copy(alpha = if (isFocused) 0.5f else 0.35f))
        )
        // Active progress track
        Box(
            modifier = Modifier
                .fillMaxWidth(currentFraction)
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(if (isFocused) Color(0xFF00E5FF) else Color(0xFF2B62F6))
        )
        // Thumb handle
        val density = LocalDensity.current
        val thumbOffsetDp = with(density) { thumbPx.toDp() }
        val thumbSize = if (isFocused) 16.dp else 10.dp
        Box(
            modifier = Modifier
                .offset(x = (thumbOffsetDp - (thumbSize / 2)).coerceAtLeast(0.dp))
                .size(thumbSize)
                .background(if (isFocused) Color(0xFF00E5FF) else Color(0xFF2B62F6), CircleShape)
                .then(
                    if (isFocused) Modifier.border(2.dp, Color.White, CircleShape)
                    else Modifier
                )
        )
    }
}

@Composable
fun GestureLevelIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    level: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.88f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.7f)),
        shadowElevation = 10.dp,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(level.coerceIn(0f, 1f))
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF00E5FF), Color(0xFF2B62F6))
                            )
                        )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${(level * 100).toInt()}%",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
