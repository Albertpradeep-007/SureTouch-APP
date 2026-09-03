package com.example.suretouchapp.ui.screens.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.VolunteerProfileDto
import com.example.suretouchapp.data.model.VolunteerAssignedCohortDto
import com.example.suretouchapp.data.repository.VolunteerRepository
import kotlinx.coroutines.launch
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.StudentProfileImage
import com.example.suretouchapp.ui.theme.sureSemanticColors
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody


private fun isCohortActive(cohort: VolunteerAssignedCohortDto): Boolean {
    val text = listOfNotNull(cohort.name, cohort.code, cohort.course).joinToString(" ").lowercase()
    if (text.contains("completed") || text.contains("graduated") || text.contains("archive") || text.contains("finished")) {
        return false
    }
    if (text.contains("2020") || text.contains("2021") || text.contains("2022") || text.contains("2023")) {
        return false
    }
    return true
}

private val PrimaryTeal = Color(0xFF0D9488)
private val ScreenBg @Composable get() = MaterialTheme.colorScheme.background
private val TextMain @Composable get() = MaterialTheme.colorScheme.onSurface
private val TextMuted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerTrusteeProfessionalProfileScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val semanticColors = sureSemanticColors()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val repository = remember(tokenManager) { VolunteerRepository(tokenManager) }
    val api = remember(tokenManager) { ApiClient.getService(tokenManager) }

    var showEditSheet by remember { mutableStateOf(false) }
    var showIdModal by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<VolunteerProfileDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isPhotoUploading by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var openTaskCount by remember { mutableIntStateOf(0) }

    val role = tokenManager.getUserRole()
    val isTrustee = role.contains("TRUSTEE")
    val roleTitle = if (isTrustee) "Trustee Executive" else "Active Volunteer"
    val idPrefix = if (isTrustee) "ST-TR-" else "ST-VL-"

    var name by remember { mutableStateOf(tokenManager.getUserName().ifBlank { roleTitle }) }
    var email by remember { mutableStateOf(tokenManager.getUserEmail()) }
    var phone by remember { mutableStateOf(tokenManager.getPhone()) }
    var designation by remember { mutableStateOf(tokenManager.getTagline()) }
    var department by remember { mutableStateOf(tokenManager.getQualification()) }
    var organization by remember { mutableStateOf(tokenManager.getCollegeName()) }
    var bio by remember { mutableStateOf(tokenManager.getBio()) }
    var availabilityNotes by remember { mutableStateOf("") }
    var linkedinUrl by remember { mutableStateOf(tokenManager.getLinkedinUrl()) }

    var coverPhotoUri by remember { mutableStateOf(tokenManager.getCoverPhotoUrl()) }
    var showCoverOptionsDialog by remember { mutableStateOf(false) }
    var profilePhotoUri by remember { mutableStateOf(tokenManager.getProfilePhotoUrl()) }
    var cohortFilter by remember { mutableStateOf("ALL") }
    var selectedCohortForDetails by remember { mutableStateOf<VolunteerAssignedCohortDto?>(null) }

    val formattedId = profile?.id
        ?.replace("-", "")
        ?.take(8)
        ?.uppercase()
        ?.let { "$idPrefix$it" }
        ?: "$idPrefix—"

    suspend fun loadProfile() {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val loaded = repository.loadProfile()
            val tasksResponse = api.getVolunteerTasks()
            if (!tasksResponse.isSuccessful) {
                throw IOException("Volunteer tasks request failed (${tasksResponse.code()})")
            }
            val activeStatuses = setOf("PENDING", "IN_PROGRESS", "OPEN", "ASSIGNED")
            openTaskCount = tasksResponse.body()?.results.orEmpty().count {
                it.status.uppercase() in activeStatuses
            }
            profile = loaded
            name = loaded.fullName.ifBlank { name }
            email = loaded.email.ifBlank { email }
            phone = loaded.phoneNumber.orEmpty()
            designation = loaded.occupation.orEmpty()
            department = loaded.skills.orEmpty()
            organization = loaded.organizationName.orEmpty()
            bio = loaded.bio.orEmpty()
            availabilityNotes = loaded.availabilityNotes.orEmpty()
            linkedinUrl = loaded.linkedinUrl.orEmpty()
            loaded.profilePhoto?.takeIf(String::isNotBlank)?.let { profilePhotoUri = it }
            isConnected = true
            hasLoadedOnce = true
            isOffline = false
        } catch (error: Exception) {
            val info = NetworkUtils.getNetworkErrorInfo(context, error)
            isConnected = false
            isOffline = info.isOffline
            errorTitle = info.title
            connectionError = info.message
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(tokenManager) { loadProfile() }

    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coverPhotoUri = uri.toString()
            tokenManager.saveCoverPhotoUrl(uri.toString())
            Toast.makeText(context, "Cover photo updated", Toast.LENGTH_SHORT).show()
        }
    }

    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val profileId = profile?.id
            if (!isConnected || profileId.isNullOrBlank()) {
                Toast.makeText(context, "Reconnect before changing the profile photo", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                isPhotoUploading = true
                try {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: error("Unable to read selected image")
                    val extension = mime.substringAfter('/', "jpg").substringBefore('+')
                    val photoPart = MultipartBody.Part.createFormData(
                        "profile_photo",
                        "volunteer-profile.$extension",
                        bytes.toRequestBody(mime.toMediaTypeOrNull())
                    )
                    val response = repository.uploadProfilePhoto(profileId, photoPart)
                    if (!response.isSuccessful) error("Photo upload failed (${response.code()})")
                    val updated = response.body() ?: repository.loadProfile()
                    profile = updated
                    val syncedPhoto = updated.profilePhoto?.let(ApiClient::resolveServerUrl).orEmpty()
                    profilePhotoUri = syncedPhoto
                    tokenManager.saveProfilePhotoUrl(syncedPhoto)
                    Toast.makeText(context, "Profile photo synced", Toast.LENGTH_SHORT).show()
                } catch (failure: Exception) {
                    Toast.makeText(context, failure.message ?: "Profile photo could not be synced", Toast.LENGTH_LONG).show()
                } finally {
                    isPhotoUploading = false
                }
            }
        }
    }

    BackendConnectionGate(
        isLoading = isLoading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Syncing $roleTitle Profile...",
        onRetry = { scope.launch { loadProfile() } }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // ── 1. Top Cover Banner & Overlapping Avatar ──
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(215.dp)
                        ) {
                            ProfileCoverBanner(
                                coverUri = coverPhotoUri,
                                modifier = Modifier.fillMaxSize()
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(top = 6.dp, start = 14.dp, end = 14.dp)
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
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 5.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = PrimaryTeal,
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
                                    color = PrimaryTeal,
                                    shadowElevation = 5.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Edit, "Edit Profile", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

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
                                    Icon(Icons.Default.CameraAlt, "Change Cover", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

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
                                badgeColor = PrimaryTeal,
                                onEditClick = { avatarLauncher.launch("image/*") }
                            )
                            if (isPhotoUploading) {
                                Spacer(Modifier.width(12.dp))
                                CircularProgressIndicator(Modifier.size(24.dp), color = PrimaryTeal, strokeWidth = 3.dp)
                            }
                        }
                    }
                }

                

                // ── 2. Credentials & Identity Header ──
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 4.dp)
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
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Verified, "Verified", tint = Color(0xFF0D9488), modifier = Modifier.size(22.dp))
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
                                    Icon(Icons.Default.Edit, "Edit", tint = TextMain, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(5.dp))

                        Text(
                            text = designation.ifBlank { "Occupation not provided" },
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted,
                            lineHeight = 20.sp
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = listOf(department, organization)
                                .filter(String::isNotBlank)
                                .joinToString(" • ")
                                .ifBlank { "Professional details not provided" },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )

                        if (availabilityNotes.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(text = availabilityNotes, fontSize = 13.sp, color = TextMuted)
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.2.dp, PrimaryTeal),
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.VolunteerActivism, null, tint = PrimaryTeal, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(roleTitle, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PrimaryTeal)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Badge, null, tint = TextMain, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(formattedId, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextMain)
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
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }

                            OutlinedButton(
                                onClick = { showIdModal = true },
                                modifier = Modifier.height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain)
                            ) {
                                Icon(Icons.Default.QrCode, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ID Badge", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // ── 3. Four-Metric Highlights Row ──
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 4.dp)) {
                        Text(
                            text = "Contribution & Impact",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricPillCard(
                                title = "Contribution",
                                value = profile?.hoursContributed?.let { "$it Hrs" } ?: "—",
                                subtitle = if (profile?.hoursContributed == null) "Not tracked by server" else "Recorded service",
                                icon = Icons.Default.AccessTime,
                                accentColor = PrimaryTeal,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPillCard(
                                title = "Cohorts",
                                value = profile?.assignedCohorts?.size?.toString() ?: "—",
                                subtitle = "Assigned",
                                icon = Icons.Default.Groups,
                                accentColor = Color(0xFF6366F1),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricPillCard(
                                title = "Upcoming",
                                value = profile?.upcomingClasses?.size?.toString() ?: "—",
                                subtitle = "Classes",
                                icon = Icons.Default.School,
                                accentColor = Color(0xFF0284C7),
                                modifier = Modifier.weight(1f)
                            )
                            MetricPillCard(
                                title = "Open Tasks",
                                value = openTaskCount.toString(),
                                subtitle = "Assigned work",
                                icon = Icons.Default.Star,
                                accentColor = Color(0xFFD97706),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── 4. About & Mission Statement ──
                item {
                    ProfileSectionCard(title = "About & Mission", icon = Icons.Default.Favorite, accentColor = PrimaryTeal) {
                        Text(
                            text = bio.ifBlank { "No biography has been added to the backend profile." },
                            fontSize = 14.sp,
                            color = TextMuted,
                            lineHeight = 22.sp
                        )
                    }
                }


                // ── 5. Assigned Cohorts & Batches ──
                val allAssigned = profile?.assignedCohorts.orEmpty()
                val activeAssigned = allAssigned.filter { isCohortActive(it) }
                val completedAssigned = allAssigned.filter { !isCohortActive(it) }

                val displayedCohorts = when (cohortFilter) {
                    "ACTIVE" -> activeAssigned
                    "COMPLETED" -> completedAssigned
                    else -> allAssigned
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Assigned Cohorts & Batches", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                        Text(
                                            "${activeAssigned.size} Active • ${completedAssigned.size} Completed (${allAssigned.size} Total)",
                                            fontSize = 11.5.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        "${allAssigned.size} Batches",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (allAssigned.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = cohortFilter == "ALL",
                                        onClick = { cohortFilter = "ALL" },
                                        label = { Text("All (${allAssigned.size})", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = cohortFilter == "ACTIVE",
                                        onClick = { cohortFilter = "ACTIVE" },
                                        label = { Text("🟢 Active (${activeAssigned.size})", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            selectedContainerColor = semanticColors.successContainer,
                                            selectedLabelColor = semanticColors.onSuccessContainer
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = cohortFilter == "COMPLETED",
                                        onClick = { cohortFilter = "COMPLETED" },
                                        label = { Text("🎓 Completed (${completedAssigned.size})", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                if (displayedCohorts.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No ${cohortFilter.lowercase()} cohorts found.",
                                            fontSize = 12.5.sp,
                                            color = TextMuted
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        displayedCohorts.forEach { cohort ->
                                            val isActive = isCohortActive(cohort)
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedCohortForDetails = cohort },
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.38f) else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            ) {
                                                Column(modifier = Modifier.padding(14.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = if (isActive) semanticColors.successContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (isActive) semanticColors.success.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
                                                            )
                                                        ) {
                                                            Text(
                                                                text = if (isActive) "● Active Live Batch" else "🎓 Graduated Batch",
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isActive) semanticColors.onSuccessContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                            )
                                                        }
                                                        if (cohort.code.isNotBlank()) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.primaryContainer
                                                            ) {
                                                                Text(
                                                                    cohort.code,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(
                                                        text = cohort.name.ifBlank { cohort.course },
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextMain
                                                    )
                                                    if (cohort.course.isNotBlank() && cohort.course != cohort.name) {
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(cohort.course, fontSize = 11.5.sp, color = TextMuted)
                                                    }
                                                    cohort.meetingLink?.takeIf(String::isNotBlank)?.let { link ->
                                                        Spacer(Modifier.height(8.dp))
                                                        OutlinedButton(
                                                            onClick = { uriHandler.openUri(link) },
                                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                                            shape = RoundedCornerShape(8.dp),
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                                                        ) {
                                                            Icon(Icons.Default.VideoCall, null, modifier = Modifier.size(16.dp))
                                                            Spacer(Modifier.width(6.dp))
                                                            Text("Join Live Class / Meet", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "No cohorts currently assigned by administration.",
                                    fontSize = 13.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                // ── 5. Trust Responsibilities & Department ──
                item {
                    ProfileSectionCard(title = "Leadership & Domains", icon = Icons.Default.AccountTree, accentColor = PrimaryTeal) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailRowItem(label = "Official Designation", value = designation)
                            DetailRowItem(label = "Skills / Focus Area", value = department.ifBlank { "Not provided" })
                            DetailRowItem(label = "Affiliation Organization", value = organization.ifBlank { "Not provided" })
                            if (availabilityNotes.isNotBlank()) {
                                DetailRowItem(label = "Availability", value = availabilityNotes)
                            }
                            DetailRowItem(label = "Official ID Code", value = formattedId)
                        }
                    }
                }

                // ── 6. Contact & Official Verification ──
                item {
                    ProfileSectionCard(title = "Contact & Verification", icon = Icons.Default.ContactPhone, accentColor = PrimaryTeal) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailRowItem(label = "Official Email", value = email)
                            DetailRowItem(label = "Contact Phone", value = phone.ifBlank { "Not provided" })
                            if (linkedinUrl.isNotBlank()) {
                                SocialLinkRow(
                                    label = "LinkedIn Profile",
                                    value = linkedinUrl,
                                    isConnected = true,
                                    icon = Icons.Default.Share,
                                    accentColor = PrimaryTeal,
                                    onClick = { uriHandler.openUri(linkedinUrl) }
                                )
                            }
                        }
                    }
                }
            }

            // Edit Bottom Sheet
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
                ModalBottomSheet(
                    onDismissRequest = { showEditSheet = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = "Edit $roleTitle Profile",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = designation,
                            onValueChange = { designation = it },
                            label = { Text("Title / Designation") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = department,
                            onValueChange = { department = it },
                            label = { Text("Skills / Focus Area") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = organization,
                            onValueChange = { organization = it },
                            label = { Text("Organization") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            label = { Text("Mission & Bio") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = availabilityNotes,
                            onValueChange = { availabilityNotes = it },
                            label = { Text("Availability Notes") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = linkedinUrl,
                            onValueChange = { linkedinUrl = it },
                            label = { Text("LinkedIn Profile URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditSheet = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    val currentProfile = profile
                                    if (currentProfile == null || isSaving) return@Button
                                    scope.launch {
                                        isSaving = true
                                        try {
                                            val updated = repository.updateProfile(
                                                profile = currentProfile,
                                                body = mapOf(
                                                    "organization_name" to organization.trim(),
                                                    "occupation" to designation.trim(),
                                                    "skills" to department.trim(),
                                                    "availability_notes" to availabilityNotes.trim(),
                                                    "bio" to bio.trim(),
                                                    "linkedin_url" to linkedinUrl.trim()
                                                ),
                                                phoneNumber = phone.trim()
                                            )
                                            profile = updated
                                            tokenManager.saveStudentProfileDetails(
                                                phone = updated.phoneNumber.orEmpty(),
                                                qualification = updated.skills.orEmpty(),
                                                collegeName = updated.organizationName.orEmpty(),
                                                bio = updated.bio.orEmpty(),
                                                tagline = updated.occupation.orEmpty(),
                                                linkedinUrl = updated.linkedinUrl.orEmpty()
                                            )
                                            Toast.makeText(context, "Profile synchronized with the backend", Toast.LENGTH_SHORT).show()
                                            showEditSheet = false
                                            isConnected = true
                                        } catch (error: Exception) {
                                            Toast.makeText(
                                                context,
                                                error.message ?: "Profile update failed",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = profile != null && !isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Save Changes", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ID Modal
            if (showIdModal) {
                StudentIdCardModal(
                    name = name,
                    studentId = formattedId,
                    role = roleTitle.uppercase(),
                    email = email,
                    college = organization,
                    qualification = department,
                    badgeColor = PrimaryTeal,
                    onDismiss = { showIdModal = false }
                )
            }
        }
    }
}
