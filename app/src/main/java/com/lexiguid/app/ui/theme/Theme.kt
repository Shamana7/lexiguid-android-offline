package com.lexiguid.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = LexiPrimary,
    onPrimary = LexiOnPrimary,
    primaryContainer = LexiPrimaryContainer,
    onPrimaryContainer = LexiOnPrimaryContainer,
    secondary = LexiSecondary,
    onSecondary = LexiOnSecondary,
    tertiary = LexiTertiary,
    onTertiary = LexiOnTertiary,
    error = LexiError,
    onError = LexiOnError,
    surface = LexiSurfaceLight,
    onSurface = LexiOnSurfaceLight,
    surfaceVariant = LexiSurfaceVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = LexiPrimary,
    onPrimary = LexiOnPrimary,
    primaryContainer = LexiOnPrimaryContainer,
    onPrimaryContainer = LexiPrimaryContainer,
    secondary = LexiSecondary,
    onSecondary = LexiOnSecondary,
    tertiary = LexiTertiary,
    onTertiary = LexiOnTertiary,
    error = LexiError,
    onError = LexiOnError,
    surface = LexiSurfaceDark,
    onSurface = LexiOnSurfaceDark,
    surfaceVariant = LexiSurfaceVariantDark
)

@Composable
fun LexiGuidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LexiGuidTypography,
        content = content
    )
}
