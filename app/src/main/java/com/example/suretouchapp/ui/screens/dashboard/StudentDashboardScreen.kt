package com.example.suretouchapp.ui.screens.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.model.*
import com.example.suretouchapp.data.repository.DashboardRepository
import com.example.suretouchapp.data.repository.DashboardSnapshot
import com.example.suretouchapp.data.repository.ModuleGrade
import com.example.suretouchapp.data.repository.StudentProfileRepository
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.BackendSyncedDashboard
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.suretouchapp.ui.components.StudentDrawerContent
import com.example.suretouchapp.ui.components.SureTrustLogo
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.components.InAppOAuthSheet
import com.example.suretouchapp.ui.components.OAuthProvider
import com.example.suretouchapp.ui.screens.attendance.AttendanceScreen
import com.example.suretouchapp.ui.screens.feedback.FeedbackScreen
import com.example.suretouchapp.ui.theme.sureSemanticColors
import com.example.suretouchapp.ui.screens.lifeskills.LifeSkillsScreen
import com.example.suretouchapp.ui.screens.softskills.SoftSkillsScreen
import com.example.suretouchapp.ui.screens.timetable.TimetableScreen
import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// =======================================================
// SESSION STATE & MULTI-CLASS SWIPEABLE SESSION MODEL
// =======================================================
enum class SessionState {
    UPCOMING, LIVE_NOW, COMPLETED
}

data class TimetableClassSession(
    val id: String,
    val courseCode: String,
    val moduleTitle: String,
    val mentorName: String,
    val startTime: String,
    val endTime: String,
    val periodStr: String,
    val dateStr: String,
    val sessionState: SessionState,
    val cohortCode: String = "Pending",
    val isPlaceholder: Boolean = false,
    val meetingLink: String? = null
)

private fun getSessionState(
    dateValue: String,
    startValue: String,
    endValue: String,
    backendStatus: String?,
    now: LocalDateTime
): SessionState {
    if (backendStatus in setOf("COMPLETED", "CANCELLED", "RESCHEDULED")) return SessionState.COMPLETED
    val date = listOf("dd-MMM-yyyy", "yyyy-MM-dd").firstNotNullOfOrNull { pattern ->
        runCatching { LocalDate.parse(dateValue, DateTimeFormatter.ofPattern(pattern, Locale.US)) }.getOrNull()
    } ?: return SessionState.UPCOMING
    fun parseTime(value: String) = runCatching {
        LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME)
    }.recoverCatching {
        LocalTime.parse(value.take(5), DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    }.getOrNull()
    val start = parseTime(startValue) ?: return SessionState.UPCOMING
    val end = parseTime(endValue) ?: start
    val startAt = LocalDateTime.of(date, start)
    val endAt = LocalDateTime.of(date, end)
    return when {
        !now.isBefore(endAt) -> SessionState.COMPLETED
        now.isBefore(startAt) -> SessionState.UPCOMING
        else -> SessionState.LIVE_NOW
    }
}

// =======================================================
// OFFICIAL DASHBOARD DESIGN TOKENS
// =======================================================
private val ColorCanvasBg @Composable get() = MaterialTheme.colorScheme.background
private val ColorCardSurface @Composable get() = MaterialTheme.colorScheme.surface
private val ColorPrimaryPurple @Composable get() = MaterialTheme.colorScheme.primary
private val ColorPurpleGradientStart = Color(0xFF6C2BD9)  // Official Vibrant Royal Purple
private val ColorPurpleGradientEnd = Color(0xFF4C1D95)    // Official Deep Violet
private val ColorTextTitles @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSubtext @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorBorderHairline @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val ColorActiveBorder = Color(0xFF6C2BD9)
private val ColorBottomNavBg @Composable get() = MaterialTheme.colorScheme.surface
private val ColorHomeActivePill @Composable get() = MaterialTheme.colorScheme.primaryContainer

// Grid Icon Container Color Tokens
private val ColorIndigoIconBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ColorIndigoIcon = Color(0xFF4F46E5)

private val ColorAmberIconBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ColorAmberIcon = Color(0xFFD97706)

private val ColorGreenIconBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ColorGreenIcon = Color(0xFF059669)

private val ColorBlueIconBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ColorBlueIcon = Color(0xFF0284C7)

private val ColorPurpleIconBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ColorPurpleIcon = Color(0xFF6D28D9)

private val ColorRedIconBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ColorRedIcon = Color(0xFFDC2626)

private val ColorTealIconBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ColorTealIcon = Color(0xFF0D9488)

private val ColorPinkIconBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ColorPinkIcon = Color(0xFFDB2777)

private val ColorGrayIconBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ColorGrayIcon = Color(0xFF475569)

data class CleanPortalTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color,
    val isSelected: Boolean = false,
    val onClickAction: (() -> Unit)? = null
)

