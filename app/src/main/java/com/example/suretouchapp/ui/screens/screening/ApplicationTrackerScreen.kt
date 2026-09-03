package com.example.suretouchapp.ui.screens.screening

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.StudentStatisticsDto
import com.example.suretouchapp.data.repository.StudentStatisticsRepository
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.components.InAppOAuthSheet
import com.example.suretouchapp.ui.components.OAuthProvider
import com.example.suretouchapp.ui.theme.sureSemanticColors
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale

private val JourneyHeader = Color(0xFF262626)
private val JourneyCanvas @Composable get() = MaterialTheme.colorScheme.background
private val JourneyPurple @Composable get() = MaterialTheme.colorScheme.primary
private val JourneyPurpleLight @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val JourneyText @Composable get() = MaterialTheme.colorScheme.onSurface
private val JourneyMuted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val JourneyBorder @Composable get() = MaterialTheme.colorScheme.outlineVariant

enum class StepState { COMPLETED, CURRENT, UPCOMING, FAILED }

data class JourneyStep(
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val dateText: String,
    val state: StepState,
    val details: String? = null,
    val actionUrl: String? = null,
    val code: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationTrackerScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit,
    onNavigateToTimetable: () -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var statistics by remember { mutableStateOf<StudentStatisticsDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var isLinkedinConnecting by remember { mutableStateOf(false) }
    var isGithubConnecting by remember { mutableStateOf(false) }
    var profileConnectionError by remember { mutableStateOf<String?>(null) }
    var oauthProvider by remember { mutableStateOf<OAuthProvider?>(null) }
    var oauthUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val statsResult = StudentStatisticsRepository(tokenManager).load()
            if (statsResult != null) {
                statistics = statsResult
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
        } finally {
            isLoading = false
        }
    }
    DisposableEffect(lifecycleOwner) {
        var hasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasPaused = true
                Lifecycle.Event.ON_RESUME -> if (hasPaused) {
                    hasPaused = false
                    refreshKey += 1
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val stats = statistics ?: StudentStatisticsDto()
    val applicationStatus = stats.applicationStatus?.uppercase(Locale.US)
    val screeningStatus = stats.screeningStatus?.uppercase(Locale.US)
    val interviewStatus = stats.interviewStatus?.uppercase(Locale.US)
    val hasApplication = applicationStatus != null
    val applicationReviewed = hasApplication && applicationStatus != "APPLIED"
    val screeningScheduled = !stats.screeningScheduledAt.isNullOrBlank() || screeningStatus != null ||
        applicationStatus in setOf("PRESCREENING_PENDING", "PRESCREENING_COMPLETED", "EXAM_PENDING", "EXAM_COMPLETED")
    val screeningCompleted = !stats.screeningPercentage.isNullOrBlank() || !stats.screeningMarksObtained.isNullOrBlank() ||
        screeningStatus in setOf("SUBMITTED", "EVALUATED") ||
        applicationStatus in setOf("EXAM_COMPLETED", "QUALIFIED", "REJECTED", "WAITLISTED", "COHORT_ASSIGNED", "IN_PROGRESS", "COMPLETED")
    val qualificationResolved = screeningCompleted && (stats.screeningQualified || applicationStatus == "REJECTED" || stats.screeningGrade != null)
    val qualified = stats.screeningQualified || applicationStatus in setOf("QUALIFIED", "WAITLISTED", "COHORT_ASSIGNED", "IN_PROGRESS", "COMPLETED")
    val interviewScheduled = !stats.interviewScheduledAt.isNullOrBlank() || interviewStatus != null
    val interviewFailed = interviewStatus == "FAILED"
    val interviewPassed = interviewStatus == "PASSED"
    val interviewJoinUrl = stats.interviewMeetingLink?.takeIf {
        interviewStatus in setOf("SCHEDULED", "RESCHEDULED") &&
            (it.startsWith("https://") || it.startsWith("http://"))
    }
    val cohortCode = stats.activeCohort?.code?.takeIf(String::isNotBlank)
        ?: tokenManager.getCohortCode().ifBlank { null }
    val roleVerified = stats.studentRoleVerified &&
        stats.isLinkedinConnected &&
        stats.isGithubLinked &&
        (interviewPassed || cohortCode != null)
    val programmeCompleted = applicationStatus == "COMPLETED" || stats.certificateCount > 0
    val certificateIssued = stats.certificateCount > 0

    val fallbackJourneyStatus = when {
        certificateIssued -> "CERTIFICATE ISSUED"
        programmeCompleted -> "REQUIREMENTS VERIFIED"
        cohortCode != null -> "COHORT ASSIGNED"
        roleVerified -> "STUDENT VERIFIED"
        interviewPassed -> "INTERVIEW COMPLETE"
        interviewScheduled -> "INTERVIEW SCHEDULED"
        qualified -> "QUALIFIED"
        screeningCompleted -> "RESULT PUBLISHED"
        screeningScheduled -> "SCREENING SCHEDULED"
        hasApplication -> "APPLICATION ACTIVE"
        else -> "START APPLICATION"
    }

    val fallbackSteps = listOf(
        JourneyStep(1, "Student Signup", "Account created", "Complete", StepState.COMPLETED, "Your authenticated SURE ProEd account is ready."),
        JourneyStep(
            2,
            "Student Profile",
            when {
                stats.studentCode.isNullOrBlank() -> "Complete your student profile"
                cohortCode == null -> "Student profile completed"
                else -> "Profile ${stats.studentCode}"
            },
            if (stats.studentCode.isNullOrBlank()) "Pending" else "Complete",
            if (stats.studentCode.isNullOrBlank()) StepState.CURRENT else StepState.COMPLETED,
            "Education and contact details are synchronized from your backend profile."
        ),
        JourneyStep(
            3,
            "Apply for Course",
            stats.applicationCourseTitle ?: if (hasApplication) "Course selected" else "Choose one published course",
            stats.appliedAt.toShortDate("Pending"),
            when { hasApplication -> StepState.COMPLETED; !stats.studentCode.isNullOrBlank() -> StepState.CURRENT; else -> StepState.UPCOMING },
            stats.applicationNumber?.let { "Application ID: $it" } ?: "Only one course can be selected at a time."
        ),
        JourneyStep(
            4,
            "Application Review",
            applicationStatus.prettyStatus("Waiting for application"),
            if (applicationReviewed) "Reviewed" else "Pending",
            when { applicationReviewed -> StepState.COMPLETED; hasApplication -> StepState.CURRENT; else -> StepState.UPCOMING },
            "The administration review status comes directly from the backend."
        ),
        JourneyStep(
            5,
            "Screening Scheduled",
            if (screeningScheduled) "Pre-screening assessment scheduled" else "Schedule pending after review",
            stats.screeningScheduledAt.toDisplayDate("Pending"),
            when { screeningScheduled -> StepState.COMPLETED; applicationReviewed -> StepState.CURRENT; else -> StepState.UPCOMING },
            "The Android app displays the schedule; the assessment itself is not taken in the app."
        ),
        JourneyStep(
            6,
            "Screening Marks & Grade",
            when {
                screeningCompleted -> "${stats.screeningMarksObtained ?: "--"}/${stats.screeningTotalMarks ?: "--"} • Grade ${stats.screeningGrade ?: "--"}"
                else -> "Marks have not been published"
            },
            stats.screeningPercentage?.let { "$it%" } ?: "Pending",
            when { screeningCompleted -> StepState.COMPLETED; screeningScheduled -> StepState.CURRENT; else -> StepState.UPCOMING },
            "Evaluated marks, percentage, and grade are read-only and published by the backend."
        ),
        JourneyStep(
            7,
            "Qualification Result",
            when { qualified -> "Qualified"; qualificationResolved -> "Not qualified"; else -> "Awaiting evaluated result" },
            if (qualificationResolved || qualified) "Published" else "Pending",
            when { qualified -> StepState.COMPLETED; qualificationResolved -> StepState.FAILED; screeningCompleted -> StepState.CURRENT; else -> StepState.UPCOMING },
            "A not-qualified result releases course selection so one new published course can be chosen."
        ),
        JourneyStep(
            8,
            "Candidate Interview",
            when { interviewPassed -> "Interview passed"; interviewFailed -> "Mentor verification failed"; interviewScheduled -> "Interview scheduled"; else -> "Schedule pending after qualification" },
            stats.interviewScheduledAt.toDisplayDate("Pending"),
            when { interviewFailed -> StepState.FAILED; interviewPassed -> StepState.COMPLETED; qualified -> StepState.CURRENT; else -> StepState.UPCOMING },
            stats.interviewScore?.let { "Interview score: $it" } ?: "Mentor verification status is synchronized from the backend.",
            interviewJoinUrl,
            "INTERVIEW"
        ),
        JourneyStep(
            9,
            "Student Role Verification",
            when {
                !stats.isLinkedinConnected && !stats.isGithubLinked -> "Connect LinkedIn and add GitHub to continue"
                !stats.isLinkedinConnected -> "Connect LinkedIn to continue"
                !stats.isGithubLinked -> "Add your GitHub profile to continue"
                roleVerified -> "Verified student access"
                else -> "LinkedIn and GitHub linked • Verification pending"
            },
            when {
                !stats.isLinkedinConnected || !stats.isGithubLinked -> "Profiles required"
                roleVerified -> "Verified"
                else -> "Pending"
            },
            when { roleVerified -> StepState.COMPLETED; interviewPassed || qualified -> StepState.CURRENT; else -> StepState.UPCOMING },
            "Step 9 passes only after LinkedIn OAuth is connected and a GitHub profile URL is provided."
        ),
        JourneyStep(
            10,
            "Assigned to Cohort",
            cohortCode?.let { "Cohort: $it" } ?: when {
                !stats.isLinkedinConnected || !stats.isGithubLinked -> "Pending • Link LinkedIn and GitHub first"
                else -> "Cohort assignment pending"
            },
            if (cohortCode == null) "Pending" else "Assigned",
            when { cohortCode != null -> StepState.COMPLETED; roleVerified -> StepState.CURRENT; else -> StepState.UPCOMING },
            stats.activeCohort?.courseTitle ?: if (!stats.isLinkedinConnected || !stats.isGithubLinked) {
                "No cohort or timetable will be assigned until both professional profiles are linked."
            } else {
                "Professional profiles linked. Cohort assignment can proceed after student-role verification."
            }
        ),
        JourneyStep(
            11,
            "Attend Classes & Module Tests",
            when { programmeCompleted -> "Classes and module requirements completed"; cohortCode != null -> "Attendance ${stats.attendancePercentage.toInt()}% • ${stats.moduleGrades.size} grades published"; else -> "Starts after cohort assignment" },
            if (programmeCompleted) "Complete" else if (cohortCode != null) "In progress" else "Upcoming",
            when { programmeCompleted -> StepState.COMPLETED; cohortCode != null -> StepState.CURRENT; else -> StepState.UPCOMING },
            "Attendance and module marks are synchronized from backend session and evaluation records."
        ),
        JourneyStep(
            12,
            "Submit Assignments",
            if (programmeCompleted) "Required submissions verified" else "Complete cohort assignments",
            if (programmeCompleted) "Complete" else if (cohortCode != null) "In progress" else "Upcoming",
            if (programmeCompleted) StepState.COMPLETED else if (cohortCode != null) StepState.CURRENT else StepState.UPCOMING,
            "Assignment submission and evaluation remain backend-controlled."
        ),
        JourneyStep(
            13,
            "Life Skills & Soft Skills Training",
            if (programmeCompleted) "LST and soft-skills training completed" else "Complete both required training tracks",
            if (programmeCompleted) "Complete" else if (cohortCode != null) "In progress" else "Upcoming",
            if (programmeCompleted) StepState.COMPLETED else if (cohortCode != null) StepState.CURRENT else StepState.UPCOMING,
            "Life Skills and Soft Skills training run alongside classes and assignments."
        ),
        JourneyStep(
            14,
            "Capstone Project",
            if (programmeCompleted) "Final capstone completed" else "Starts after coursework, assignments, and skills training",
            if (programmeCompleted) "Complete" else "Upcoming",
            if (programmeCompleted) StepState.COMPLETED else StepState.UPCOMING,
            "Build and submit the final capstone project after the parallel learning phase."
        ),
        JourneyStep(
            15,
            "Tree Plantation",
            if (programmeCompleted) "Plantation activity verified" else "Participate in a verified tree-plantation activity",
            if (programmeCompleted) "Complete" else "Upcoming",
            if (programmeCompleted) StepState.COMPLETED else StepState.UPCOMING,
            "Environmental responsibility is part of the official SURE TRUST student pathway."
        ),
        JourneyStep(
            16,
            "Blood Donation & Helping Society",
            if (programmeCompleted) "Community activities verified" else "Complete approved social-responsibility activities",
            if (programmeCompleted) "Complete" else "Upcoming",
            if (programmeCompleted) StepState.COMPLETED else StepState.UPCOMING,
            "Blood donation follows medical eligibility and guidance; helping-society activity requires verification."
        ),
        JourneyStep(
            17,
            "Requirements Verification",
            if (programmeCompleted) "All programme requirements verified" else "Final backend verification pending",
            if (programmeCompleted) "Verified" else "Upcoming",
            when { programmeCompleted -> StepState.COMPLETED; else -> StepState.UPCOMING },
            "The backend verifies classes, grades, assignments, projects, skills training, and community activities."
        ),
        JourneyStep(
            18,
            "Official SURE ProEd Certificate",
            if (certificateIssued) "Official certificate issued" else "Certificate issued after final verification",
            if (certificateIssued) "Issued" else "Pending",
            when { certificateIssued -> StepState.COMPLETED; programmeCompleted -> StepState.CURRENT; else -> StepState.UPCOMING },
            "The official certificate appears only after every required stage is verified by the backend."
        )
    )
    val journeyStatus = stats.journey?.status
        ?.replace('_', ' ')
        ?.takeIf(String::isNotBlank)
        ?: fallbackJourneyStatus
    val steps = stats.journey?.steps
        ?.takeIf { it.isNotEmpty() }
        ?.map { backendStep ->
            JourneyStep(
                stepNumber = backendStep.stepNumber,
                title = backendStep.title,
                subtitle = backendStep.subtitle,
                dateText = backendStep.date.toDisplayDate(
                    if (backendStep.completed) "Complete" else backendStep.state.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.titlecase(Locale.US) }
                ),
                state = runCatching { StepState.valueOf(backendStep.state.uppercase(Locale.US)) }
                    .getOrDefault(StepState.UPCOMING),
                details = backendStep.details,
                actionUrl = backendStep.actionUrl,
                code = backendStep.code,
            )
        }
        ?: fallbackSteps

    BackendConnectionGate(
        isLoading = isLoading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Journey Tracker...",
        onRetry = { refreshKey += 1 },
        onLogout = null
    ) {
        Scaffold(
            topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Application Tracking", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) } },
                actions = {
                    IconButton(onClick = { refreshKey += 1 }, enabled = !isLoading) {
                        if (isLoading) SureTrustLoadingIndicator(size = 30.dp, logoSize = 18.dp, spinnerColor = Color.White)
                        else Icon(Icons.Default.Refresh, "Refresh journey", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JourneyHeader)
            )
        },
        containerColor = JourneyCanvas
    ) { padding ->
        val isLocalBackendConnected = com.example.suretouchapp.ui.components.LocalBackendConnected.current
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isConnected) item { JourneySyncNotice { refreshKey += 1 } }
            item {
                ApplicationSummaryCard(
                    stats = stats,
                    journeyStatus = journeyStatus,
                    cohortCode = cohortCode,
                    onNavigateToTimetable = onNavigateToTimetable
                )
            }
            if (isLocalBackendConnected && (!stats.isLinkedinConnected || (qualified && !stats.isGithubLinked))) {
                item {
                    LinkedInRequirementCard(
                        isLoading = isLinkedinConnecting,
                        isGithubLoading = isGithubConnecting,
                        linkedinConnected = stats.isLinkedinConnected,
                        githubLinked = stats.isGithubLinked,
                        githubRequired = qualified,
                        error = profileConnectionError,
                        onAddGithub = {
                            if (!isGithubConnecting) {
                                scope.launch {
                                    isGithubConnecting = true
                                    profileConnectionError = null
                                    val response = runCatching {
                                        ApiClient.getService(tokenManager).getGitHubAuthUrl()
                                    }.getOrNull()
                                    val url = response
                                        ?.takeIf { it.isSuccessful }
                                        ?.body()
                                        ?.let { it.authorizationUrl ?: it.authUrl ?: it.url }
                                    if (url.isNullOrBlank()) {
                                        profileConnectionError = "Could not start GitHub verification. Please try again."
                                    } else {
                                        oauthProvider = OAuthProvider.GITHUB
                                        oauthUrl = url
                                    }
                                    isGithubConnecting = false
                                }
                            }
                        },
                        onConnect = {
                            if (!isLinkedinConnecting) {
                                scope.launch {
                                    isLinkedinConnecting = true
                                    profileConnectionError = null
                                    val response = runCatching {
                                        ApiClient.getService(tokenManager).getLinkedInAuthUrl()
                                    }.getOrNull()
                                    val url = response
                                        ?.takeIf { it.isSuccessful }
                                        ?.body()
                                        ?.let { it.authorizationUrl ?: it.authUrl ?: it.url }
                                    if (url.isNullOrBlank()) {
                                        profileConnectionError = "Could not start LinkedIn verification. Please try again."
                                    } else {
                                        oauthProvider = OAuthProvider.LINKEDIN
                                        oauthUrl = url
                                    }
                                    isLinkedinConnecting = false
                                }
                            }
                        }
                    )
                }
            }
            item {
                Text("Complete SURE TRUST Student Journey", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = JourneyText)
            }
            itemsIndexed(steps) { index, step ->
                TimelineStepCard(step, index == steps.lastIndex)
            }
        }
    }

    val activeProvider = oauthProvider
    val activeUrl = oauthUrl
    if (activeProvider != null && !activeUrl.isNullOrBlank()) {
        InAppOAuthSheet(
            provider = activeProvider,
            initialUrl = activeUrl,
            onDismiss = { oauthProvider = null; oauthUrl = null },
            onResult = { callback ->
                if (callback.getQueryParameter("status").equals("success", ignoreCase = true)) {
                    refreshKey += 1
                    profileConnectionError = null
                } else {
                    profileConnectionError = callback.getQueryParameter("message")
                        ?: "Authentication was not completed. Please try again."
                }
                oauthProvider = null
                oauthUrl = null
            }
        )
    }
}
}

