package com.example.suretouchapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Uses the untouched official logo with a separate animated progress ring around it. */
@Composable
fun SureTrustLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 74.dp,
    logoSize: Dp = 48.dp,
    spinnerColor: Color = MaterialTheme.colorScheme.primary,
    message: String? = null
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = spinnerColor,
                strokeWidth = if (size <= 36.dp) 2.dp else 3.dp
            )
            SureTrustLogo(size = logoSize, showSubtext = false)
        }
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