private val QuickAccessSelectionSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    tokenManager: TokenManager,
    onNavigateToCourses: () -> Unit,
    onNavigateToEnrolledCourse: () -> Unit,
    onNavigateToAssignments: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToTimetable: () -> Unit = {},
    onNavigateToApplicationTracker: () -> Unit = {},
    onNavigateToCertificates: () -> Unit = {},
    onNavigateToLiveClass: () -> Unit = {},
    onNavigateToMentorDesk: () -> Unit = {},
    onNavigateToNotices: () -> Unit = {},
    onNavigateToAttendance: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToLifeSkills: () -> Unit = {},
    onNavigateToSoftSkills: () -> Unit = {},
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showTimetableScreen by remember { mutableStateOf(false) }
    var showSubmissionSheet by remember { mutableStateOf(false) }
    var showAttendanceScreen by remember { mutableStateOf(false) }
    var showGradesScreen by remember { mutableStateOf(false) }
    var showFeedbackScreen by remember { mutableStateOf(false) }
    var showLifeSkillsScreen by remember { mutableStateOf(false) }
    var showSoftSkillsScreen by remember { mutableStateOf(false) }
    val dashboardRepository = remember(tokenManager) { DashboardRepository(tokenManager) }
    var dashboardSnapshot by remember {
        mutableStateOf(DashboardSnapshot(cohortCode = tokenManager.getCohortCode().ifBlank { null }))
    }
    var isDashboardLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var isLinkedinActionLoading by remember { mutableStateOf(false) }
    var isGithubActionLoading by remember { mutableStateOf(false) }
    var oauthProvider by remember { mutableStateOf<OAuthProvider?>(null) }
    var oauthUrl by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var profilesReadyNoticeShown by rememberSaveable { mutableStateOf(false) }
    val profileRepository = remember(tokenManager) { StudentProfileRepository(tokenManager) }
    var studentProfile by remember { mutableStateOf<StudentProfileDto?>(null) }
    var showProfilePendingDialog by remember { mutableStateOf(false) }
    var isProfilePending by remember { mutableStateOf(false) }
    var hasDismissedProfilePopupThisSession by rememberSaveable { mutableStateOf(false) }

    fun checkIsProfilePending(prof: StudentProfileDto?): Boolean {
        if (prof == null) return true
        val hasCollege = !prof.college.isNullOrBlank() || tokenManager.getCollegeName().isNotBlank()
        val hasDegree = !prof.degree.isNullOrBlank() || tokenManager.getQualification().isNotBlank()
        return !hasCollege || !hasDegree
    }

    fun refreshDashboard() {
        scope.launch {
            isDashboardLoading = true
            connectionError = null
            errorTitle = null
            val result = runCatching {
                coroutineScope {
                    val dashboardDeferred = async { dashboardRepository.load(force = true) }
                    val profileDeferred = async { profileRepository.load() }
                    Pair(dashboardDeferred.await(), profileDeferred.await())
                }
            }
            if (result.isSuccess) {
                val pair = result.getOrThrow()
                val snapshot = pair.first
                val loadedProfile = pair.second
                dashboardSnapshot = snapshot
                studentProfile = loadedProfile
                val pending = checkIsProfilePending(loadedProfile)
                isProfilePending = pending
                if (pending && !hasDismissedProfilePopupThisSession) {
                    showProfilePendingDialog = true
                }
                isConnected = true
                isOffline = false
                connectionError = null
                errorTitle = null
            } else {
                val ex = result.exceptionOrNull()
                val errorInfo = NetworkUtils.getNetworkErrorInfo(context, ex)
                isConnected = false
                isOffline = errorInfo.isOffline
                errorTitle = errorInfo.title
                connectionError = errorInfo.message
            }
            if (result.isSuccess) hasLoadedOnce = true
            isDashboardLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val email = tokenManager.getUserEmail().trim().lowercase()
        val role = tokenManager.getUserRole().trim().uppercase()
        if (role == "ADMIN" || role == "SUPERADMIN" || email.startsWith("admin@") || email.contains("admin")) {
            tokenManager.clear()
            return@LaunchedEffect
        }
        refreshDashboard()
    }
    LaunchedEffect(tokenManager) {
        delay(2500L) // Yield network priority to initial dashboard load
        while (true) {
            val personalized = runCatching {
                ApiClient.getService(tokenManager).getNotifications()
                    .takeIf { it.isSuccessful }
                    ?.body()
                    ?.results
                    .orEmpty()
            }.getOrDefault(emptyList())
            val unread = personalized.count { !it.isRead }
            dashboardSnapshot = dashboardSnapshot.copy(unreadNotificationCount = unread)
            SureProEdNotificationManager.syncUnread(context, personalized)

            val announcements = runCatching {
                ApiClient.getService(tokenManager).getAnnouncements()
                    .takeIf { it.isSuccessful }
                    ?.body()
                    ?.results
                    .orEmpty()
            }.getOrDefault(emptyList())
            if (announcements.isNotEmpty()) {
                SureProEdNotificationManager.syncAnnouncements(context, announcements)
            }

            val sessions = runCatching {
                ApiClient.getService(tokenManager).getAttendance()
                    .takeIf { it.isSuccessful }
                    ?.body()
                    ?.results
                    .orEmpty()
            }.getOrDefault(emptyList())
            if (sessions.isNotEmpty()) {
                SureProEdNotificationManager.syncTimetableAndClasses(context, sessions)
            }

            val assignments = runCatching {
                ApiClient.getService(tokenManager).getAssignments()
                    .takeIf { it.isSuccessful }
                    ?.body()
                    ?.results
                    .orEmpty()
            }.getOrDefault(emptyList())
            if (assignments.isNotEmpty()) {
                SureProEdNotificationManager.syncAssignments(context, assignments)
            }

            val submissions = runCatching {
                ApiClient.getService(tokenManager).getSubmissions()
                    .takeIf { it.isSuccessful }
                    ?.body()
                    ?.results
                    .orEmpty()
            }.getOrDefault(emptyList())
            SureProEdNotificationManager.syncSubmissionsAndGrades(context, submissions, assignments)

            delay(30_000L)
        }
    }
    // Suppress auto-popup notification if student is already active or in a cohort
    DisposableEffect(lifecycleOwner) {
        var hasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasPaused = true
                Lifecycle.Event.ON_RESUME -> if (hasPaused) {
                    hasPaused = false
                    refreshDashboard()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showApplicationLockedDialog by remember { mutableStateOf(false) }
    var showTrainingLockedDialog by remember { mutableStateOf(false) }
    var submissionText by remember { mutableStateOf("") }
    var submissionSubmitted by remember { mutableStateOf(false) }

    if (showTimetableScreen) {
        TimetableScreen(
            tokenManager = tokenManager,
            onBack = { showTimetableScreen = false }
        )
        return
    }

    if (showAttendanceScreen) {
        AttendanceScreen(tokenManager = tokenManager, onNavigateBack = { showAttendanceScreen = false })
        return
    }

    if (showGradesScreen) {
        ProfessionalGradesScreen(
            snapshot = dashboardSnapshot,
            onBack = { showGradesScreen = false }
        )
        return
    }

    if (showFeedbackScreen) {
        FeedbackScreen(
            tokenManager = tokenManager,
            onBack = { showFeedbackScreen = false }
        )
        return
    }

    if (showLifeSkillsScreen) {
        LifeSkillsScreen(
            tokenManager = tokenManager,
            onBack = { showLifeSkillsScreen = false }
        )
        return
    }

    if (showSoftSkillsScreen) {
        SoftSkillsScreen(
            tokenManager = tokenManager,
            onBack = { showSoftSkillsScreen = false }
        )
        return
    }

    BackendConnectionGate(
        isLoading = isDashboardLoading && !hasLoadedOnce,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Student Portal...",
        onRetry = { refreshDashboard() },
        onLogout = onLogout
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
        drawerContent = {
            StudentDrawerContent(
                tokenManager = tokenManager,
                onNavigateToAttendance = {
                    showAttendanceScreen = true
                },
                onNavigateToCourses = onNavigateToCourses,
                onNavigateToAssignments = onNavigateToAssignments,
                onNavigateToScreening = onNavigateToApplicationTracker,
                onNavigateToNotifications = onNavigateToNotifications,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToFeedback = {
                    showFeedbackScreen = true
                },
                onNavigateToLifeSkills = {
                    if (dashboardSnapshot.cohortCode == null) showTrainingLockedDialog = true
                    else showLifeSkillsScreen = true
                },
                onNavigateToSoftSkills = {
                    if (dashboardSnapshot.cohortCode == null) showTrainingLockedDialog = true
                    else showSoftSkillsScreen = true
                },
                cohortCode = dashboardSnapshot.cohortCode,
                courseName = dashboardSnapshot.courseName,
                onLogout = onLogout,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            containerColor = ColorCanvasBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isDashboardLoading) {
                    DashboardTopBar(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNotificationsClick = onNavigateToNotifications,
                        onProfileClick = onNavigateToProfile,
                        unreadNotificationCount = dashboardSnapshot.unreadNotificationCount,
                        userPhotoUrl = studentProfile?.profilePhoto ?: tokenManager.getProfilePhotoUrl(),
                        displayName = tokenManager.getUserName()
                    )
                }
            },
            bottomBar = {
                DashboardBottomNavigation(
                    onCoursesClick = {
                        if (dashboardSnapshot.cohortCode == null) onNavigateToCourses()
                        else showApplicationLockedDialog = true
                    },
                    onCenterClick = onNavigateToEnrolledCourse,
                    onTasksClick = onNavigateToAssignments,
                    onFeedbackClick = { showFeedbackScreen = true }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ColorCanvasBg)
                    .padding(paddingValues)
            ) {
                // Official SURE Trust Logo Watermark in Student Dashboard Background
                Image(
                    painter = painterResource(id = com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(340.dp)
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = 0.055f
                            scaleX = 1.3f
                            scaleY = 1.3f
                        }
                )

                CleanTimetableDashboardView(
                    onNavigateToCourses = onNavigateToCourses,
                    onNavigateToAssignments = onNavigateToAssignments,
                    onNavigateToNotifications = onNavigateToNotifications,
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToTimetable = { showTimetableScreen = true },
                    onNavigateToApplicationTracker = onNavigateToApplicationTracker,
                    onNavigateToCertificates = onNavigateToCertificates,
                    onNavigateToLiveClass = onNavigateToLiveClass,
                    onNavigateToMentorDesk = onNavigateToMentorDesk,
                    onNavigateToNotices = onNavigateToNotices,
                    onNavigateToFeedback = onNavigateToFeedback,
                    onNavigateToLifeSkills = {
                        if (dashboardSnapshot.cohortCode == null) showTrainingLockedDialog = true
                        else onNavigateToLifeSkills()
                    },
                    onNavigateToSoftSkills = {
                        if (dashboardSnapshot.cohortCode == null) showTrainingLockedDialog = true
                        else onNavigateToSoftSkills()
                    },
                    dashboardSnapshot = dashboardSnapshot,
                    isDashboardLoading = isDashboardLoading,
                    hasLoadedOnce = hasLoadedOnce,
                    onRefreshDashboard = ::refreshDashboard,
                    onOpenSubmitAssignment = { showSubmissionSheet = true },
                    onOpenAttendanceDetails = { showAttendanceScreen = true },
                    onNavigateToGrades = { showGradesScreen = true },
                    isLinkedinActionLoading = isLinkedinActionLoading,
                    isGithubActionLoading = isGithubActionLoading,
                    onGithubAction = {
                        scope.launch {
                            if (isGithubActionLoading) return@launch
                            isGithubActionLoading = true
                            val response = runCatching {
                                ApiClient.getService(tokenManager).getGitHubAuthUrl()
                            }.getOrNull()
                            val url = response
                                ?.takeIf { it.isSuccessful }
                                ?.body()
                                ?.let { it.authorizationUrl ?: it.authUrl ?: it.url }
                            if (url.isNullOrBlank()) {
                                snackbarHostState.showSnackbar("Could not start GitHub verification. Please try again.")
                            } else {
                                oauthProvider = OAuthProvider.GITHUB
                                oauthUrl = url
                            }
                            isGithubActionLoading = false
                        }
                    },
                    onLinkedinAction = {
                        scope.launch {
                            if (isLinkedinActionLoading) return@launch
                            isLinkedinActionLoading = true
                            val api = ApiClient.getService(tokenManager)
                            if (dashboardSnapshot.isLinkedinConnected) {
                                val response = runCatching { api.disconnectLinkedIn() }.getOrNull()
                                if (response?.isSuccessful == true) {
                                    dashboardSnapshot = dashboardSnapshot.copy(isLinkedinConnected = false)
                                }
                            } else {
                                val response = runCatching { api.getLinkedInAuthUrl() }.getOrNull()
                                val url = response?.takeIf { it.isSuccessful }?.body()?.let { it.authorizationUrl ?: it.authUrl ?: it.url }
                                if (url.isNullOrBlank()) {
                                    snackbarHostState.showSnackbar("Could not start LinkedIn verification. Please try again.")
                                } else {
                                    oauthProvider = OAuthProvider.LINKEDIN
                                    oauthUrl = url
                                }
                            }
                            isLinkedinActionLoading = false
                        }
                    },
                    isProfilePending = isProfilePending
                )

                // Profile Incomplete Popup Dialog
                if (showProfilePendingDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showProfilePendingDialog = false
                            hasDismissedProfilePopupThisSession = true
                        },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF6C2BD9), Color(0xFF4C1D95))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Complete Your Student Profile",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Your academic and personal profile details are currently pending. Completing your profile is required to enable cohort assignment, attendance tracking, and official certificates.",
                                    fontSize = 13.sp,
                                    color = ColorTextSubtext,
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Center
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, ColorBorderHairline),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircleOutline, null, tint = Color(0xFF6C2BD9), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("College & Degree / Specialization", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitles)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircleOutline, null, tint = Color(0xFF6C2BD9), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Contact Phone & Location details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitles)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircleOutline, null, tint = Color(0xFF6C2BD9), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("LinkedIn & GitHub profile links", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitles)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showProfilePendingDialog = false
                                    hasDismissedProfilePopupThisSession = true
                                    onNavigateToProfile()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C2BD9)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Complete Profile Now →", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showProfilePendingDialog = false
                                    hasDismissedProfilePopupThisSession = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Remind Me Later", color = ColorTextSubtext, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    )
                }

                // Application-Locked State Dialog
                if (showApplicationLockedDialog) {
                    AlertDialog(
                        onDismissRequest = { showApplicationLockedDialog = false },
                        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ColorPrimaryPurple, modifier = Modifier.size(36.dp)) },
                        title = { Text("One-Active-Enrollment Limit", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles) },
                        text = {
                            Text(
                                "You are currently enrolled in ${dashboardSnapshot.courseName ?: "your assigned programme"} " +
                                    "(Cohort ${dashboardSnapshot.cohortCode ?: "pending"}). Finish the active cohort or submit a transfer request before enrolling in another course.",
                                fontSize = 12.sp,
                                color = ColorTextSubtext
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { showApplicationLockedDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple)
                            ) {
                                Text("Got It")
                            }
                        }
                    )
                }

                if (showTrainingLockedDialog) {
                    AlertDialog(
                        onDismissRequest = { showTrainingLockedDialog = false },
                        icon = { Icon(Icons.Default.Lock, null, tint = ColorPrimaryPurple, modifier = Modifier.size(38.dp)) },
                        title = { Text("Training locked", fontWeight = FontWeight.Bold) },
                        text = { Text("Life Skills and Soft Skills Training become available after your cohort is assigned by SURE Trust.") },
                        confirmButton = {
                            TextButton(onClick = { showTrainingLockedDialog = false }) { Text("OK") }
                        }
                    )
                }

                val activeOAuthProvider = oauthProvider
                val activeOAuthUrl = oauthUrl
                if (activeOAuthProvider != null && !activeOAuthUrl.isNullOrBlank()) {
                    InAppOAuthSheet(
                        provider = activeOAuthProvider,
                        initialUrl = activeOAuthUrl,
                        onDismiss = {
                            oauthProvider = null
                            oauthUrl = null
                        },
                        onResult = { callback ->
                            val succeeded = callback.getQueryParameter("status")
                                .equals("success", ignoreCase = true)
                            val message = callback.getQueryParameter("message")
                            oauthProvider = null
                            oauthUrl = null
                            scope.launch {
                                if (succeeded) {
                                    refreshDashboard()
                                    snackbarHostState.showSnackbar(
                                        "${activeOAuthProvider.title.removePrefix("Connect ")} connected successfully."
                                    )
                                } else {
                                    snackbarHostState.showSnackbar(
                                        message ?: "Authentication was not completed. Please try again."
                                    )
                                }
                            }
                        }
                    )
                }

                // Assignment Submission Bottom Sheet
                if (showSubmissionSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showSubmissionSheet = false },
                        containerColor = ColorCardSurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text("Submit Assignment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                            Text("Building REST APIs with Django • Module 4", fontSize = 12.sp, color = ColorTextSubtext)
                            Spacer(modifier = Modifier.height(16.dp))

                            if (submissionSubmitted) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFECFDF5),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Submission received! Status: Under Evaluation", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showSubmissionSheet = false; submissionSubmitted = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Done")
                                }
                            } else {
                                OutlinedTextField(
                                    value = submissionText,
                                    onValueChange = { submissionText = it },
                                    label = { Text("GitHub Repo URL or Project Submission Link") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 3
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { if (submissionText.isNotBlank()) submissionSubmitted = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Submit Assignment")
                                }
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
private fun DashboardTopBar(
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    unreadNotificationCount: Int,
    userPhotoUrl: String? = null,
    displayName: String = "Student"
) {
    Surface(
        color = ColorCardSurface,
        shadowElevation = 1.dp
    ) {
        Column {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(42.dp)) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open drawer",
                        tint = ColorPrimaryPurple,
                        modifier = Modifier.size(28.dp)
                    )
                }
                SureTrustLogo(size = 36.dp, showSubtext = false)
                Spacer(modifier = Modifier.width(7.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SURE ProEd",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = ColorTextTitles,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Student Learning Portal",
                        fontSize = 11.sp,
                        color = ColorTextSubtext,
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier.size(46.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotificationCount > 0) {
                                Badge(containerColor = Color(0xFFDC2626)) {
                                    Text(
                                        unreadNotificationCount.coerceAtMost(99).toString(),
                                        fontSize = 10.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = ColorTextTitles,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ColorIndigoIconBg)
                        .border(1.dp, ColorBorderHairline, CircleShape)
                        .clickable(onClick = onProfileClick),
                    contentAlignment = Alignment.Center
                ) {
                    com.example.suretouchapp.ui.components.StudentProfileImage(
                        photo = userPhotoUrl,
                        displayName = displayName,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 50
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF16A34A))
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardBottomNavigation(
    onCoursesClick: () -> Unit,
    onCenterClick: () -> Unit,
    onTasksClick: () -> Unit,
    onFeedbackClick: () -> Unit
) {
    Surface(
        color = ColorBottomNavBg,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardNavItem(
                label = "Home",
                icon = Icons.Default.Home,
                active = true,
                onClick = {},
                modifier = Modifier.weight(1f)
            )
            DashboardNavItem(
                label = "Courses",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onCoursesClick,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = (-8).dp)
                        .size(64.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))
                            )
                        )
                        .border(5.dp, Color.White, CircleShape)
                        .clickable(onClick = onCenterClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "My enrolled course",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            DashboardNavItem(
                label = "Tasks",
                icon = Icons.AutoMirrored.Filled.Assignment,
                onClick = onTasksClick,
                modifier = Modifier.weight(1f)
            )
            DashboardNavItem(
                label = "Feedback",
                icon = Icons.Default.Feedback,
                onClick = onFeedbackClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DashboardNavItem(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val itemShape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .clip(itemShape)
            .background(if (active) ColorHomeActivePill else Color.Transparent)
            .then(
                if (active) {
                    Modifier.border(1.dp, ColorPrimaryPurple.copy(alpha = 0.18f), itemShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) ColorPrimaryPurple else ColorTextSubtext,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            color = if (active) ColorPrimaryPurple else ColorTextSubtext
        )
    }
}

// =========================================================================
// DASHBOARD VIEW: CLEAN TIMETABLE HERO CARD
// =========================================================================
@Composable
private fun DashboardLinkedInVerificationCard(
    isLoading: Boolean,
    isGithubLoading: Boolean,
    linkedinConnected: Boolean,
    githubLinked: Boolean,
    onAddGithub: () -> Unit,
    onConnect: () -> Unit
) {
    val linkedInBlue = Color(0xFF0A66C2)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconBg = if (!linkedinConnected) linkedInBlue else Color(0xFF24292F)
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                if (!linkedinConnected) {
                    Text("in", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                } else {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "GitHub",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Connect Professional Profiles", color = ColorTextTitles, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !linkedinConnected && !githubLinked -> "Link your LinkedIn and GitHub accounts to receive your cohort assignment."
                        !linkedinConnected -> "Connect your LinkedIn profile to verify your student credentials."
                        else -> "Link your GitHub account to access cohort coding projects."
                    },
                    color = ColorTextSubtext,
                    fontSize = 11.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!linkedinConnected) {
                    Button(
                        onClick = onConnect,
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = linkedInBlue),
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("LinkedIn", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
                if (!githubLinked) {
                    OutlinedButton(
                        onClick = onAddGithub,
                        enabled = !isGithubLoading,
                        border = BorderStroke(1.dp, Color(0xFF24292F)),
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        if (isGithubLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF24292F),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("GitHub", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF24292F))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanTimetableDashboardView(
    onNavigateToCourses: () -> Unit,
    onNavigateToAssignments: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToTimetable: () -> Unit = {},
    onNavigateToApplicationTracker: () -> Unit = {},
    onNavigateToCertificates: () -> Unit = {},
    onNavigateToLiveClass: () -> Unit = {},
    onNavigateToMentorDesk: () -> Unit = {},
    onNavigateToNotices: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToLifeSkills: () -> Unit = {},
    onNavigateToSoftSkills: () -> Unit = {},
    dashboardSnapshot: DashboardSnapshot = DashboardSnapshot(),
    isDashboardLoading: Boolean = false,
    hasLoadedOnce: Boolean = true,
    onRefreshDashboard: () -> Unit = {},
    onOpenSubmitAssignment: () -> Unit = {},
    onOpenAttendanceDetails: () -> Unit = {},
    onNavigateToGrades: () -> Unit = {},
    isLinkedinActionLoading: Boolean = false,
    isGithubActionLoading: Boolean = false,
    onGithubAction: () -> Unit = {},
    onLinkedinAction: () -> Unit = {},
    isProfilePending: Boolean = false
) {
    var showCustomizeSheet by rememberSaveable { mutableStateOf(false) }
    val completedGrades = dashboardSnapshot.grades.filter { it.marks != null }
    val hasPreScreenResult = !dashboardSnapshot.screeningMarksObtained.isNullOrBlank() ||
        !dashboardSnapshot.screeningPercentage.isNullOrBlank() ||
        !dashboardSnapshot.screeningGrade.isNullOrBlank()
    val gradeAverage = completedGrades.mapNotNull { it.percentage }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toInt()
    val applicationStatus = dashboardSnapshot.applicationStatus?.uppercase(Locale.US)
    val screeningStatus = dashboardSnapshot.screeningStatus?.uppercase(Locale.US)
    val screeningSubmitted = screeningStatus in setOf("SUBMITTED", "EVALUATED") ||
        applicationStatus in setOf("EXAM_COMPLETED", "QUALIFIED", "WAITLISTED", "COHORT_ASSIGNED", "IN_PROGRESS", "COMPLETED")
    val screeningSubtitle = when {
        dashboardSnapshot.cohortCode != null -> "Completed"
        dashboardSnapshot.studentRoleVerified -> "Student verified"
        dashboardSnapshot.screeningQualified -> "Verification pending"
        screeningSubmitted -> "Result pending"
        dashboardSnapshot.applicationStatus != null -> "Assessment pending"
        else -> "Start application"
    }
    val gradesSubtitle = when {
        hasPreScreenResult -> dashboardSnapshot.screeningPercentage
            ?.takeIf { it.isNotBlank() }
            ?.let { "Pre-screen • ${if (it.endsWith("%")) it else "$it%"}" }
            ?: "Pre-screen result published"
        dashboardSnapshot.cohortCode == null -> "Module grades after cohort"
        completedGrades.isEmpty() -> "No scores yet"
        gradeAverage != null -> "${completedGrades.size} tests • $gradeAverage%"
        else -> "Module test results"
    }

    // Clean 3x3 Grid Modules
    val gridModules = listOf(
        CleanPortalTile(
            title = "My Courses",
            subtitle = dashboardSnapshot.courseName
                ?: if (dashboardSnapshot.cohortCode == null) "Browse courses" else "1 Enrolled",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            iconTint = ColorIndigoIcon,
            iconBg = ColorIndigoIconBg,
            isSelected = true,
            onClickAction = onNavigateToCourses
        ),
        CleanPortalTile(
            title = "Announcements",
            subtitle = if (dashboardSnapshot.announcementCount > 0) {
                "${dashboardSnapshot.announcementCount} active"
            } else {
                "SURE ProEd updates"
            },
            icon = Icons.Default.Campaign,
            iconTint = ColorPinkIcon,
            iconBg = ColorPinkIconBg,
            onClickAction = onNavigateToNotices
        ),
        CleanPortalTile(
            title = "Assignments",
            subtitle = if (dashboardSnapshot.cohortCode == null) "Cohort required" else "View tasks",
            icon = Icons.AutoMirrored.Filled.Assignment,
            iconTint = ColorAmberIcon,
            iconBg = ColorAmberIconBg,
            onClickAction = onNavigateToAssignments
        ),
        CleanPortalTile(
            title = "Request Form",
            subtitle = if (dashboardSnapshot.openRequestCount > 0) "${dashboardSnapshot.openRequestCount} open requests" else "Ask for support",
            icon = Icons.Default.SupportAgent,
            iconTint = ColorPinkIcon,
            iconBg = ColorPinkIconBg,
            onClickAction = onNavigateToFeedback
        ),
        CleanPortalTile(
            title = "Attendance",
            subtitle = if (dashboardSnapshot.cohortCode == null) "No cohort yet" else "View record",
            icon = Icons.Default.EventAvailable,
            iconTint = ColorGreenIcon,
            iconBg = ColorGreenIconBg,
            onClickAction = onOpenAttendanceDetails
        ),
        CleanPortalTile(
            title = "Screening",
            subtitle = screeningSubtitle,
            icon = Icons.Default.Quiz,
            iconTint = ColorBlueIcon,
            iconBg = ColorBlueIconBg,
            onClickAction = onNavigateToApplicationTracker
        ),
        CleanPortalTile(
            title = "Grades",
            subtitle = gradesSubtitle,
            icon = Icons.Default.Grade,
            iconTint = ColorPurpleIcon,
            iconBg = ColorPurpleIconBg,
            onClickAction = onNavigateToGrades
        ),
        CleanPortalTile(
            title = "Certificates",
            subtitle = when {
                dashboardSnapshot.certificateCount > 0 -> "${dashboardSnapshot.certificateCount} issued"
                dashboardSnapshot.cohortCode == null -> "After programme"
                else -> "None issued yet"
            },
            icon = Icons.Default.WorkspacePremium,
            iconTint = ColorPurpleIcon,
            iconBg = ColorPurpleIconBg,
            onClickAction = onNavigateToCertificates
        ),
        CleanPortalTile(
            title = "Live Class",
            subtitle = dashboardSnapshot.sessions.firstOrNull()?.let { "Next • ${it.startTime}" } ?: "No class scheduled",
            icon = Icons.Default.LiveTv,
            iconTint = ColorRedIcon,
            iconBg = ColorRedIconBg,
            onClickAction = onNavigateToLiveClass
        ),
        CleanPortalTile(
            title = "Mentor Desk",
            subtitle = if (dashboardSnapshot.cohortCode == null) {
                "No cohort assigned"
            } else {
                dashboardSnapshot.mentorName ?: "Mentor pending"
            },
            icon = Icons.Default.HeadsetMic,
            iconTint = ColorTealIcon,
            iconBg = ColorTealIconBg,
            onClickAction = onNavigateToMentorDesk
        ),
        CleanPortalTile(
            title = "Profile",
            subtitle = "Account Details",
            icon = Icons.Default.AccountCircle,
            iconTint = ColorGrayIcon,
            iconBg = ColorGrayIconBg,
            onClickAction = onNavigateToProfile
        ),
        CleanPortalTile(
            title = "Life Skills",
            subtitle = if (dashboardSnapshot.cohortCode == null) "Cohort required" else "LST Training",
            icon = Icons.Default.Psychology,
            iconTint = ColorPurpleIcon,
            iconBg = ColorPurpleIconBg,
            onClickAction = onNavigateToLifeSkills
        ),
        CleanPortalTile(
            title = "Soft Skills",
            subtitle = if (dashboardSnapshot.cohortCode == null) "Cohort required" else "Career Readiness",
            icon = Icons.Default.Groups,
            iconTint = ColorBlueIcon,
            iconBg = ColorBlueIconBg,
            onClickAction = onNavigateToSoftSkills
        )
    )

    val defaultTileTitles = gridModules.take(9).map { it.title }.toSet()
    var enabledTileTitles by rememberSaveable(stateSaver = QuickAccessSelectionSaver) {
        mutableStateOf(defaultTileTitles)
    }
    val visibleGridModules = gridModules.filter { it.title in enabledTileTitles }

    val isLocalBackendConnected = com.example.suretouchapp.ui.components.LocalBackendConnected.current
    PullToRefreshBox(
        isRefreshing = isDashboardLoading,
        onRefresh = onRefreshDashboard,
        modifier = Modifier.fillMaxSize(),
        indicator = {}
    ) {
        BackendSyncedDashboard(isLoading = isDashboardLoading) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                    contentDescription = "SURE Trust Official Logo Watermark",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(300.dp)
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = 0.04f
                            scaleX = 1.35f
                            scaleY = 1.35f
                        }
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
        if (isProfilePending) {
            item {
                ProfileIncompleteBanner(onNavigateToProfile = onNavigateToProfile)
            }
        }
        // =========================================================================
        // TODAY'S TIMETABLE HERO CAROUSEL (SWIPEABLE MULTI-CLASS PAGER)
        // =========================================================================
        item {
            var dashboardClock by remember { mutableStateOf(LocalDateTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    dashboardClock = LocalDateTime.now()
                    delay(30_000L)
                }
            }
            val dashboardDate = remember {
                SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(java.util.Date()).uppercase(Locale.US)
            }
            val pendingScheduleMessage = when {
                dashboardSnapshot.cohortCode != null -> "There are no upcoming sessions in the current timetable."
                dashboardSnapshot.applicationStatus == null -> "Apply for a course to begin your student journey."
                !screeningSubmitted -> "Complete the pre-screening assessment to continue."
                !dashboardSnapshot.screeningQualified -> "Your screening result is awaiting a backend update."
                !dashboardSnapshot.studentRoleVerified -> "Student role verification is in progress."
                else -> "Your verified student account is waiting for cohort assignment."
            }
            val timetableSessions = remember(dashboardSnapshot, dashboardClock) {
                val cohortCode = dashboardSnapshot.cohortCode ?: "Pending"
                dashboardSnapshot.sessions.map { session ->
                    TimetableClassSession(
                        id = session.id,
                        courseCode = session.title,
                        moduleTitle = session.moduleTitle,
                        mentorName = "${session.mentorName} • ${if (session.meetingLink.isNullOrBlank()) "Class session" else "Live Online Class"}",
                        startTime = session.startTime,
                        endTime = session.endTime,
                        periodStr = session.period,
                        dateStr = session.date.ifBlank { dashboardDate },
                        sessionState = getSessionState(
                            session.rawDate ?: session.date,
                            session.rawStartTime ?: session.startTime,
                            session.rawEndTime ?: session.endTime,
                            session.classStatus,
                            dashboardClock
                        ),
                        cohortCode = cohortCode,
                        meetingLink = session.meetingLink
                    )
                }.ifEmpty {
                    listOf(
                        TimetableClassSession(
                            id = "pending",
                            courseCode = if (dashboardSnapshot.cohortCode == null) "Cohort assignment pending" else "Await Upcoming Classes",
                            moduleTitle = if (dashboardSnapshot.cohortCode != null) "Your mentors will publish the next session shortly." else pendingScheduleMessage,
                            mentorName = if (dashboardSnapshot.cohortCode == null) "Admission progress" else "Cohort ${dashboardSnapshot.cohortCode}",
                            startTime = "--:--",
                            endTime = "--:--",
                            periodStr = "IST",
                            dateStr = dashboardDate,
                            sessionState = SessionState.UPCOMING,
                            cohortCode = cohortCode,
                            isPlaceholder = true
                        )
                    )
                }
            }
            val pagerState = rememberPagerState(pageCount = { timetableSessions.size })

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Today’s Timetable",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextTitles
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = ColorPrimaryPurple,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = timetableSessions[pagerState.currentPage].dateStr,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorPrimaryPurple,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.clickable(enabled = !isDashboardLoading) { onRefreshDashboard() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isDashboardLoading) {
                                SureTrustLoadingIndicator(size = 28.dp, logoSize = 17.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = ColorPrimaryPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = if (timetableSessions[pagerState.currentPage].cohortCode == "Pending") {
                                    "Cohort pending"
                                } else timetableSessions[pagerState.currentPage].cohortCode,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ColorPrimaryPurple,
                                maxLines = 1
                            )
                            if (!isDashboardLoading) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh cohort",
                                    tint = ColorTextSubtext,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Swipeable HorizontalPager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    pageSpacing = 12.dp
                ) { pageIndex ->
                    val session = timetableSessions[pageIndex]
                    val railStartTime = if (session.sessionState == SessionState.COMPLETED) "--:--" else session.startTime
                    val railEndTime = if (session.sessionState == SessionState.COMPLETED) "--:--" else session.endTime
                    val railPeriod = if (session.sessionState == SessionState.COMPLETED) "---" else session.periodStr
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(244.dp)
                            .clickable {
                                when {
                                    !session.isPlaceholder && session.sessionState == SessionState.LIVE_NOW && !session.meetingLink.isNullOrBlank() -> onNavigateToLiveClass()
                                    !session.isPlaceholder -> onNavigateToTimetable()
                                    dashboardSnapshot.cohortCode != null -> onNavigateToTimetable()
                                    dashboardSnapshot.applicationStatus == null -> onNavigateToCourses()
                                    else -> onNavigateToApplicationTracker()
                                }
                            },
                        shape = RoundedCornerShape(22.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            ColorPurpleGradientStart,
                                            ColorPurpleGradientEnd
                                        )
                                    )
                                )
                        ) {
                            Image(
                                painter = painterResource(id = com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                                contentDescription = "SURE Trust official logo watermark",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(160.dp)
                                    .align(Alignment.CenterEnd)
                                    .offset(x = 24.dp)
                                    .graphicsLayer {
                                        alpha = 0.18f
                                        scaleX = 1.35f
                                        scaleY = 1.35f
                                    }
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(174.dp)
                                    .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Time block
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(58.dp)
                                ) {
                                    Text(
                                        text = railStartTime,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Box(
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 3.dp)
                                                .width(2.dp)
                                                .height(36.dp)
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.70f),
                                                            Color.White.copy(alpha = 0.20f)
                                                        )
                                                    )
                                                )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                    Text(
                                        text = railEndTime,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.90f)
                                    )
                                    Text(
                                        text = railPeriod,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = Color(0xFF74C600)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(118.dp)
                                        .background(Color.White.copy(alpha = 0.32f))
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 8.dp, end = 2.dp)
                                ) {
                                    Text(
                                        text = session.courseCode,
                                        modifier = Modifier.padding(end = 78.dp),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = when {
                                            session.courseCode.length > 52 -> 11.sp
                                            session.courseCode.length > 36 -> 13.sp
                                            session.courseCode.length > 24 -> 15.sp
                                            else -> 17.sp
                                        },
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = session.moduleTitle,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val mentorDetails = session.mentorName.split(" • ", limit = 2)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.94f),
                                            border = BorderStroke(1.dp, Color.White)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = ColorPrimaryPurple,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(9.dp))
                                        Column {
                                            Text(
                                                text = mentorDetails.first(),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (mentorDetails.size > 1) {
                                                Text(
                                                    text = mentorDetails[1],
                                                    fontSize = 11.5.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(Color.White)
                                    .clickable {
                                        when {
                                            !session.isPlaceholder && session.sessionState == SessionState.LIVE_NOW && !session.meetingLink.isNullOrBlank() -> onNavigateToLiveClass()
                                            !session.isPlaceholder -> onNavigateToTimetable()
                                            dashboardSnapshot.cohortCode != null -> onNavigateToTimetable()
                                            dashboardSnapshot.applicationStatus == null -> onNavigateToCourses()
                                            else -> onNavigateToApplicationTracker()
                                        }
                                    }
                                    .padding(horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (session.isPlaceholder) {
                                        if (dashboardSnapshot.cohortCode != null) Icons.Default.CalendarMonth
                                        else if (dashboardSnapshot.applicationStatus == null) Icons.Default.School
                                        else Icons.Default.Quiz
                                    } else when (session.sessionState) {
                                        SessionState.LIVE_NOW -> Icons.Default.Videocam
                                        SessionState.UPCOMING -> Icons.Default.CalendarMonth
                                        SessionState.COMPLETED -> Icons.Default.Schedule
                                    },
                                    contentDescription = null,
                                    tint = ColorPrimaryPurple,
                                    modifier = Modifier.size(23.dp)
                                )
                                val hasCohortAssigned = dashboardSnapshot.cohortCode != null
                                Text(
                                    text = when {
                                        !session.isPlaceholder && session.sessionState == SessionState.LIVE_NOW -> "Join Session"
                                        !session.isPlaceholder && session.sessionState == SessionState.COMPLETED -> "Ended • ${session.startTime} – ${session.endTime}"
                                        !session.isPlaceholder -> "View Schedule"
                                        hasCohortAssigned -> "Await Upcoming Classes"
                                        dashboardSnapshot.applicationStatus == null -> "Browse Courses"
                                        else -> "Await Upcoming Classes"
                                    },
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = ColorPrimaryPurple
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = ColorPrimaryPurple,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Compact session-status bubble, kept clear of course information.
                            val statusDotColor = when (session.sessionState) {
                                SessionState.UPCOMING  -> Color(0xFFFBBF24)
                                SessionState.LIVE_NOW  -> Color(0xFF4ADE80)
                                SessionState.COMPLETED -> Color(0xFF94A3B8)
                            }
                            val statusLabel = if (session.isPlaceholder) "Pending" else when (session.sessionState) {
                                SessionState.UPCOMING  -> "Upcoming"
                                SessionState.LIVE_NOW  -> "Live Now"
                                SessionState.COMPLETED -> "Completed"
                            }

                            val infiniteTransition = rememberInfiniteTransition(label = "StatusDotBlink_$pageIndex")
                            val alphaAnimation by infiniteTransition.animateFloat(
                                initialValue = 1.0f,
                                targetValue = 0.20f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "StatusDotAlpha_$pageIndex"
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 12.dp, end = 12.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .graphicsLayer { alpha = alphaAnimation }
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = statusLabel,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Upgraded Glowing Active Pill Page Indicator Dots (...)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(timetableSessions.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(if (isSelected) 22.dp else 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isSelected) ColorPrimaryPurple else Color(0xFFCBD5E1)
                                )
                        )
                    }
                }
            }
        }

        if (isLocalBackendConnected && (!dashboardSnapshot.isLinkedinConnected || !dashboardSnapshot.isGithubLinked)) {
            item {
                DashboardLinkedInVerificationCard(
                    isLoading = isLinkedinActionLoading,
                    isGithubLoading = isGithubActionLoading,
                    linkedinConnected = dashboardSnapshot.isLinkedinConnected,
                    githubLinked = dashboardSnapshot.isGithubLinked,
                    onAddGithub = onGithubAction,
                    onConnect = onLinkedinAction
                )
            }
        }

        // =========================================================================
        // QUICK ACCESS SECTION
        // =========================================================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Access",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextTitles
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCustomizeSheet = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customize",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorPrimaryPurple
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Customize quick access",
                        tint = ColorPrimaryPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val tileRows = visibleGridModules.chunked(3)
                tileRows.forEach { rowTiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowTiles.forEach { tile ->
                            Box(modifier = Modifier.weight(1f)) {
                                SpaciousBadgelessGridCard(tile = tile)
                            }
                        }
                        repeat(3 - rowTiles.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

                item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showCustomizeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCustomizeSheet = false },
            containerColor = ColorCardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
            ) {
                Text(
                    text = "Customize Quick Access",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextTitles
                )
                Text(
                    text = "Choose the shortcuts shown on your dashboard.",
                    fontSize = 13.sp,
                    color = ColorTextSubtext
                )
                Spacer(modifier = Modifier.height(14.dp))

                gridModules.forEach { tile ->
                    val isEnabled = tile.title in enabledTileTitles
                    val canDisable = enabledTileTitles.size > 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isEnabled || canDisable) {
                                enabledTileTitles = if (isEnabled) {
                                    enabledTileTitles - tile.title
                                } else {
                                    enabledTileTitles + tile.title
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(tile.iconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tile.icon,
                                contentDescription = null,
                                tint = tile.iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = tile.title,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorTextTitles
                        )
                        Checkbox(
                            checked = isEnabled,
                            enabled = !isEnabled || canDisable,
                            onCheckedChange = {
                                enabledTileTitles = if (isEnabled) {
                                    enabledTileTitles - tile.title
                                } else {
                                    enabledTileTitles + tile.title
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = ColorPrimaryPurple)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { enabledTileTitles = defaultTileTitles },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, ColorPrimaryPurple)
                    ) {
                        Text("Reset", color = ColorPrimaryPurple)
                    }
                    Button(
                        onClick = { showCustomizeSheet = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color
) {
    Surface(
        modifier = Modifier.width(142.dp),
        shape = RoundedCornerShape(16.dp),
        color = ColorCardSurface,
        border = BorderStroke(1.dp, ColorBorderHairline)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextTitles)
                Text(label, fontSize = 10.sp, color = ColorTextSubtext, maxLines = 1)
            }
        }
    }
}



@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProfessionalGradesScreen(
    snapshot: DashboardSnapshot,
    onBack: () -> Unit
) {
    val percentage = snapshot.screeningPercentage?.trim()?.takeIf { it.isNotEmpty() }?.let {
        if (it.endsWith("%")) it else "$it%"
    } ?: "--"
    val marks = when {
        !snapshot.screeningMarksObtained.isNullOrBlank() && !snapshot.screeningTotalMarks.isNullOrBlank() ->
            "${snapshot.screeningMarksObtained} / ${snapshot.screeningTotalMarks}"
        !snapshot.screeningMarksObtained.isNullOrBlank() -> snapshot.screeningMarksObtained
        else -> "--"
    }
    val publishedModules = snapshot.grades.count { it.marks != null }

    Scaffold(
        containerColor = ColorCanvasBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Academic Marks", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF24123D))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        Modifier.fillMaxWidth().background(
                            Brush.linearGradient(listOf(ColorPurpleGradientStart, ColorPurpleGradientEnd))
                        )
                    ) {
                        Image(
                            painter = painterResource(id = com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                            contentDescription = "SURE Trust official logo watermark",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 24.dp)
                                .size(150.dp)
                                .graphicsLayer {
                                    alpha = 0.18f
                                    scaleX = 1.35f
                                    scaleY = 1.35f
                                }
                        )
                        Column(Modifier.padding(18.dp)) {
                            Text("STUDENT PERFORMANCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD8B4FE), letterSpacing = 1.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(snapshot.courseName ?: "SURE ProEd Programme", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2)
                            Text(
                                snapshot.cohortCode?.let { "Cohort $it" } ?: "Admission assessment record",
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            Spacer(Modifier.height(18.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)).padding(vertical = 13.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                GradeSummaryCell("SCORE", percentage)
                                Box(Modifier.width(1.dp).height(38.dp).background(Color.White.copy(alpha = 0.22f)))
                                GradeSummaryCell("GRADE", snapshot.screeningGrade ?: "--")
                                Box(Modifier.width(1.dp).height(38.dp).background(Color.White.copy(alpha = 0.22f)))
                                GradeSummaryCell("STATUS", if (snapshot.screeningQualified) "Qualified" else "Published")
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("Marks Statement", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextTitles)
                        Text("Official results published by SURE ProEd", fontSize = 12.sp, color = ColorTextSubtext)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = ColorGreenIconBg) {
                        Text("${1 + publishedModules} RESULT${if (publishedModules == 0) "" else "S"}", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorGreenIcon)
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ColorBorderHairline),
                    shadowElevation = 2.dp
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ASSESSMENT", Modifier.weight(1f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = ColorTextSubtext)
                            Text("MARKS", Modifier.width(100.dp), textAlign = TextAlign.Center, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = ColorTextSubtext)
                            Text("RESULT", Modifier.width(76.dp), textAlign = TextAlign.End, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = ColorTextSubtext)
                        }
                        AssessmentMarksRow(
                            title = "Pre-Screen Examination",
                            subtitle = "$percentage • Grade ${snapshot.screeningGrade ?: "--"}",
                            marks = marks,
                            result = if (snapshot.screeningQualified) "PASS" else "PUBLISHED",
                            passed = snapshot.screeningQualified
                        )
                    }
                }
            }

            item {
                Text("Module Test Results", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                Text("Module marks appear here after mentor evaluation.", fontSize = 12.sp, color = ColorTextSubtext)
            }

            if (snapshot.grades.isEmpty()) {
                item { EmptyGradesMessage(cohortAssigned = snapshot.cohortCode != null) }
            } else {
                items(snapshot.grades.sortedBy { it.moduleNumber }.size) { index ->
                    val grade = snapshot.grades.sortedBy { it.moduleNumber }[index]
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, ColorBorderHairline)
                    ) {
                        AssessmentMarksRow(
                            title = grade.title.ifBlank { "Module ${grade.moduleNumber} Test" },
                            subtitle = grade.percentage?.let { "$it%" } ?: "Result not published",
                            marks = grade.marks?.let { "$it / ${grade.maxMarks}" } ?: "--",
                            result = when { grade.marks == null -> if (grade.unlocked) "READY" else "LOCKED"; grade.passed -> "PASS"; else -> "RETRY" },
                            passed = grade.passed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeSummaryCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 76.dp)) {
        Text(value, fontSize = if (value.length > 8) 12.sp else 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.66f), letterSpacing = 0.7.sp)
    }
}

@Composable
private fun AssessmentMarksRow(
    title: String,
    subtitle: String,
    marks: String,
    result: String,
    passed: Boolean
) {
    val resultColor = when {
        passed -> ColorGreenIcon
        result == "RETRY" -> ColorRedIcon
        else -> ColorPrimaryPurple
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(resultColor.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(if (passed) Icons.Default.CheckCircle else Icons.Default.Assessment, null, tint = resultColor, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles, maxLines = 2)
            Text(subtitle, fontSize = 10.5.sp, color = ColorTextSubtext)
        }
        Text(marks, Modifier.width(100.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextTitles)
        Surface(shape = RoundedCornerShape(7.dp), color = resultColor.copy(alpha = 0.10f), modifier = Modifier.width(76.dp)) {
            Text(result, modifier = Modifier.padding(vertical = 6.dp), textAlign = TextAlign.Center, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = resultColor, maxLines = 1)
        }
    }
}

@Composable
private fun PreScreenGradeCard(
    marks: String?,
    maxMarks: String?,
    percentage: String?,
    grade: String?,
    qualified: Boolean
) {
    val resultColor = if (qualified) Color(0xFF059669) else ColorPrimaryPurple
    val marksText = when {
        !marks.isNullOrBlank() && !maxMarks.isNullOrBlank() -> "$marks / $maxMarks"
        !marks.isNullOrBlank() -> marks
        else -> "Published"
    }
    val percentageText = percentage?.trim()?.takeIf { it.isNotEmpty() }?.let {
        if (it.endsWith("%")) it else "$it%"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = resultColor.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, resultColor.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = resultColor.copy(alpha = 0.14f)) {
                Icon(
                    imageVector = if (qualified) Icons.Default.CheckCircle else Icons.Default.Quiz,
                    contentDescription = null,
                    tint = resultColor,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Pre-Screen Exam", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                Text(
                    if (qualified) "Qualified" else "Result published",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = resultColor
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(marksText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextTitles)
                Text(
                    listOfNotNull(
                        percentageText,
                        grade?.takeIf { it.isNotBlank() }?.let { "Grade $it" }
                    ).joinToString(" • ").ifBlank { "Evaluated" },
                    fontSize = 10.5.sp,
                    color = ColorTextSubtext
                )
            }
        }
    }
}

@Composable
private fun EmptyGradesMessage(cohortAssigned: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (cohortAssigned) Icons.Default.LockOpen else Icons.Default.Lock,
                null,
                tint = ColorTextSubtext,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (cohortAssigned) "Module 1 test will appear after the module is completed."
                else "Module tests unlock after your cohort is assigned.",
                fontSize = 11.5.sp,
                color = ColorTextSubtext
            )
        }
    }
}

@Composable
private fun ModuleGradeRow(grade: ModuleGrade, compact: Boolean = false) {
    val resultColor = when {
        grade.marks == null -> ColorTextSubtext
        grade.passed -> Color(0xFF059669)
        else -> Color(0xFFDC2626)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(if (compact) 30.dp else 36.dp),
            shape = CircleShape,
            color = resultColor.copy(alpha = 0.10f)
        ) {
            Icon(
                imageVector = when {
                    grade.marks != null && grade.passed -> Icons.Default.CheckCircle
                    grade.marks != null -> Icons.Default.Error
                    grade.unlocked -> Icons.Default.LockOpen
                    else -> Icons.Default.Lock
                },
                contentDescription = null,
                tint = resultColor,
                modifier = Modifier.padding(if (compact) 7.dp else 8.dp)
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                grade.title.ifBlank { "Module ${grade.moduleNumber} Test" },
                fontSize = if (compact) 12.sp else 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorTextTitles,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(
                    when {
                        grade.marks != null && grade.passed -> "Passed • next module unlocked"
                        grade.marks != null -> "Pass this test to unlock the next module"
                        grade.unlocked -> "Ready to take"
                        else -> "Locked"
                    },
                    fontSize = 10.5.sp,
                    color = ColorTextSubtext
                )
            }
        }
        Text(
            grade.marks?.let { "$it/${grade.maxMarks}" } ?: if (grade.unlocked) "READY" else "LOCKED",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = resultColor
        )
    }
}

// =========================================================================
// SPACIOUS BADGELESS CENTER-ALIGNED GRID CARD
// =========================================================================
@Composable
fun SpaciousBadgelessGridCard(tile: CleanPortalTile) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clickable { tile.onClickAction?.invoke() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
        border = BorderStroke(
            width = if (tile.isSelected) 1.8.dp else 1.dp,
            color = if (tile.isSelected) ColorActiveBorder else ColorBorderHairline
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(tile.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = tile.title,
                    tint = tile.iconTint,
                    modifier = Modifier.size(25.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = tile.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ColorTextTitles,
                textAlign = TextAlign.Center,
                letterSpacing = 0.1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tile.subtitle,
                fontWeight = FontWeight.Medium,
                fontSize = 10.5.sp,
                color = ColorTextSubtext,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileIncompleteBanner(
    onNavigateToProfile: () -> Unit
) {
    val semanticColors = sureSemanticColors()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = semanticColors.warningContainer,
        border = BorderStroke(1.dp, semanticColors.warning.copy(alpha = 0.55f)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToProfile() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(semanticColors.warningContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PriorityHigh,
                    contentDescription = null,
                    tint = semanticColors.warning,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Student Profile Incomplete",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = semanticColors.onWarningContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Fill in your college, degree, and contact details to unlock cohort allocation.",
                    fontSize = 11.sp,
                    color = semanticColors.onWarningContainer,
                    lineHeight = 15.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onNavigateToProfile,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Complete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
