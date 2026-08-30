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
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardShimmerBox(Modifier.width(190.dp).height(27.dp))
                    DashboardShimmerBox(
                        Modifier.width(116.dp).height(14.dp),
                        RoundedCornerShape(7.dp)
                    )
                }
                DashboardShimmerBox(
                    Modifier.width(100.dp).height(38.dp),
                    RoundedCornerShape(19.dp)
                )
            }
        }
        item {
            DashboardShimmerBox(
                modifier = Modifier.fillMaxWidth().height(244.dp),
                shape = RoundedCornerShape(22.dp),
                baseColor = Color(0xFFD8D1F1)
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashboardShimmerBox(Modifier.width(138.dp).height(24.dp))
                DashboardShimmerBox(
                    Modifier.width(86.dp).height(28.dp),
                    RoundedCornerShape(14.dp)
                )
            }
        }
        repeat(3) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) {
                        DashboardShimmerBox(
                            modifier = Modifier.weight(1f).height(118.dp),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardShimmerBox(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    baseColor: Color = Color(0xFFE7E9EF)
) {
    val transition = rememberInfiniteTransition(label = "dashboard_skeleton_shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -700f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashboard_skeleton_shimmer_x"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseColor, Color.White.copy(alpha = 0.94f), baseColor),
        start = Offset(shimmerX - 360f, 0f),
        end = Offset(shimmerX, 460f)
    )
    Box(modifier = modifier.clip(shape).background(shimmerBrush))
}
