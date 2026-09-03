package com.example.suretouchapp.ui.screens.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.StudentProfileDto
import com.example.suretouchapp.data.model.StudentStatisticsDto
import com.example.suretouchapp.data.repository.StudentProfileRepository
import com.example.suretouchapp.data.repository.StudentStatisticsRepository
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.LocalBackendConnected
import com.example.suretouchapp.ui.theme.sureSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

private val PrimaryPurple = Color(0xFF6D28D9)
private val DeepPurple = Color(0xFF4C1D95)
private val VerifiedGreen = Color(0xFF059669)
private val ScreenBg @Composable get() = MaterialTheme.colorScheme.background
private val TextMain @Composable get() = MaterialTheme.colorScheme.onSurface
private val TextMuted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentProfessionalProfileScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(tokenManager) { StudentProfileRepository(tokenManager) }
    val statsRepository = remember(tokenManager) { StudentStatisticsRepository(tokenManager) }

    var profile by remember { mutableStateOf<StudentProfileDto?>(null) }
    var stats by remember { mutableStateOf<StudentStatisticsDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            errorTitle = null
            val result = runCatching { repository.load() }
            if (result.isSuccess) {
                profile = result.getOrNull()
                isConnected = true
                hasLoadedOnce = true
                isOffline = false
            } else {
                val ex = result.exceptionOrNull()
                val errorInfo = NetworkUtils.getNetworkErrorInfo(context, ex)
                isConnected = false
                isOffline = errorInfo.isOffline
                errorTitle = errorInfo.title
                error = errorInfo.message
                if (profile == null) {
                    profile = repository.load()
                }
            }
            val statsRes = runCatching { statsRepository.load() }.getOrNull()
            if (statsRes != null) {
                stats = statsRes
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
        loadingMessage = "Connecting to Student Portal...",
        onRetry = { load() }
    ) {
        StudentProfileContent(
            profile = profile,
            stats = stats,
            tokenManager = tokenManager,
            repository = repository,
            onBack = onBack,
            onProfileUpdated = { updated -> profile = updated }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StudentProfileContent(
    profile: StudentProfileDto?,
    stats: StudentStatisticsDto?,
    tokenManager: TokenManager,
    repository: StudentProfileRepository,
    onBack: () -> Unit,
    onProfileUpdated: (StudentProfileDto) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val isLocalBackendConnected = LocalBackendConnected.current
    val graphicsLayer = rememberGraphicsLayer()
    val semanticColors = sureSemanticColors()

    var showEditSheet by remember { mutableStateOf(false) }
    var showPhotoOptionsSheet by remember { mutableStateOf(false) }
    var showQrModal by remember { mutableStateOf(false) }
    var isPhotoUploading by remember { mutableStateOf(false) }
    var isResumeUploading by remember { mutableStateOf(false) }
    var isLinkedInConnecting by remember { mutableStateOf(false) }

    // Accurate Student Profile Data Resolution
    val apiName = listOfNotNull(profile?.user?.firstName, profile?.user?.lastName).joinToString(" ").trim()
    val name = tokenManager.getUserName().ifBlank { apiName.ifBlank { "Student Scholar" } }

    val rawStudentCode = profile?.studentCode?.takeIf(String::isNotBlank)
        ?: tokenManager.getStudentCode().takeIf(String::isNotBlank)
        ?: stats?.studentCode?.takeIf(String::isNotBlank)

    val cachedApp = tokenManager.getApplicationSnapshot()
    val appNumber = stats?.applicationNumber ?: cachedApp?.applicationNumber

    val email = tokenManager.getUserEmail().ifBlank { profile?.user?.email.orEmpty() }

    val studentIdDisplay = when {
        !rawStudentCode.isNullOrBlank() -> {
            val clean = rawStudentCode.removePrefix("STU-").removePrefix("ST-").removePrefix("ST_").trim()
            "STU-$clean"
        }
        !appNumber.isNullOrBlank() -> {
            val parts = appNumber.removePrefix("APP-").removePrefix("APP_").split("-")
            val cleanId = if (parts.size >= 2 && parts[0].length == 4 && parts[1].all { it.isDigit() }) {
                "${parts[0]}-${parts[1]}"
            } else if (parts.isNotEmpty()) {
                parts[0]
            } else {
                appNumber.take(10)
            }
            "STU-$cleanId"
        }
        else -> {
            val emailHash = email.hashCode().let { kotlin.math.abs(it) % 100000 }
            "STU-${String.format(java.util.Locale.US, "%05d", emailHash)}"
        }
    }
    LaunchedEffect(studentIdDisplay) {
        if (tokenManager.getStudentCode().isBlank() || tokenManager.getStudentCode().startsWith("APP-")) {
            tokenManager.saveStudentCode(studentIdDisplay)
        }
    }
    val isVerifiedStudent = true

    val phone = profile?.phone?.takeIf(String::isNotBlank) ?: tokenManager.getPhone()
    val gender = profile?.gender?.takeIf(String::isNotBlank) ?: tokenManager.getGender()
    val dob = profile?.dateOfBirth?.takeIf(String::isNotBlank) ?: tokenManager.getDob()
    val permanentAddress = profile?.permanentAddress?.takeIf(String::isNotBlank) ?: tokenManager.getPermanentAddress()

    val tagline = profile?.tagline?.takeIf(String::isNotBlank)
        ?: tokenManager.getTagline().takeIf(String::isNotBlank)
        ?: "Aspiring Technology Professional • SURE Trust Scholar"

    val college = profile?.college?.takeIf(String::isNotBlank)
        ?: tokenManager.getCollegeName().takeIf(String::isNotBlank)
        ?: "Academic Institution"

    val qualification = profile?.degree?.takeIf(String::isNotBlank)
        ?: profile?.educationLevel?.takeIf(String::isNotBlank)
        ?: tokenManager.getQualification().takeIf(String::isNotBlank)
        ?: "Degree Program"

    val specialization = profile?.specialization?.takeIf(String::isNotBlank)
        ?: tokenManager.getSpecialization().takeIf(String::isNotBlank)

    val gradYear = profile?.graduationYear ?: tokenManager.getGraduationYear()

    val city = profile?.city?.takeIf(String::isNotBlank) ?: tokenManager.getCity()
    val state = profile?.state?.takeIf(String::isNotBlank) ?: tokenManager.getState()
    val country = profile?.country?.takeIf(String::isNotBlank) ?: tokenManager.getCountry()
    val location = listOfNotNull(
        city.takeIf { it.isNotBlank() },
        state.takeIf { it.isNotBlank() },
        country.takeIf { it.isNotBlank() }
    ).joinToString(", ").ifBlank { "India" }

    val courseTitle = stats?.applicationCourseTitle?.takeIf(String::isNotBlank)
        ?: stats?.activeCohort?.courseTitle?.takeIf(String::isNotBlank)
        ?: tokenManager.getCourseTitle().takeIf(String::isNotBlank)
        ?: cachedApp?.courseTitle.orEmpty()

    val cohortCode = profile?.cohortCode?.takeIf(String::isNotBlank)
        ?: tokenManager.getCohortCode().takeIf(String::isNotBlank)
        ?: stats?.activeCohort?.code.orEmpty()

    val enrollmentStatus = stats?.applicationStatus
        ?: cachedApp?.status
        ?: if (cohortCode.isNotBlank()) "Cohort Assigned" else "Registered"

    val bio = profile?.bio?.takeIf(String::isNotBlank) ?: tokenManager.getBio()
    val skills = if (profile?.skills?.isNotEmpty() == true) profile.skills else tokenManager.getSkills()
    val hobbies = if (profile?.hobbies?.isNotEmpty() == true) profile.hobbies else tokenManager.getHobbies()
    val languages = if (profile?.languages?.isNotEmpty() == true) profile.languages else tokenManager.getLanguages()

    val resumeUrl = profile?.resumeUrl?.takeIf(String::isNotBlank)
        ?: profile?.resume?.takeIf(String::isNotBlank)
        ?: tokenManager.getResumeUrl().takeIf(String::isNotBlank)
    val resumeName = tokenManager.getResumeName().ifBlank { "Student_Resume_CV.pdf" }
    var showInAppResumeViewer by remember { mutableStateOf(false) }

    val linkedinUrl = profile?.linkedinUrl?.takeIf(String::isNotBlank) ?: tokenManager.getLinkedinUrl().takeIf(String::isNotBlank)
    val githubUrl = profile?.githubUrl?.takeIf(String::isNotBlank)
        ?: profile?.githubUsername?.takeIf(String::isNotBlank)?.let { if (it.startsWith("http")) it else "https://github.com/$it" }
        ?: tokenManager.getGithubUrl().takeIf(String::isNotBlank)
    val portfolioUrl = profile?.portfolioUrl?.takeIf(String::isNotBlank) ?: tokenManager.getPortfolioUrl().takeIf(String::isNotBlank)
    val isLinkedinConnected = profile?.isLinkedinConnected == true || !linkedinUrl.isNullOrBlank()

    // Cover and Avatar
    var coverPhotoUri by remember { mutableStateOf(tokenManager.getCoverPhotoUrl()) }
    var showCoverOptionsDialog by remember { mutableStateOf(false) }
    var profilePhotoUri by remember {
        mutableStateOf(tokenManager.getProfilePhotoUrl() ?: profile?.effectiveProfilePhoto)
    }

    LaunchedEffect(profile) {
        val resolved = profile?.effectiveProfilePhoto ?: tokenManager.getProfilePhotoUrl()
        if (!resolved.isNullOrBlank()) {
            profilePhotoUri = resolved
        }
    }

    fun launchLinkedInConnect() {
        scope.launch {
            try {
                isLinkedInConnecting = true
                val api = ApiClient.getService(tokenManager)
                val authRes = api.getLinkedInAuthUrl("mobile")
                val authUrl = authRes.body()?.authUrl ?: authRes.body()?.authorizationUrl ?: authRes.body()?.url
                if (!authUrl.isNullOrBlank()) {
                    val intent = CustomTabsIntent.Builder().build()
                    intent.launchUrl(context, Uri.parse(authUrl))
                } else {
                    Toast.makeText(context, "Could not start LinkedIn connection. Please try again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error starting LinkedIn. Please check connection.", Toast.LENGTH_SHORT).show()
            } finally {
                isLinkedInConnecting = false
            }
        }
    }

    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coverPhotoUri = uri.toString()
            tokenManager.saveCoverPhotoUrl(uri.toString())
            Toast.makeText(context, "Cover photo updated", Toast.LENGTH_SHORT).show()
        }
    }

    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            profilePhotoUri = uri.toString()
            tokenManager.saveProfilePhotoUrl(uri.toString())
            if (!isLocalBackendConnected) {
                Toast.makeText(context, "Photo saved locally.", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                isPhotoUploading = true
                try {
                    val profileId = profile?.id?.takeIf(String::isNotBlank) ?: "me"
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: error("Failed to read image")
                    val ext = mime.substringAfter('/', "jpg").substringBefore('+')
                    val part = MultipartBody.Part.createFormData(
                        "profile_photo", "photo.$ext", bytes.toRequestBody(mime.toMediaTypeOrNull())
                    )
                    val res = repository.uploadPhoto(profileId, part)
                    if (res.isSuccessful && res.body() != null) {
                        onProfileUpdated(res.body()!!)
                        Toast.makeText(context, "Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Photo saved locally.", Toast.LENGTH_SHORT).show()
                } finally {
                    isPhotoUploading = false
                }
            }
        }
    }

    val resumeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (!isLocalBackendConnected) {
                Toast.makeText(context, "Cannot upload resume while offline. Reconnect to sync.", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                isResumeUploading = true
                try {
                    val fileName = withContext(Dispatchers.IO) {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "resume.pdf"
                    }
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: error("Failed to read resume file")
                    val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                    val part = MultipartBody.Part.createFormData(
                        "resume", fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                    )
                    val res = repository.uploadResume(profile?.id?.takeIf(String::isNotBlank) ?: "me", part)
                    if (res.isSuccessful && res.body() != null) {
                        val updatedProfile = res.body()!!
                        onProfileUpdated(updatedProfile)
                        val serverUrl = updatedProfile.resumeUrl?.takeIf(String::isNotBlank)
                            ?: updatedProfile.resume?.takeIf(String::isNotBlank)
                        if (serverUrl != null) {
                            tokenManager.saveResumeDetails(serverUrl, fileName)
                        } else {
                            tokenManager.saveResumeDetails(uri.toString(), fileName)
                        }
                        Toast.makeText(context, "Resume uploaded successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Upload failed: ${res.code()} — please retry.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: "resume.pdf"
                    tokenManager.saveResumeDetails(uri.toString(), fallbackName)
                    Toast.makeText(context, "Resume saved locally.", Toast.LENGTH_SHORT).show()
                } finally {
                    isResumeUploading = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            scope.launch {
                                try {
                                    Toast.makeText(context, "Saving Profile Card...", Toast.LENGTH_SHORT).show()
                                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                    saveBitmapToGalleryHelper(context, bitmap, "SureTrust_Student_${name.replace(" ", "_")}")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                },
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // ── 1. Top Cover Banner & Overlapping Avatar ──
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
                                .clickable { showCoverOptionsDialog = true },
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
                        StudentProfileAvatar(
                            photo = profilePhotoUri,
                            displayName = name,
                            size = 104.dp,
                            badgeColor = PrimaryPurple,
                            onEditClick = { showPhotoOptionsSheet = true }
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = name,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = TextMain,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isVerifiedStudent) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Student",
                                    tint = VerifiedGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { showEditSheet = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 1.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = TextMain,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    Text(
                        text = tagline,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "$qualification • $college",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = location,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.2.dp, PrimaryPurple),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "Student Scholar",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryPurple
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.2.dp, if (isVerifiedStudent) Color(0xFF059669) else MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = if (isVerifiedStudent) Color(0xFF059669) else TextMain,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = studentIdDisplay,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVerifiedStudent) Color(0xFF059669) else TextMain
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showEditSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }

                        OutlinedButton(
                            onClick = { showQrModal = true },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ID Card", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── 3. Academic Highlights Row (Accurate & Calculated Metrics) ──
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = "Academic Highlights",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                    Spacer(Modifier.height(10.dp))

                    val coursesCount = stats?.activeCohort?.let { 1 }
                        ?: stats?.totalApplications
                        ?: if (cohortCode.isNotBlank() || courseTitle.isNotBlank()) 1 else 0

                    val attendancePct = if (stats != null && stats.attendancePercentage > 0.0) {
                        String.format(Locale.US, "%.0f%%", stats.attendancePercentage)
                    } else if (stats != null) {
                        "0%"
                    } else {
                        "—"
                    }

                    val passedModules = stats?.moduleTestsPassed ?: stats?.examsTaken ?: 0
                    val certsCount = stats?.certificateCount ?: if (stats?.screeningQualified == true) 1 else 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricPillCard(
                            title = "Courses",
                            value = "$coursesCount",
                            subtitle = if (coursesCount == 1) "Enrolled" else "Enrolled",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            accentColor = PrimaryPurple,
                            modifier = Modifier.weight(1f)
                        )
                        MetricPillCard(
                            title = "Attendance",
                            value = attendancePct,
                            subtitle = "Overall Record",
                            icon = Icons.Default.EventAvailable,
                            accentColor = Color(0xFF059669),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricPillCard(
                            title = "Modules Passed",
                            value = "$passedModules",
                            subtitle = "Assessments",
                            icon = Icons.Default.AssignmentTurnedIn,
                            accentColor = Color(0xFF0284C7),
                            modifier = Modifier.weight(1f)
                        )
                        MetricPillCard(
                            title = "Certificates",
                            value = "$certsCount",
                            subtitle = "Earned",
                            icon = Icons.Default.WorkspacePremium,
                            accentColor = Color(0xFFD97706),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 4. Personal & Contact Details Section ──
            item {
                ProfileSectionCard(title = "Personal & Contact Details", icon = Icons.Default.ContactMail, accentColor = PrimaryPurple) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailRowItem(label = "Email Address", value = email.ifBlank { "Not provided" })
                        DetailRowItem(label = "Phone Number", value = phone.ifBlank { "Not provided" })
                        if (gender.isNotBlank()) {
                            DetailRowItem(label = "Gender", value = gender)
                        }
                        if (dob.isNotBlank()) {
                            DetailRowItem(label = "Date of Birth", value = dob)
                        }
                        DetailRowItem(label = "Location", value = location)
                        if (permanentAddress.isNotBlank()) {
                            DetailRowItem(label = "Permanent Address", value = permanentAddress)
                        }
                    }
                }
            }

            // ── 5. Academic & Cohort Details ──
            item {
                ProfileSectionCard(title = "Education & Academics", icon = Icons.Default.School, accentColor = PrimaryPurple) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (courseTitle.isNotBlank()) {
                            DetailRowItem(label = "Enrolled Program / Course", value = courseTitle)
                        }
                        if (cohortCode.isNotBlank()) {
                            DetailRowItem(label = "Assigned Cohort", value = cohortCode)
                        }
                        DetailRowItem(label = "Degree / Current Qualification", value = qualification)
                        if (!specialization.isNullOrBlank()) {
                            DetailRowItem(label = "Specialization / Branch", value = specialization)
                        }
                        DetailRowItem(label = "College / University", value = college)
                        if (gradYear != null && gradYear > 0) {
                            DetailRowItem(label = "Graduation Year", value = gradYear.toString())
                        }
                        DetailRowItem(label = "Student Code", value = studentIdDisplay)
                        DetailRowItem(label = "Enrollment Status", value = enrollmentStatus)
                    }
                }
            }

            // ── 6. About & Personal Statement ──
            item {
                ProfileSectionCard(title = "About / Bio", icon = Icons.Default.Person, accentColor = PrimaryPurple) {
                    if (bio.isNotBlank()) {
                        Text(
                            text = bio,
                            fontSize = 14.sp,
                            color = TextMuted,
                            lineHeight = 22.sp
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "No personal bio added yet.",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Edit Profile' to share your professional background, goals, and interests.",
                                fontSize = 12.sp,
                                color = PrimaryPurple,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { showEditSheet = true }
                            )
                        }
                    }
                }
            }

            // ── 7. Technical Skills ──
            item {
                ProfileSectionCard(title = "Skills & Competencies", icon = Icons.Default.Code, accentColor = PrimaryPurple) {
                    if (skills.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            skills.forEach { skill ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = skill,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextMain,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "No skills added yet.",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Edit Profile' to list your technical skills and competencies.",
                                fontSize = 12.sp,
                                color = PrimaryPurple,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { showEditSheet = true }
                            )
                        }
                    }
                }
            }

            // ── 8. Hobbies & Interests ──
            if (hobbies.isNotEmpty()) {
                item {
                    ProfileSectionCard(title = "Hobbies & Interests", icon = Icons.Default.SportsEsports, accentColor = PrimaryPurple) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            hobbies.forEach { hobby ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = hobby,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PrimaryPurple,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 9. Languages Known ──
            if (languages.isNotEmpty()) {
                item {
                    ProfileSectionCard(title = "Languages Known", icon = Icons.Default.Translate, accentColor = PrimaryPurple) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            languages.forEach { lang ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = semanticColors.successContainer,
                                    border = BorderStroke(1.dp, semanticColors.success.copy(alpha = 0.55f))
                                ) {
                                    Text(
                                        text = lang,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = semanticColors.onSuccessContainer,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 10. Official Resume / CV ──
            item {
                ProfileSectionCard(title = "Resume / Curriculum Vitae", icon = Icons.Default.Description, accentColor = PrimaryPurple) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!resumeUrl.isNullOrBlank()) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showInAppResumeViewer = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = resumeName.ifBlank { "Student_Resume_CV.pdf" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = TextMain,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Official Resume Document • Tap to View",
                                            fontSize = 11.5.sp,
                                            color = VerifiedGreen
                                        )
                                    }
                                    IconButton(onClick = { showInAppResumeViewer = true }) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "View in App", tint = PrimaryPurple)
                                    }
                                }
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = semanticColors.warningContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.UploadFile,
                                                contentDescription = null,
                                                tint = semanticColors.warning,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "No resume uploaded yet",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = TextMain
                                        )
                                        Text(
                                            text = "Upload your CV below to make it visible to mentors and employers",
                                            fontSize = 11.5.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { resumeLauncher.launch("application/pdf") },
                            enabled = !isResumeUploading && isLocalBackendConnected,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!resumeUrl.isNullOrBlank()) Color(0xFF1E293B) else PrimaryPurple
                            )
                        ) {
                            if (isResumeUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = if (!isLocalBackendConnected) "Upload Disabled (Offline)" else if (!resumeUrl.isNullOrBlank()) "Replace Resume (PDF)" else "Upload Resume (PDF)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // ── 11. Professional Links & Social Profiles ──
            item {
                ProfileSectionCard(title = "Professional Links", icon = Icons.Default.Link, accentColor = PrimaryPurple) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SocialLinkRow(
                            label = "LinkedIn Profile",
                            value = if (!linkedinUrl.isNullOrBlank()) linkedinUrl else if (isLinkedinConnected) "Connected via OAuth" else "Not linked • Tap to connect & sync photo",
                            isConnected = isLinkedinConnected,
                            icon = Icons.Default.Share,
                            accentColor = Color(0xFF0A66C2),
                            onClick = {
                                if (!linkedinUrl.isNullOrBlank()) {
                                    val url = if (linkedinUrl.startsWith("http")) linkedinUrl else "https://$linkedinUrl"
                                    uriHandler.openUri(url)
                                } else {
                                    launchLinkedInConnect()
                                }
                            }
                        )

                        if (!isLinkedinConnected) {
                            Button(
                                onClick = { launchLinkedInConnect() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A66C2))
                            ) {
                                if (isLinkedInConnecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Connect LinkedIn & Fetch Photo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        SocialLinkRow(
                            label = "GitHub Profile",
                            value = if (!githubUrl.isNullOrBlank()) githubUrl else "Not linked • Tap to add GitHub URL",
                            isConnected = !githubUrl.isNullOrBlank(),
                            icon = Icons.Default.Code,
                            accentColor = PrimaryPurple,
                            onClick = {
                                if (!githubUrl.isNullOrBlank()) {
                                    val url = if (githubUrl.startsWith("http")) githubUrl else "https://$githubUrl"
                                    uriHandler.openUri(url)
                                } else {
                                    showEditSheet = true
                                }
                            }
                        )
                        SocialLinkRow(
                            label = "Personal Portfolio",
                            value = if (!portfolioUrl.isNullOrBlank()) portfolioUrl else "Not linked • Tap to add Portfolio URL",
                            isConnected = !portfolioUrl.isNullOrBlank(),
                            icon = Icons.Default.Language,
                            accentColor = PrimaryPurple,
                            onClick = {
                                if (!portfolioUrl.isNullOrBlank()) {
                                    val url = if (portfolioUrl.startsWith("http")) portfolioUrl else "https://$portfolioUrl"
                                    uriHandler.openUri(url)
                                } else {
                                    showEditSheet = true
                                }
                            }
                        )
                    }
                }
            }
        }

        // Photo Options Bottom Sheet
        if (showPhotoOptionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPhotoOptionsSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Update Profile Photo",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                    Text(
                        text = "Choose how you want to update your profile photo.",
                        fontSize = 13.sp,
                        color = TextMuted
                    )

                    Spacer(Modifier.height(4.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoOptionsSheet = false
                                avatarLauncher.launch("image/*")
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PrimaryPurple.copy(alpha = 0.12f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PhotoLibrary, null, tint = PrimaryPurple, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Choose from Gallery / Camera", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMain)
                                Text("Select an image file from your device", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoOptionsSheet = false
                                launchLinkedInConnect()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0A66C2).copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Share, null, tint = Color(0xFF0A66C2), modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Connect & Fetch from LinkedIn", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMain)
                                Text("Sync your verified LinkedIn profile avatar", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoOptionsSheet = false
                                showEditSheet = true
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF64748B).copy(alpha = 0.12f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Link, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Enter Photo URL Manually", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMain)
                                Text("Paste a direct link to your photo in Edit Profile", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }

        // Edit Profile Bottom Sheet
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
            EditStudentProfileBottomSheet(
                tokenManager = tokenManager,
                currentName = name,
                currentPhone = phone,
                currentGender = gender,
                currentDob = dob,
                currentPermanentAddress = permanentAddress,
                currentQualification = qualification,
                currentCollege = college,
                currentTagline = tagline,
                currentSpecialization = specialization ?: "",
                currentGradYear = gradYear,
                currentCity = city,
                currentState = state,
                currentCountry = country,
                currentBio = bio,
                currentSkills = skills,
                currentHobbies = hobbies,
                currentLanguages = languages,
                currentPhotoUrl = profilePhotoUri ?: "",
                currentLinkedin = linkedinUrl ?: "",
                currentGithub = githubUrl ?: "",
                currentPortfolio = portfolioUrl ?: "",
                onDismiss = { showEditSheet = false },
                onSave = { updatedDetails ->
                    scope.launch {
                        try {
                            if (updatedDetails.name.isNotBlank()) {
                                tokenManager.saveUserInfo(updatedDetails.name, email)
                            }
                            if (updatedDetails.photoUrl.isNotBlank()) {
                                tokenManager.saveProfilePhotoUrl(updatedDetails.photoUrl)
                                profilePhotoUri = updatedDetails.photoUrl
                            }
                            tokenManager.saveStudentProfileDetails(
                                phone = updatedDetails.phone,
                                qualification = updatedDetails.qualification,
                                collegeName = updatedDetails.college,
                                bio = updatedDetails.bio,
                                githubUrl = updatedDetails.github,
                                linkedinUrl = updatedDetails.linkedin,
                                tagline = updatedDetails.tagline,
                                specialization = updatedDetails.specialization,
                                graduationYear = updatedDetails.gradYear,
                                city = updatedDetails.city,
                                state = updatedDetails.state,
                                country = updatedDetails.country,
                                skills = updatedDetails.skills,
                                hobbies = updatedDetails.hobbies,
                                languages = updatedDetails.languages,
                                portfolioUrl = updatedDetails.portfolio,
                                gender = updatedDetails.gender,
                                dob = updatedDetails.dob,
                                permanentAddress = updatedDetails.permanentAddress
                            )

                            if (isLocalBackendConnected && profile?.id?.isNotBlank() == true) {
                                val body: MutableMap<String, Any?> = mutableMapOf(
                                    "tagline" to updatedDetails.tagline,
                                    "bio" to updatedDetails.bio,
                                    "degree" to updatedDetails.qualification,
                                    "college" to updatedDetails.college,
                                    "specialization" to updatedDetails.specialization,
                                    "graduation_year" to updatedDetails.gradYear,
                                    "city" to updatedDetails.city,
                                    "state" to updatedDetails.state,
                                    "country" to updatedDetails.country,
                                    "gender" to updatedDetails.gender,
                                    "date_of_birth" to updatedDetails.dob,
                                    "permanent_address" to updatedDetails.permanentAddress,
                                    "github_url" to updatedDetails.github,
                                    "linkedin_url" to updatedDetails.linkedin,
                                    "portfolio_url" to updatedDetails.portfolio,
                                    "skills" to updatedDetails.skills,
                                    "hobbies" to updatedDetails.hobbies,
                                    "languages" to updatedDetails.languages
                                )
                                if (updatedDetails.photoUrl.isNotBlank()) {
                                    body["profile_photo"] = updatedDetails.photoUrl
                                    body["profile_photo_url"] = updatedDetails.photoUrl
                                }
                                val updatedDto = repository.update(profile.id, body)
                                if (updatedDto.isSuccessful && updatedDto.body() != null) {
                                    onProfileUpdated(updatedDto.body()!!)
                                }
                                val userId = profile.userId ?: profile.user?.id
                                if (!userId.isNullOrBlank()) {
                                    val userBody = mutableMapOf<String, Any?>()
                                    if (updatedDetails.phone.isNotBlank()) userBody["phone_number"] = updatedDetails.phone
                                    if (updatedDetails.gender.isNotBlank()) userBody["gender"] = updatedDetails.gender
                                    if (updatedDetails.dob.isNotBlank()) userBody["date_of_birth"] = updatedDetails.dob
                                    val nameParts = updatedDetails.name.trim().split(" ", limit = 2)
                                    if (nameParts.isNotEmpty()) userBody["first_name"] = nameParts[0]
                                    if (nameParts.size > 1) userBody["last_name"] = nameParts[1]
                                    runCatching { repository.patchUser(userId, userBody) }
                                }
                            } else {
                                // Update local profile state immediately
                                onProfileUpdated(
                                    StudentProfileDto(
                                        id = profile?.id.orEmpty(),
                                        studentCode = rawStudentCode,
                                        profilePhoto = updatedDetails.photoUrl.takeIf(String::isNotBlank) ?: profilePhotoUri,
                                        tagline = updatedDetails.tagline,
                                        degree = updatedDetails.qualification,
                                        college = updatedDetails.college,
                                        specialization = updatedDetails.specialization,
                                        graduationYear = updatedDetails.gradYear,
                                        city = updatedDetails.city,
                                        state = updatedDetails.state,
                                        country = updatedDetails.country,
                                        gender = updatedDetails.gender,
                                        dateOfBirth = updatedDetails.dob,
                                        permanentAddress = updatedDetails.permanentAddress,
                                        skills = updatedDetails.skills,
                                        hobbies = updatedDetails.hobbies,
                                        languages = updatedDetails.languages,
                                        bio = updatedDetails.bio,
                                        githubUrl = updatedDetails.github,
                                        linkedinUrl = updatedDetails.linkedin,
                                        portfolioUrl = updatedDetails.portfolio,
                                        cohortCode = cohortCode
                                    )
                                )
                            }
                            Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            showEditSheet = false
                        } catch (e: Exception) {
                            Toast.makeText(context, "Profile saved locally.", Toast.LENGTH_SHORT).show()
                            showEditSheet = false
                        }
                    }
                }
            )
        }

        // Student ID Card Modal
        if (showQrModal) {
            StudentIdCardModal(
                name = name,
                studentId = studentIdDisplay,
                role = "STUDENT SCHOLAR",
                email = email,
                college = college,
                qualification = qualification,
                badgeColor = PrimaryPurple,
                onDismiss = { showQrModal = false }
            )
        }

        // In-App Resume & PDF Document Viewer Modal
        if (showInAppResumeViewer) {
            com.example.suretouchapp.ui.components.InAppDocumentViewerDialog(
                documentUrl = resumeUrl ?: "",
                documentTitle = resumeName.ifBlank { "Student_Resume_CV.pdf" },
                onDismiss = { showInAppResumeViewer = false }
            )
        }
    }
}

// ── Edit Student Profile Bottom Sheet ──

data class StudentProfileEditState(
    val name: String,
    val phone: String,
    val gender: String,
    val dob: String,
    val permanentAddress: String,
    val qualification: String,
    val college: String,
    val tagline: String,
    val specialization: String,
    val gradYear: Int?,
    val city: String,
    val state: String,
    val country: String,
    val bio: String,
    val skills: List<String>,
    val hobbies: List<String>,
    val languages: List<String>,
    val photoUrl: String,
    val linkedin: String,
    val github: String,
    val portfolio: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStudentProfileBottomSheet(
    tokenManager: TokenManager,
    currentName: String,
    currentPhone: String,
    currentGender: String = "",
    currentDob: String = "",
    currentPermanentAddress: String = "",
    currentQualification: String,
    currentCollege: String,
    currentTagline: String,
    currentSpecialization: String,
    currentGradYear: Int?,
    currentCity: String = "",
    currentState: String = "",
    currentCountry: String = "",
    currentBio: String,
    currentSkills: List<String>,
    currentHobbies: List<String>,
    currentLanguages: List<String>,
    currentPhotoUrl: String = "",
    currentLinkedin: String,
    currentGithub: String,
    currentPortfolio: String,
    onDismiss: () -> Unit,
    onSave: (StudentProfileEditState) -> Unit
) {
    val isConnected = LocalBackendConnected.current
    var name by remember { mutableStateOf(currentName) }
    var phone by remember { mutableStateOf(currentPhone) }
    var gender by remember { mutableStateOf(currentGender) }
    var dob by remember { mutableStateOf(currentDob) }
    var permanentAddress by remember { mutableStateOf(currentPermanentAddress) }
    var qualification by remember { mutableStateOf(currentQualification) }
    var college by remember { mutableStateOf(currentCollege) }
    var tagline by remember { mutableStateOf(currentTagline) }
    var specialization by remember { mutableStateOf(currentSpecialization) }
    var gradYearStr by remember { mutableStateOf(currentGradYear?.toString() ?: "") }
    var city by remember { mutableStateOf(currentCity) }
    var state by remember { mutableStateOf(currentState) }
    var country by remember { mutableStateOf(currentCountry) }
    var bio by remember { mutableStateOf(currentBio) }
    var skillsStr by remember { mutableStateOf(currentSkills.joinToString(", ")) }
    var hobbiesStr by remember { mutableStateOf(currentHobbies.joinToString(", ")) }
    var languagesStr by remember { mutableStateOf(currentLanguages.joinToString(", ")) }
    var photoUrl by remember { mutableStateOf(currentPhotoUrl) }
    var linkedin by remember { mutableStateOf(currentLinkedin) }
    var github by remember { mutableStateOf(currentGithub) }
    var portfolio by remember { mutableStateOf(currentPortfolio) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Edit Student Profile",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
            Text(
                text = "Update your verified academic and personal credentials.",
                fontSize = 12.5.sp,
                color = TextMuted
            )

            if (!isConnected) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Offline Mode: Changes will be saved locally and sync upon reconnecting.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        label = { Text("Professional Headline / Tagline") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = photoUrl,
                        onValueChange = { photoUrl = it },
                        label = { Text("Profile Photo URL / LinkedIn Photo Link") },
                        placeholder = { Text("https://media.licdn.com/... or image URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = gender,
                        onValueChange = { gender = it },
                        label = { Text("Gender (e.g. Male, Female, Other)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { dob = it },
                        label = { Text("Date of Birth (dd-mm-yyyy)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = qualification,
                        onValueChange = { qualification = it },
                        label = { Text("Degree / Current Program") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = specialization,
                        onValueChange = { specialization = it },
                        label = { Text("Specialization / Branch") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = college,
                        onValueChange = { college = it },
                        label = { Text("College / University Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = gradYearStr,
                        onValueChange = { gradYearStr = it },
                        label = { Text("Graduation Year") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("City") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state,
                            onValueChange = { state = it },
                            label = { Text("State") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = permanentAddress,
                        onValueChange = { permanentAddress = it },
                        label = { Text("Permanent Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("About / Bio") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = skillsStr,
                        onValueChange = { skillsStr = it },
                        label = { Text("Technical Skills (Comma Separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = hobbiesStr,
                        onValueChange = { hobbiesStr = it },
                        label = { Text("Hobbies (Comma Separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = languagesStr,
                        onValueChange = { languagesStr = it },
                        label = { Text("Languages Known (Comma Separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = linkedin,
                        onValueChange = { linkedin = it },
                        label = { Text("LinkedIn Profile URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = github,
                        onValueChange = { github = it },
                        label = { Text("GitHub Profile URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = portfolio,
                        onValueChange = { portfolio = it },
                        label = { Text("Portfolio / Website URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val parsedSkills = skillsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val parsedHobbies = hobbiesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val parsedLangs = languagesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val gradYear = gradYearStr.trim().toIntOrNull()
                        onSave(
                            StudentProfileEditState(
                                name = name.trim(),
                                phone = phone.trim(),
                                gender = gender.trim(),
                                dob = dob.trim(),
                                permanentAddress = permanentAddress.trim(),
                                qualification = qualification.trim(),
                                college = college.trim(),
                                tagline = tagline.trim(),
                                specialization = specialization.trim(),
                                gradYear = gradYear,
                                city = city.trim(),
                                state = state.trim(),
                                country = country.trim(),
                                bio = bio.trim(),
                                skills = parsedSkills,
                                hobbies = parsedHobbies,
                                languages = parsedLangs,
                                photoUrl = photoUrl.trim(),
                                linkedin = linkedin.trim(),
                                github = github.trim(),
                                portfolio = portfolio.trim()
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
