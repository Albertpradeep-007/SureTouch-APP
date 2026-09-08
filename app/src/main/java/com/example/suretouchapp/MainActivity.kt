package com.example.suretouchapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AppVersionInfoDto
import com.example.suretouchapp.data.model.LinkedInCallbackRequest
import com.example.suretouchapp.data.ota.AppUpdateManager
import com.example.suretouchapp.data.ota.UpdateState
import com.example.suretouchapp.ui.components.AppUpdateDialog
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.navigation.Screen
import com.example.suretouchapp.ui.screens.assignments.AssignmentsScreen
import com.example.suretouchapp.ui.screens.attendance.AttendanceScreen
import com.example.suretouchapp.ui.screens.auth.AuthScreen
import com.example.suretouchapp.ui.screens.courses.CoursesScreen
import com.example.suretouchapp.ui.screens.courses.EnrolledCourseScreen
import com.example.suretouchapp.ui.screens.dashboard.StudentDashboardScreen
import com.example.suretouchapp.ui.screens.feedback.FeedbackScreen
import com.example.suretouchapp.ui.screens.support.SupportRequestsScreen
import com.example.suretouchapp.ui.screens.lifeskills.LifeSkillsScreen
import com.example.suretouchapp.ui.screens.mentor.MentorDashboardScreen
import com.example.suretouchapp.ui.screens.mentor.MentorMessagesScreen
import com.example.suretouchapp.ui.screens.trustee.VolunteerTrusteeDashboardScreen
import com.example.suretouchapp.ui.screens.trustee.TrusteePeopleScreen
import com.example.suretouchapp.ui.screens.trustee.VolunteerImpactScreen
import com.example.suretouchapp.ui.screens.trustee.VolunteerInterviewsScreen
import com.example.suretouchapp.ui.screens.trustee.VolunteerProgrammesScreen
import com.example.suretouchapp.ui.screens.trustee.VolunteerScheduleScreen
import com.example.suretouchapp.ui.screens.trustee.VolunteerTasksScreen
import com.example.suretouchapp.ui.screens.notifications.NotificationSyncWorker
import com.example.suretouchapp.ui.screens.notifications.NotificationsScreen
import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import com.example.suretouchapp.ui.screens.profile.ProfileScreen
import com.example.suretouchapp.ui.screens.softskills.SoftSkillsScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.suretouchapp.ui.screens.splash.SureTrustSplashScreen
import com.example.suretouchapp.ui.screens.timetable.TimetableScreen
import com.example.suretouchapp.ui.theme.SureTouchAPPTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var currentDeepLinkData by mutableStateOf<Uri?>(null)
    private var liveClassNavigationRequest by mutableLongStateOf(0L)
    private var notificationNavigationRequest by mutableLongStateOf(0L)
    private var noticesNavigationRequest by mutableLongStateOf(0L)
    private var assignmentsNavigationRequest by mutableLongStateOf(0L)
    private var otaPreviewRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentDeepLinkData = intent?.data
        if (intent?.getBooleanExtra("open_live_class", false) == true) liveClassNavigationRequest++
        otaPreviewRequested = BuildConfig.DEBUG && intent?.getBooleanExtra("preview_ota_update", false) == true
        if (intent?.getBooleanExtra("open_notifications", false) == true) {
            notificationNavigationRequest++
        }
        if (intent?.getBooleanExtra("open_notices", false) == true) {
            noticesNavigationRequest++
        }
        if (intent?.getBooleanExtra("open_assignments", false) == true) {
            assignmentsNavigationRequest++
        }
        SureProEdNotificationManager.createChannels(this)
        NotificationSyncWorker.schedulePeriodicSync(this)
        NotificationSyncWorker.triggerImmediateSync(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        enableEdgeToEdge()
        setContent {
            SureTouchAPPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplashScreen by remember { mutableStateOf(true) }
                    AnimatedContent(
                        targetState = showSplashScreen,
                        transitionSpec = {
                            fadeIn(tween(350)) togetherWith fadeOut(tween(250))
                        },
                        label = "splash_transition"
                    ) { isSplash ->
                        if (isSplash) {
                            SureTrustSplashScreen(onTimeout = { showSplashScreen = false })
                        } else {
                            AppNavigation(
                                deepLinkUri = currentDeepLinkData,
                                notificationRequestId = notificationNavigationRequest,
                                liveClassRequestId = liveClassNavigationRequest,
                                noticesRequestId = noticesNavigationRequest,
                                assignmentsRequestId = assignmentsNavigationRequest,
                                otaPreviewRequested = otaPreviewRequested
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        currentDeepLinkData = intent.data
        if (intent.getBooleanExtra("open_live_class", false)) liveClassNavigationRequest++
        otaPreviewRequested = BuildConfig.DEBUG && intent.getBooleanExtra("preview_ota_update", false)
        if (intent.getBooleanExtra("open_notifications", false)) {
            notificationNavigationRequest++
        }
        if (intent.getBooleanExtra("open_notices", false)) {
            noticesNavigationRequest++
        }
        if (intent.getBooleanExtra("open_assignments", false)) {
            assignmentsNavigationRequest++
        }
    }
}

@Composable
fun AppNavigation(
    deepLinkUri: Uri? = null,
    notificationRequestId: Long = 0L,
    liveClassRequestId: Long = 0L,
    noticesRequestId: Long = 0L,
    assignmentsRequestId: Long = 0L,
    otaPreviewRequested: Boolean = false
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val accountSession = remember { mutableStateOf(tokenManager.getSessionId()) }
    DisposableEffect(tokenManager) {
        val prefs = context.getSharedPreferences("sure_proed_prefs", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, name ->
            if (name == "session_id" || name == null) accountSession.value = tokenManager.getSessionId()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val updateState by AppUpdateManager.updateState.collectAsState()
    var otaPreviewDismissed by remember(otaPreviewRequested) { mutableStateOf(false) }

    LaunchedEffect(otaPreviewRequested) {
        if (!otaPreviewRequested) AppUpdateManager.checkForUpdates()
    }

    val displayedUpdateState = if (otaPreviewRequested && BuildConfig.DEBUG && !otaPreviewDismissed) {
        UpdateState.UpdateAvailable(
            AppVersionInfoDto(
                versionCode = AppUpdateManager.currentVersionCode + 1,
                versionName = "1.2.0",
                downloadUrl = "",
                releaseNotes = "• Unified backend-synced dashboard loading\n• Smoother OTA update experience\n• Stability and performance improvements",
                isMandatory = false,
                minSupportedVersionCode = AppUpdateManager.currentVersionCode,
                fileSizeBytes = 24_000_000L,
                publishedAt = "Preview"
            )
        )
    } else if (otaPreviewRequested && BuildConfig.DEBUG) {
        UpdateState.Idle
    } else {
        updateState
    }

    // Complete the authenticated OAuth exchange after the browser returns to the app.
    LaunchedEffect(deepLinkUri) {
        deepLinkUri?.let { uri ->
            val scheme = uri.scheme.orEmpty().lowercase()
            val path = uri.path.orEmpty().lowercase()
            val host = uri.host.orEmpty().lowercase()
            val isOAuthCallback = scheme == "suretrust" ||
                    path.contains("linkedin") ||
                    path.contains("github") ||
                    path.contains("oauth") ||
                    host.contains("oauth") ||
                    uri.getQueryParameter("code") != null

            if (isOAuthCallback) {
                val code = uri.getQueryParameter("code")
                val accessParam = uri.getQueryParameter("access") ?: uri.getQueryParameter("access_token") ?: uri.getQueryParameter("token")
                val refreshParam = uri.getQueryParameter("refresh") ?: uri.getQueryParameter("refresh_token") ?: ""
                val emailParam = uri.getQueryParameter("email") ?: ""
                val nameParam = uri.getQueryParameter("name") ?: uri.getQueryParameter("first_name") ?: ""
                val roleParam = uri.getQueryParameter("role") ?: "STUDENT"

                val photoParam = uri.getQueryParameter("profile_photo")
                    ?: uri.getQueryParameter("profile_picture")
                    ?: uri.getQueryParameter("photo")
                    ?: uri.getQueryParameter("avatar")
                    ?: uri.getQueryParameter("picture")
                    ?: uri.getQueryParameter("photo_url")
                    ?: uri.getQueryParameter("linkedin_profile_photo_url")

                scope.launch {
                    if (!accessParam.isNullOrBlank()) {
                        tokenManager.saveToken(accessParam, refreshParam)
                        val displayName = nameParam.ifBlank { emailParam.substringBefore("@").ifBlank { "Student" } }
                        tokenManager.saveUserInfo(displayName, emailParam)
                        tokenManager.saveUserRole(roleParam)
                    }
                    if (!photoParam.isNullOrBlank()) {
                        tokenManager.saveProfilePhotoUrl(photoParam)
                    }

                    // GitHub's backend callback completes account linking before it
                    // redirects here with status=success. LinkedIn still returns a code
                    // that the app exchanges with the backend.
                    val isGitHub = path.contains("github") || host == "github-oauth"
                    if (!code.isNullOrBlank() && !isGitHub) {
                        runCatching {
                            val api = ApiClient.getService(tokenManager)
                            val res = api.connectLinkedInCallback(LinkedInCallbackRequest(code = code))
                            if (res.isSuccessful && res.body() != null) {
                                val body = res.body()!!
                                if (!body.access.isNullOrBlank()) {
                                    tokenManager.saveToken(body.access, body.refresh ?: "")
                                    val userName = listOfNotNull<String>(body.user?.firstName, body.user?.lastName)
                                        .joinToString(" ")
                                        .ifBlank { body.user?.email?.substringBefore("@") ?: "Student" }
                                    tokenManager.saveUserInfo(userName, body.user?.email ?: "")
                                    tokenManager.saveUserRole(body.user?.role ?: "STUDENT")
                                }
                                val photoUrl = body.profilePhoto
                                    ?: body.profilePicture
                                    ?: body.photoUrl
                                    ?: body.avatar
                                    ?: body.picture
                                    ?: body.linkedinProfilePhotoUrl
                                    ?: body.user?.effectiveProfilePhoto
                                if (!photoUrl.isNullOrBlank()) {
                                    tokenManager.saveProfilePhotoUrl(photoUrl)
                                }
                                if (!body.linkedinUrl.isNullOrBlank()) {
                                    tokenManager.saveStudentProfileDetails(linkedinUrl = body.linkedinUrl)
                                }
                            }
                        }
                    }

                    // Sync user profile details and student profile from backend if logged in
                    if (tokenManager.isLoggedIn()) {
                        runCatching {
                            com.example.suretouchapp.data.repository.AccountSessionRepository(tokenManager)
                                .verifyCurrentAccount()
                            if (tokenManager.getUserRole() == "STUDENT") {
                                com.example.suretouchapp.data.repository.StudentProfileRepository(tokenManager).load()
                            }
                        }
                    }

                    val destination = accountDestination(tokenManager)

                    navController.navigate(destination) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    val role = tokenManager.getUserRole().trim().uppercase()
    val isUnsupportedRole = com.example.suretouchapp.data.repository.accountWorkspace(role) ==
        com.example.suretouchapp.data.repository.AccountWorkspace.UNSUPPORTED
    val startDestination = remember { accountDestination(tokenManager) }

    LaunchedEffect(tokenManager) {
        if (isUnsupportedRole && tokenManager.isLoggedIn()) {
            tokenManager.clear()
            navController.navigate(Screen.Auth.route) {
                popUpTo(0) { inclusive = true }
            }
        }
        TokenManager.sessionExpiredFlow.collect {
            navController.navigate(Screen.Auth.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(notificationRequestId, noticesRequestId, assignmentsRequestId, liveClassRequestId) {
        if (tokenManager.hasVerifiedIdentity()) {
            if (liveClassRequestId > 0) {
                navController.navigate(Screen.LiveClass.route)
            } else if (notificationRequestId > 0) {
                navController.navigate(Screen.Notifications.route)
            } else if (noticesRequestId > 0) {
                navController.navigate(Screen.Notices.route)
            } else if (assignmentsRequestId > 0) {
                navController.navigate(Screen.Assignments.route)
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Auth.route) {
            AuthScreen(
                tokenManager = tokenManager,
                onAuthSuccess = {
                    NotificationSyncWorker.triggerImmediateSync(context)
                    val destination = accountDestination(tokenManager)
                    navController.navigate(destination) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        accountComposable(Screen.Dashboard.route, accountSession) {
            if (tokenManager.getUserRole() != "STUDENT" || !tokenManager.isLoggedIn()) {
                LaunchedEffect(Unit) {
                    navController.navigate(accountDestination(tokenManager)) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
                return@accountComposable
            }
            StudentDashboardScreen(
                tokenManager = tokenManager,
                onNavigateToCourses = { navController.navigate(Screen.Courses.route) },
                onNavigateToEnrolledCourse = { navController.navigate(Screen.EnrolledCourse.route) },
                onNavigateToAssignments = { navController.navigate(Screen.Assignments.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToTimetable = { navController.navigate(Screen.Timetable.route) },
                onNavigateToApplicationTracker = { navController.navigate(Screen.ApplicationTracker.route) },
                onNavigateToCertificates = { navController.navigate(Screen.Certificates.route) },
                onNavigateToLiveClass = { navController.navigate(Screen.LiveClass.route) },
                onNavigateToMentorDesk = { navController.navigate(Screen.MentorDesk.route) },
                onNavigateToNotices = { navController.navigate(Screen.Notices.route) },
                onNavigateToAttendance = { navController.navigate(Screen.Attendance.route) },
                onNavigateToFeedback = { navController.navigate(Screen.Support.route) },
                onNavigateToLifeSkills = { navController.navigate(Screen.LifeSkillsTraining.route) },
                onNavigateToSoftSkills = { navController.navigate(Screen.SoftSkillsTraining.route) },
                onLogout = {
                    tokenManager.clear()
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        accountComposable(Screen.MentorDashboard.route, accountSession) {
            MentorDashboardScreen(
                tokenManager = tokenManager,
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToMessages = { navController.navigate(Screen.MentorMessages.route) },
                onNavigateToCourses = { navController.navigate(Screen.Courses.route) },
                onNavigateToAssignments = { navController.navigate(Screen.Assignments.route) },
                onNavigateToAttendance = { navController.navigate(Screen.Attendance.route) },
                onNavigateToTimetable = { navController.navigate(Screen.Timetable.route) },
                onNavigateToLiveClass = { navController.navigate(Screen.LiveClass.route) },
                onNavigateToNotices = { navController.navigate(Screen.Notices.route) },
                onNavigateToSupport = { navController.navigate(Screen.Support.route) },
                onLogout = {
                    tokenManager.clear()
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        accountComposable(Screen.VolunteerTrusteeDashboard.route, accountSession) {
            VolunteerTrusteeDashboardScreen(
                tokenManager = tokenManager,
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToAttendance = { navController.navigate(Screen.Attendance.route) },
                onNavigateToProgrammes = { navController.navigate(Screen.VolunteerProgrammes.route) },
                onNavigateToSchedule = { navController.navigate(Screen.VolunteerSchedule.route) },
                onNavigateToPeople = { navController.navigate(Screen.TrusteePeople.route) },
                onNavigateToTasks = { navController.navigate(Screen.VolunteerTasks.route) },
                onNavigateToImpact = { navController.navigate(Screen.VolunteerImpact.route) },
                onNavigateToInterviews = { navController.navigate(Screen.VolunteerInterviews.route) },
                onNavigateToActivities = { navController.navigate(Screen.VolunteerImpact.route) },
                onNavigateToMentors = { navController.navigate(Screen.TrusteeMentors.route) },
                onNavigateToAnnouncements = { navController.navigate(Screen.Notices.route) },
                onNavigateToSupport = { navController.navigate(Screen.Support.route) },
                onLogout = {
                    tokenManager.clear()
                    navController.navigate(Screen.Auth.route) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        accountComposable(Screen.MentorMessages.route, accountSession) {
            MentorMessagesScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.TrusteePeople.route, accountSession) {
            TrusteePeopleScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.TrusteeMentors.route, accountSession) {
            TrusteePeopleScreen(
                tokenManager = tokenManager,
                initialFilter = "MENTORS",
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.VolunteerTasks.route, accountSession) {
            VolunteerTasksScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.VolunteerImpact.route, accountSession) {
            VolunteerImpactScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.VolunteerInterviews.route, accountSession) {
            VolunteerInterviewsScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.VolunteerProgrammes.route, accountSession) {
            VolunteerProgrammesScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() },
                onOpenSchedule = { navController.navigate(Screen.VolunteerSchedule.route) }
            )
        }

        accountComposable(Screen.VolunteerSchedule.route, accountSession) {
            VolunteerScheduleScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.Courses.route, accountSession) {
            CoursesScreen(
                tokenManager = tokenManager,
                onBack = {
                    tokenManager.markCourseApplied()
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onApplicationSubmitted = {
                    tokenManager.markCourseApplied()
                    navController.navigate(Screen.ApplicationTracker.route)
                }
            )
        }

        accountComposable(Screen.EnrolledCourse.route, accountSession) {
            EnrolledCourseScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() },
                onBrowseCourses = { navController.navigate(Screen.Courses.route) },
                onViewJourney = { navController.navigate(Screen.ApplicationTracker.route) }
            )
        }

        accountComposable(Screen.Assignments.route, accountSession) {
            AssignmentsScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.Attendance.route, accountSession) {
            AttendanceScreen(
                tokenManager = tokenManager,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.Notifications.route, accountSession) {
            NotificationsScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.Profile.route, accountSession) {
            ProfileScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.Support.route, accountSession) {
            SupportRequestsScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.LifeSkillsTraining.route, accountSession) {
            LifeSkillsScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.SoftSkillsTraining.route, accountSession) {
            SoftSkillsScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.Timetable.route, accountSession) {
            TimetableScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() },
                onNavigateToLiveClass = { navController.navigate(Screen.LiveClass.route) }
            )
        }

        accountComposable(Screen.ApplicationTracker.route, accountSession) {
            com.example.suretouchapp.ui.screens.screening.ApplicationTrackerScreen(
                tokenManager = tokenManager,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onNavigateToTimetable = { navController.navigate(Screen.Timetable.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
            )
        }

        accountComposable(Screen.Certificates.route, accountSession) {
            com.example.suretouchapp.ui.screens.certificates.CertificatesScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.LiveClass.route, accountSession) {
            com.example.suretouchapp.ui.screens.liveclass.LiveClassScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() },
                onNavigateToTimetable = { navController.navigate(Screen.Timetable.route) }
            )
        }

        accountComposable(Screen.MentorDesk.route, accountSession) {
            com.example.suretouchapp.ui.screens.mentor.MentorDeskScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }

        accountComposable(Screen.Notices.route, accountSession) {
            com.example.suretouchapp.ui.screens.notices.NoticesScreen(
                tokenManager = tokenManager,
                onBack = { navController.popBackStack() }
            )
        }
    }

    AppUpdateDialog(
        updateState = displayedUpdateState,
        onDismiss = {
            if (otaPreviewRequested && BuildConfig.DEBUG) {
                otaPreviewDismissed = true
            } else {
                val availableCode = (displayedUpdateState as? UpdateState.UpdateAvailable)?.info?.versionCode
                    ?: (displayedUpdateState as? UpdateState.ReadyToInstall)?.info?.versionCode
                AppUpdateManager.dismissUpdate(availableCode)
            }
        }
    )
}

private fun accountDestination(tokenManager: TokenManager): String {
    if (!tokenManager.hasVerifiedIdentity()) return Screen.Auth.route
    return when (com.example.suretouchapp.data.repository.accountWorkspace(tokenManager.getUserRole())) {
        com.example.suretouchapp.data.repository.AccountWorkspace.STUDENT -> {
            if (tokenManager.needsCourseSelection()) Screen.Courses.route else Screen.Dashboard.route
        }
        com.example.suretouchapp.data.repository.AccountWorkspace.MENTOR -> Screen.MentorDashboard.route
        com.example.suretouchapp.data.repository.AccountWorkspace.VOLUNTEER -> Screen.VolunteerTrusteeDashboard.route
        com.example.suretouchapp.data.repository.AccountWorkspace.UNSUPPORTED -> Screen.Auth.route
    }
}

/** Keep sign-in alive while tokens are saved; discard each private screen's old state. */
private fun androidx.navigation.NavGraphBuilder.accountComposable(
    route: String,
    sessionId: State<String>,
    content: @Composable () -> Unit
) {
    composable(route) { key(sessionId.value) { content() } }
}
