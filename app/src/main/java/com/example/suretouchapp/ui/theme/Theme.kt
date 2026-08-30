package com.example.suretouchapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SurePurpleLight,
    onPrimary = Color.White,
    primaryContainer = SureSurfaceVariantDark,
    onPrimaryContainer = SurePurpleContainer,
    secondary = SureLimeSecondary,
    onSecondary = Color.Black,
    secondaryContainer = SureSurfaceVariantDark,
    onSecondaryContainer = SureLimeContainer,
    tertiary = SurePinkAccent,
    background = SureBackgroundDark,
    onBackground = Color.White,
    surface = SureSurfaceDark,
    onSurface = Color.White,
    surfaceVariant = SureSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFD8D2EC)
)

private val LightColorScheme = lightColorScheme(
    primary = SurePurplePrimary,
    onPrimary = Color.White,
    primaryContainer = SurePurpleContainer,
    onPrimaryContainer = SureOnPurpleContainer,
    secondary = SureLimeSecondary,
    onSecondary = Color.Black,
    secondaryContainer = SureLimeContainer,
    onSecondaryContainer = SureOnLimeContainer,
    tertiary = SurePinkAccent,
    background = SureBackgroundLight,
    onBackground = Color(0xFF1E1535),
    surface = SureSurfaceLight,
    onSurface = Color(0xFF1E1535),
    surfaceVariant = SureSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF4C3E75)
)

@Composable
fun SureTouchAPPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false so SURE TRUST brand colors are always displayed
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
        typography = Typography,
        content = content
    )
}
