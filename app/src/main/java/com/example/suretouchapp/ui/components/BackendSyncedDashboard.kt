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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    gridColumnCount: Int = 3,
    detailedGridCards: Boolean = true,
    heroHeight: androidx.compose.ui.unit.Dp = 244.dp,
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
        if (showSkeleton) {
            StandardDashboardSkeleton(
                gridColumnCount = gridColumnCount,
                detailedGridCards = detailedGridCards,
                heroHeight = heroHeight
            )
        } else {
            content()
        }
    }
}

@Composable
fun StandardDashboardSkeleton(
    modifier: Modifier = Modifier,
    gridColumnCount: Int = 3,
    detailedGridCards: Boolean = true,
    heroHeight: androidx.compose.ui.unit.Dp = 244.dp
) {
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
                modifier = Modifier.fillMaxWidth().height(heroHeight),
                shape = RoundedCornerShape(22.dp),
                baseColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
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
                    repeat(gridColumnCount.coerceAtLeast(1)) {
                        if (detailedGridCards) {
                            DashboardGridSkeletonCard(
                                modifier = Modifier.weight(1f).height(128.dp)
                            )
                        } else {
                            DashboardShimmerBox(
                                modifier = Modifier.weight(1f).height(112.dp),
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardGridSkeletonCard(modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DashboardShimmerBox(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.height(12.dp))
            DashboardShimmerBox(
                Modifier.fillMaxWidth(0.92f).height(10.dp),
                RoundedCornerShape(5.dp)
            )
            Spacer(Modifier.height(7.dp))
            DashboardShimmerBox(
                Modifier.fillMaxWidth(0.72f).height(9.dp),
                RoundedCornerShape(5.dp)
            )
        }
    }
}

@Composable
private fun DashboardShimmerBox(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant
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
        colors = listOf(
            baseColor,
            MaterialTheme.colorScheme.surface.copy(alpha = 0.99f),
            baseColor
        ),
        start = Offset(shimmerX - 360f, 0f),
        end = Offset(shimmerX, 460f)
    )
    Box(modifier = modifier.clip(shape).background(shimmerBrush))
}