@Composable
private fun LinkedInRequirementCard(
    isLoading: Boolean,
    isGithubLoading: Boolean,
    linkedinConnected: Boolean,
    githubLinked: Boolean,
    githubRequired: Boolean,
    error: String?,
    onAddGithub: () -> Unit,
    onConnect: () -> Unit
) {
    val linkedInBlue = Color(0xFF0A66C2)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(linkedInBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text("in", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (githubRequired) "Professional profiles required" else "LinkedIn profile required",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = JourneyText
                    )
                    Text(
                        if (githubRequired) "Link GitHub after qualification to continue" else "Connect LinkedIn to continue your application",
                        fontSize = 12.sp,
                        color = JourneyMuted
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!linkedinConnected) {
                Button(
                    onClick = onConnect,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = linkedInBlue),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (isLoading) "Opening LinkedIn…" else "Connect LinkedIn", fontWeight = FontWeight.Bold)
                }
            }
            if (githubRequired && !githubLinked) {
                if (!linkedinConnected) Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAddGithub,
                    enabled = !isGithubLoading,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    if (isGithubLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("GH", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isGithubLoading) "Opening GitHub…" else "Connect GitHub",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            error?.let {
                Spacer(Modifier.height(7.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ApplicationSummaryCard(
    stats: StudentStatisticsDto,
    journeyStatus: String,
    cohortCode: String?,
    onNavigateToTimetable: () -> Unit
) {
    val semanticColors = sureSemanticColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, JourneyBorder),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = JourneyPurpleLight,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                ) {
                    Text(
                        text = stats.applicationNumber?.let { "APP ID: $it" } ?: "NO ACTIVE APPLICATION",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JourneyPurple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (cohortCode == null) semanticColors.warningContainer else semanticColors.successContainer
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (cohortCode == null) Icons.Default.Schedule else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (cohortCode == null) semanticColors.onWarningContainer else semanticColors.onSuccessContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = journeyStatus,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (cohortCode == null) semanticColors.onWarningContainer else semanticColors.onSuccessContainer,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stats.applicationCourseTitle ?: "Choose a SURE ProEd programme", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = JourneyText)
            Text("Application status: ${stats.applicationStatus.prettyStatus("Not applied")}", fontSize = 13.sp, color = JourneyMuted)
            Text(cohortCode?.let { "Assigned cohort: $it" } ?: "Your cohort has not been assigned yet", fontSize = 13.sp, color = JourneyMuted, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(13.dp))
            Button(
                onClick = onNavigateToTimetable,
                enabled = cohortCode != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = JourneyPurple),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("View Cohort Timetable", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TimelineStepCard(step: JourneyStep, isLastStep: Boolean) {
    val uriHandler = LocalUriHandler.current
    val semanticColors = sureSemanticColors()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
            val (icon, background, foreground) = when (step.state) {
                StepState.COMPLETED -> Triple(Icons.Default.Check, semanticColors.success, Color.White)
                StepState.CURRENT -> Triple(Icons.Default.Star, JourneyPurple, Color.White)
                StepState.UPCOMING -> Triple(Icons.Default.HourglassEmpty, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                StepState.FAILED -> Triple(Icons.Default.Close, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
            }
            Box(Modifier.size(28.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = foreground, modifier = Modifier.size(15.dp))
            }
            if (!isLastStep) {
                Box(Modifier.width(2.dp).height(58.dp).background(if (step.state == StepState.COMPLETED) semanticColors.success.copy(alpha = .55f) else JourneyBorder))
            }
        }
        Spacer(Modifier.width(12.dp))
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = if (step.state == StepState.CURRENT) JourneyPurpleLight else MaterialTheme.colorScheme.surface),
            border = BorderStroke(if (step.state == StepState.CURRENT) 1.5.dp else 1.dp, if (step.state == StepState.CURRENT) JourneyPurple else JourneyBorder),
            elevation = CardDefaults.cardElevation(if (step.state == StepState.CURRENT) 2.dp else 1.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${step.stepNumber}. ${step.title}", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = if (step.state == StepState.CURRENT) JourneyPurple else JourneyText, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(step.dateText, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = JourneyMuted)
                }
                Spacer(Modifier.height(2.dp))
                Text(step.subtitle, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = JourneyMuted)
                step.details?.takeIf(String::isNotBlank)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontSize = 11.5.sp, color = JourneyMuted.copy(alpha = .88f), lineHeight = 16.sp)
                }
                step.actionUrl?.takeIf {
                    (step.code == "INTERVIEW" || step.stepNumber == 8) &&
                        (it.startsWith("https://") || it.startsWith("http://"))
                }?.let { url ->
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { runCatching { uriHandler.openUri(url) } }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, JourneyPurple)) {
                        Icon(Icons.Default.VideoCall, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Join Scheduled Interview", color = JourneyPurple)
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneySyncNotice(onRetry: () -> Unit) {
    val semanticColors = sureSemanticColors()
    Surface(color = semanticColors.warningContainer, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, null, tint = semanticColors.onWarningContainer, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Could not sync the latest journey data.", modifier = Modifier.weight(1f), fontSize = 11.5.sp, color = JourneyText)
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Retry", color = JourneyPurple) }
        }
    }
}

private fun String?.prettyStatus(fallback: String): String = this
    ?.lowercase(Locale.US)
    ?.replace('_', ' ')
    ?.replaceFirstChar { it.titlecase(Locale.US) }
    ?: fallback

private fun String?.toShortDate(fallback: String): String = this?.take(10) ?: fallback

private fun String?.toDisplayDate(fallback: String): String {
    if (this.isNullOrBlank()) return fallback
    val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(take(19)) }.getOrNull()
        ?: return take(10)
    return SimpleDateFormat("dd MMM yyyy", Locale.US).format(parsed)
}
