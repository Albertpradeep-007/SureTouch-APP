package com.example.suretouchapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Immutable
data class SureSemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

/** Shared status colours with WCAG-friendly foregrounds in both app themes. */
@Composable
fun sureSemanticColors(): SureSemanticColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        SureSemanticColors(
            success = Color(0xFF6EE7B7),
            successContainer = Color(0xFF064E3B),
            onSuccessContainer = Color(0xFFD1FAE5),
            warning = Color(0xFFFBBF24),
            warningContainer = Color(0xFF451A03),
            onWarningContainer = Color(0xFFFEF3C7),
            info = Color(0xFF7DD3FC),
            infoContainer = Color(0xFF0C4A6E),
            onInfoContainer = Color(0xFFE0F2FE)
        )
    } else {
        SureSemanticColors(
            success = Color(0xFF047857),
            successContainer = Color(0xFFECFDF5),
            onSuccessContainer = Color(0xFF065F46),
            warning = Color(0xFFB45309),
            warningContainer = Color(0xFFFFFBEB),
            onWarningContainer = Color(0xFF92400E),
            info = Color(0xFF0369A1),
            infoContainer = Color(0xFFF0F9FF),
            onInfoContainer = Color(0xFF075985)
        )
    }
}

object SureFormDefaults {
    /** One explicit input palette prevents inherited light text/label colours in dark mode. */
    @Composable
    fun outlinedTextFieldColors(): TextFieldColors {
        val colors = MaterialTheme.colorScheme
        return OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            disabledTextColor = colors.onSurface.copy(alpha = 0.58f),
            errorTextColor = colors.onSurface,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.55f),
            errorContainerColor = colors.surface,
            cursorColor = colors.primary,
            errorCursorColor = colors.error,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            disabledBorderColor = colors.outlineVariant,
            errorBorderColor = colors.error,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant,
            disabledLabelColor = colors.onSurfaceVariant.copy(alpha = 0.58f),
            errorLabelColor = colors.error,
            focusedPlaceholderColor = colors.onSurfaceVariant,
            unfocusedPlaceholderColor = colors.onSurfaceVariant,
            disabledPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.58f),
            errorPlaceholderColor = colors.onSurfaceVariant,
            focusedLeadingIconColor = colors.primary,
            unfocusedLeadingIconColor = colors.onSurfaceVariant,
            focusedTrailingIconColor = colors.primary,
            unfocusedTrailingIconColor = colors.onSurfaceVariant,
            disabledLeadingIconColor = colors.onSurfaceVariant.copy(alpha = 0.58f),
            disabledTrailingIconColor = colors.onSurfaceVariant.copy(alpha = 0.58f),
            errorLeadingIconColor = colors.error,
            errorTrailingIconColor = colors.error
        )
    }
}
