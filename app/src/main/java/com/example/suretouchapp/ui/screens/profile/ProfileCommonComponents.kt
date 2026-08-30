package com.example.suretouchapp.ui.screens.profile

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.ui.components.StudentProfileImage

val SharedPrimaryPurple = Color(0xFF6D28D9)
val SharedDeepIndigo = Color(0xFF4C1D95)
val SharedBorderColor = Color(0xFFE2E8F0)
val SharedCardBg = Color(0xFFFFFFFF)
val SharedTextMain = Color(0xFF0F172A)
val SharedTextMuted = Color(0xFF64748B)

@Composable
fun ProfileSectionCard(
    title: String,
    icon: ImageVector,
    accentColor: Color = SharedPrimaryPurple,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SharedCardBg,
        border = BorderStroke(1.dp, SharedBorderColor),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SharedTextMain
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun MetricPillCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SharedCardBg,
        border = BorderStroke(1.dp, SharedBorderColor),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SharedTextMuted)
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = SharedTextMain)
            Text(text = subtitle, fontSize = 11.sp, color = SharedTextMuted)
        }
    }
}

@Composable
fun DetailRowItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 11.5.sp, color = SharedTextMuted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, color = SharedTextMain, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SocialLinkRow(
    label: String,
    value: String,
    isConnected: Boolean,
    icon: ImageVector,
    accentColor: Color = SharedPrimaryPurple,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (isConnected) accentColor else SharedTextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontSize = 12.sp, color = SharedTextMuted)
                Text(
                    text = value,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isConnected) SharedTextMain else SharedTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isConnected) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ProfileCoverBanner(
    coverUri: String?,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, coverUri) {
        value = if (coverUri.isNullOrBlank()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val resolved = com.example.suretouchapp.data.api.ApiClient.resolveServerUrl(coverUri)
                when {
                    resolved.startsWith("content://") || resolved.startsWith("file://") ->
                        context.contentResolver.openInputStream(android.net.Uri.parse(resolved))?.use(android.graphics.BitmapFactory::decodeStream)
                    resolved.startsWith("http://") || resolved.startsWith("https://") -> {
                        val token = com.example.suretouchapp.data.api.TokenManager(context).getAccessToken()
                        val conn = java.net.URL(resolved).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 10000
                        conn.readTimeout = 10000
                        if (!token.isNullOrBlank()) {
                            conn.setRequestProperty("Authorization", "Bearer $token")
                        }
                        conn.inputStream.use(android.graphics.BitmapFactory::decodeStream)
                    }
                    else -> null
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF2E1065),
                        Color(0xFF4C1D95),
                        Color(0xFF1E1B4B)
                    )
                )
            )
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Profile Cover",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.example.suretouchapp.R.drawable.mentor_profile_galaxy_header),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay gradient for readability and depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.40f)
                        )
                    )
                )
        )
    }
}

@Composable
fun StudentProfileAvatar(
    photo: String?,
    displayName: String,
    size: Dp = 104.dp,
    badgeColor: Color = SharedPrimaryPurple,
    onEditClick: () -> Unit = {}
) {
    val initials = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = Modifier.clickable { onEditClick() }
    ) {
        Surface(
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(4.dp, Color.White),
            shadowElevation = 6.dp
        ) {
            val hasValidPhoto = !photo.isNullOrBlank()
            if (hasValidPhoto) {
                StudentProfileImage(
                    photo = photo,
                    displayName = displayName,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(badgeColor, SharedDeepIndigo)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontSize = (size.value * 0.38f).sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // Camera Edit Badge (~80% inside avatar boundary via inset padding)
        Surface(
            modifier = Modifier
                .padding(end = 4.dp, bottom = 4.dp)
                .size(30.dp)
                .clip(CircleShape)
                .clickable { onEditClick() },
            shape = CircleShape,
            color = badgeColor,
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Change Photo",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun StudentIdCardModal(
    name: String,
    studentId: String,
    role: String,
    email: String,
    college: String,
    qualification: String,
    badgeColor: Color = SharedPrimaryPurple,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", fontWeight = FontWeight.Bold) }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Badge, null, tint = badgeColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Official Identity Card", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SURE Trust", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Surface(shape = RoundedCornerShape(4.dp), color = badgeColor) {
                            Text(
                                role,
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(qualification, color = Color(0xFF94A3B8), fontSize = 12.5.sp)
                    Text(college, color = Color(0xFFCBD5E1), fontSize = 12.5.sp)
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ID NUMBER", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(studentId, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("STATUS", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("ACTIVE", color = Color(0xFF4ADE80), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    )
}

fun saveBitmapToGalleryHelper(context: Context, bitmap: Bitmap, filename: String) {
    val resolver = context.contentResolver
    val imageDetails = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SureTrust")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageDetails)
    if (imageUri != null) {
        try {
            resolver.openOutputStream(imageUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                imageDetails.clear()
                imageDetails.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, imageDetails, null, null)
            }
            Toast.makeText(context, "Saved to Pictures/SureTrust!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Saved.", Toast.LENGTH_SHORT).show()
        }
    }
}


@Composable
fun CoverPhotoOptionDialog(
    hasCustomCover: Boolean,
    onUploadNew: () -> Unit,
    onRemoveCover: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cover Banner Options", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (hasCustomCover) "You can change your profile banner or remove it to restore the default theme banner."
                    else "Choose a cover banner photo from your gallery to customize your profile.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onUploadNew()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (hasCustomCover) "Change Photo" else "Upload Photo")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasCustomCover) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onRemoveCover()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove (Default)")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
