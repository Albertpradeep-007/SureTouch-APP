package com.example.suretouchapp.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

enum class OAuthProvider(val title: String, val badge: String, val color: Color) {
    LINKEDIN("Connect LinkedIn", "in", Color(0xFF0A66C2)),
    GITHUB("Connect GitHub", "GH", Color(0xFF24292F))
}

/**
 * Provider-owned authentication rendered inside the app. The app never reads or
 * stores credentials; it only consumes the final backend deep-link result.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppOAuthSheet(
    provider: OAuthProvider,
    initialUrl: String,
    onDismiss: () -> Unit,
    onResult: (Uri) -> Unit
) {
    var loading by remember(initialUrl) { mutableStateOf(true) }
    var callbackConsumed by remember(initialUrl) { mutableStateOf(false) }

    fun consumeIfAppCallback(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        if (!uri.scheme.equals("suretrust", ignoreCase = true)) return false
        if (!callbackConsumed) {
            callbackConsumed = true
            onResult(uri)
        }
        return true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                Modifier.padding(vertical = 10.dp).width(42.dp).height(4.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(provider.color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(provider.badge, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, Modifier.size(12.dp), tint = Color(0xFF16A34A))
                        Spacer(Modifier.width(4.dp))
                        Text("Secure provider sign-in", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close authentication")
                }
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = provider.color)
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportMultipleWindows(false)
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                                consumeIfAppCallback(url)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                consumeIfAppCallback(url)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url?.toString()
                                if (consumeIfAppCallback(target)) return true
                                val scheme = request?.url?.scheme.orEmpty().lowercase()
                                return scheme !in setOf("http", "https")
                            }
                        }
                        loadUrl(initialUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
