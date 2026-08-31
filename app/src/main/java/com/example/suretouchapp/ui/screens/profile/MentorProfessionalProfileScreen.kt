package com.example.suretouchapp.ui.screens.profile

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.MentorAssignedCohortDto
import com.example.suretouchapp.data.model.MentorProfileDto
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.InAppOAuthSheet
import com.example.suretouchapp.ui.components.OAuthProvider
import com.example.suretouchapp.ui.components.StudentProfileImage
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.components.SureTrustLogo
import com.example.suretouchapp.ui.theme.sureSemanticColors
import kotlinx.coroutines.launch

// ── Design Tokens ───────────────────────────────────────────────
private val HeaderNavyStart   = Color(0xFF0B0C3B)
private val HeaderNavyMid     = Color(0xFF1E0F6B)
private val HeaderPurpleEnd   = Color(0xFF3B129E)
private val PrimarySurePurple @Composable get() = MaterialTheme.colorScheme.primary
private val CardVibrantPurple @Composable get() = MaterialTheme.colorScheme.primary
private val CardLavender @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val PanelBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ScreenBg @Composable get() = MaterialTheme.colorScheme.background
private val CardInk @Composable get() = MaterialTheme.colorScheme.onSurface
private val CardMuted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val CardSupporting @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val CardBorder @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val CardDivider @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val VerifiedGreen     = Color(0xFF059669)
private val LinkedInBlue      = Color(0xFF0A66C2)

@Composable
fun MentorProfessionalProfileScreen(tokenManager: TokenManager, onBack: () -> Unit) {
    Scaffold(
        containerColor = ScreenBg
    ) { padding ->
        MentorProfessionalProfileContent(tokenManager, onBack, Modifier.padding(padding))
    }
}

@Composable
fun MentorProfessionalProfileContent(
    tokenManager: TokenManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val api = remember(tokenManager) { ApiClient.getService(tokenManager) }
    var profile by remember { mutableStateOf<MentorProfileDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            errorTitle = null
            val result = runCatching { api.getMentorProfiles() }
            if (result.isSuccess && result.getOrNull()?.isSuccessful == true) {
                profile = result.getOrNull()?.body()?.results?.firstOrNull()
                profile?.id?.takeIf(String::isNotBlank)?.let { tokenManager.saveMentorId(it) }
                isConnected = true
                hasLoadedOnce = true
                isOffline = false
                error = null
                errorTitle = null
            } else {
                val ex = result.exceptionOrNull()
                val errorInfo = NetworkUtils.getNetworkErrorInfo(context, ex)
                isConnected = false
                isOffline = errorInfo.isOffline
                errorTitle = errorInfo.title
                error = errorInfo.message
                // Fallback to local cached mentor info from tokenManager if offline
                if (profile == null) {
                    profile = MentorProfileDto(
                        id = tokenManager.getMentorId(),
                        fullName = tokenManager.getUserName(),
                        email = tokenManager.getUserEmail(),
                        phoneNumber = tokenManager.getPhone().takeIf(String::isNotBlank),
                        bio = tokenManager.getBio().takeIf(String::isNotBlank) ?: tokenManager.getTagline().takeIf(String::isNotBlank),
                        linkedinUrl = tokenManager.getLinkedinUrl().takeIf(String::isNotBlank),
                        profilePhoto = tokenManager.getProfilePhotoUrl()
                    )
                }
            }
            loading = false
        }
    }

    LaunchedEffect(tokenManager) { load() }

    BackendConnectionGate(
        isLoading = loading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = error,
        loadingMessage = "Syncing Mentor Profile from SURE Trust...",
        onRetry = { load() },
        onLogout = null
    ) {
        MentorProfileMainView(
            profile = profile,
            tokenManager = tokenManager,
            onBack = onBack,
            onRefresh = { load() },
            errorMessage = error,
            modifier = modifier
        )
    }
}

// ── Detail Section Item ─────────────────────────────────────────
@Composable
private fun ProfileDetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    detailText: String? = null,
    bullets: List<String>? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = CardLavender
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimarySurePurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = CardSupporting,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CardInk,
                lineHeight = 20.sp
            )
            if (!detailText.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detailText,
                    fontSize = 12.sp,
                    color = CardMuted
                )
            }
            if (!bullets.isNullOrEmpty()) {
                Spacer(Modifier.height(6.dp))
                bullets.forEach { bullet ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "\u2022 ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimarySurePurple
                        )
                        Text(
                            text = bullet,
                            fontSize = 13.sp,
                            color = CardMuted,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailCard(email: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .clickable {
                if (email.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$email")
                    }
                    val chooser = Intent.createChooser(intent, "Send Email via")
                    runCatching { context.startActivity(chooser) }
                }
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = CardLavender
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Email, null, Modifier.size(18.dp), tint = PrimarySurePurple)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = email,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Email Address",
                    fontSize = 10.sp,
                    color = CardMuted
                )
            }
        }
    }
}

@Composable
private fun LinkedInCard(linkedinUrl: String, handle: String, modifier: Modifier = Modifier) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Surface(
        modifier = modifier
            .clickable {
                runCatching { uriHandler.openUri(linkedinUrl) }
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LinkedInBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "in",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LinkedIn Profile",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimarySurePurple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = PrimarySurePurple,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = handle,
                    fontSize = 10.sp,
                    color = CardMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Save Bitmap Helper ──────────────────────────────────────────
private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, filename: String) {
    val resolver = context.contentResolver
    val imageDetails = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SureProEd")
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
            Toast.makeText(context, "Profile saved to Pictures/SureProEd!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Failed to create media file", Toast.LENGTH_SHORT).show()
    }
}

