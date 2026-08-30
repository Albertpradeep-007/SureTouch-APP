package com.example.suretouchapp.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun StudentProfileImage(
    photo: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 24
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo) {
        value = if (photo.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                when {
                    photo.startsWith("content://") || photo.startsWith("file://") -> {
                        context.contentResolver.openInputStream(Uri.parse(photo))?.use(BitmapFactory::decodeStream)
                    }
                    photo.startsWith("http://") || photo.startsWith("https://") -> {
                        fetchBitmapFromUrl(photo)
                    }
                    photo.startsWith("data:image") || photo.contains("base64,") -> {
                        val encoded = photo.substringAfter("base64,", photo)
                        val bytes = Base64.decode(encoded, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    else -> {
                        val resolved = ApiClient.resolveServerUrl(photo)
                        if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                            fetchBitmapFromUrl(resolved)
                        } else {
                            val encoded = photo.substringAfter("base64,", photo)
                            val bytes = Base64.decode(encoded, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)).background(Color(0xFFE9E3FF)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), "Student profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            androidx.compose.material3.Text(
                displayName.trim().take(1).ifBlank { "S" }.uppercase(),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF6C2BD9)
            )
        }
    }
}

private fun fetchBitmapFromUrl(url: String): android.graphics.Bitmap? {
    val cleanUrl = ApiClient.resolveServerUrl(url)
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    val request = Request.Builder()
        .url(cleanUrl)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .build()
    return client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
            response.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
        } else null
    }
}
