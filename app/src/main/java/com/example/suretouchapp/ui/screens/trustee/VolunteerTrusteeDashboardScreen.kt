package com.example.suretouchapp.ui.screens.trustee

import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import com.example.suretouchapp.ui.components.SureTrustLogo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.suretouchapp.R
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AnnouncementDto
import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.model.NotificationDto
import com.example.suretouchapp.data.model.VolunteerProfileDto
import com.example.suretouchapp.data.model.VolunteerTaskDto
import com.example.suretouchapp.data.repository.VolunteerRepository
import com.example.suretouchapp.data.repository.isCancelledSession
import com.example.suretouchapp.data.repository.isCompletedSession
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.BackendSyncedDashboard
import com.example.suretouchapp.ui.components.StudentProfileImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private val Purple @Composable get() = MaterialTheme.colorScheme.primary
private val DeepPurple = Color(0xFF46138F)
private val Ink @Composable get() = MaterialTheme.colorScheme.onSurface
private val Slate @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Line @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val Page @Composable get() = MaterialTheme.colorScheme.background

private data class Shortcut(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color, val bg: Color, val onClick: () -> Unit)

private data class VolunteerDashboardSummary(
    val profileName: String = "",
    val profilePhoto: String? = null,
    val cohortCount: Int = 0,
    val upcomingSessions: List<AttendanceDto> = emptyList(),
    val openTasks: Int = 0,
    val announcementCount: Int = 0,
    val unreadNotifications: Int = 0
)

private data class DashboardApiPayload(
    val profile: VolunteerProfileDto,
    val attendance: List<AttendanceDto>,
    val tasks: List<VolunteerTaskDto>,
    val announcements: List<AnnouncementDto>,
    val notifications: List<NotificationDto>
)

