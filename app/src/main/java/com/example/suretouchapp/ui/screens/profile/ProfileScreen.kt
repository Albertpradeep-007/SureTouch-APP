package com.example.suretouchapp.ui.screens.profile

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.StudentProfileDto
import com.example.suretouchapp.data.repository.StudentProfileRepository
import com.example.suretouchapp.data.repository.StudentStatisticsRepository
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.components.StudentProfileImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private val ProfilePurple = Color(0xFF6C2BD9)
private val ProfileDeep = Color(0xFF4C1D95)
private val ProfileInk = Color(0xFF101A3C)
private val ProfileMuted = Color(0xFF52617E)
private val ProfileCanvas = Color(0xFFF7F8FE)
private val ProfileBorder = Color(0xFFE1E5F2)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(tokenManager: TokenManager, onBack: () -> Unit) {
    when {
        tokenManager.isMentor() -> {
            MentorProfessionalProfileScreen(tokenManager = tokenManager, onBack = onBack)
        }
        tokenManager.isVolunteerTrustee() -> {
            VolunteerTrusteeProfessionalProfileScreen(tokenManager = tokenManager, onBack = onBack)
        }
        tokenManager.isCompany() -> {
            CompanyProfessionalProfileScreen(tokenManager = tokenManager, onBack = onBack)
        }
        else -> {
            StudentProfessionalProfileScreen(tokenManager = tokenManager, onBack = onBack)
        }
    }
}

