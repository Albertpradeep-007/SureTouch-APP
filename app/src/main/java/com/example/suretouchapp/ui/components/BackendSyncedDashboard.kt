package com.example.suretouchapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Global dashboard state transition. Loading is controlled only by the active backend request;
 * there is no artificial minimum duration and no directional movement.
 */
@Composable
fun BackendSyncedDashboard(
    isLoading: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = isLoading,
        transitionSpec = {
            if (targetState) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                fadeIn(tween(220)) togetherWith fadeOut(tween(0))
            }
        },
        contentAlignment = Alignment.TopCenter,
        modifier = modifier,
        label = "backend_synced_dashboard"
    ) { showSkeleton ->
        if (showSkeleton) StandardDashboardSkeleton() else content()
    }
}

@Composable
fun StandardDashboardSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        SureTrustLoadingIndicator(
            size = 80.dp,
            logoSize = 52.dp,
            message = "Loading SURE Trust Portal..."
        )
    }
}
