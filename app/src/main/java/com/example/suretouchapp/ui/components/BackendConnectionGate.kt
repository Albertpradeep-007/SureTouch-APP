package com.example.suretouchapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composition local providing the real-time backend connection status to all child views.
 * When false, mutations/edits are locked in read-only mode until connection is re-established.
 */
val LocalBackendConnected = staticCompositionLocalOf { true }
val LocalHasBackendGate = staticCompositionLocalOf { false }

/**
 * Clean & Crisp Top Red/Crimson Offline Connectivity Bar (WhatsApp style).
 * 
 * Features:
 * - Proper insets so the top bar sits perfectly below device status bar / notch.
 * - Rich Crimson/Red background (Color 0xFF991B1B) with crisp white text.
 * - Non-intrusive: UI below renders completely in read-only offline mode.
 * - Integrated [Retry] / [Sync] tap target.
 */
@Composable
fun BackendConnectionGate(
    isLoading: Boolean,
    isConnected: Boolean,
    hasData: Boolean = true,
    isOffline: Boolean = false,
    errorTitle: String? = null,
    errorMessage: String? = null,
    loadingMessage: String = "Connecting to SURE Trust...",
    onRetry: () -> Unit,
    onLogout: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val alreadyGated = LocalHasBackendGate.current
    if (alreadyGated) {
        CompositionLocalProvider(LocalBackendConnected provides isConnected) {
            content()
        }
        return
    }

    CompositionLocalProvider(
        LocalBackendConnected provides isConnected,
        LocalHasBackendGate provides true
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Clean Top Connectivity Banner (Only shown when a connection failure has occurred and not actively loading)
            AnimatedVisibility(
                visible = !isConnected && !isLoading && (isOffline || !errorMessage.isNullOrBlank()),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val bannerBg = when {
                    isOffline -> Color(0xFF991B1B) // Crimson Red when offline
                    else -> Color(0xFF881337)      // Deep Rose Red when server unreachable
                }

                Surface(
                    color = bannerBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .clickable { onRetry() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOffline) Icons.Default.WifiOff else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFFFECDD3),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isOffline) "No Internet" else "Server offline",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "Retry",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Normal Screen Content
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                content()
            }
        }
    }
}
