package com.example.suretouchapp.ui.theme

import android.app.Activity
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
    primaryContainer = SurePurpleDark,
    onPrimaryContainer = SurePurpleContainer,
    secondary = SureLimeSecondary,
    onSecondary = Color.Black,
    secondaryContainer = SureSurfaceVariantDark,
    onSecondaryContainer = SureLimeContainer,
    tertiary = SurePinkAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF831843),
    onTertiaryContainer = Color(0xFFFCE7F3),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = SureBackgroundDark,
    onBackground = Color(0xFFF8FAFC),
    surface = SureSurfaceDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = SureSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF1E293B),
    inversePrimary = SurePurpleDark,
    scrim = Color.Black
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
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCE7F3),
    onTertiaryContainer = Color(0xFF831843),
    error = Color(0xFFB91C1C),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = SureBackgroundLight,
    onBackground = Color(0xFF0F172A),
    surface = SureSurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = SureSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF8FAFC),
    inversePrimary = SurePurpleLight,
    scrim = Color.Black
)

@Composable
fun SureTouchAPPTheme(
    darkTheme: Boolean = false, // Default to Light theme
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
