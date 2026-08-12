package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CineColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardBackground: Color,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val outline: Color,
    val liveRed: Color,
    val liveRedBg: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

// Dark Theme Color Palette matching the reference design image
val DarkCineColors = CineColors(
    background = Color(0xFF0A0E1A),        // Sleek deep navy background
    surface = Color(0xFF121829),           // Dark blue-gray card surface
    surfaceVariant = Color(0xFF171F33),    // Top & Bottom navigation bar surface
    cardBackground = Color(0xFF121829),
    primary = Color(0xFF2563EB),           // Vibrant electric blue accent
    primaryContainer = Color(0xFF1E293B),  // Icon container / Pill background
    onPrimaryContainer = Color(0xFF60A5FA),// High contrast blue on primary container
    secondary = Color(0xFF94A3B8),
    outline = Color(0xFF1E293B),           // Card outline border
    liveRed = Color(0xFFEF4444),           // Vibrant Live Badge Red
    liveRedBg = Color(0xFF3F1115),
    textPrimary = Color(0xFFFFFFFF),       // Crisp white text
    textSecondary = Color(0xFF94A3B8)      // Muted slate text
)

// Light Theme Color Palette
val LightCineColors = CineColors(
    background = Color(0xFFFEF7FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3EDF7),
    cardBackground = Color(0xFFFFFFFF),
    primary = Color(0xFF2563EB),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF625B71),
    outline = Color(0xFFE2E8F0),
    liveRed = Color(0xFFDC2626),
    liveRedBg = Color(0xFFFEF2F2),
    textPrimary = Color(0xFF1D1B20),
    textSecondary = Color(0xFF49454F)
)

val LocalCineColors = staticCompositionLocalOf { DarkCineColors }

// Dynamic Composable Color getters ensuring seamless instant theme toggling across all screens
val CineBackground: Color @Composable get() = LocalCineColors.current.background
val CineSurface: Color @Composable get() = LocalCineColors.current.surface
val CineSurfaceVariant: Color @Composable get() = LocalCineColors.current.surfaceVariant
val CineCardBackground: Color @Composable get() = LocalCineColors.current.cardBackground

val CinePrimary: Color @Composable get() = LocalCineColors.current.primary
val CinePrimaryContainer: Color @Composable get() = LocalCineColors.current.primaryContainer
val CineOnPrimaryContainer: Color @Composable get() = LocalCineColors.current.onPrimaryContainer

val CineSecondary: Color @Composable get() = LocalCineColors.current.secondary
val CineOutline: Color @Composable get() = LocalCineColors.current.outline

val CineLiveRed: Color @Composable get() = LocalCineColors.current.liveRed
val CineLiveRedBg: Color @Composable get() = LocalCineColors.current.liveRedBg

val CineTextPrimary: Color @Composable get() = LocalCineColors.current.textPrimary
val CineTextSecondary: Color @Composable get() = LocalCineColors.current.textSecondary

val CinePrimaryBlue: Color @Composable get() = LocalCineColors.current.primary
val CineAccentBlue: Color = Color(0xFF7D5260)