private fun <T> Response<T>.requiredBody(label: String): T {
    if (!isSuccessful) throw IOException("$label request failed (${code()})")
    return body() ?: throw IOException("$label returned an empty response")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerTrusteeDashboardScreen(
    tokenManager: TokenManager,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToAttendance: () -> Unit = {},
    onNavigateToProgrammes: () -> Unit = {},
    onNavigateToSchedule: () -> Unit = {},
    onNavigateToPeople: () -> Unit = {},
    onNavigateToTasks: () -> Unit = {},
    onNavigateToImpact: () -> Unit = {},
    onNavigateToInterviews: () -> Unit = {},
    onNavigateToActivities: () -> Unit = {},
    onNavigateToMentors: () -> Unit = {},
    onNavigateToAnnouncements: () -> Unit = {},
    onNavigateToSupport: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val volunteerRepository = remember(tokenManager) { VolunteerRepository(tokenManager) }
    var summary by remember { mutableStateOf(VolunteerDashboardSummary()) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var showRecurringScheduleDialog by remember { mutableStateOf(false) }
    var showGitHubProvisioningDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = drawerState.isOpen || showRecurringScheduleDialog || showGitHubProvisioningDialog) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            showRecurringScheduleDialog -> showRecurringScheduleDialog = false
            showGitHubProvisioningDialog -> showGitHubProvisioningDialog = false
        }
    }
    val context = LocalContext.current

    suspend fun loadDashboard() {
        var loadSucceeded = false
        isLoading = true
        connectionError = null
        try {
            val api = ApiClient.getService(tokenManager)
            val payload = coroutineScope {
                val profile = async { volunteerRepository.loadProfile() }
                val attendance = async { api.getAttendance().requiredBody("Attendance").results }
                val tasks = async { api.getVolunteerTasks().requiredBody("Volunteer tasks").results }
                val announcements = async { api.getAnnouncements().requiredBody("Announcements").results }
                val notifications = async { api.getNotifications().requiredBody("Notifications").results }
                DashboardApiPayload(
                    profile = profile.await(),
                    attendance = attendance.await(),
                    tasks = tasks.await(),
                    announcements = announcements.await(),
                    notifications = notifications.await()
                )
            }
            val cohortIds = payload.profile.assignedCohorts.map { it.id }.filter(String::isNotBlank).toSet()
            val today = LocalDate.now().toString()
            fun isSessionUpcoming(session: AttendanceDto): Boolean {
                val isCompleted = session.isCompletedSession() || session.isCancelledSession()
                val isPastDate = session.date.take(10) < today
                return !isCompleted && !isPastDate
            }
            val upcomingSessions = payload.attendance
                .filter { it.cohort in cohortIds && isSessionUpcoming(it) }
                .sortedWith(compareBy<AttendanceDto> { it.date }.thenBy { it.startTime })
            val activeTaskStatuses = setOf("PENDING", "IN_PROGRESS", "OPEN", "ASSIGNED")
            val openTasks = payload.tasks.count { it.status.uppercase() in activeTaskStatuses }
            summary = VolunteerDashboardSummary(
                profileName = payload.profile.fullName,
                profilePhoto = payload.profile.profilePhoto,
                cohortCount = payload.profile.assignedCohorts.size,
                upcomingSessions = upcomingSessions,
                openTasks = openTasks,
                announcementCount = payload.announcements.count { it.isActive },
                unreadNotifications = payload.notifications.count { !it.isRead }
            )
            SureProEdNotificationManager.syncUnread(context, payload.notifications)
            SureProEdNotificationManager.syncAnnouncements(context, payload.announcements)
            isConnected = true
            isOffline = false
            connectionError = null
            errorTitle = null
            loadSucceeded = true
        } catch (e: Exception) {
            val errorInfo = NetworkUtils.getNetworkErrorInfo(context, e)
            if (!hasLoadedOnce) {
                isConnected = false
                isOffline = errorInfo.isOffline
                errorTitle = errorInfo.title
                connectionError = errorInfo.message
            }
        } finally {
            if (loadSucceeded) hasLoadedOnce = true
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadDashboard() }

    if (showRecurringScheduleDialog) {
        AddRecurringScheduleDialog(
            tokenManager = tokenManager,
            onDismiss = { showRecurringScheduleDialog = false },
            onSaved = { scope.launch { loadDashboard() } }
        )
    }

    if (showGitHubProvisioningDialog) {
        GitHubRepoProvisioningDialog(
            tokenManager = tokenManager,
            onDismiss = { showGitHubProvisioningDialog = false },
            onUpdated = { scope.launch { loadDashboard() } }
        )
    }

    BackendConnectionGate(
        isLoading = isLoading && !hasLoadedOnce,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Volunteer Portal...",
        onRetry = { scope.launch { loadDashboard() } },
        onLogout = onLogout
    ) {
        ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            TrusteeDrawer(
                onClose = { scope.launch { drawerState.close() } },
                onPeople = { scope.launch { drawerState.close() }; onNavigateToPeople() },
                onMentors = { scope.launch { drawerState.close() }; onNavigateToMentors() },
                onProgrammes = { scope.launch { drawerState.close() }; onNavigateToProgrammes() },
                onAttendance = { scope.launch { drawerState.close() }; onNavigateToAttendance() },
                onAnnouncements = { scope.launch { drawerState.close() }; onNavigateToAnnouncements() },
                onMeetings = { scope.launch { drawerState.close() }; onNavigateToSchedule() },
                onRecurringSchedule = { scope.launch { drawerState.close() }; showRecurringScheduleDialog = true },
                onGithubRepos = { scope.launch { drawerState.close() }; showGitHubProvisioningDialog = true },
                onImpact = { scope.launch { drawerState.close() }; onNavigateToImpact() },
                onInterviews = { scope.launch { drawerState.close() }; onNavigateToInterviews() },
                onActivities = { scope.launch { drawerState.close() }; onNavigateToActivities() },
                onTasks = { scope.launch { drawerState.close() }; onNavigateToTasks() },
                onSupport = { scope.launch { drawerState.close() }; onNavigateToSupport() },
                onProfile = { scope.launch { drawerState.close() }; onNavigateToProfile() },
                onLogout = { scope.launch { drawerState.close() }; onLogout() }
            )
        }
    ) {
    Scaffold(
        containerColor = Page,
        bottomBar = { TrusteeBottomBar(onNavigateToPeople, onNavigateToImpact, onNavigateToTasks, onNavigateToProfile) }
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { scope.launch { loadDashboard() } },
            modifier = Modifier.fillMaxSize().padding(inner),
            indicator = {}
        ) {
            BackendSyncedDashboard(isLoading = isLoading) {
                    Box(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.sure_trust_official_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(240.dp)
                            .align(Alignment.Center)
                            .graphicsLayer { alpha = 0.05f }
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        item {
                            TopBrandBar(
                                onMenu = { scope.launch { drawerState.open() } },
                                onNotifications = onNavigateToNotifications,
                                onProfile = onNavigateToProfile,
                                unreadCount = summary.unreadNotifications,
                                profileName = summary.profileName.ifBlank { tokenManager.getUserName() },
                                profilePhoto = summary.profilePhoto
                            )
                        }
                        item {
                            DashboardHeading(
                                profileName = summary.profileName.ifBlank { tokenManager.getUserName() },
                                onProgrammes = onNavigateToProgrammes
                            )
                        }
                        item { OperationsCard(summary, isLoading, onNavigateToProgrammes) }
                        item { VolunteerScheduleCard(summary.upcomingSessions, onAttendance = onNavigateToAttendance, onRecurringSchedule = { showRecurringScheduleDialog = true }) }
                        item {
                            QuickAccess(
                                onAttendance = onNavigateToAttendance,
                                onProgrammes = onNavigateToProgrammes,
                                onVolunteers = onNavigateToPeople,
                                onMentors = onNavigateToMentors,
                                onCohorts = onNavigateToProgrammes,
                                onAnnouncements = onNavigateToAnnouncements,
                                onMeetings = onNavigateToSchedule,
                                onRecurringSchedule = { showRecurringScheduleDialog = true },
                                onGithubRepos = { showGitHubProvisioningDialog = true },
                                onImpact = onNavigateToImpact,
                                onInterviews = onNavigateToInterviews,
                                onActivities = onNavigateToActivities,
                                onSupport = onNavigateToSupport
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
private fun TrusteeDrawer(
    onClose: () -> Unit,
    onPeople: () -> Unit,
    onMentors: () -> Unit,
    onProgrammes: () -> Unit,
    onAttendance: () -> Unit,
    onAnnouncements: () -> Unit,
    onMeetings: () -> Unit,
    onRecurringSchedule: () -> Unit,
    onGithubRepos: () -> Unit,
    onImpact: () -> Unit,
    onInterviews: () -> Unit,
    onActivities: () -> Unit,
    onTasks: () -> Unit,
    onSupport: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val items = listOf(
        Triple("Volunteers", Icons.Default.Groups, onPeople),
        Triple("Mentors", Icons.Default.School, onMentors),
        Triple("Programmes", Icons.AutoMirrored.Filled.MenuBook, onProgrammes),
        Triple("Attendance", Icons.Default.EventAvailable, onAttendance),
        Triple("Training Schedule", Icons.Default.EventRepeat, onRecurringSchedule),
        Triple("GitHub Repos", Icons.Default.Terminal, onGithubRepos),
        Triple("Updates", Icons.Default.Campaign, onAnnouncements),
        Triple("Meetings", Icons.AutoMirrored.Filled.EventNote, onMeetings),
        Triple("Impact Reports", Icons.Default.BarChart, onImpact),
        Triple("Candidate Interviews", Icons.Default.HowToReg, onInterviews),
        Triple("Community Activities", Icons.Default.VolunteerActivism, onActivities),
        Triple("Tasks", Icons.AutoMirrored.Filled.Assignment, onTasks),
        Triple("Request Form", Icons.Default.SupportAgent, onSupport),
        Triple("Profile", Icons.Default.Person, onProfile)
    )
    ModalDrawerSheet(modifier = Modifier.fillMaxHeight().width(310.dp), drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF7027E5), DeepPurple))).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(painterResource(R.drawable.sure_trust_official_logo), null, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Volunteer Portal", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                    Text("Assigned cohort assistance", color = Color.White.copy(.82f), fontSize = 12.sp)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                NavigationDrawerItem(
                    label = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                    selected = true,
                    onClick = onClose,
                    icon = { Icon(Icons.Default.Home, null) },
                    colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                items.forEach { (label, icon, action) ->
                    NavigationDrawerItem(
                        label = { Text(label, fontWeight = FontWeight.Medium) },
                        selected = false,
                        onClick = { onClose(); action() },
                        icon = { Icon(icon, null) },
                        colors = NavigationDrawerItemDefaults.colors(unselectedIconColor = Slate, unselectedTextColor = Ink),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
            HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onClose()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out", tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("LOGOUT", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TopBrandBar(
    onMenu: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    unreadCount: Int,
    profileName: String,
    profilePhoto: String?
) {
    Row(
        Modifier.fillMaxWidth().height(74.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Menu, "Menu", tint = Purple, modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onMenu).padding(4.dp))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.Image(
            painterResource(R.drawable.sure_trust_official_logo), "SURE ProEd",
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(7.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("SURE ProEd", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text("Volunteer Portal", color = Slate, fontSize = 11.sp)
        }
        Box(Modifier.size(42.dp).clickable(onClick = onNotifications), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Notifications, "Notifications", tint = Ink, modifier = Modifier.size(28.dp))
            if (unreadCount > 0) {
                Box(Modifier.align(Alignment.TopEnd).offset((-2).dp, 2.dp).size(16.dp).background(Purple, CircleShape), contentAlignment = Alignment.Center) {
                    Text(unreadCount.coerceAtMost(9).toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onProfile), contentAlignment = Alignment.Center) {
            StudentProfileImage(
                photo = profilePhoto,
                displayName = profileName,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 20
            )
            Box(Modifier.align(Alignment.BottomEnd).size(10.dp).background(Color(0xFF11B85B), CircleShape).border(2.dp, Color.White, CircleShape))
        }
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun DashboardHeading(profileName: String, onProgrammes: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = profileName.substringBefore(" ").takeIf(String::isNotBlank)?.let { "Welcome, $it" }
                    ?: "Volunteer Dashboard",
                color = Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, tint = Purple, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date()).uppercase(), color = Purple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Surface(modifier = Modifier.clickable(onClick = onProgrammes), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.padding(horizontal = 9.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp)); Text("All Programmes", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Spacer(Modifier.width(5.dp)); Icon(Icons.Default.ExpandCircleDown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun OperationsCard(summary: VolunteerDashboardSummary, isLoading: Boolean, onAssignedCohorts: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().shadow(7.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF7027E5), DeepPurple)))) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 8.dp)
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VolunteerActivism, null, tint = Purple, modifier = Modifier.size(31.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Volunteer overview", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("Here’s your snapshot for today.", color = Color.White.copy(.9f), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Metric(if (isLoading) "-" else summary.upcomingSessions.size.toString(), "Upcoming\nsessions", Icons.Default.CalendarMonth, Purple, Color(0xFFF4EDFF), Modifier.weight(1f))
                    DividerBar()
                    Metric(if (isLoading) "-" else summary.cohortCount.toString(), "Assigned\ncohorts", Icons.Default.Groups, Color(0xFFE87500), Color(0xFFFFF4E5), Modifier.weight(1f))
                    DividerBar()
                    Metric(if (isLoading) "-" else summary.openTasks.toString(), "Open\ntasks", Icons.AutoMirrored.Filled.Assignment, Color(0xFF079447), Color(0xFFEAFBF1), Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            Surface(Modifier.fillMaxWidth().clickable(onClick = onAssignedCohorts), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(horizontal = 17.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)); Spacer(Modifier.weight(1f))
                        Text("View Assigned Cohorts", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VolunteerScheduleCard(
    sessions: List<AttendanceDto>,
    onAttendance: () -> Unit,
    onRecurringSchedule: () -> Unit
) {
    val nextSession = sessions.firstOrNull()
    val todayDateStr = remember {
        SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(Date()).uppercase(Locale.US)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SureTrustLogo(size = 34.dp, showSubtext = false)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Today’s Timetable", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = Purple, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = nextSession?.date?.takeIf(String::isNotBlank) ?: todayDateStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable(onClick = onRecurringSchedule)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = Purple, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Schedule", color = Purple, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = onAttendance, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("View all", color = Purple, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Hero Timetable Card with SURE Trust Logo Watermark
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clickable(onClick = onAttendance),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF7027E5), DeepPurple)
                        )
                    )
            ) {
                // Official SURE Trust Logo Watermark
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.sure_trust_official_logo),
                    contentDescription = "SURE Trust Official Logo Watermark",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(160.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 22.dp)
                        .graphicsLayer {
                            alpha = 0.18f
                            scaleX = 1.35f
                            scaleY = 1.35f
                        }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Time Rail
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(58.dp)
                        ) {
                            Text(
                                text = nextSession?.startTime?.take(5) ?: "--:--",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Box(contentAlignment = Alignment.TopCenter) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .width(2.dp)
                                        .height(28.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.2f))
                                            )
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                            Text(
                                text = nextSession?.endTime?.take(5) ?: "--:--",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "IST",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF86EFAC)
                            )
                        }

                        Spacer(Modifier.width(14.dp))
                        Box(Modifier.width(1.dp).height(80.dp).background(Color.White.copy(alpha = 0.25f)))
                        Spacer(Modifier.width(14.dp))

                        // Middle Session Info
                        Column(Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = nextSession?.cohortCode?.takeIf(String::isNotBlank) ?: "Assigned Cohort",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = nextSession?.sessionTitle ?: "Assigned Class Session",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = if (sessions.isEmpty()) "No active sessions today" else "${sessions.size} sessions in assigned schedule",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Bottom Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.22f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (sessions.isNotEmpty()) Color(0xFF4ADE80) else Color(0xFFFBBF24))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (sessions.isNotEmpty()) "LIVE CLASS AVAILABLE" else "TIMETABLE UP TO DATE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Open Timetable",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DividerBar() = Box(Modifier.width(1.dp).height(54.dp).background(Color.White.copy(.25f)))

@Composable
private fun Metric(value: String, label: String, icon: ImageVector, tint: Color, bg: Color, modifier: Modifier) {
    Row(modifier.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(bg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp)) }
        Spacer(Modifier.width(7.dp)); Column { Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text(label, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun QuickAccess(
    onAttendance: () -> Unit,
    onProgrammes: () -> Unit,
    onVolunteers: () -> Unit,
    onMentors: () -> Unit,
    onCohorts: () -> Unit,
    onAnnouncements: () -> Unit,
    onMeetings: () -> Unit,
    onRecurringSchedule: () -> Unit,
    onGithubRepos: () -> Unit,
    onImpact: () -> Unit,
    onInterviews: () -> Unit,
    onActivities: () -> Unit,
    onSupport: () -> Unit
) {
    val shortcuts = listOf(
        Shortcut("Volunteers", "View & manage", Icons.Default.Groups, Purple, Color(0xFFF1E9FF), onVolunteers),
        Shortcut("Mentors", "Mentor network", Icons.Default.School, Color(0xFFE87500), Color(0xFFFFF1E3), onMentors),
        Shortcut("Cohorts", "Active cohorts", Icons.Default.Groups, Color(0xFF079447), Color(0xFFE7F8ED), onCohorts),
        Shortcut("Attendance", "Track participation", Icons.Default.EventAvailable, Color(0xFF087EBF), Color(0xFFE8F4FC), onAttendance),
        Shortcut("Training Schedule", "Recurring classes", Icons.Default.EventRepeat, Color(0xFF7C3AED), Color(0xFFEDE9FE), onRecurringSchedule),
        Shortcut("GitHub Repos", "Auto / 15-day task", Icons.Default.Terminal, Ink, MaterialTheme.colorScheme.surfaceVariant, onGithubRepos),
        Shortcut("Programmes", "All programmes", Icons.AutoMirrored.Filled.MenuBook, Purple, Color(0xFFF0E8FF), onProgrammes),
        Shortcut("Updates", "Announcements", Icons.Default.Campaign, Color(0xFFD51B76), Color(0xFFFDEAF4), onAnnouncements),
        Shortcut("Meetings", "Schedule & notes", Icons.AutoMirrored.Filled.EventNote, Color(0xFF079491), Color(0xFFE5F8F6), onMeetings),
        Shortcut("Impact", "Reports & outcomes", Icons.Default.BarChart, Purple, Color(0xFFF1E9FF), onImpact),
        Shortcut("Activities", "Plan & verify", Icons.Default.VolunteerActivism, Color(0xFF047857), Color(0xFFE8F8F1), onActivities),
        Shortcut("Request Form", "Request admin help", Icons.Default.SupportAgent, Color(0xFFE53935), Color(0xFFFFECE9), onSupport)
    )
    var showCustomizer by remember { mutableStateOf(false) }
    var visibleTitles by remember { mutableStateOf(shortcuts.map { it.title }.toSet()) }
    val visibleShortcuts = shortcuts.filter { it.title in visibleTitles }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quick Access", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { showCustomizer = true }.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Customize", color = Purple, fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Tune, null, tint = Purple, modifier = Modifier.size(21.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        visibleShortcuts.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    ShortcutCard(item, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (showCustomizer) {
        AlertDialog(
            onDismissRequest = { showCustomizer = false },
            icon = { Icon(Icons.Default.Tune, null, tint = Purple) },
            title = { Text("Customize quick access") },
            text = {
                Column {
                    shortcuts.forEach { item ->
                        Row(Modifier.fillMaxWidth().clickable {
                            visibleTitles = if (item.title in visibleTitles && visibleTitles.size > 3) visibleTitles - item.title else visibleTitles + item.title
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.title in visibleTitles,
                                onCheckedChange = { checked -> visibleTitles = if (checked) visibleTitles + item.title else if (visibleTitles.size > 3) visibleTitles - item.title else visibleTitles },
                                colors = CheckboxDefaults.colors(checkedColor = Purple)
                            )
                            Text(item.title, color = Ink, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showCustomizer = false }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Done") } },
            dismissButton = { TextButton(onClick = { visibleTitles = shortcuts.map { it.title }.toSet() }) { Text("Reset") } }
        )
    }
}

@Composable
private fun ShortcutCard(item: Shortcut, modifier: Modifier) {
    Surface(onClick = item.onClick, modifier = modifier.height(140.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, Line), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(50.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) { Icon(item.icon, item.title, tint = item.tint, modifier = Modifier.size(27.dp)) }
            Spacer(Modifier.height(9.dp))
            Text(item.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(item.subtitle, color = Slate, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrusteeBottomBar(onPeople: () -> Unit, onImpact: () -> Unit, onTasks: () -> Unit, onProfile: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(96.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(84.dp).align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(topStart = 27.dp, topEnd = 27.dp)
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
                NavItem("Home", Icons.Default.Home, true, {})
                NavItem("People", Icons.Default.Groups, false, onPeople)
                Box(Modifier.width(66.dp).fillMaxHeight().clickable(onClick = onImpact))
                NavItem("Tasks", Icons.AutoMirrored.Filled.Assignment, false, onTasks)
                NavItem("Profile", Icons.Default.Person, false, onProfile)
            }
        }
        Column(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-14).dp).zIndex(2f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(74.dp).shadow(9.dp, CircleShape).background(MaterialTheme.colorScheme.surface, CircleShape).padding(5.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), DeepPurple)), CircleShape)
                    .clickable(onClick = onImpact),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VolunteerActivism, "Impact", tint = Color.White, modifier = Modifier.size(43.dp))
            }
            Text("Impact", color = Slate, fontSize = 11.sp, modifier = Modifier.offset(y = (-2).dp))
        }
    }
}

@Composable private fun NavItem(label: String, icon: ImageVector, selected: Boolean, click: () -> Unit) {
    Column(Modifier.width(58.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable(onClick = click).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else Slate, modifier = Modifier.size(25.dp)); Text(label, color = if (selected) MaterialTheme.colorScheme.primary else Slate, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 10.sp) }
}
