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
import androidx.compose.ui.zIndex
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
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import kotlinx.coroutines.launch
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.StudentProfileImage
import com.example.suretouchapp.ui.theme.SureFormDefaults

private val PrimaryBlue = Color(0xFF0284C7)
private val DeepBlue = Color(0xFF0369A1)
private val ScreenBg @Composable get() = MaterialTheme.colorScheme.background
private val TextMain @Composable get() = MaterialTheme.colorScheme.onSurface
private val TextMuted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompanyProfessionalProfileScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var showEditSheet by remember { mutableStateOf(false) }
    var showIdModal by remember { mutableStateOf(false) }

    var companyName by remember { mutableStateOf(tokenManager.getCompany().ifBlank { tokenManager.getUserName() }) }
    var recruiterName by remember { mutableStateOf(tokenManager.getUserName()) }
    var email by remember { mutableStateOf(tokenManager.getUserEmail()) }
    var phone by remember { mutableStateOf(tokenManager.getPhone().ifBlank { "+91 98765 00000" }) }
    var industry by remember { mutableStateOf(tokenManager.getTagline().ifBlank { "Information Technology & Enterprise Cloud" }) }
    var hqLocation by remember { mutableStateOf(tokenManager.getCity().ifBlank { "Bengaluru, India" }) }
    var websiteUrl by remember { mutableStateOf(tokenManager.getPortfolioUrl().ifBlank { "https://suretrust.org/partners" }) }
    var linkedinUrl by remember { mutableStateOf(tokenManager.getLinkedinUrl()) }
    var hiringStack by remember {
        mutableStateOf(
            tokenManager.getSkills().ifEmpty {
                listOf("Full Stack Java", "Python / Django", "React / Next.js", "Cloud DevOps", "Data Engineering", "QA Automation")
            }
        )
    }
    var aboutCompany by remember {
        mutableStateOf(
            tokenManager.getBio().ifBlank {
                "Premier industry hiring partner collaborating with SURE Trust to recruit, train, and onboard highly motivated engineering talent across innovative enterprise software solutions."
            }
        )
    }

    val rawId = (companyName.hashCode().coerceAtLeast(100000)).toString().take(6)
    val formattedId = "ST-CP-$rawId"

    var coverPhotoUri by remember { mutableStateOf(tokenManager.getCoverPhotoUrl()) }
    var showCoverOptionsDialog by remember { mutableStateOf(false) }
    var profilePhotoUri by remember { mutableStateOf(tokenManager.getProfilePhotoUrl()) }

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
            Toast.makeText(context, "Company logo updated", Toast.LENGTH_SHORT).show()
        }
    }

    BackendConnectionGate(
        isLoading = false,
        isConnected = true,
        hasData = true,
        loadingMessage = "Connecting to Partner Portal...",
        onRetry = {}
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
                                    .padding(top = 10.dp, start = 14.dp, end = 14.dp),
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
                                displayName = companyName,
                                size = 104.dp,
                                badgeColor = PrimaryBlue,
                                onEditClick = { avatarLauncher.launch("image/*") }
                            )
                        }
                    }
                }

                

                // ── 2. Company Credentials & Header ──
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
                                    text = companyName,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextMain,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Verified, "Verified Corporate Partner", tint = PrimaryBlue, modifier = Modifier.size(22.dp))
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
                            text = industry,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Representative: $recruiterName",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(text = hqLocation, fontSize = 13.sp, color = TextMuted)

                        Spacer(Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.2.dp, PrimaryBlue),
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Business, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Corporate Partner", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
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
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Company", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
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
                                Text("Partner Card", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // ── 3. Four-Metric Highlights Row ──
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 4.dp)) {
                        Text(
                            text = "Hiring & Placement Metrics",
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
                                title = "Open Roles",
                                value = "4",
                                subtitle = "Active Postings",
                                icon = Icons.Default.Work,
                                accentColor = PrimaryBlue,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPillCard(
                                title = "Applications",
                                value = "62",
                                subtitle = "Scholars Applied",
                                icon = Icons.Default.Group,
                                accentColor = Color(0xFF8B5CF6),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricPillCard(
                                title = "Shortlisted",
                                value = "18",
                                subtitle = "Interview Stage",
                                icon = Icons.Default.FactCheck,
                                accentColor = Color(0xFFD97706),
                                modifier = Modifier.weight(1f)
                            )
                            MetricPillCard(
                                title = "Offers Made",
                                value = "8",
                                subtitle = "Placed Candidates",
                                icon = Icons.Default.Stars,
                                accentColor = Color(0xFF059669),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── 4. Company Overview & Mission ──
                item {
                    ProfileSectionCard(title = "Company Overview", icon = Icons.Default.Business, accentColor = PrimaryBlue) {
                        Text(
                            text = aboutCompany,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }
                }

                // ── 5. Talent & Tech Stack Preferences ──
                item {
                    ProfileSectionCard(title = "Target Tech Stack & Skills", icon = Icons.Default.Code, accentColor = PrimaryBlue) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            hiringStack.forEach { skill ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = skill,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 6. Recruiter Contact & Links ──
                item {
                    ProfileSectionCard(title = "Representative Contact & Links", icon = Icons.Default.Link, accentColor = PrimaryBlue) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailRowItem(label = "Official Recruiter Email", value = email)
                            DetailRowItem(label = "Direct Contact Phone", value = phone)
                            if (websiteUrl.isNotBlank()) {
                                SocialLinkRow(
                                    label = "Official Careers / Website",
                                    value = websiteUrl,
                                    isConnected = true,
                                    icon = Icons.Default.Language,
                                    accentColor = PrimaryBlue,
                                    onClick = { uriHandler.openUri(websiteUrl) }
                                )
                            }
                            if (linkedinUrl.isNotBlank()) {
                                SocialLinkRow(
                                    label = "Company LinkedIn Page",
                                    value = linkedinUrl,
                                    isConnected = true,
                                    icon = Icons.Default.Share,
                                    accentColor = PrimaryBlue,
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
                            text = "Edit Company Profile",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = companyName,
                            onValueChange = { companyName = it },
                            label = { Text("Company Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = industry,
                            onValueChange = { industry = it },
                            label = { Text("Industry / Domain") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = recruiterName,
                            onValueChange = { recruiterName = it },
                            label = { Text("Recruiter Representative Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Contact Phone") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = aboutCompany,
                            onValueChange = { aboutCompany = it },
                            label = { Text("Company Mission & Overview") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = websiteUrl,
                            onValueChange = { websiteUrl = it },
                            label = { Text("Official Website URL") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SureFormDefaults.outlinedTextFieldColors()
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
                                    tokenManager.saveStudentProfileDetails(
                                        phone = phone.trim(),
                                        qualification = industry.trim(),
                                        collegeName = companyName.trim(),
                                        bio = aboutCompany.trim(),
                                        portfolioUrl = websiteUrl.trim(),
                                        linkedinUrl = linkedinUrl.trim(),
                                        city = hqLocation.substringBefore(",").trim(),
                                        state = hqLocation.substringAfter(",", "").trim()
                                    )
                                    scope.launch {
                                        try {
                                            val payload: Map<String, Any?> = mapOf(
                                                "name" to companyName.trim(),
                                                "industry" to industry.trim(),
                                                "website" to websiteUrl.trim(),
                                                "linkedin_url" to linkedinUrl.trim(),
                                                "description" to aboutCompany.trim()
                                            )
                                            ApiClient.getService(tokenManager).createCompany(payload)
                                        } catch (_: Exception) {}
                                    }
                                    Toast.makeText(context, "Company profile updated!", Toast.LENGTH_SHORT).show()
                                    showEditSheet = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("Save Changes", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ID Modal
            if (showIdModal) {
                StudentIdCardModal(
                    name = companyName,
                    studentId = formattedId,
                    role = "CORPORATE PARTNER",
                    email = email,
                    college = industry,
                    qualification = "Representative: $recruiterName",
                    badgeColor = PrimaryBlue,
                    onDismiss = { showIdModal = false }
                )
            }
        }
    }
}