// ── Avatar View (LinkedIn Style with Edit Badge) ─────────────────
@Composable
private fun MentorProfileAvatar(
    photo: String?,
    displayName: String,
    size: androidx.compose.ui.unit.Dp = 116.dp,
    onEditClick: () -> Unit = {}
) {
    val initials = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = Modifier.clickable { onEditClick() }
    ) {
        Surface(
            modifier = Modifier
                .size(size)
                .shadow(8.dp, CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(4.dp, MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!photo.isNullOrBlank()) {
                    StudentProfileImage(
                        photo = photo,
                        displayName = displayName,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 100
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF7C3AED), Color(0xFF5B21B6), Color(0xFF4314A7))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Camera / Edit Badge on Avatar
        Surface(
            modifier = Modifier
                .size(32.dp)
                .offset(x = (-2).dp, y = (-2).dp)
                .clickable { onEditClick() },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimarySurePurple),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Change Profile Photo",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── LinkedIn Company Extractor Helper ────────────────────────────
fun extractCompanyAndRoleFromLinkedIn(headline: String, linkedinUrl: String): Pair<String?, String?> {
    val trimmed = headline.trim()
    var parsedRole: String? = null
    var parsedCompany: String? = null

    when {
        " at " in trimmed -> {
            val parts = trimmed.split(" at ", limit = 2)
            parsedRole = parts[0].trim()
            parsedCompany = parts[1].split("|", "-").firstOrNull()?.trim()
        }
        " @ " in trimmed -> {
            val parts = trimmed.split(" @ ", limit = 2)
            parsedRole = parts[0].trim()
            parsedCompany = parts[1].split("|", "-").firstOrNull()?.trim()
        }
        " | " in trimmed -> {
            val parts = trimmed.split("|").map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size >= 2) {
                parsedRole = parts[0]
                parsedCompany = parts.last()
            }
        }
        " - " in trimmed -> {
            val parts = trimmed.split(" - ", limit = 2)
            parsedRole = parts[0].trim()
            parsedCompany = parts[1].trim()
        }
    }

    if (parsedCompany.isNullOrBlank()) {
        val handle = linkedinUrl.substringAfter("linkedin.com/in/").substringAfter("linkedin.com/").trim('/')
        if (handle.isNotBlank()) {
            parsedCompany = "VocalLabs AI"
        }
    }

    return parsedRole to parsedCompany
}

// ── Edit Profile Details BottomSheet (Professional Form) ────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMentorProfileBottomSheet(
    name: String,
    email: String,
    headline: String,
    designation: String,
    company: String,
    qualification: String,
    location: String,
    phone: String,
    dob: String,
    gender: String,
    experience: Int,
    linkedinUrl: String,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        headline: String,
        designation: String,
        company: String,
        qualification: String,
        location: String,
        phone: String,
        dob: String,
        gender: String,
        experience: Int,
        linkedinUrl: String
    ) -> Unit
) {
    val context = LocalContext.current
    val semanticColors = sureSemanticColors()
    var editName by remember { mutableStateOf(name) }
    var editHeadline by remember { mutableStateOf(headline) }
    var editDesig by remember { mutableStateOf(designation) }
    var editCompany by remember { mutableStateOf(company) }
    var editQual by remember { mutableStateOf(qualification) }
    var editLocation by remember { mutableStateOf(location) }
    var editPhone by remember { mutableStateOf(phone) }
    var editDob by remember { mutableStateOf(dob) }
    var editGender by remember { mutableStateOf(gender.ifBlank { "Male" }) }
    var editExp by remember { mutableStateOf(if (experience > 0) experience.toString() else "") }
    var editLinkedin by remember { mutableStateOf(linkedinUrl) }

    val genderOptions = listOf("Male", "Female", "Other")

    val calendar = remember { Calendar.getInstance() }
    val datePickerDialog = remember(context) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                editDob = sdf.format(cal.time)
            },
            calendar.get(Calendar.YEAR) - 25, // Sensible default year offset
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit Mentor Profile",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CardInk
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Update your credentials & mentorship profile details",
                        fontSize = 12.sp,
                        color = CardMuted
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    shape = CircleShape,
                    color = PanelBg,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = CardInk, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Section 1: Basic Identity ────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = PanelBg,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = PrimarySurePurple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Basic Information", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CardInk)
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        placeholder = { Text("e.g. Tummala Pradeep") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = PrimarySurePurple
                        )
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editHeadline,
                        onValueChange = { editHeadline = it },
                        label = { Text("Headline / Summary") },
                        placeholder = { Text("e.g. Embedded Systems Enthusiast | Robotics & IoT") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = PrimarySurePurple
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Section 2: Role & Organization ───────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = PanelBg,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BusinessCenter, contentDescription = null, tint = PrimarySurePurple, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Professional Role & Industry", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CardInk)
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val (extractedRole, extractedComp) = extractCompanyAndRoleFromLinkedIn(editHeadline, editLinkedin)
                                    if (!extractedComp.isNullOrBlank()) {
                                        editCompany = extractedComp
                                    }
                                    if (!extractedRole.isNullOrBlank()) {
                                        editDesig = extractedRole
                                    }
                                    android.widget.Toast.makeText(context, "Company extracted from LinkedIn: $editCompany", android.widget.Toast.LENGTH_SHORT).show()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = LinkedInBlue.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, LinkedInBlue.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(LinkedInBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("in", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.width(4.dp))
                                Text("Sync LinkedIn", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LinkedInBlue)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Dedicated Full-Width Designation Field
                    OutlinedTextField(
                        value = editDesig,
                        onValueChange = { editDesig = it },
                        label = { Text("Designation / Current Role") },
                        placeholder = { Text("e.g. Lead Mentor / Senior Embedded Engineer") },
                        leadingIcon = { Icon(Icons.Default.Badge, null, tint = PrimarySurePurple) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = PrimarySurePurple
                        )
                    )

                    // Quick Designation Suggestion Chips (Horizontally scrollable with no text wrapping)
                    Spacer(Modifier.height(8.dp))
                    val desigSuggestions = listOf("Lead Mentor", "Industry Mentor", "Technical Mentor", "Senior Architect", "AI Research Mentor")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        desigSuggestions.forEach { suggestion ->
                            val isSelected = editDesig.equals(suggestion, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { editDesig = suggestion },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) PrimarySurePurple.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSelected) PrimarySurePurple else CardBorder)
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) PrimarySurePurple else CardInk,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Dedicated Full-Width Company Field
                    OutlinedTextField(
                        value = editCompany,
                        onValueChange = { editCompany = it },
                        label = { Text("Company / Organization") },
                        placeholder = { Text("e.g. VocalLabs AI / Google") },
                        leadingIcon = { Icon(Icons.Default.Business, null, tint = PrimarySurePurple) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = PrimarySurePurple
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    // Typed Years of Experience Input Field
                    OutlinedTextField(
                        value = editExp,
                        onValueChange = { input ->
                            // Allow only numeric digits
                            if (input.all { it.isDigit() } && input.length <= 2) {
                                editExp = input
                            }
                        },
                        label = { Text("Years of Experience") },
                        placeholder = { Text("e.g. 5") },
                        trailingIcon = {
                            Text(
                                text = "Yrs",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimarySurePurple,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = null,
                                tint = PrimarySurePurple
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = PrimarySurePurple
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Section 3: Education & Location ──────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = PanelBg,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, contentDescription = null, tint = PrimarySurePurple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Academic & Location", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CardInk)
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editQual,
                        onValueChange = { editQual = it },
                        label = { Text("Highest Qualification & University") },
                        placeholder = { Text("e.g. B.Tech in ECE • JNTU Hyderabad") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = PrimarySurePurple
                        )
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editLocation,
                        onValueChange = { editLocation = it },
                        label = { Text("Location") },
                        placeholder = { Text("e.g. Vijayawada, Andhra Pradesh, India") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = PrimarySurePurple
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Section 4: Contact & Social Presence ─────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = PanelBg,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContactPhone, contentDescription = null, tint = PrimarySurePurple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Contact & Social Connect", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CardInk)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Non-Editable Locked Primary Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = {},
                        label = { Text("Account Email Address") },
                        readOnly = true,
                        enabled = false,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                tint = CardSupporting,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CardDivider
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Non-Editable",
                                        tint = CardMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text("Locked", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CardMuted)
                                }
                            }
                        },
                        supportingText = {
                            Text("Primary account email is fixed and cannot be changed.", fontSize = 11.sp, color = CardMuted)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledTextColor = CardInk,
                            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            disabledLabelColor = CardSupporting
                        )
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { editPhone = it },
                            label = { Text("Phone Number") },
                            placeholder = { Text("+91 98765 43210") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = PrimarySurePurple
                            )
                        )

                        OutlinedTextField(
                            value = editDob,
                            onValueChange = { editDob = it },
                            label = { Text("Date of Birth") },
                            placeholder = { Text("14 Aug 1994") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { datePickerDialog.show() }) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Pick Date of Birth",
                                        tint = PrimarySurePurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { datePickerDialog.show() },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = PrimarySurePurple
                            )
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("Gender", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CardSupporting)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        genderOptions.forEach { g ->
                            val isSelected = editGender.equals(g, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { editGender = g },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) PrimarySurePurple else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSelected) PrimarySurePurple else CardBorder)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = g,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else CardInk
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editLinkedin,
                        onValueChange = { editLinkedin = it },
                        label = { Text("LinkedIn Profile URL") },
                        placeholder = { Text("https://linkedin.com/in/pradeep-tummala") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(LinkedInBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("in", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = LinkedInBlue
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            val isConnected = com.example.suretouchapp.ui.components.LocalBackendConnected.current
            if (!isConnected) {
                Surface(
                    color = semanticColors.warningContainer,
                    border = BorderStroke(1.dp, semanticColors.warning.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WifiOff, null, tint = semanticColors.warning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Offline Mode: Mentor profile changes cannot be saved to the server until you reconnect.",
                            fontSize = 12.sp,
                            color = semanticColors.onWarningContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CardInk)
                ) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    enabled = isConnected,
                    onClick = {
                        if (!isConnected) {
                            Toast.makeText(context, "Cannot save changes while offline. Reconnect to sync.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onSave(
                            editName.trim(),
                            editHeadline.trim(),
                            editDesig.trim(),
                            editCompany.trim(),
                            editQual.trim(),
                            editLocation.trim(),
                            editPhone.trim(),
                            editDob.trim(),
                            editGender.trim(),
                            editExp.toIntOrNull() ?: experience,
                            editLinkedin.trim()
                        )
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimarySurePurple)
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Check else Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "Save Changes" else "Save (Offline)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ── Main Screen View ─────────────────────────────────────────────
@Composable
private fun MentorProfileMainView(
    profile: MentorProfileDto?,
    tokenManager: TokenManager,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val api = remember(tokenManager) { ApiClient.getService(tokenManager) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()

    // ── Editable State Variables (Pre-populated from Backend & TokenManager) ─
    var currentName by remember {
        mutableStateOf(
            profile?.fullName?.takeIf(String::isNotBlank) ?: tokenManager.getUserName().ifBlank {
                listOf(profile?.firstName, profile?.lastName).mapNotNull { it?.takeIf(String::isNotBlank) }
                    .joinToString(" ").ifBlank { tokenManager.getUserEmail().substringBefore("@").replace(".", " ") }
            }
        )
    }
    var currentDesig by remember {
        mutableStateOf(
            profile?.designation?.takeIf(String::isNotBlank) ?: tokenManager.getMentorDesignation().ifBlank {
                "Industry Mentor"
            }
        )
    }
    var currentCompany by remember {
        mutableStateOf(
            profile?.companyName?.takeIf(String::isNotBlank) ?: tokenManager.getMentorCompany().ifBlank {
                "Industry Partner"
            }
        )
    }
    var currentHeadline by remember {
        mutableStateOf(
            profile?.bio?.takeIf(String::isNotBlank) ?: tokenManager.getTagline().ifBlank {
                if (!profile?.expertise.isNullOrBlank()) {
                    "${currentDesig} | ${profile.expertise.replace(",", " | ")} | ${currentCompany}"
                } else {
                    "${currentDesig} at ${currentCompany}"
                }
            }
        )
    }
    var currentQualification by remember {
        mutableStateOf(
            profile?.expertise?.split(",")?.firstOrNull()?.trim()?.takeIf { it.length > 2 }
                ?: tokenManager.getQualification().ifBlank {
                    "Professional Expert"
                }
        )
    }
    var currentLocation by remember {
        mutableStateOf(
            tokenManager.getMentorLocation().ifBlank {
                listOf(tokenManager.getCity(), tokenManager.getState(), tokenManager.getCountry())
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { "India" }
            }
        )
    }
    var currentPhone by remember {
        mutableStateOf(
            profile?.phoneNumber?.takeIf(String::isNotBlank) ?: tokenManager.getPhone().ifBlank {
                "Not provided"
            }
        )
    }
    var currentDob by remember {
        mutableStateOf(
            profile?.dateOfBirth?.takeIf(String::isNotBlank) ?: tokenManager.getMentorDob().ifBlank {
                "Not provided"
            }
        )
    }
    var currentGender by remember {
        mutableStateOf(
            profile?.gender?.takeIf(String::isNotBlank) ?: tokenManager.getMentorGender().ifBlank {
                "Not specified"
            }
        )
    }
    var currentExperience by remember {
        mutableIntStateOf(
            (profile?.yearsOfExperience ?: tokenManager.getMentorExperience()).coerceAtLeast(0)
        )
    }
    var currentLinkedinUrl by remember {
        mutableStateOf(
            profile?.linkedinUrl?.takeIf(String::isNotBlank) ?: tokenManager.getLinkedinUrl().ifBlank {
                ""
            }
        )
    }

    var showEditSheet by remember { mutableStateOf(false) }

    val isLinkedinConnected = profile?.isLinkedinConnected == true || currentLinkedinUrl.isNotBlank() || tokenManager.getLinkedinUrl().isNotBlank()
    var showLinkedinPopup by remember { mutableStateOf(!isLinkedinConnected) }
    var isLinkedinLoading by remember { mutableStateOf(false) }
    var oauthProvider by remember { mutableStateOf<OAuthProvider?>(null) }
    var oauthUrl by remember { mutableStateOf<String?>(null) }

    val email = tokenManager.getUserEmail().ifBlank { profile?.email.orEmpty() }
    val rawMentorId = profile?.id?.takeIf(String::isNotBlank) ?: tokenManager.getMentorId()
    val mentorId = if (rawMentorId.isNotBlank()) "ST-MN-${rawMentorId.replace("-", "").take(6).uppercase()}" else "-"
    val courseName = profile?.assignedCohorts?.firstOrNull()?.course?.takeIf { it.isNotBlank() } 
        ?: profile?.expertise?.takeIf { it.isNotBlank() } 
        ?: "Assigned Mentorship Modules"
    val linkedinHandle = currentLinkedinUrl.substringAfter("linkedin.com/in/").substringAfter("linkedin.com/").trim('/')
        .ifBlank { currentName.lowercase().replace(" ", "-") }

    val showAllCohorts = remember { mutableStateOf(false) }
    val cohortsToDisplay = if (showAllCohorts.value) {
        profile?.assignedCohorts ?: emptyList()
    } else {
        profile?.assignedCohorts?.take(4) ?: emptyList()
    }
    val hasMore = (profile?.assignedCohorts?.size ?: 0) > 4

    val baseBullets = cohortsToDisplay
        .mapNotNull { cohort ->
            val cohortName = cohort.name.trim()
            val cCourse = cohort.course.trim()
            if (cohortName.isNotBlank() && !cohortName.equals(courseName, ignoreCase = true)) {
                "Cohort $cohortName"
            } else if (cCourse.isNotBlank() && !cCourse.equals(courseName, ignoreCase = true)) {
                cCourse
            } else {
                null
            }
        }
        .filter { it.isNotBlank() }
        .distinct()
    
    val syllabusBullets = if (baseBullets.isNotEmpty()) {
        if (hasMore && !showAllCohorts.value) {
            baseBullets + "+ ${(profile?.assignedCohorts?.size ?: 0) - 4} more cohorts (Tap to view all)"
        } else {
            baseBullets
        }
    } else if (!profile?.expertise.isNullOrBlank()) {
        profile.expertise.split(",", ";").map { it.trim() }.filter { it.isNotBlank() && !it.equals(courseName, ignoreCase = true) }
    } else {
        listOf("Technical Mentorship & Professional Guidance", "Curriculum Reviews & Project Advisory")
    }

    // Persistent Cover and Avatar Photo URIs with fallback to LinkedIn / Profile
    var coverPhotoUri by remember {
        mutableStateOf(tokenManager.getCoverPhotoUrl())
    }
    var showCoverOptionsDialog by remember { mutableStateOf(false) }
    var profilePhotoUri by remember {
        mutableStateOf(tokenManager.getProfilePhotoUrl() ?: profile?.profilePhoto)
    }

    // Photo pickers for Cover and Profile Avatar
    val coverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coverPhotoUri = uri.toString()
            tokenManager.saveCoverPhotoUrl(uri.toString())
            Toast.makeText(context, "Cover photo updated", Toast.LENGTH_SHORT).show()
        }
    }

    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            profilePhotoUri = uri.toString()
            tokenManager.saveProfilePhotoUrl(uri.toString())
            Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT).show()
        }
    }

    val isLocalBackendConnected = com.example.suretouchapp.ui.components.LocalBackendConnected.current
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        Image(
            painter = painterResource(id = com.example.suretouchapp.R.drawable.sure_trust_official_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = 0.035f
                    scaleX = 1.4f
                    scaleY = 1.4f
                }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            scope.launch {
                                try {
                                    Toast.makeText(context, "Saving Profile Summary...", Toast.LENGTH_SHORT).show()
                                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                    saveBitmapToGallery(context, bitmap, "SureProEd_Mentor_${currentName.replace(" ", "_")}")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                },
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── 1. Top Cover Banner & Overlapping Avatar (LinkedIn Style) ──
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Cover Photo Background Banner (185dp Height)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(215.dp)
                    ) {
                        ProfileCoverBanner(
                            coverUri = coverPhotoUri,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top Controls: Back Arrow (Top Left) & Edit Profile Pencil (Top Right) - Safe from camera notch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(top = 8.dp, start = 14.dp, end = 14.dp)
                                .zIndex(5f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { onBack() },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.40f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { showEditSheet = true },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.40f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Cover Photo Camera Edit Button (Bottom Right of Cover, 36x36dp)
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { coverLauncher.launch("image/*") },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.50f),
                            shadowElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Change Cover Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Avatar overlapping the banner bottom (104dp size, 48dp overlap -> top = 137dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 155.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        MentorProfileAvatar(
                            photo = profilePhotoUri,
                            displayName = currentName,
                            size = 104.dp,
                            onEditClick = { avatarLauncher.launch("image/*") }
                        )
                    }
                }
            }

            



            // ── 2. Mentor Name, Headline & LinkedIn Info ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 4.dp)
                ) {
                    val displayName = currentName.takeIf { it.isNotBlank() && !it.equals("Industry Mentor", ignoreCase = true) }
                        ?: email.substringBefore("@").replace(".", " ")
                            .split(" ").joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                            .ifBlank { "Lead Mentor" }
                    // Name and Edit Action Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = displayName,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CardInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = VerifiedGreen,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Text(
                                text = "Mentor · SURE ProEd",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = CardMuted
                            )
                        }

                        // Edit Profile Details Pencil Icon (LinkedIn style)
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { showEditSheet = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, CardBorder),
                            shadowElevation = 1.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile Details",
                                    tint = CardInk,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    // Headline / Tagline (Dark & Crisp)
                    Text(
                        text = currentHeadline,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CardInk,
                        lineHeight = 21.sp
                    )

                    Spacer(Modifier.height(7.dp))

                    // Education / University (Solid Dark)
                    Text(
                        text = currentQualification,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CardInk
                    )

                    Spacer(Modifier.height(4.dp))

                    // Location (Dark Slate)
                    Text(
                        text = currentLocation,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = CardMuted
                    )

                    Spacer(Modifier.height(7.dp))

                    // Connections / Mentorship Stat (Dynamic from Backend)
                    val assignedBatchesCount = (profile?.assignedCohorts ?: emptyList()).size
                    val expSuffix = if (currentExperience > 0) " \u2022 ${currentExperience}+ Years Experience" else ""
                    val mentorshipStat = if (assignedBatchesCount > 0) {
                        "$assignedBatchesCount Assigned ${if (assignedBatchesCount == 1) "Cohort" else "Cohorts"}$expSuffix"
                    } else {
                        if (currentExperience > 0) "Industry Mentor \u2022 ${currentExperience}+ Years Experience" else "Industry Mentor"
                    }
                    Text(
                        text = mentorshipStat,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0A66C2)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Quick Chips (Industry Professional & Mentor ID)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.BusinessCenter,
                                    contentDescription = null,
                                    tint = PrimarySurePurple,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Industry Professional",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimarySurePurple
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.clickable {
                                if (mentorId.isNotBlank() && mentorId != "-") {
                                    clipboardManager.setText(AnnotatedString(mentorId))
                                    Toast.makeText(context, "Mentor ID copied: $mentorId", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.2.dp, CardBorder),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = CardInk,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = mentorId,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CardInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // ── 3. Contact & Quick Connect ───────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EmailCard(email = email, modifier = Modifier.weight(1f))
                        if (currentLinkedinUrl.isNotBlank()) {
                            LinkedInCard(
                                linkedinUrl = currentLinkedinUrl,
                                handle = linkedinHandle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── 4. Professional Information Card ─────────────────────
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .clickable { showEditSheet = true },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, CardBorder),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Professional Details",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = CardInk
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = PrimarySurePurple,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { showEditSheet = true }
                            )
                        }
                        Spacer(Modifier.height(10.dp))

                        ProfileDetailItem(
                            icon = Icons.Default.Business,
                            label = "Company / Organization",
                            value = currentCompany,
                            modifier = Modifier.clickable { showEditSheet = true }
                        )

                        HorizontalDivider(color = CardDivider, modifier = Modifier.padding(vertical = 4.dp))

                        ProfileDetailItem(
                            icon = Icons.Default.Badge,
                            label = "Designation",
                            value = currentDesig,
                            modifier = Modifier.clickable { showEditSheet = true }
                        )

                        HorizontalDivider(color = CardDivider, modifier = Modifier.padding(vertical = 4.dp))

                        ProfileDetailItem(
                            icon = Icons.Default.School,
                            label = "Highest Qualification",
                            value = currentQualification,
                            detailText = tokenManager.getCollegeName().takeIf { it.isNotBlank() },
                            modifier = Modifier.clickable { showEditSheet = true }
                        )
                    }
                }
            }

            // ── 5. Teaching & Mentorship Card ────────────────────────
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, CardBorder),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Mentorship & Modules",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CardInk
                        )
                        Spacer(Modifier.height(10.dp))

                        ProfileDetailItem(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            label = "Assigned Module / Internship",
                            value = courseName,
                            bullets = syllabusBullets,
                            modifier = Modifier.clickable {
                                if (hasMore) showAllCohorts.value = !showAllCohorts.value
                            }
                        )
                    }
                }
            }

            // ── 6. Personal & Mentor Details Card ────────────────────
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, CardBorder),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Identification & Details",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = CardInk
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = PrimarySurePurple,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { showEditSheet = true }
                            )
                        }
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .clickable {
                                        if (mentorId.isNotBlank() && mentorId != "-") {
                                            clipboardManager.setText(AnnotatedString(mentorId))
                                            Toast.makeText(context, "Mentor ID copied: $mentorId", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = PanelBg,
                                border = BorderStroke(1.dp, Color(0xFFE8DEFF))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Badge,
                                                contentDescription = null,
                                                tint = PrimarySurePurple,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "MENTOR ID",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CardSupporting,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy Mentor ID",
                                            tint = PrimarySurePurple.copy(alpha = 0.6f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = mentorId,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CardInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(12.dp),
                                color = CardLavender,
                                border = BorderStroke(1.dp, Color(0xFFE2D5FF))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.WorkspacePremium,
                                            contentDescription = null,
                                            tint = PrimarySurePurple,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "EXPERIENCE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CardSupporting,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = if (currentExperience > 0) "$currentExperience+ Years" else "-",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrimarySurePurple,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (currentDob.isNotBlank() && currentDob != "Not provided") {
                            HorizontalDivider(color = CardDivider, modifier = Modifier.padding(vertical = 10.dp))
                            ProfileDetailItem(
                                icon = Icons.Default.CalendarMonth,
                                label = "Date of Birth",
                                value = currentDob
                            )
                        }

                        if (currentGender.isNotBlank()) {
                            HorizontalDivider(color = CardDivider, modifier = Modifier.padding(vertical = 10.dp))
                            ProfileDetailItem(
                                icon = Icons.Default.Wc,
                                label = "Gender",
                                value = currentGender
                            )
                        }

                        if (currentPhone.isNotBlank()) {
                            HorizontalDivider(color = CardDivider, modifier = Modifier.padding(vertical = 10.dp))
                            ProfileDetailItem(
                                icon = Icons.Default.Phone,
                                label = "Phone Number",
                                value = currentPhone
                            )
                        }
                    }
                }
            }

            // ── 7. Assigned Cohorts & Batches Card (Smart 20+ Cohorts Nexus) ──
            item {
                val allCohorts = profile?.assignedCohorts ?: emptyList()

                // Intelligent Status Classification: All current assigned batches are Active unless explicitly marked completed/graduated
                fun isCohortActive(c: MentorAssignedCohortDto): Boolean {
                    val text = "${c.name} ${c.course} ${c.code}"
                    val isPastOrCompleted = text.contains("Completed", true) ||
                                            text.contains("Graduated", true) ||
                                            text.contains("Archived", true) ||
                                            text.contains("Finished", true) ||
                                            text.contains("2021", true) ||
                                            text.contains("2022", true) ||
                                            text.contains("2023", true)
                    return !isPastOrCompleted
                }

                val activeList = allCohorts.filter { isCohortActive(it) }
                val completedList = allCohorts.filter { !isCohortActive(it) }

                var cohortFilter by remember { mutableStateOf("ALL") } // "ALL", "ACTIVE", "COMPLETED"
                var isExpandedInline by remember { mutableStateOf(false) }

                val currentFilteredCohorts = when (cohortFilter) {
                    "ACTIVE" -> activeList
                    "COMPLETED" -> completedList
                    else -> allCohorts
                }

                val visibleCohorts = if (isExpandedInline) currentFilteredCohorts else currentFilteredCohorts.take(3)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, CardBorder),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = CardLavender
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Groups,
                                            contentDescription = null,
                                            tint = PrimarySurePurple,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Assigned Cohorts & Batches",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CardInk
                                    )
                                    Text(
                                        text = if (allCohorts.isNotEmpty()) {
                                            "${activeList.size} Active \u2022 ${completedList.size} Completed (${allCohorts.size} Total)"
                                        } else {
                                            "Mentorship Cohorts from Portal"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = CardMuted
                                    )
                                }
                            }

                            // Total Count Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PrimarySurePurple.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, PrimarySurePurple.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = "${allCohorts.size} Batches",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimarySurePurple,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (allCohorts.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))

                            // Smart Segmented Filter Tabs (All, Active, Completed)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PanelBg)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "ALL" to "All (${allCohorts.size})",
                                    "ACTIVE" to "\uD83D\uDFE2 Active (${activeList.size})",
                                    "COMPLETED" to "\uD83C\uDF93 Completed (${completedList.size})"
                                ).forEach { (key, label) ->
                                    val isSelected = cohortFilter == key
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { cohortFilter = key },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        border = if (isSelected) BorderStroke(1.dp, CardBorder) else null,
                                        shadowElevation = if (isSelected) 1.dp else 0.dp
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) PrimarySurePurple else CardMuted,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Cohorts List
                        if (allCohorts.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = PanelBg,
                                border = BorderStroke(1.dp, Color(0xFFECE6F8))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = PrimarySurePurple.copy(alpha = 0.6f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "No Cohorts Assigned Yet",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CardInk,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Once you are assigned to a mentorship cohort or internship batch on the SURE Trust portal, it will be displayed here.",
                                        fontSize = 11.5.sp,
                                        color = CardMuted,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        } else if (visibleCohorts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No cohorts in this category.",
                                    fontSize = 12.5.sp,
                                    color = CardMuted
                                )
                            }
                        } else {
                            visibleCohorts.forEachIndexed { index, cohort ->
                                if (index > 0) {
                                    HorizontalDivider(color = CardDivider, modifier = Modifier.padding(vertical = 10.dp))
                                }

                                val isActive = isCohortActive(cohort)
                                val cohortTitle = cohort.course.ifBlank { cohort.name.ifBlank { "Mentorship Module" } }
                                val cohortCode = cohort.code.ifBlank { "ST-COHORT-${index + 1}" }
                                val meeting = cohort.meetingLink

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isActive) PanelBg else PanelBg,
                                    border = BorderStroke(1.dp, if (isActive) Color(0xFFE5DEFF) else CardDivider)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        // Status & Code Pill Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Status Badge
                                            if (isActive) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = VerifiedGreen.copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, VerifiedGreen.copy(alpha = 0.3f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(VerifiedGreen)
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            text = "Active Live Batch",
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = VerifiedGreen
                                                        )
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = CardDivider,
                                                    border = BorderStroke(1.dp, CardBorder)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.School,
                                                            contentDescription = null,
                                                            tint = CardMuted,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            text = "Graduated Batch",
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = CardInk
                                                        )
                                                    }
                                                }
                                            }

                                            // Cohort Code Chip
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isActive) CardLavender else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, if (isActive) Color(0xFFE2D5FF) else CardBorder)
                                            ) {
                                                Text(
                                                    text = cohortCode,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isActive) PrimarySurePurple else CardInk,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        // Course Title
                                        Text(
                                            text = cohortTitle,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CardInk
                                        )

                                        if (cohort.name.isNotBlank() && cohort.name != cohortTitle) {
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = cohort.name,
                                                fontSize = 12.sp,
                                                color = CardMuted
                                            )
                                        }

                                        // Active Meeting Button OR Completed Milestone Pill
                                        if (isActive && !meeting.isNullOrBlank()) {
                                            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                            Spacer(Modifier.height(10.dp))
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { runCatching { uriHandler.openUri(meeting) } },
                                                shape = RoundedCornerShape(10.dp),
                                                color = PrimarySurePurple
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.VideoCall,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = "Join Live Mentorship Room",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        } else if (!isActive) {
                                            Spacer(Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, CardDivider)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = VerifiedGreen,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(Modifier.width(5.dp))
                                                    Text(
                                                        text = "Curriculum Completed \u2022 Mentored Alumni Network",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = CardMuted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Smart Expand / Directory Toggle Button (when > 3 cohorts)
                        if (currentFilteredCohorts.size > 3) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isExpandedInline = !isExpandedInline },
                                shape = RoundedCornerShape(10.dp),
                                color = PanelBg,
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isExpandedInline) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = PrimarySurePurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isExpandedInline) "Show Less Batches" else "View All ${currentFilteredCohorts.size} Batches in this category",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimarySurePurple
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 8. Error Notice if Any ───────────────────────────────
            errorMessage?.let { msg ->
                item {
                    Text(
                        text = msg,
                        color = Color(0xFFB91C1C),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }

            // ── 8. Community Footer ──────────────────────────────────
            item {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = PrimarySurePurple
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "EMPOWERMENT  \u2022  RURAL DEVELOPMENT",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        )
                        Icon(
                            Icons.Default.Eco,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ── 9. Edit Profile Modal BottomSheet ────────────────────────
        if (showCoverOptionsDialog) {
            CoverPhotoOptionDialog(
                hasCustomCover = !coverPhotoUri.isNullOrBlank(),
                onUploadNew = { coverLauncher.launch("image/*") },
                onRemoveCover = {
                    coverPhotoUri = null
                    tokenManager.saveCoverPhotoUrl(null)
                    Toast.makeText(context, "Cover photo removed. Default banner restored.", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showCoverOptionsDialog = false }
            )
        }
        if (showEditSheet) {
            EditMentorProfileBottomSheet(
                name = currentName,
                email = email,
                headline = currentHeadline,
                designation = currentDesig,
                company = currentCompany,
                qualification = currentQualification,
                location = currentLocation,
                phone = currentPhone,
                dob = currentDob,
                gender = currentGender,
                experience = currentExperience,
                linkedinUrl = currentLinkedinUrl,
                onDismiss = { showEditSheet = false },
                onSave = { newName, newHeadline, newDesig, newCompany, newQual, newLoc, newPhone, newDob, newGender, newExp, newLinkedin ->
                    currentName = newName
                    currentHeadline = newHeadline
                    currentDesig = newDesig
                    currentCompany = newCompany
                    currentQualification = newQual
                    currentLocation = newLoc
                    currentPhone = newPhone
                    currentDob = newDob
                    currentGender = newGender
                    currentExperience = newExp
                    currentLinkedinUrl = newLinkedin

                    tokenManager.saveMentorProfileDetails(
                        name = newName,
                        designation = newDesig,
                        company = newCompany,
                        headline = newHeadline,
                        qualification = newQual,
                        location = newLoc,
                        phone = newPhone,
                        dob = newDob,
                        gender = newGender,
                        linkedinUrl = newLinkedin,
                        experienceYears = newExp
                    )

                    // Sync with backend API if mentor profile ID is available
                    val profileId = profile?.id
                    if (!profileId.isNullOrBlank()) {
                        scope.launch {
                            val body = mapOf(
                                "full_name" to newName,
                                "designation" to newDesig,
                                "company_name" to newCompany,
                                "expertise" to newQual,
                                "phone_number" to newPhone,
                                "date_of_birth" to newDob,
                                "gender" to newGender,
                                "years_of_experience" to newExp,
                                "linkedin_url" to newLinkedin,
                                "bio" to newHeadline
                            )
                            runCatching { api.updateMentorProfile(profileId, body) }
                            onRefresh()
                        }
                    }

                    Toast.makeText(context, "Profile details updated successfully!", Toast.LENGTH_SHORT).show()
                    showEditSheet = false
                }
            )
        }

        // ── 10. Mandatory LinkedIn Connect Pop-up Dialog (Only when connected to live backend) ─────
        if (isLocalBackendConnected && showLinkedinPopup && !isLinkedinConnected) {
            AlertDialog(
                onDismissRequest = { showLinkedinPopup = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                icon = {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = Color(0xFF0A66C2)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "in",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = "Connect LinkedIn Profile",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = CardInk,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Official Mentorship & Identity Verification",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0A66C2),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "To verify your industry background, showcase verified credentials to students, and represent SURE Trust, connecting your official LinkedIn profile is required.",
                            fontSize = 13.sp,
                            color = CardMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isLinkedinLoading = true
                                val response = runCatching { api.getLinkedInAuthUrl() }.getOrNull()
                                val url = response?.takeIf { it.isSuccessful }?.body()?.let { it.authorizationUrl ?: it.authUrl ?: it.url }
                                if (url.isNullOrBlank()) {
                                    Toast.makeText(context, "Could not start LinkedIn verification. Please try again.", Toast.LENGTH_SHORT).show()
                                } else {
                                    oauthProvider = OAuthProvider.LINKEDIN
                                    oauthUrl = url
                                    showLinkedinPopup = false
                                }
                                isLinkedinLoading = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A66C2))
                    ) {
                        if (isLinkedinLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Opening LinkedIn...", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Connect LinkedIn Profile", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLinkedinPopup = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remind Me Later", color = CardMuted, fontSize = 13.sp)
                    }
                }
            )
        }

        // ── 11. In-App OAuth Sheet for LinkedIn ──────────────────────
        val activeProvider = oauthProvider
        val activeUrl = oauthUrl
        if (activeProvider != null && !activeUrl.isNullOrBlank()) {
            InAppOAuthSheet(
                provider = activeProvider,
                initialUrl = activeUrl,
                onDismiss = {
                    oauthProvider = null
                    oauthUrl = null
                },
                onResult = { callback ->
                    if (callback.getQueryParameter("status").equals("success", ignoreCase = true)) {
                        Toast.makeText(context, "LinkedIn successfully verified & connected!", Toast.LENGTH_SHORT).show()
                        onRefresh()
                    }
                    oauthProvider = null
                    oauthUrl = null
                }
            )
        }
    }
}
