package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun CineArenaTheme(
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val cineColors = if (isDarkMode) DarkCineColors else LightCineColors

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDarkMode
                insetsController.isAppearanceLightNavigationBars = !isDarkMode
            }
        }
    }

    val colorScheme = if (isDarkMode) {
        darkColorScheme(
            primary = cineColors.primary,
            onPrimary = Color.White,
            primaryContainer = cineColors.primaryContainer,
            onPrimaryContainer = cineColors.onPrimaryContainer,
            secondary = cineColors.secondary,
            onSecondary = Color.White,
            tertiary = cineColors.liveRed,
            background = cineColors.background,
            onBackground = cineColors.textPrimary,
            surface = cineColors.surface,
            onSurface = cineColors.textPrimary,
            surfaceVariant = cineColors.surfaceVariant,
            onSurfaceVariant = cineColors.textSecondary,
            outline = cineColors.outline
        )
    } else {
        lightColorScheme(
            primary = cineColors.primary,
            onPrimary = Color.White,
            primaryContainer = cineColors.primaryContainer,
            onPrimaryContainer = cineColors.onPrimaryContainer,
            secondary = cineColors.secondary,
            onSecondary = Color.White,
            tertiary = cineColors.liveRed,
            background = cineColors.background,
            onBackground = cineColors.textPrimary,
            surface = cineColors.surface,
            onSurface = cineColors.textPrimary,
            surfaceVariant = cineColors.surfaceVariant,
            onSurfaceVariant = cineColors.textSecondary,
            outline = cineColors.outline
        )
    }

    CompositionLocalProvider(LocalCineColors provides cineColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}