@Composable
private fun LegacyStudentProfileScreenUnused(tokenManager: TokenManager, onBack: () -> Unit) {
    var profile by remember { mutableStateOf<StudentProfileDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isPhotoUploading by remember { mutableStateOf(false) }
    var photoUploadError by remember { mutableStateOf<String?>(null) }
    var isResumeUploading by remember { mutableStateOf(false) }
    var resumeUploadError by remember { mutableStateOf<String?>(null) }
    var resumeUploadSuccess by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileRepository = remember(tokenManager) { StudentProfileRepository(tokenManager) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (!isConnected) {
                photoUploadError = "Cannot change profile photo while offline. Reconnect to sync changes."
                return@rememberLauncherForActivityResult
            }
            selectedPhotoUri = uri
            scope.launch {
                isPhotoUploading = true
                photoUploadError = null
                try {
                    val profileId = profile?.id?.takeIf(String::isNotBlank)
                        ?: error("Student profile is not available")
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: error("The selected image could not be read")
                    val extension = mimeType.substringAfter('/', "jpg").substringBefore('+')
                    val photoPart = MultipartBody.Part.createFormData(
                        "profile_photo",
                        "profile-photo.$extension",
                        bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                    )
                    val response = profileRepository.uploadPhoto(profileId, photoPart)
                    if (!response.isSuccessful) error("Photo upload failed")
                    profile = response.body() ?: profileRepository.load()
                    selectedPhotoUri = null
                } catch (_: Exception) {
                    photoUploadError = "The photo could not be uploaded. Check your connection and try again."
                } finally {
                    isPhotoUploading = false
                }
            }
        }
    }

    val resumePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (!isConnected) {
                resumeUploadError = "Cannot upload resume while offline. Reconnect to sync changes."
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                isResumeUploading = true
                resumeUploadError = null
                resumeUploadSuccess = null
                try {
                    var profileId = profile?.id?.takeIf(String::isNotBlank)
                    if (profileId.isNullOrBlank()) {
                        val refreshed = profileRepository.load()
                        profileId = refreshed?.id?.takeIf(String::isNotBlank)
                    }
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) cursor.getString(nameIndex) else "resume.pdf"
                    } ?: "resume.pdf"

                    val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: error("The selected resume file could not be read")

                    if (!profileId.isNullOrBlank()) {
                        val resumePart = MultipartBody.Part.createFormData(
                            "resume",
                            fileName,
                            bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                        )
                        val response = runCatching { profileRepository.uploadResume(profileId, resumePart) }.getOrNull()
                        if (response?.isSuccessful == true) {
                            profile = response.body() ?: profileRepository.load()
                        }
                    }
                    tokenManager.saveResumeDetails(
                        resumeUrl = profile?.resume ?: profile?.resumeUrl ?: uri.toString(),
                        fileName = fileName
                    )
                    resumeUploadSuccess = "Resume '$fileName' uploaded successfully!"
                } catch (e: Exception) {
                    resumeUploadError = "Could not upload resume. Please check your connection and try again."
                } finally {
                    isResumeUploading = false
                }
            }
        }
    }

    var applicationNumber by remember {
        mutableStateOf(tokenManager.getApplicationSnapshot()?.applicationNumber)
    }

    suspend fun loadProfile() {
        val email = tokenManager.getUserEmail().trim().lowercase()
        val role = tokenManager.getUserRole().trim().uppercase()
        if (role == "ADMIN" || role == "SUPERADMIN" || email.startsWith("admin@") || email.contains("admin")) {
            tokenManager.clear()
            return
        }
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val loaded = profileRepository.load()
            if (loaded != null) {
                profile = loaded
                val stats = runCatching { StudentStatisticsRepository(tokenManager).load() }.getOrNull()
                if (!stats?.applicationNumber.isNullOrBlank()) {
                    applicationNumber = stats?.applicationNumber
                }
                isConnected = true
                hasLoadedOnce = true
                isOffline = false
                connectionError = null
                errorTitle = null
            } else {
                val errorInfo = NetworkUtils.getNetworkErrorInfo(context, null)
                isConnected = false
                isOffline = errorInfo.isOffline
                errorTitle = errorInfo.title
                connectionError = errorInfo.message
            }
        } catch (e: Exception) {
            val errorInfo = NetworkUtils.getNetworkErrorInfo(context, e)
            isConnected = false
            isOffline = errorInfo.isOffline
            errorTitle = errorInfo.title
            connectionError = errorInfo.message
            // Offline fallback: load cached profile from local TokenManager so UI is always visible
            if (profile == null) {
                profile = profileRepository.load()
            }
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadProfile()
    }

    val isMentor = tokenManager.isMentor()
    val isVolunteerTrustee = tokenManager.isVolunteerTrustee()
    val roleLabel = when {
        isMentor -> "Trust Mentor"
        isVolunteerTrustee -> "Volunteer Trustee"
        else -> "Student"
    }

    val apiUser = profile?.user
    val apiName = listOfNotNull(apiUser?.firstName, apiUser?.lastName)
        .joinToString(" ").trim()
    val displayName = apiName.ifBlank { tokenManager.getUserName().ifBlank { roleLabel } }
    val assignedCohort = profile?.cohortCode?.takeIf { it.isNotBlank() }
        ?: tokenManager.getCohortCode().ifBlank { null }
    val isStudentIdentityIssued = assignedCohort != null || isMentor || isVolunteerTrustee
    val studentCode = when {
        isMentor -> "Trust Mentor"
        isVolunteerTrustee -> "Volunteer Trustee"
        !profile?.studentCode.isNullOrBlank() -> {
            val clean = profile?.studentCode.orEmpty().removePrefix("STU-").removePrefix("ST-").removePrefix("ST_").trim()
            "STU-$clean"
        }
        !tokenManager.getStudentCode().isNullOrBlank() -> {
            val clean = tokenManager.getStudentCode().removePrefix("STU-").removePrefix("ST-").removePrefix("ST_").trim()
            "STU-$clean"
        }
        !applicationNumber.isNullOrBlank() -> {
            val parts = applicationNumber.orEmpty().removePrefix("APP-").removePrefix("APP_").split("-")
            val cleanId = if (parts.size >= 2 && parts[0].length == 4 && parts[1].all { it.isDigit() }) {
                "${parts[0]}-${parts[1]}"
            } else if (parts.isNotEmpty()) {
                parts[0]
            } else {
                applicationNumber.orEmpty().take(10)
            }
            "STU-$cleanId"
        }
        else -> "STU-${String.format(java.util.Locale.US, "%05d", kotlin.math.abs(tokenManager.getUserEmail().hashCode()) % 100000)}"
    }
    val cohort = when {
        isMentor -> "Mentor Desk"
        isVolunteerTrustee -> "Trustee Board"
        assignedCohort != null -> assignedCohort
        else -> "Cohort Assignment pending"
    }
    val degreeLine = listOfNotNull(
        profile?.degree ?: tokenManager.getQualification().takeIf(String::isNotBlank),
        profile?.specialization ?: tokenManager.getSpecialization().takeIf(String::isNotBlank)
    ).filter { it.isNotBlank() }.joinToString(" • ")
        .ifBlank { if (isMentor || isVolunteerTrustee) "Profile details verified" else "Academic details not provided" }

    val resolvedTagline = profile?.tagline?.takeIf(String::isNotBlank) ?: tokenManager.getTagline().takeIf(String::isNotBlank)
    val isLinkedinSynced = profile?.isLinkedinConnected == true || !profile?.linkedinUrl.isNullOrBlank() || tokenManager.getLinkedinUrl().isNotBlank()

    BackendConnectionGate(
        isLoading = isLoading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Student Profile...",
        onRetry = { scope.launch { loadProfile() } },
        onLogout = null
    ) {
        Scaffold(containerColor = ProfileCanvas) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                ProfileHeader(onBack = onBack, onQr = { showQr = true })
                ProfileHeroCard(
                    displayName = displayName,
                    studentCode = studentCode,
                    degreeLine = degreeLine,
                    cohort = cohort,
                    tagline = resolvedTagline,
                    isLinkedinConnected = isLinkedinSynced,
                    photoUrl = selectedPhotoUri?.toString() ?: profile?.profilePhoto ?: tokenManager.getProfilePhotoUrl(),
                    isPhotoUploading = isPhotoUploading,
                    isStudentIdentityIssued = isStudentIdentityIssued,
                    onChangePhoto = { photoPicker.launch("image/*") },
                    onEdit = { showEdit = true }
                )
                photoUploadError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                BasicInformationCard(profile = profile, tokenManager = tokenManager)
                AcademicAndLinksCard(profile = profile, tokenManager = tokenManager)
                SkillsAndPreferencesCard(
                    profile = profile,
                    tokenManager = tokenManager,
                    onEditPreferences = { showEdit = true }
                )
                ResumeAndDocumentsCard(
                    profile = profile,
                    tokenManager = tokenManager,
                    isUploading = isResumeUploading,
                    onUploadResume = { resumePicker.launch("*/*") }
                )
                resumeUploadError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                resumeUploadSuccess?.let { message ->
                    Text(message, color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { tokenManager.clear() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFEE2E2),
                        contentColor = Color(0xFFDC2626)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Log Out", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showQr) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            icon = { Icon(Icons.Default.QrCode2, null, Modifier.size(72.dp), tint = ProfilePurple) },
            title = {
                Text(
                    when {
                        isStudentIdentityIssued -> "Student Identity QR"
                        !applicationNumber.isNullOrBlank() -> "Application QR (Pending)"
                        else -> "Student ID Pending"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (isStudentIdentityIssued) {
                        "$displayName\n$studentCode\nCohort $cohort"
                    } else if (!applicationNumber.isNullOrBlank()) {
                        "$displayName\nApplication ID: $applicationNumber\nStatus: Screening in progress"
                    } else {
                        "Your official Student ID and QR will be issued after enrollment screening is completed and a cohort is assigned."
                    }
                )
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Done") } }
        )
    }

    if (showEdit) {
        ProfileEditSheet(
            tokenManager = tokenManager,
            profile = profile,
            onDismiss = { showEdit = false },
            onSaved = { updated ->
                profile = updated ?: profile
                showEdit = false
            }
        )
    }
}

@Composable
private fun ProfileHeader(onBack: () -> Unit, onQr: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 3.dp
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ProfileInk) } }
        Text("Profile", fontSize = 26.sp, fontWeight = FontWeight.Black, color = ProfileInk)
        Surface(
            onClick = onQr,
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, ProfileBorder)
        ) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode2, null, tint = ProfilePurple)
                Spacer(Modifier.width(6.dp))
                Text("QR", color = ProfilePurple, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProfileHeroCard(
    displayName: String,
    studentCode: String,
    degreeLine: String,
    cohort: String,
    tagline: String? = null,
    isLinkedinConnected: Boolean = false,
    photoUrl: String?,
    isPhotoUploading: Boolean,
    isStudentIdentityIssued: Boolean,
    onChangePhoto: () -> Unit,
    onEdit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, Color(0xFFD7CCFF), RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color.White, Color(0xFFF0EBFF))))
            .padding(18.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, Color(0xFFD7CCFF)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit Profile")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    StudentProfileImage(photoUrl, displayName, Modifier.size(112.dp))
                    Surface(
                        onClick = onChangePhoto,
                        modifier = Modifier.align(Alignment.TopEnd).size(34.dp),
                        shape = CircleShape,
                        color = ProfilePurple,
                        shadowElevation = 3.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isPhotoUploading) {
                                SureTrustLoadingIndicator(size = 25.dp, logoSize = 15.dp, spinnerColor = Color.White)
                            } else {
                                Icon(Icons.Default.PhotoCamera, "Change profile photo", tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                    if (isStudentIdentityIssued) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFD7CCFF)),
                            shadowElevation = 2.dp
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, null, Modifier.size(16.dp), tint = ProfilePurple)
                                Spacer(Modifier.width(4.dp))
                                Text("Verified", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ProfilePurple)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            displayName,
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = 23.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.Black,
                            color = ProfileInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isStudentIdentityIssued) {
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Default.Verified, null, tint = ProfilePurple, modifier = Modifier.size(22.dp))
                        }
                    }
                    if (!tagline.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tagline,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ProfileDeep,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(studentCode, fontSize = 13.sp, color = ProfileMuted, fontWeight = FontWeight.SemiBold)
                    Text(degreeLine, fontSize = 12.5.sp, lineHeight = 17.sp, color = ProfileMuted, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color(0xFFD7CCFF))
                        ) {
                            Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.School, null, Modifier.size(16.dp), tint = ProfilePurple)
                                Spacer(Modifier.width(5.dp))
                                Text("Cohort $cohort", fontSize = 11.5.sp, color = ProfilePurple, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (isLinkedinConnected) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFE8F3FF),
                                border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("in", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A66C2))
                                    Spacer(Modifier.width(4.dp))
                                    Text("LinkedIn", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0A66C2))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BasicInformationCard(profile: StudentProfileDto?, tokenManager: TokenManager) {
    val missing = "Not provided"
    val email = profile?.user?.email?.takeIf { it.isNotBlank() } ?: tokenManager.getUserEmail().ifBlank { missing }
    val phone = profile?.phone?.takeIf { it.isNotBlank() } ?: tokenManager.getPhone().ifBlank { missing }
    val tagline = profile?.tagline?.takeIf { it.isNotBlank() } ?: tokenManager.getTagline().ifBlank { missing }
    val bio = profile?.bio?.takeIf { it.isNotBlank() } ?: tokenManager.getBio().ifBlank { null }
    val cityAddress = listOfNotNull(
        profile?.city ?: tokenManager.getCity().takeIf(String::isNotBlank),
        profile?.state ?: tokenManager.getState().takeIf(String::isNotBlank),
        profile?.country ?: tokenManager.getCountry().takeIf(String::isNotBlank)
    ).filter { it.isNotBlank() }.joinToString(", ").ifBlank { missing }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ProfileBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(44.dp), CircleShape, color = Color(0xFFF0EBFF)) {
                    Icon(Icons.Default.PersonOutline, null, Modifier.padding(10.dp), tint = ProfilePurple)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Basic Information", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ProfileInk)
                    Box(Modifier.padding(top = 5.dp).width(62.dp).height(3.dp).clip(CircleShape).background(ProfilePurple))
                }
            }
            ProfileInfoItem("Headline / Tagline", tagline, Icons.Default.Badge, Modifier.fillMaxWidth())
            ProfileInfoItem("Location", cityAddress, Icons.Default.LocationOn, Modifier.fillMaxWidth())
            if (!bio.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    color = Color(0xFFFBFBFE),
                    border = BorderStroke(1.dp, ProfileBorder)
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
                        Surface(Modifier.size(38.dp), CircleShape, color = Color(0xFFF3EFFF)) {
                            Icon(Icons.Default.FormatQuote, null, Modifier.padding(9.dp), tint = ProfilePurple)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bio / Summary", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                            Spacer(Modifier.height(3.dp))
                            Text(bio, fontSize = 12.sp, lineHeight = 17.sp, color = ProfileMuted)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileInfoItem("Contact No.", phone, Icons.Default.Phone, Modifier.weight(1f))
                ProfileInfoItem("Email", email, Icons.Default.Email, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AcademicAndLinksCard(profile: StudentProfileDto?, tokenManager: TokenManager) {
    val uriHandler = LocalUriHandler.current
    val missing = "Not provided"
    val degree = profile?.degree?.takeIf { it.isNotBlank() } ?: tokenManager.getQualification().ifBlank { missing }
    val college = profile?.college?.takeIf { it.isNotBlank() } ?: tokenManager.getCollegeName().ifBlank { missing }
    val specialization = profile?.specialization?.takeIf { it.isNotBlank() } ?: tokenManager.getSpecialization().ifBlank { missing }
    val graduationYear = profile?.graduationYear ?: tokenManager.getGraduationYear()
    val github = profile?.githubUrl?.takeIf { it.isNotBlank() } ?: tokenManager.getGithubUrl().ifBlank { "Not linked" }
    val linkedin = profile?.linkedinUrl?.takeIf { it.isNotBlank() } ?: tokenManager.getLinkedinUrl().ifBlank { "Not linked" }
    val portfolio = profile?.portfolioUrl?.takeIf { it.isNotBlank() } ?: tokenManager.getPortfolioUrl().ifBlank { "Not linked" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ProfileBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(44.dp), CircleShape, color = Color(0xFFF0EBFF)) {
                    Icon(Icons.Default.School, null, Modifier.padding(10.dp), tint = ProfilePurple)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Education & Professional Links", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ProfileInk)
                    Box(Modifier.padding(top = 5.dp).width(62.dp).height(3.dp).clip(CircleShape).background(ProfilePurple))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileInfoItem("Degree", degree, Icons.Default.School, Modifier.weight(1f))
                ProfileInfoItem("Graduation Year", graduationYear?.toString() ?: missing, Icons.Default.CalendarMonth, Modifier.weight(1f))
            }
            ProfileInfoItem("College / Institution", college, Icons.Default.AccountBalance, Modifier.fillMaxWidth())
            ProfileInfoItem("Specialization / Branch", specialization, Icons.Default.AutoStories, Modifier.fillMaxWidth())

            Text("Connected profiles", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
            ProfileLinkRow("LinkedIn", linkedin, Color(0xFF0A66C2), "in") {
                if (linkedin.startsWith("http")) uriHandler.openUri(linkedin)
            }
            ProfileLinkRow("GitHub", github, Color(0xFF24292F), "GH") {
                if (github.startsWith("http")) uriHandler.openUri(github)
            }
            ProfileLinkRow("Portfolio / Website", portfolio, ProfilePurple, "WEB") {
                if (portfolio.startsWith("http")) uriHandler.openUri(portfolio)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsAndPreferencesCard(
    profile: StudentProfileDto?,
    tokenManager: TokenManager,
    onEditPreferences: () -> Unit
) {
    val skills = if (profile?.skills?.isNotEmpty() == true) profile.skills else tokenManager.getSkills()
    val languages = if (profile?.languages?.isNotEmpty() == true) profile.languages else tokenManager.getLanguages()
    val hobbies = if (profile?.hobbies?.isNotEmpty() == true) profile.hobbies else tokenManager.getHobbies()
    val hasContent = skills.isNotEmpty() || languages.isNotEmpty() || hobbies.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ProfileBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(44.dp), CircleShape, color = Color(0xFFEFF6FF)) {
                    Icon(Icons.Default.Psychology, null, Modifier.padding(10.dp), tint = Color(0xFF4F46E5))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Skills & Preferences", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ProfileInk)
                    Box(Modifier.padding(top = 5.dp).width(62.dp).height(3.dp).clip(CircleShape).background(Color(0xFF4F46E5)))
                }
            }

            if (!hasContent) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF0A66C2), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Automatic LinkedIn Sync", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                        }
                        Text(
                            "Technical skills, languages, and hobbies are automatically synchronized from your LinkedIn profile or can be added manually.",
                            fontSize = 12.sp,
                            color = ProfileMuted,
                            lineHeight = 17.sp
                        )
                        OutlinedButton(
                            onClick = onEditPreferences,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ProfilePurple),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProfilePurple)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Skills & Preferences", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Technical Skills Section
                if (skills.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Technical Skills (${skills.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            skills.forEach { skill ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEEF2FF),
                                    border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                                ) {
                                    Text(
                                        skill.trim(),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF3730A3)
                                    )
                                }
                            }
                        }
                    }
                }

                // Languages Section
                if (languages.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Translate, null, tint = Color(0xFF0D9488), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Languages Known (${languages.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            languages.forEach { lang ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFCCFBF1),
                                    border = BorderStroke(1.dp, Color(0xFF99F6E4))
                                ) {
                                    Text(
                                        lang.trim(),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F766E)
                                    )
                                }
                            }
                        }
                    }
                }

                // Hobbies Section
                if (hobbies.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Interests, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hobbies & Interests (${hobbies.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            hobbies.forEach { hobby ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                                ) {
                                    Text(
                                        hobby.trim(),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileLinkRow(label: String, value: String, brandColor: Color, mark: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = value.startsWith("http"), onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ProfileBorder)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(38.dp), CircleShape, color = brandColor) {
                Box(contentAlignment = Alignment.Center) {
                    Text(mark, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                Text(value, fontSize = 11.5.sp, color = ProfileMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (value.startsWith("http")) Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(17.dp), tint = ProfilePurple)
        }
    }
}

@Composable
private fun ResumeAndDocumentsCard(
    profile: StudentProfileDto?,
    tokenManager: TokenManager,
    isUploading: Boolean,
    onUploadResume: () -> Unit
) {
    var showInAppViewer by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val resumeUrl = profile?.resume?.takeIf(String::isNotBlank)
        ?: profile?.resumeUrl?.takeIf(String::isNotBlank)
        ?: tokenManager.getResumeUrl().ifBlank { null }
    val resumeFileName = tokenManager.getResumeName().ifBlank {
        if (resumeUrl != null) "Uploaded_Resume.pdf" else "No resume uploaded yet"
    }

    if (showInAppViewer && !resumeUrl.isNullOrBlank()) {
        com.example.suretouchapp.ui.components.InAppDocumentViewerDialog(
            documentUrl = resumeUrl,
            documentTitle = resumeFileName,
            onDismiss = { showInAppViewer = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ProfileBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(44.dp), CircleShape, color = Color(0xFFEFF6FF)) {
                    Icon(Icons.Default.Description, null, Modifier.padding(10.dp), tint = Color(0xFF2563EB))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Resume & Documents", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ProfileInk)
                    Box(Modifier.padding(top = 5.dp).width(62.dp).height(3.dp).clip(CircleShape).background(Color(0xFF2563EB)))
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!resumeUrl.isNullOrBlank()) Modifier.clickable { showInAppViewer = true }
                        else Modifier
                    ),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (resumeUrl != null) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (resumeUrl != null) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = if (resumeUrl != null) Color(0xFF16A34A) else Color(0xFF64748B),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            resumeFileName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ProfileInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (resumeUrl != null) "Verified Student Resume • Tap to View" else "PDF, DOCX, or DOC up to 10MB",
                            fontSize = 11.5.sp,
                            color = ProfileMuted
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onUploadResume,
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProfilePurple)
                ) {
                    if (isUploading) {
                        SureTrustLoadingIndicator(size = 24.dp, logoSize = 14.dp, spinnerColor = Color.White)
                    } else {
                        Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (resumeUrl != null) "Replace Resume" else "Upload Resume", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (resumeUrl != null) {
                    OutlinedButton(
                        onClick = { showInAppViewer = true },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2563EB)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View in App", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoItem(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(17.dp), color = Color.White, border = BorderStroke(1.dp, ProfileBorder)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
            Surface(Modifier.size(38.dp), CircleShape, color = Color(0xFFF3EFFF)) {
                Icon(icon, null, Modifier.padding(9.dp), tint = ProfilePurple)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                Spacer(Modifier.height(3.dp))
                Text(value, fontSize = 12.sp, lineHeight = 17.sp, color = ProfileMuted, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditSheet(
    tokenManager: TokenManager,
    profile: StudentProfileDto?,
    onDismiss: () -> Unit,
    onSaved: (StudentProfileDto?) -> Unit
) {
    val initialFirstName = remember(profile) {
        profile?.user?.firstName?.takeIf(String::isNotBlank)
            ?: tokenManager.getUserName().split(" ").firstOrNull().orEmpty()
    }
    val initialLastName = remember(profile) {
        profile?.user?.lastName?.takeIf(String::isNotBlank)
            ?: tokenManager.getUserName().split(" ").drop(1).joinToString(" ")
    }
    var firstName by remember(profile) { mutableStateOf(initialFirstName) }
    var lastName by remember(profile) { mutableStateOf(initialLastName) }
    var phone by remember(profile) { mutableStateOf(profile?.phone?.takeIf(String::isNotBlank) ?: tokenManager.getPhone()) }
    var tagline by remember(profile) { mutableStateOf(profile?.tagline ?: tokenManager.getTagline()) }
    var city by remember(profile) { mutableStateOf(profile?.city ?: tokenManager.getCity()) }
    var state by remember(profile) { mutableStateOf(profile?.state ?: tokenManager.getState()) }
    var country by remember(profile) { mutableStateOf(profile?.country ?: tokenManager.getCountry()) }
    var degree by remember(profile) { mutableStateOf(profile?.degree ?: tokenManager.getQualification()) }
    var college by remember(profile) { mutableStateOf(profile?.college ?: tokenManager.getCollegeName()) }
    var bio by remember(profile) { mutableStateOf(profile?.bio ?: tokenManager.getBio()) }
    var specialization by remember(profile) { mutableStateOf(profile?.specialization ?: tokenManager.getSpecialization()) }
    var educationLevel by remember(profile) { mutableStateOf(profile?.educationLevel.orEmpty()) }
    var graduationYear by remember(profile) {
        mutableStateOf(profile?.graduationYear?.toString() ?: tokenManager.getGraduationYear()?.toString().orEmpty())
    }
    var skillsText by remember(profile) {
        val list = if (profile?.skills?.isNotEmpty() == true) profile.skills else tokenManager.getSkills()
        mutableStateOf(list.joinToString(", "))
    }
    var languagesText by remember(profile) {
        val list = if (profile?.languages?.isNotEmpty() == true) profile.languages else tokenManager.getLanguages()
        mutableStateOf(list.joinToString(", "))
    }
    var hobbiesText by remember(profile) {
        val list = if (profile?.hobbies?.isNotEmpty() == true) profile.hobbies else tokenManager.getHobbies()
        mutableStateOf(list.joinToString(", "))
    }
    var github by remember(profile) { mutableStateOf(profile?.githubUrl ?: tokenManager.getGithubUrl()) }
    var linkedin by remember(profile) { mutableStateOf(profile?.linkedinUrl ?: tokenManager.getLinkedinUrl()) }
    var portfolio by remember(profile) { mutableStateOf(profile?.portfolioUrl ?: tokenManager.getPortfolioUrl()) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit Profile", fontSize = 22.sp, fontWeight = FontWeight.Black, color = ProfileInk)
            
            // LinkedIn Auto-sync Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF0A66C2), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Connecting with LinkedIn automatically populates your headline, bio, education, location, skills, languages, and profile photo.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF1E40AF),
                        lineHeight = 16.sp
                    )
                }
            }

            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            Text("Account & Identity (Verified / Read-Only)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    label = { Text("First name") },
                    readOnly = true,
                    enabled = false,
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Default.Lock, "Locked", modifier = Modifier.size(16.dp), tint = ProfileMuted) }
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    label = { Text("Last name") },
                    readOnly = true,
                    enabled = false,
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Default.Lock, "Locked", modifier = Modifier.size(16.dp), tint = ProfileMuted) }
                )
            }
            OutlinedTextField(
                value = tokenManager.getUserEmail(),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email Address") },
                supportingText = { Text("Primary account ID cannot be changed.") },
                readOnly = true,
                enabled = false,
                singleLine = true,
                trailingIcon = { Icon(Icons.Default.Lock, "Locked", modifier = Modifier.size(16.dp), tint = ProfileMuted) }
            )

            Text("Personal Details & Tagline", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone") }, singleLine = true)
            OutlinedTextField(
                value = tagline,
                onValueChange = { tagline = it.take(200) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tagline / Headline (from LinkedIn)") },
                placeholder = { Text("e.g. Software Engineer | AI Enthusiast") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(city, { city = it }, Modifier.weight(1f), label = { Text("City") }, singleLine = true)
                OutlinedTextField(state, { state = it }, Modifier.weight(1f), label = { Text("State") }, singleLine = true)
                OutlinedTextField(country, { country = it }, Modifier.weight(1f), label = { Text("Country") }, singleLine = true)
            }
            OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Bio / Summary (from LinkedIn)") }, minLines = 2)

            Text("Education & Branch", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
            OutlinedTextField(degree, { degree = it }, Modifier.fillMaxWidth(), label = { Text("Degree (e.g. B.Tech / MCA / B.Sc)") }, singleLine = true)
            OutlinedTextField(college, { college = it }, Modifier.fillMaxWidth(), label = { Text("College / University / Institution") }, singleLine = true)
            OutlinedTextField(specialization, { specialization = it }, Modifier.fillMaxWidth(), label = { Text("Specialization / Branch (e.g. CSE / AI / ECE)") }, singleLine = true)
            OutlinedTextField(graduationYear, { graduationYear = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Graduation / Pass-out Year (e.g. 2025)") }, singleLine = true)
            OutlinedTextField(educationLevel, { educationLevel = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("Education Level / Designation") }, singleLine = true)

            Text("Skills & Preferences", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
            OutlinedTextField(
                value = skillsText,
                onValueChange = { skillsText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Technical Skills (comma-separated)") },
                placeholder = { Text("e.g. Python, Kotlin, React, SQL, Git") },
                minLines = 2
            )
            OutlinedTextField(
                value = languagesText,
                onValueChange = { languagesText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Languages Known (comma-separated)") },
                placeholder = { Text("e.g. English, Telugu, Hindi") },
                singleLine = true
            )
            OutlinedTextField(
                value = hobbiesText,
                onValueChange = { hobbiesText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hobbies & Interests (comma-separated)") },
                placeholder = { Text("e.g. Coding, Reading, Cricket") },
                singleLine = true
            )

            Text("Professional Links & Portfolio", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
            OutlinedTextField(
                value = if (profile?.isLinkedinConnected == true) linkedin else if (linkedin.isNotBlank()) linkedin else "Connected via LinkedIn OAuth",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("LinkedIn (OAuth Verified)") },
                supportingText = { Text("Managed automatically through LinkedIn OAuth.") },
                readOnly = true,
                enabled = false,
                singleLine = true,
                trailingIcon = { Icon(Icons.Default.Lock, "Locked", modifier = Modifier.size(16.dp), tint = ProfileMuted) }
            )
            OutlinedTextField(
                value = if (profile?.isLinkedinConnected == true || github.isNotBlank()) github else "Connected via GitHub OAuth",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub (OAuth Verified)") },
                supportingText = { Text("Managed automatically through GitHub OAuth.") },
                readOnly = true,
                enabled = false,
                singleLine = true,
                trailingIcon = { Icon(Icons.Default.Lock, "Locked", modifier = Modifier.size(16.dp), tint = ProfileMuted) }
            )
            OutlinedTextField(
                value = portfolio,
                onValueChange = { portfolio = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Portfolio / Personal Website Link") },
                placeholder = { Text("https://yourportfolio.dev") },
                singleLine = true
            )
            val isConnected = com.example.suretouchapp.ui.components.LocalBackendConnected.current
            if (!isConnected) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WifiOff, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Offline Mode: Profile changes cannot be saved to the server until you reconnect.",
                            fontSize = 12.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Button(
                enabled = !saving && isConnected,
                onClick = {
                    if (!isConnected) {
                        saveError = "Cannot save profile changes while offline. Reconnect to the internet."
                        return@Button
                    }
                    saving = true
                    saveError = null
                    scope.launch {
                        try {
                            val service = ApiClient.getService(tokenManager)
                            val email = tokenManager.getUserEmail().trim().lowercase()

                            // 1. Resolve User ID
                            var userId = profile?.user?.id ?: profile?.userId
                            if (userId.isNullOrBlank()) {
                                val userRes = runCatching { service.getUsers() }.getOrNull()
                                val userList = userRes?.body()?.results.orEmpty()
                                val meUser = userList.find { it.email.trim().equals(email, ignoreCase = true) }
                                userId = meUser?.id
                            }

                            // 2. Patch User phone_number
                            if (!userId.isNullOrBlank() && phone.isNotBlank()) {
                                val userBody = mutableMapOf<String, Any?>("phone_number" to phone.trim())
                                runCatching { service.patchUser(userId, userBody) }
                            }

                            val parsedSkills = skillsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val parsedHobbies = hobbiesText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val parsedLanguages = languagesText.split(",").map { it.trim() }.filter { it.isNotBlank() }

                            // 3. Update or create Student Profile record
                            val profileBody = mutableMapOf<String, Any?>(
                                "is_public" to (profile?.isPublic ?: true),
                                "tagline" to tagline.ifBlank { null },
                                "status" to (profile?.status ?: "AVAILABLE"),
                                "city" to city.ifBlank { null },
                                "state" to state.ifBlank { null },
                                "country" to country.ifBlank { null },
                                "degree" to degree.ifBlank { null },
                                "college" to college.ifBlank { null },
                                "specialization" to specialization.ifBlank { null },
                                "education_level" to educationLevel.ifBlank { null },
                                "graduation_year" to graduationYear.toIntOrNull(),
                                "bio" to bio.ifBlank { null },
                                "skills" to parsedSkills,
                                "hobbies" to parsedHobbies,
                                "languages" to parsedLanguages,
                                "portfolio_url" to portfolio.ifBlank { null }
                            )

                            val profileId = profile?.id?.takeIf(String::isNotBlank)
                            var updatedProfile: StudentProfileDto? = null

                            if (!profileId.isNullOrBlank()) {
                                val res = runCatching { service.updateStudentProfile(profileId, profileBody) }.getOrNull()
                                if (res?.isSuccessful == true) {
                                    updatedProfile = res.body()
                                }
                            } else if (!userId.isNullOrBlank()) {
                                profileBody["user"] = userId
                                val createRes = runCatching { service.createStudentProfile(profileBody) }.getOrNull()
                                if (createRes?.isSuccessful == true) {
                                    updatedProfile = createRes.body()
                                }
                            }

                            // 4. Save local preferences for all dashboards
                            tokenManager.saveStudentProfileDetails(
                                phone = phone,
                                qualification = degree,
                                collegeName = college,
                                bio = bio,
                                githubUrl = github,
                                linkedinUrl = linkedin,
                                tagline = tagline,
                                specialization = specialization,
                                graduationYear = graduationYear.toIntOrNull(),
                                city = city,
                                state = state,
                                country = country,
                                skills = parsedSkills,
                                hobbies = parsedHobbies,
                                languages = parsedLanguages,
                                portfolioUrl = portfolio
                            )

                            val resultProfile = updatedProfile ?: StudentProfileDto(
                                id = profile?.id.orEmpty(),
                                tagline = tagline,
                                bio = bio,
                                status = profile?.status ?: "AVAILABLE",
                                city = city,
                                state = state,
                                country = country,
                                college = college,
                                degree = degree,
                                specialization = specialization,
                                educationLevel = educationLevel,
                                graduationYear = graduationYear.toIntOrNull(),
                                skills = parsedSkills,
                                hobbies = parsedHobbies,
                                languages = parsedLanguages,
                                githubUrl = github,
                                linkedinUrl = linkedin,
                                portfolioUrl = portfolio,
                                isPublic = profile?.isPublic ?: true
                            )

                            onSaved(resultProfile)
                            onDismiss()
                        } catch (e: Exception) {
                            saveError = "Changes could not be saved: ${e.localizedMessage ?: "Please try again."}"
                        } finally {
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProfilePurple)
            ) {
                if (saving) SureTrustLoadingIndicator(size = 28.dp, logoSize = 17.dp, spinnerColor = Color.White)
                else if (!isConnected) Text("Save Disabled (Offline)", fontWeight = FontWeight.Bold)
                else Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}
