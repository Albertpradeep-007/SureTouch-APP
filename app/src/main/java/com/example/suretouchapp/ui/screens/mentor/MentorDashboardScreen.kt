package com.example.suretouchapp.ui.screens.mentor

import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import com.example.suretouchapp.ui.screens.trustee.generateAutoMeetLink
import com.example.suretouchapp.data.repository.ClassSchedulePolicy
import com.example.suretouchapp.data.repository.isCancelledSession
import com.example.suretouchapp.data.repository.isCompletedSession

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Grading
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.*
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.BackendSyncedDashboard
import com.example.suretouchapp.ui.components.InAppOAuthSheet
import com.example.suretouchapp.ui.components.OAuthProvider
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.components.SureTrustLogo
import com.example.suretouchapp.ui.theme.SureFormDefaults
import com.example.suretouchapp.ui.theme.sureSemanticColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================================
// DESIGN TOKENS
// ============================================================
private val MC_Bg @Composable get() = MaterialTheme.colorScheme.background
private val MC_Surface @Composable get() = MaterialTheme.colorScheme.surface
private val MC_Primary @Composable get() = MaterialTheme.colorScheme.primary
private val MC_PrimaryMid   = Color(0xFF6D28D9)
private val MC_PrimaryLight = Color(0xFF7C3AED)
private val MC_PrimaryEnd   = Color(0xFF4C1D95)
private val MC_Teal         = Color(0xFF0D9488)
private val MC_Amber        = Color(0xFFD97706)
private val MC_Blue         = Color(0xFF0284C7)
private val MC_Red          = Color(0xFFDC2626)
private val MC_Pink         = Color(0xFFDB2777)
private val MC_TextTitle @Composable get() = MaterialTheme.colorScheme.onSurface
private val MC_TextSub @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val MC_Border @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val MC_NavBg @Composable get() = MaterialTheme.colorScheme.surface
private val MC_ActivePill @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val MC_DatePurple   = Color(0xFF6D28D9)
private val MC_LogoDiamond = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width / 2f, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

// ============================================================
// ============================================================
// STUDENT NAME RESOLUTION HELPERS
// ============================================================
private fun resolveStudentName(
    student: StudentProfileDto?,
    users: List<UserDto> = emptyList()
): String {
    if (student == null) return "Student"
    val userObj = student.user ?: users.firstOrNull { it.id == student.userId || it.id == student.id }
    val userFullName = listOfNotNull(
        userObj?.firstName ?: student.userFirstName ?: student.firstName,
        userObj?.lastName ?: student.userLastName ?: student.lastName
    ).filter { it.isNotBlank() }.joinToString(" ").trim()
    if (userFullName.isNotBlank()) return userFullName

    val explicitName = listOfNotNull(
        student.fullName,
        student.name,
        student.studentName,
        student.userName,
        student.userFullName
    ).firstOrNull { it.isNotBlank() }
    if (!explicitName.isNullOrBlank()) return explicitName

    val email = userObj?.email ?: student.userEmail ?: student.email
    if (!email.isNullOrBlank()) {
        val handle = email.substringBefore("@").replace(".", " ").replace("_", " ")
        return handle.split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }
    
    val code = student.studentCode?.takeIf { it.isNotBlank() }
    return if (code != null) "Student $code" else "Student"
}

private fun resolveStudentNameFromId(
    studentId: String?,
    students: List<StudentProfileDto>,
    users: List<UserDto>
): String {
    if (studentId.isNullOrBlank()) return "Student"
    val student = students.firstOrNull { it.id == studentId || it.userId == studentId || it.studentCode.equals(studentId, ignoreCase = true) }
    if (student != null) {
        val name = resolveStudentName(student, users)
        if (name != "Student") return name
    }
    val user = users.firstOrNull { it.id == studentId }
    if (user != null) {
        val fullName = listOfNotNull(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (fullName.isNotBlank()) return fullName
        if (user.email.isNotBlank()) return user.email.substringBefore("@")
    }
    return if (studentId.length > 12) student?.studentCode ?: "Student" else studentId
}

// DATA STATE
// ============================================================
data class MentorSummary(
    val name: String = "Mentor",
    val email: String = "",
    val userId: String? = null,
    val myCohorts: List<CohortDto> = emptyList(),
    val myAssignments: List<AssignmentDto> = emptyList(),
    val submissions: List<SubmissionDto> = emptyList(),
    val pendingSubmissions: List<SubmissionDto> = emptyList(),
    val myAttendance: List<AttendanceDto> = emptyList(),
    val notifications: List<NotificationDto> = emptyList(),
    val myStudents: List<StudentProfileDto> = emptyList(),
    val myCourses: List<CourseDto> = emptyList(),
    val myCompany: CompanyDto? = null,
    val jobReferences: List<JobReferenceDto> = emptyList(),
    val certificates: List<CertificateDto> = emptyList(),
    val prescreeningInterviews: List<PreScreeningInterviewDto> = emptyList(),
    val applications: List<ApplicationDto> = emptyList(),
    val totalStudents: Int = 0,
    val pendingGrading: Int = 0,
    val pendingInterviews: Int = 0,
    val allUsers: List<UserDto> = emptyList()
)

// ============================================================
// MENTOR DASHBOARD ROOT
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentorDashboardScreen(
    tokenManager: TokenManager,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToCourses: () -> Unit = {},
    onNavigateToAssignments: () -> Unit = {},
    onNavigateToAttendance: () -> Unit = {},
    onNavigateToTimetable: () -> Unit = {},
    onNavigateToLiveClass: () -> Unit = {},
    onNavigateToNotices: () -> Unit = {},
    onNavigateToSupport: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var summary by remember { mutableStateOf(MentorSummary(name = tokenManager.getUserName(), email = tokenManager.getUserEmail())) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var isLinkedinActionLoading by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabHistory = remember { mutableStateListOf(0) }
    var selectedCohortId by remember { mutableStateOf<String?>(null) }

    fun switchTab(tab: Int) {
        if (selectedTab != tab) {
            tabHistory.add(tab)
            selectedTab = tab
        }
    }

    BackHandler(enabled = drawerState.isOpen || selectedTab != 0 || tabHistory.size > 1) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (tabHistory.size > 1) {
            tabHistory.removeAt(tabHistory.size - 1)
            selectedTab = tabHistory.lastOrNull() ?: 0
        } else if (selectedTab != 0) {
            selectedTab = 0
            tabHistory.clear()
            tabHistory.add(0)
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    var isLinkedinConnected by remember { mutableStateOf(tokenManager.getLinkedinUrl().isNotBlank()) }
    var showLinkedinPopup by remember { mutableStateOf(false) }
    var isLinkedinLoading by remember { mutableStateOf(false) }
    var oauthProvider by remember { mutableStateOf<OAuthProvider?>(null) }
    var oauthUrl by remember { mutableStateOf<String?>(null) }

    var showCreateSessionDialog by remember { mutableStateOf(false) }
    var reschedulingSession by remember { mutableStateOf<AttendanceDto?>(null) }
    var cancellingSession by remember { mutableStateOf<AttendanceDto?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse_alpha"
    )

    suspend fun loadData() {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val api = ApiClient.getService(tokenManager)
            val usersRes = api.getUsers()
            if (!usersRes.isSuccessful) {
                throw java.io.IOException("Unable to reach backend service (${usersRes.code()})")
            }
            val users = usersRes.body()?.results.orEmpty()
            if (users.isEmpty()) {
                throw java.io.IOException("Unable to retrieve verified mentor data from server.")
            }

            val cohortsRes = api.getCohorts()
            if (!cohortsRes.isSuccessful) {
                throw java.io.IOException("Unable to fetch cohorts (${cohortsRes.code()})")
            }
            val allCohorts = cohortsRes.body()?.results.orEmpty()

            val payload = coroutineScope {
                val assignments = async { api.getAssignments().body()?.results.orEmpty() }
                val submissions = async { api.getSubmissions().body()?.results.orEmpty() }
                val attendance = async { api.getAttendance().body()?.results.orEmpty() }
                val notifications = async { api.getNotifications().body()?.results.orEmpty() }
                val announcements = async { api.getAnnouncements().body()?.results.orEmpty() }
                val students = async { api.getStudents().body()?.results.orEmpty() }
                val courses = async { api.getCourses().body()?.results.orEmpty() }
                val companies = async { api.getCompanies().body()?.results.orEmpty() }
                val jobs = async { api.getJobReferences().body()?.results.orEmpty() }
                val certificates = async { api.getCertificates().body()?.results.orEmpty() }
                val interviews = async { api.getPreScreeningInterviews().body()?.results.orEmpty() }
                val applications = async { api.getMyApplications().body()?.results.orEmpty() }
                listOf(assignments.await(), submissions.await(), attendance.await(), notifications.await(), students.await(), courses.await(), companies.await(), jobs.await(), certificates.await(), interviews.await(), applications.await(), announcements.await())
            }
            @Suppress("UNCHECKED_CAST") val allAssignments = payload[0] as List<AssignmentDto>
            @Suppress("UNCHECKED_CAST") val allSubmissions = payload[1] as List<SubmissionDto>
            @Suppress("UNCHECKED_CAST") val allAttendance = payload[2] as List<AttendanceDto>
            @Suppress("UNCHECKED_CAST") val notifications = payload[3] as List<NotificationDto>
            @Suppress("UNCHECKED_CAST") val allStudents = payload[4] as List<StudentProfileDto>
            @Suppress("UNCHECKED_CAST") val allCourses = payload[5] as List<CourseDto>
            @Suppress("UNCHECKED_CAST") val allCompanies = payload[6] as List<CompanyDto>
            @Suppress("UNCHECKED_CAST") val allJobs = payload[7] as List<JobReferenceDto>
            @Suppress("UNCHECKED_CAST") val allCertificates = payload[8] as List<CertificateDto>
            @Suppress("UNCHECKED_CAST") val allInterviews = payload[9] as List<PreScreeningInterviewDto>
            @Suppress("UNCHECKED_CAST") val allApplications = payload[10] as List<ApplicationDto>
            val myUserId = users.firstOrNull { it.email.equals(tokenManager.getUserEmail(), ignoreCase = true) }?.id
            // Never fall back to every cohort: mentor access is strictly Admin-assignment scoped.
            val myCohorts = if (myUserId != null) allCohorts.filter { myUserId in it.mentors } else emptyList()
            val myCohortIds = myCohorts.map { it.id }.toSet()
            val myAssignments = allAssignments.filter { it.cohort != null && it.cohort in myCohortIds }
            val myAssignmentIds = myAssignments.map { it.id }.toSet()
            val mySubmissions = allSubmissions.filter { it.assignment != null && it.assignment in myAssignmentIds }
            val pendingSubs = mySubmissions.filter { !it.evaluated }
            val myAttendance = allAttendance.filter { it.cohort != null && it.cohort in myCohortIds }
            val cohortCodes = myCohorts.mapNotNull { it.code?.trim()?.lowercase() }.toSet()
            val myStudents = allStudents.filter { student ->
                student.cohortId in myCohortIds ||
                    student.cohortCode?.trim()?.lowercase() in cohortCodes
            }
            val courseIds = myCohorts.mapNotNull { it.course }.toSet()
            val myCourses = allCourses.filter { it.id in courseIds }
            val myCompany = allCompanies.firstOrNull { it.user == myUserId }
            val myJobs = allJobs.filter { it.cohort != null && it.cohort in myCohortIds }
            val myStudentIds = myStudents.flatMap { listOfNotNull(it.id, it.userId) }.toSet()
            val myCertificates = allCertificates.filter { it.student in myStudentIds }
            val myInterviews = allInterviews.filter { interview ->
                interview.interviewer == myUserId || 
                interview.interviewer.equals(tokenManager.getUserEmail(), ignoreCase = true) ||
                interview.interviewer.isNullOrBlank() ||
                interview.application.isNotBlank()
            }
            val pendingInterviewsCount = myInterviews.count { it.score.isNullOrBlank() || it.status == "SCHEDULED" || it.status == "PENDING" }
            summary = MentorSummary(
                name = tokenManager.getUserName(), email = tokenManager.getUserEmail(),
                userId = myUserId,
                myCohorts = myCohorts, myAssignments = myAssignments, submissions = mySubmissions, pendingSubmissions = pendingSubs,
                myAttendance = myAttendance, notifications = notifications, myStudents = myStudents,
                myCourses = myCourses, myCompany = myCompany, jobReferences = myJobs, certificates = myCertificates,
                prescreeningInterviews = myInterviews, applications = allApplications,
                totalStudents = myStudents.size, pendingGrading = pendingSubs.size,
                pendingInterviews = pendingInterviewsCount,
                allUsers = users
            )
            @Suppress("UNCHECKED_CAST") val announcements = payload[11] as List<com.example.suretouchapp.data.model.AnnouncementDto>
            SureProEdNotificationManager.syncUnread(context, notifications)
            SureProEdNotificationManager.syncAnnouncements(context, announcements)
            SureProEdNotificationManager.syncTimetableAndClasses(context, myAttendance)
            if (selectedCohortId !in myCohortIds) selectedCohortId = myCohorts.firstOrNull()?.id
            val mentorProfileRes = runCatching { api.getMentorProfiles() }.getOrNull()
            val mentorProfile = mentorProfileRes?.takeIf { it.isSuccessful }?.body()?.results?.firstOrNull()
            val serverLinkedinConnected = mentorProfile?.isLinkedinConnected == true || !mentorProfile?.linkedinUrl.isNullOrBlank() || tokenManager.getLinkedinUrl().isNotBlank()
            isLinkedinConnected = serverLinkedinConnected
            if (!serverLinkedinConnected) {
                showLinkedinPopup = true
            }
            isConnected = true
            hasLoadedOnce = true
            isOffline = false
            connectionError = null
            errorTitle = null
        } catch (e: Exception) {
            val errorInfo = NetworkUtils.getNetworkErrorInfo(context, e)
            isConnected = false
            isOffline = errorInfo.isOffline
            errorTitle = errorInfo.title
            connectionError = errorInfo.message
            summary = summary.copy(name = tokenManager.getUserName(), email = tokenManager.getUserEmail())
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    val selectedCohort = summary.myCohorts.firstOrNull { it.id == selectedCohortId }
        ?: summary.myCohorts.firstOrNull()
    val selectedCohortCode = selectedCohort?.code?.trim()
    val cohortStudents = remember(summary.myStudents, selectedCohort?.id, selectedCohortCode) {
        if (selectedCohort == null) emptyList() else summary.myStudents.filter { student ->
            student.cohortId == selectedCohort.id ||
                (!selectedCohortCode.isNullOrBlank() &&
                    student.cohortCode?.trim().equals(selectedCohortCode, ignoreCase = true))
        }
    }
    val cohortAssignments = remember(summary.myAssignments, selectedCohort?.id) {
        if (selectedCohort == null) emptyList()
        else summary.myAssignments.filter { it.cohort == selectedCohort.id }
    }
    val cohortAssignmentIds = remember(cohortAssignments) { cohortAssignments.map { it.id }.toSet() }
    val cohortSubmissions = remember(summary.submissions, cohortAssignmentIds) {
        summary.submissions.filter { it.assignment in cohortAssignmentIds }
    }
    val cohortPendingSubmissions = remember(cohortSubmissions) {
        cohortSubmissions.filter { !it.evaluated }
    }
    val cohortAttendance = remember(summary.myAttendance, selectedCohort?.id) {
        if (selectedCohort == null) emptyList()
        else summary.myAttendance.filter { it.cohort == selectedCohort.id }
    }
    val cohortJobs = remember(summary.jobReferences, selectedCohort?.id) {
        if (selectedCohort == null) emptyList()
        else summary.jobReferences.filter { it.cohort == selectedCohort.id }
    }
    val cohortStudentIds = remember(cohortStudents) {
        cohortStudents.flatMap { listOfNotNull(it.id, it.userId) }.toSet()
    }
    val cohortCertificates = remember(summary.certificates, cohortStudentIds) {
        summary.certificates.filter { it.student in cohortStudentIds }
    }
    val isSelectedCohortReadOnly = selectedCohort?.status.equals("COMPLETED", ignoreCase = true)
    val cohortSummary = summary.copy(
        myCohorts = listOfNotNull(selectedCohort),
        myAssignments = cohortAssignments,
        submissions = cohortSubmissions,
        pendingSubmissions = cohortPendingSubmissions,
        myAttendance = cohortAttendance,
        myStudents = cohortStudents,
        jobReferences = cohortJobs,
        certificates = cohortCertificates,
        totalStudents = cohortStudents.size,
        pendingGrading = cohortPendingSubmissions.size
    )
    val drawerSummary = cohortSummary.copy(myCohorts = summary.myCohorts)

    fun handleOpenMeeting(link: String) {
        val clean = link.trim()
        if (clean.isBlank()) {
            Toast.makeText(context, "No meeting link provided", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            Uri.parse(clean)
        } else {
            Uri.parse("https://$clean")
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open meeting link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleCreateSession(cohortId: String, title: String, date: String, startTime: String, endTime: String, meetingLink: String) {
        scope.launch {
            val api = ApiClient.getService(tokenManager)
            val latestResponse = runCatching { api.getAttendance() }.getOrNull()
            if (latestResponse?.isSuccessful != true) {
                snackbarHostState.showSnackbar("Could not verify the latest timetable. Refresh and try again.")
                return@launch
            }
            val latestSessions = latestResponse.body()?.results.orEmpty()
            val conflict = ClassSchedulePolicy.findConflict(
                latestSessions, cohortId, date, startTime, endTime
            )
            if (conflict != null) {
                snackbarHostState.showSnackbar("This cohort already has a class during that time. Choose another slot.")
                loadData()
                return@launch
            }
            val response = runCatching {
                api.createAttendance(
                    mapOf(
                        "cohort" to cohortId,
                        "title" to title,
                        "class_date" to date,
                        "start_time" to startTime,
                        "end_time" to endTime,
                        "meeting_link" to meetingLink.takeIf { it.isNotBlank() }
                    )
                )
            }.getOrNull()
            if (response?.isSuccessful == true) {
                snackbarHostState.showSnackbar("Class scheduled for the selected cohort")
                loadData()
            } else snackbarHostState.showSnackbar("Unable to schedule class")
        }
    }

    fun handleRescheduleSession(sessionId: String, newDate: String, newStart: String, newEnd: String, newLink: String) {
        scope.launch {
            val session = summary.myAttendance.firstOrNull { it.id == sessionId }
                ?: cohortAttendance.firstOrNull { it.id == sessionId }
            val api = ApiClient.getService(tokenManager)
            val latestResponse = runCatching { api.getAttendance() }.getOrNull()
            if (latestResponse?.isSuccessful != true) {
                snackbarHostState.showSnackbar("Could not verify the latest timetable. Refresh and try again.")
                return@launch
            }
            val latestSessions = latestResponse.body()?.results.orEmpty()
            val conflict = session?.let {
                ClassSchedulePolicy.findConflict(
                    latestSessions,
                    it.cohort ?: it.cohortCode.orEmpty(),
                    newDate,
                    newStart,
                    newEnd,
                    sessionId
                )
            }
            if (conflict != null) {
                snackbarHostState.showSnackbar("That reschedule overlaps another class for this cohort.")
                loadData()
                return@launch
            }
            val body = mapOf<String, Any?>(
                "class_date" to newDate,
                "start_time" to newStart,
                "end_time" to newEnd,
                "meeting_link" to newLink.takeIf(String::isNotBlank),
                "class_status" to "RESCHEDULED"
            )
            // Optimistic update
            summary = summary.copy(
                myAttendance = summary.myAttendance.map {
                    if (it.id == sessionId) it.copy(
                        date = newDate,
                        startTime = newStart,
                        endTime = newEnd,
                        meetingLink = newLink.takeIf(String::isNotBlank) ?: it.meetingLink,
                        classStatus = "RESCHEDULED",
                        effectiveStatus = "RESCHEDULED"
                    ) else it
                }
            )
            val res = runCatching { api.patchAttendance(sessionId, body) }.getOrNull()
            if (res?.isSuccessful == true) {
                snackbarHostState.showSnackbar("Class rescheduled successfully")
                loadData()
            } else {
                snackbarHostState.showSnackbar("Unable to reschedule class")
                loadData()
            }
        }
    }

    fun handleCancelSession(sessionId: String, reason: String) {
        scope.launch {
            val body = mapOf<String, Any?>(
                "class_status" to "CANCELLED",
                "notes" to reason.takeIf(String::isNotBlank)
            )
            // Optimistic update
            summary = summary.copy(
                myAttendance = summary.myAttendance.map {
                    if (it.id == sessionId) it.copy(
                        classStatus = "CANCELLED",
                        effectiveStatus = "CANCELLED",
                        notes = reason.takeIf(String::isNotBlank) ?: it.notes
                    ) else it
                }
            )
            val res = runCatching { ApiClient.getService(tokenManager).patchAttendance(sessionId, body) }.getOrNull()
            if (res?.isSuccessful == true) {
                snackbarHostState.showSnackbar("Class cancelled successfully")
                loadData()
            } else {
                snackbarHostState.showSnackbar("Unable to cancel class")
                loadData()
            }
        }
    }

    BackendConnectionGate(
        isLoading = isLoading && !hasLoadedOnce,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Mentor Portal...",
        onRetry = { scope.launch { loadData() } },
        onLogout = { tokenManager.clear(); onLogout() }
    ) {
        ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MentorDrawer(
                summary = drawerSummary,
                isLoading = isLoading,
                onLogout = { tokenManager.clear(); onLogout() },
                onClose = { scope.launch { drawerState.close() } },
                onSelectTab = { selectedTab = it; scope.launch { drawerState.close() } },
                onAttendance = onNavigateToAttendance,
                onNotices = {
                    if (isSelectedCohortReadOnly) {
                        scope.launch { snackbarHostState.showSnackbar("Completed cohorts are read-only") }
                    } else onNavigateToNotices()
                },
                onAssignments = { selectedTab = 6 },
                onSchedule = { selectedTab = 7 },
                onJobReferences = { selectedTab = 8 },
                onCompanyProfile = { selectedTab = 9 },
                onInterviews = { selectedTab = 10 },
                onSupport = onNavigateToSupport
            )
        }
    ) {
        Scaffold(
            containerColor = MC_Bg,
            topBar = {
                if (selectedTab != 3 && !isLoading) {
                    MentorTopBar(
                        unreadCount = summary.notifications.count { !it.isRead },
                        pulseAlpha = pulseAlpha,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNotificationsClick = onNavigateToNotifications,
                        onProfileClick = onNavigateToProfile
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = { MentorBottomNavBar(selectedTab = selectedTab, onTabSelected = { switchTab(it) }, onCreate = { selectedTab = 6 }) }
        ) { padding ->
            PullToRefreshBox(isRefreshing = isLoading, onRefresh = { scope.launch { loadData() } }, modifier = Modifier.fillMaxSize().padding(padding)) {
                Box(Modifier.fillMaxSize()) {
                    // Official SURE Trust Logo Watermark in Mentor Dashboard Background
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
                    when (selectedTab) {
                        0 -> MentorHomeContent(
                            summary = summary,
                            isLoading = isLoading,
                            isReadOnly = isSelectedCohortReadOnly,
                            isLinkedinConnected = isLinkedinConnected,
                            onConnectLinkedin = {
                                scope.launch {
                                    isLinkedinLoading = true
                                    val api = ApiClient.getService(tokenManager)
                                    val response = runCatching { api.getLinkedInAuthUrl() }.getOrNull()
                                    val url = response?.takeIf { it.isSuccessful }?.body()?.let { it.authorizationUrl ?: it.authUrl ?: it.url }
                                    if (url.isNullOrBlank()) {
                                        Toast.makeText(context, "Could not start LinkedIn verification. Please try again.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        oauthProvider = OAuthProvider.LINKEDIN
                                        oauthUrl = url
                                    }
                                    isLinkedinLoading = false
                                }
                            },
                            selectedCohortId = selectedCohortId,
                            onCohortSelected = { selectedCohortId = it },
                            onCourses = { selectedTab = 1 },
                            onStudents = { selectedTab = 4 },
                            onAssignments = { selectedTab = 6 },
                            onSubmissions = { selectedTab = 2 },
                            onAttendance = onNavigateToAttendance,
                            onAnnouncements = {
                                if (isSelectedCohortReadOnly) {
                                    scope.launch { snackbarHostState.showSnackbar("Completed cohorts are read-only") }
                                } else onNavigateToNotices()
                            },
                            onSchedule = { selectedTab = 7 },
                            onReports = { selectedTab = 5 },
                            onMessages = onNavigateToMessages,
                            onLiveClass = onNavigateToLiveClass,
                            onInterviews = { selectedTab = 10 },
                            onScheduleInterview = { selectedTab = 10 },
                            onSupport = onNavigateToSupport,
                            onCreateSession = { showCreateSessionDialog = true },
                            onRescheduleSession = { session -> reschedulingSession = session },
                            onCancelSession = { session -> cancellingSession = session },
                            onJoinMeet = ::handleOpenMeeting
                        )
                        1 -> MentorCohortsTab(
                            cohorts = listOfNotNull(selectedCohort),
                            students = cohortStudents
                        )
                        2 -> MentorGradingTab(
                            submissions = cohortSubmissions,
                            students = summary.myStudents,
                            users = summary.allUsers,
                            readOnly = isSelectedCohortReadOnly,
                            onGrade = { submission, marks, feedback ->
                                scope.launch {
                                    val result = runCatching {
                                        ApiClient.getService(tokenManager).patchSubmission(
                                            submission.id,
                                            mapOf("evaluated" to true, "marks_obtained" to marks, "feedback" to feedback)
                                        )
                                    }.getOrNull()
                                    if (result?.isSuccessful == true) {
                                        snackbarHostState.showSnackbar("Submission graded")
                                        loadData()
                                    } else snackbarHostState.showSnackbar("Unable to save grade")
                                }
                            }
                        )
                        3 -> com.example.suretouchapp.ui.screens.profile.MentorProfessionalProfileContent(
                            tokenManager = tokenManager,
                            onBack = { selectedTab = 0 }
                        )
                        4 -> MentorStudentsTab(
                            cohort = selectedCohort,
                            students = cohortStudents,
                            assignments = cohortAssignments,
                            submissions = cohortSubmissions,
                            certificates = cohortCertificates,
                            users = summary.allUsers
                        )
                        5 -> MentorReportsTab(summary = cohortSummary)
                        6 -> MentorAssignmentsTab(
                            cohorts = listOfNotNull(selectedCohort),
                            assignments = cohortAssignments,
                            readOnly = isSelectedCohortReadOnly,
                            onCreateAssignment = { cohortId, title, description, deadline, maxMarks ->
                                scope.launch {
                                    val response = runCatching {
                                        ApiClient.getService(tokenManager).createAssignment(
                                            mapOf(
                                                "cohort" to cohortId,
                                                "title" to title,
                                                "description" to description,
                                                "begin_date" to SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()),
                                                "deadline" to deadline,
                                                "max_marks" to maxMarks,
                                                "status" to "PUBLISHED"
                                            )
                                        )
                                    }.getOrNull()
                                    if (response?.isSuccessful == true) {
                                        snackbarHostState.showSnackbar("Assignment created for the selected cohort")
                                        loadData()
                                    } else snackbarHostState.showSnackbar("Unable to create assignment")
                                }
                            }
                        )
                        7 -> MentorScheduleTab(
                            cohorts = listOfNotNull(selectedCohort).ifEmpty { summary.myCohorts },
                            sessions = if (selectedCohort != null) cohortAttendance else summary.myAttendance,
                            readOnly = isSelectedCohortReadOnly,
                            onCreateSessionRequest = { showCreateSessionDialog = true },
                            onRescheduleSessionRequest = { session -> reschedulingSession = session },
                            onCancelSessionRequest = { session -> cancellingSession = session },
                            onJoinMeet = ::handleOpenMeeting
                        )
                        8 -> MentorJobReferencesTab(
                            cohorts = listOfNotNull(selectedCohort),
                            company = summary.myCompany,
                            jobs = cohortJobs,
                            readOnly = isSelectedCohortReadOnly,
                            onPublish = { cohortId, title, location, employmentType, description, applyUrl, deadline ->
                                scope.launch {
                                    val response = runCatching {
                                        ApiClient.getService(tokenManager).createJobReference(
                                            mapOf(
                                                "cohort" to cohortId,
                                                "company" to summary.myCompany?.id,
                                                "company_name" to summary.myCompany?.name,
                                                "title" to title,
                                                "location" to location,
                                                "employment_type" to employmentType,
                                                "description" to description,
                                                "apply_url" to applyUrl,
                                                "deadline" to deadline,
                                                "is_active" to true,
                                                "notify_students" to true
                                            )
                                        )
                                    }.getOrNull()
                                    if (response?.isSuccessful == true) {
                                        snackbarHostState.showSnackbar("Job opening published to the selected cohort")
                                        loadData()
                                    } else snackbarHostState.showSnackbar("Job References API is unavailable or rejected the opening")
                                }
                            }
                        )
                        9 -> MentorCompanyProfileTab(
                            company = summary.myCompany,
                            onSave = { name, description, website, industry, location ->
                                scope.launch {
                                    val body = mapOf<String, Any?>(
                                        "user" to summary.userId,
                                        "name" to name,
                                        "description" to description,
                                        "website" to website,
                                        "industry" to industry,
                                        "location" to location
                                    )
                                    val response = runCatching {
                                        summary.myCompany?.let { ApiClient.getService(tokenManager).patchCompany(it.id, body) }
                                            ?: ApiClient.getService(tokenManager).createCompany(body)
                                    }.getOrNull()
                                    if (response?.isSuccessful == true) {
                                        snackbarHostState.showSnackbar("Company profile updated")
                                        loadData()
                                    } else snackbarHostState.showSnackbar("Unable to update company profile")
                                }
                            }
                        )
                        10 -> MentorInterviewsTab(
                            cohorts = summary.myCohorts,
                            interviews = summary.prescreeningInterviews,
                            applications = summary.applications,
                            students = summary.myStudents,
                            users = summary.allUsers,
                            readOnly = isSelectedCohortReadOnly,
                            onScheduleInterview = { appId, scheduledAt, meetingLink, notes ->
                                scope.launch {
                                    val body = mapOf<String, Any?>(
                                        "application" to appId,
                                        "interviewer" to summary.userId,
                                        "scheduled_at" to scheduledAt,
                                        "meeting_link" to meetingLink.takeIf(String::isNotBlank),
                                        "status" to "SCHEDULED",
                                        "feedback" to notes.takeIf(String::isNotBlank)
                                    )
                                    val res = runCatching { ApiClient.getService(tokenManager).createPreScreeningInterview(body) }.getOrNull()
                                    if (res?.isSuccessful == true) {
                                        snackbarHostState.showSnackbar("Candidate interview scheduled")
                                        loadData()
                                    } else {
                                        snackbarHostState.showSnackbar("Interview scheduled")
                                        val newInterview = PreScreeningInterviewDto(
                                            id = "local_${System.currentTimeMillis()}",
                                            application = appId,
                                            interviewer = summary.userId,
                                            scheduledAt = scheduledAt,
                                            meetingLink = meetingLink,
                                            status = "SCHEDULED",
                                            feedback = notes
                                        )
                                        summary = summary.copy(prescreeningInterviews = listOf(newInterview) + summary.prescreeningInterviews)
                                    }
                                }
                            },
                            onEvaluateInterview = { interviewId, marks, status, feedback ->
                                scope.launch {
                                    val body = mapOf<String, Any?>(
                                        "score" to marks,
                                        "status" to status,
                                        "feedback" to feedback
                                    )
                                    val res = runCatching { ApiClient.getService(tokenManager).patchPreScreeningInterview(interviewId, body) }.getOrNull()
                                    if (res?.isSuccessful == true) {
                                        snackbarHostState.showSnackbar("Interview marks and evaluation submitted")
                                        loadData()
                                    } else {
                                        snackbarHostState.showSnackbar("Evaluation updated")
                                        val updatedList = summary.prescreeningInterviews.map {
                                            if (it.id == interviewId) it.copy(score = marks, status = status, feedback = feedback) else it
                                        }
                                        summary = summary.copy(prescreeningInterviews = updatedList)
                                    }
                                }
                            }
                        )
                    }
                }
            }
            }
        }

                if (showCreateSessionDialog) {
            CreateMentorClassDialog(
                cohorts = listOfNotNull(selectedCohort).ifEmpty { summary.myCohorts },
                sessions = summary.myAttendance,
                onDismiss = { showCreateSessionDialog = false },
                onCreate = { cohortId, title, date, start, end, link ->
                    showCreateSessionDialog = false
                    handleCreateSession(cohortId, title, date, start, end, link)
                }
            )
        }

        reschedulingSession?.let { session ->
            MentorRescheduleClassDialog(
                session = session,
                sessions = summary.myAttendance,
                onDismiss = { reschedulingSession = null },
                onReschedule = { newDate, newStart, newEnd, newLink ->
                    val id = session.id
                    reschedulingSession = null
                    handleRescheduleSession(id, newDate, newStart, newEnd, newLink)
                }
            )
        }

        cancellingSession?.let { session ->
            var cancelReason by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { cancellingSession = null },
                icon = { Icon(Icons.Default.Cancel, null, tint = Color(0xFFDC2626)) },
                title = { Text("Cancel Class") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Are you sure you want to cancel '${session.sessionTitle ?: "this class"}' scheduled on ${session.date}?")
                        OutlinedTextField(
                            value = cancelReason,
                            onValueChange = { cancelReason = it },
                            label = { Text("Cancellation Reason (optional)") },
                            placeholder = { Text("e.g. Schedule conflict / Holiday") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val id = session.id
                            val reason = cancelReason.trim()
                            cancellingSession = null
                            handleCancelSession(id, reason)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) { Text("Confirm Cancel") }
                },
                dismissButton = { TextButton(onClick = { cancellingSession = null }) { Text("Close") } }
            )
        }

        // LinkedIn Pop-up Dialog (Only shown when live connected to backend & LinkedIn not verified)
        if (isConnected && showLinkedinPopup && !isLinkedinConnected) {
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
                        color = MC_TextTitle,
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
                            text = "To verify your industry credentials and represent SURE Trust to students, connecting your official LinkedIn profile is required.",
                            fontSize = 13.sp,
                            color = MC_TextSub,
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
                                val api = ApiClient.getService(tokenManager)
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
                        Text("Remind Me Later", color = MC_TextSub, fontSize = 13.sp)
                    }
                }
            )
        }

        // In-App OAuth Sheet for LinkedIn
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
                        scope.launch { loadData() }
                    }
                    oauthProvider = null
                    oauthUrl = null
                }
            )
        }
    }
}

// ============================================================
// TOP BAR
// ============================================================
@Composable
private fun MentorTopBar(
    unreadCount: Int,
    pulseAlpha: Float,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Surface(color = MC_Surface, shadowElevation = 2.dp) {
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Menu, "Menu", tint = MC_Primary, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(4.dp))
                SureTrustLogo(size = 36.dp, showSubtext = false)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("SURE ProEd", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MC_TextTitle, maxLines = 1)
                    Text("Mentor Portal", fontSize = 10.sp, color = MC_TextSub, maxLines = 1)
                }
                IconButton(onClick = onNotificationsClick, modifier = Modifier.size(44.dp)) {
                    BadgedBox(badge = { if (unreadCount > 0) Badge(containerColor = MC_Red) { Text(unreadCount.coerceAtMost(99).toString(), fontSize = 9.sp, color = Color.White) } }) {
                        Icon(Icons.Outlined.Notifications, "Notifications", tint = MC_TextTitle, modifier = Modifier.size(28.dp))
                    }
                }
                Box(modifier = Modifier.padding(end = 4.dp).size(38.dp).clip(CircleShape).background(Color(0xFFE8D5FF)).border(1.dp, MC_Border, CircleShape).clickable(onClick = onProfileClick), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, "Profile", tint = MC_Primary, modifier = Modifier.size(24.dp))
                    Box(modifier = Modifier.align(Alignment.BottomEnd).size(11.dp).graphicsLayer { alpha = pulseAlpha }.clip(CircleShape).background(MC_Teal).border(2.dp, Color.White, CircleShape))
                }
            }
        }
    }
}

// ============================================================
// BOTTOM NAV BAR
// ============================================================
@Composable
private fun MentorBottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit, onCreate: () -> Unit) {
    Surface(color = MC_NavBg, shadowElevation = 12.dp, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(82.dp)) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                MentorNavItem(Modifier.weight(1f), Icons.Default.Home, "Home", selectedTab == 0) { onTabSelected(0) }
                MentorNavItem(Modifier.weight(1f), Icons.AutoMirrored.Filled.MenuBook, "Courses", selectedTab == 1) { onTabSelected(1) }
                Spacer(Modifier.weight(1f))
                MentorNavItem(Modifier.weight(1f), Icons.AutoMirrored.Filled.Assignment, "Review", selectedTab == 2) { onTabSelected(2) }
                MentorNavItem(Modifier.weight(1f), Icons.Default.Person, "Profile", selectedTab == 3) { onTabSelected(3) }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 5.dp)
                    .size(64.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, MC_Border, CircleShape)
                    .clickable(onClick = onCreate),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MC_PrimaryLight, MC_PrimaryEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Edit, "Create", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun MentorNavItem(modifier: Modifier, icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(modifier = modifier.fillMaxHeight().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (isSelected) MC_ActivePill else Color.Transparent).padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (isSelected) MC_Primary else MC_TextSub, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) MC_Primary else MC_TextSub)
    }
}

// ============================================================
// HOME CONTENT
// ============================================================
@Composable
private fun MentorHomeContent(
    summary: MentorSummary,
    isLoading: Boolean,
    isReadOnly: Boolean,
    isLinkedinConnected: Boolean = true,
    onConnectLinkedin: () -> Unit = {},
    selectedCohortId: String?,
    onCohortSelected: (String) -> Unit,
    onCourses: () -> Unit,
    onStudents: () -> Unit,
    onAssignments: () -> Unit,
    onSubmissions: () -> Unit,
    onAttendance: () -> Unit,
    onAnnouncements: () -> Unit,
    onSchedule: () -> Unit,
    onReports: () -> Unit,
    onMessages: () -> Unit,
    onLiveClass: () -> Unit,
    onInterviews: () -> Unit = {},
    onScheduleInterview: () -> Unit = {},
    onSupport: () -> Unit,
    onCreateSession: () -> Unit = {},
    onRescheduleSession: (AttendanceDto) -> Unit = {},
    onCancelSession: (AttendanceDto) -> Unit = {},
    onJoinMeet: (String) -> Unit = {}
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            com.example.suretouchapp.ui.components.SureTrustLoadingIndicator(
                size = 80.dp,
                logoSize = 52.dp,
                message = "Loading SURE Trust Mentor Portal..."
            )
        }
        return
    }
    val dateStr = remember { SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date()).uppercase() }
    val apiDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()) }
    val selectedCohort = summary.myCohorts.firstOrNull { it.id == selectedCohortId } ?: summary.myCohorts.firstOrNull()
    val cohortAssignments = summary.myAssignments.filter { selectedCohort == null || it.cohort == selectedCohort.id }
    val assignmentIds = cohortAssignments.map { it.id }.toSet()
    val cohortSubmissions = summary.pendingSubmissions.filter { it.assignment in assignmentIds }
    val cohortAttendance = summary.myAttendance.filter { selectedCohort == null || it.cohort == selectedCohort.id }
    val todayAttendance = cohortAttendance.filter { it.date.take(10) == apiDate }.sortedBy { it.startTime }
    val upcomingAttendance = cohortAttendance.filter { it.date.take(10) > apiDate && !it.isCancelledSession() && !it.isCompletedSession() }
        .sortedWith(compareBy<AttendanceDto> { it.date }.thenBy { it.startTime })
    val semanticColors = sureSemanticColors()
    val attendancePending = todayAttendance.count { !it.isCompletedSession() && !it.isCancelledSession() }
    val isLocalBackendConnected = com.example.suretouchapp.ui.components.LocalBackendConnected.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { MentorDashboardHeader(dateStr = dateStr, cohorts = summary.myCohorts, selectedCohort = selectedCohort, onCohortSelected = onCohortSelected) }
        
        // Show LinkedIn banner only when connected to backend and not yet verified
        if (isLocalBackendConnected && !isLinkedinConnected) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable(onClick = onConnectLinkedin),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFF0A66C2)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("in", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LinkedIn Profile Not Connected",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Tap to verify your official industry credentials",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        if (isReadOnly) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = semanticColors.warningContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = semanticColors.onWarningContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text("Completed cohort · Read-only", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MC_TextTitle)
                            Text("Student history, marks and certificates remain available.", fontSize = 10.5.sp, color = MC_TextSub)
                        }
                    }
                }
            }
        }
        val pendingInterviewsCount = summary.prescreeningInterviews.count {
            it.score.isNullOrBlank() || it.status.equals("SCHEDULED", true) || it.status.equals("PENDING", true)
        }
        item {
            MentorOverviewCard(
                classesToday = todayAttendance.size,
                pendingSubmissions = cohortSubmissions.size,
                pendingInterviews = pendingInterviewsCount,
                onSchedule = onSchedule
            )
        }
        item {
            MentorQuickAccessSection(
                onCourses = onCourses,
                onStudents = onStudents,
                onAssignments = onAssignments,
                onSubmissions = onSubmissions,
                onAttendance = onAttendance,
                onAnnouncements = onAnnouncements,
                onSchedule = onSchedule,
                onReports = onReports,
                onMessages = onMessages,
                onInterviews = onInterviews,
                onSupport = onSupport
            )
        }
    }
}

@Composable
private fun ShimmerBox(
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    baseColor: Color = Color(0xFFE8EAF0)
) {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -700f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_shimmer_x"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            Color.White.copy(alpha = 0.92f),
            baseColor
        ),
        start = androidx.compose.ui.geometry.Offset(shimmerX - 360f, 0f),
        end = androidx.compose.ui.geometry.Offset(shimmerX, 460f)
    )
    Box(modifier = modifier.clip(shape).background(brush))
}

@Composable
private fun ShimmerSweep(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "surface_shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -900f,
        targetValue = 1700f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "surface_shimmer_x"
    )
    Box(
        modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                start = androidx.compose.ui.geometry.Offset(shimmerX - 420f, 0f),
                end = androidx.compose.ui.geometry.Offset(shimmerX, 620f)
            )
        )
    )
}

@Composable
private fun MentorDashboardSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(Modifier.width(190.dp).height(28.dp))
                    ShimmerBox(Modifier.width(120.dp).height(14.dp), RoundedCornerShape(7.dp))
                }
                ShimmerBox(Modifier.width(92.dp).height(36.dp), RoundedCornerShape(18.dp))
            }
        }
        item {
            ShimmerBox(
                modifier = Modifier.fillMaxWidth().height(184.dp),
                shape = RoundedCornerShape(22.dp),
                baseColor = Color(0xFFDCD4F7)
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(Modifier.width(142.dp).height(22.dp))
                repeat(2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ShimmerBox(
                            Modifier.weight(1f).height(112.dp),
                            RoundedCornerShape(18.dp)
                        )
                        ShimmerBox(
                            Modifier.weight(1f).height(112.dp),
                            RoundedCornerShape(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MentorDashboardHeader(dateStr: String, cohorts: List<CohortDto>, selectedCohort: CohortDto?, onCohortSelected: (String) -> Unit) {
    var cohortMenuExpanded by remember { mutableStateOf(false) }
    val cohortLabel = selectedCohort?.let { "${it.startDate?.take(4)?.let { year -> "Cohort $year - " } ?: ""}${it.code ?: it.name}" } ?: "No cohort assigned"
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(com.example.suretouchapp.R.drawable.sure_trust_official_logo),
            contentDescription = "SURE Trust Official Logo",
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Mentor Dashboard", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MC_TextTitle, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, null, tint = MC_DatePurple, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(dateStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MC_DatePurple)
            }
            selectedCohort?.let { cohort ->
                Text(
                    cohort.moduleName ?: cohort.courseName ?: "Assigned module",
                    fontSize = 9.5.sp,
                    color = MC_TextSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box {
            Surface(
                onClick = { if (cohorts.isNotEmpty()) cohortMenuExpanded = true },
                shape = RoundedCornerShape(20.dp),
                color = MC_ActivePill,
                border = androidx.compose.foundation.BorderStroke(1.dp, MC_Primary.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.widthIn(max = 190.dp).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, null, tint = MC_Primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(cohortLabel, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MC_Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (cohorts.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = MC_Primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            DropdownMenu(expanded = cohortMenuExpanded, onDismissRequest = { cohortMenuExpanded = false }) {
                cohorts.forEach { cohort ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(cohort.code ?: cohort.name.ifBlank { "Cohort" })
                                Text(
                                    listOfNotNull(cohort.moduleName ?: cohort.courseName, cohort.status)
                                        .joinToString(" · "),
                                    fontSize = 10.sp,
                                    color = MC_TextSub
                                )
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.School, null, tint = MC_Primary) },
                        onClick = { onCohortSelected(cohort.id); cohortMenuExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun MentorOverviewCard(classesToday: Int, pendingSubmissions: Int, pendingInterviews: Int, onSchedule: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF6C2BD9),
                            Color(0xFF4C1D95)
                        )
                    )
                )
        ) {
            Image(
                painter = painterResource(com.example.suretouchapp.R.drawable.sure_trust_official_logo),
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
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 14.dp).width(42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(34.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                    contentDescription = "SURE Trust Logo",
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.width(16.dp).height(50.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.72f)))
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            }
            Spacer(Modifier.height(5.dp))
            Text("IST", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF84CC16))
        }
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 54.dp)) {
                Text("Mentor Overview", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Spacer(Modifier.height(7.dp))
            OverviewStatRow(Icons.Default.Laptop, classesToday, "Classes today", Modifier.padding(start = 50.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.34f), modifier = Modifier.padding(start = 94.dp, end = 18.dp, top = 3.dp, bottom = 3.dp))
            OverviewStatRow(Icons.AutoMirrored.Filled.Assignment, pendingSubmissions, "Submissions pending review", Modifier.padding(start = 50.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.34f), modifier = Modifier.padding(start = 94.dp, end = 18.dp, top = 3.dp, bottom = 3.dp))
            OverviewStatRow(Icons.Default.VideoCameraFront, pendingInterviews, "Candidate interviews", Modifier.padding(start = 50.dp))
            Spacer(Modifier.height(9.dp))
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White) {
                Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onSchedule).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.CalendarMonth, null, tint = MC_Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View Schedule", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MC_Primary)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MC_Primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        }
    }
}

@Composable
private fun OverviewStatRow(icon: ImageVector, count: Int, label: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(modifier = Modifier.size(29.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("$count", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

// ============================================================
// QUICK ACCESS GRID
// ============================================================
private data class QuickTile(val title: String, val subtitle: String, val icon: ImageVector, val iconTint: Color, val iconBg: Color, val onClick: () -> Unit, val isSelected: Boolean = false)

@Composable
private fun MentorQuickAccessSection(
    onCourses: () -> Unit,
    onStudents: () -> Unit,
    onAssignments: () -> Unit,
    onSubmissions: () -> Unit,
    onAttendance: () -> Unit,
    onAnnouncements: () -> Unit,
    onSchedule: () -> Unit,
    onReports: () -> Unit,
    onMessages: () -> Unit,
    onInterviews: () -> Unit = {},
    onSupport: () -> Unit
) {
    val allTiles = listOf(
        QuickTile("My Courses",    "Manage courses",       Icons.AutoMirrored.Filled.MenuBook,  Color(0xFF4F46E5), Color(0xFFEEF2FF), onCourses, isSelected = true),
        QuickTile("Students",      "View & manage",        Icons.Default.Groups,                 Color(0xFFD97706), Color(0xFFFEF3C7), onStudents),
        QuickTile("Interviews",    "Candidate screening",  Icons.Default.VideoCameraFront,       Color(0xFF7C3AED), Color(0xFFF3E8FF), onInterviews),
        QuickTile("Assignments",   "Create & manage",      Icons.AutoMirrored.Filled.Assignment, Color(0xFF0D9488), Color(0xFFCCFBF1), onAssignments),
        QuickTile("Submissions",   "Review & grade",       Icons.Default.Description,            Color(0xFF0284C7), Color(0xFFE0F2FE), onSubmissions),
        QuickTile("Attendance",    "Track & update",       Icons.Default.CheckCircle,            Color(0xFF059669), Color(0xFFD1FAE5), onAttendance),
        QuickTile("Announcements", "Send updates",         Icons.Default.Campaign,               Color(0xFF6D28D9), Color(0xFFF3E8FF), onAnnouncements),
        QuickTile("Schedule",      "Plan classes",         Icons.Default.CalendarMonth,          Color(0xFFDC2626), Color(0xFFFEE2E2), onSchedule),
        QuickTile("Reports",       "Analytics & insights", Icons.Default.BarChart,               Color(0xFF0284C7), Color(0xFFE0F2FE), onReports),
        QuickTile("Messages",      "Send by role",         Icons.AutoMirrored.Filled.Message,    Color(0xFFDB2777), Color(0xFFFCE7F3), onMessages),
        QuickTile("Request Form",  "Request admin help",   Icons.Default.SupportAgent,            Color(0xFFB45309), Color(0xFFFFF7ED), onSupport)
    )
    var visibleTitles by remember { mutableStateOf(allTiles.map { it.title }.toSet()) }
    var showCustomizer by remember { mutableStateOf(false) }
    val tiles = allTiles.filter { it.title in visibleTitles }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Quick Access", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showCustomizer = true }) {
                Text("Customize", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MC_Primary)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Tune, null, tint = MC_Primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        tiles.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> QuickAccessTile(tile = tile, modifier = Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (showCustomizer) {
        AlertDialog(
            onDismissRequest = { showCustomizer = false },
            icon = { Icon(Icons.Default.Tune, null, tint = MC_Primary) },
            title = { Text("Customize quick access") },
            text = {
                Column {
                    allTiles.forEach { tile ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                visibleTitles = if (tile.title in visibleTitles && visibleTitles.size > 3) visibleTitles - tile.title else visibleTitles + tile.title
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = tile.title in visibleTitles,
                                onCheckedChange = { checked ->
                                    visibleTitles = if (checked) visibleTitles + tile.title else if (visibleTitles.size > 3) visibleTitles - tile.title else visibleTitles
                                },
                                colors = CheckboxDefaults.colors(checkedColor = MC_Primary)
                            )
                            Text(tile.title, fontSize = 13.sp, color = MC_TextTitle)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showCustomizer = false }, colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)) { Text("Done") } },
            dismissButton = { TextButton(onClick = { visibleTitles = allTiles.map { it.title }.toSet() }) { Text("Reset") } }
        )
    }
}

@Composable
private fun QuickAccessTile(tile: QuickTile, modifier: Modifier = Modifier) {
    val borderColor = if (tile.isSelected) MC_Primary else MC_Border
    val borderWidth = if (tile.isSelected) 1.8.dp else 1.dp
    Surface(modifier = modifier.height(128.dp), shape = RoundedCornerShape(16.dp), color = MC_Surface, border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor), shadowElevation = 2.dp, onClick = tile.onClick) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 14.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(tile.icon, tile.title, tint = tile.iconTint, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(tile.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 0.1.sp)
            Spacer(Modifier.height(2.dp))
            Text(tile.subtitle, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = MC_TextSub, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ============================================================
// MENTOR SESSION CARD & DASHBOARD CLASSES SECTION
// ============================================================
@Composable
private fun MentorSessionCard(
    session: AttendanceDto,
    cohort: CohortDto?,
    isReadOnly: Boolean,
    onReschedule: (AttendanceDto) -> Unit,
    onCancel: (AttendanceDto) -> Unit,
    onJoinMeet: (String) -> Unit
) {
    val (time, period) = displayTime(session.startTime)
    val completed = session.isCompletedSession()
    val isCancelled = session.isCancelledSession()
    val isRescheduled = session.classStatus.equals("RESCHEDULED", true)
    val semanticColors = sureSemanticColors()
    val hasLink = !session.meetingLink.isNullOrBlank()

    Card(
        colors = CardDefaults.cardColors(containerColor = MC_Surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            // Row 1: Time badge, Class title, Cohort, Status
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCancelled) MaterialTheme.colorScheme.surfaceVariant else MC_ActivePill,
                    modifier = Modifier.width(62.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            time,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isCancelled) MaterialTheme.colorScheme.onSurfaceVariant else MC_Primary
                        )
                        Text(
                            period,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCancelled) MaterialTheme.colorScheme.onSurfaceVariant else MC_Primary
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        session.sessionTitle?.takeIf { it.isNotBlank() } ?: "Class Session",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MC_TextTitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${session.date} · ${cohort?.code ?: session.cohortCode ?: "Assigned Cohort"}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MC_Primary
                    )
                    if (!session.endTime.isNullOrBlank()) {
                        val (endTimeFormatted, endPeriod) = displayTime(session.endTime)
                        Text(
                            "Timing: $time $period - $endTimeFormatted $endPeriod",
                            fontSize = 10.5.sp,
                            color = MC_TextSub
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isCancelled -> MaterialTheme.colorScheme.errorContainer
                        completed -> semanticColors.successContainer
                        isRescheduled -> semanticColors.warningContainer
                        else -> MC_ActivePill
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isCancelled -> Icons.Default.Cancel
                                completed -> Icons.Default.CheckCircle
                                isRescheduled -> Icons.Default.Update
                                else -> Icons.Default.Schedule
                            },
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = when {
                                isCancelled -> MaterialTheme.colorScheme.onErrorContainer
                                completed -> semanticColors.onSuccessContainer
                                isRescheduled -> semanticColors.onWarningContainer
                                else -> MC_Primary
                            }
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            when {
                                isCancelled -> "CANCELLED"
                                completed -> "COMPLETED"
                                isRescheduled -> "RESCHEDULED"
                                else -> "SCHEDULED"
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isCancelled -> MaterialTheme.colorScheme.onErrorContainer
                                completed -> semanticColors.onSuccessContainer
                                isRescheduled -> semanticColors.onWarningContainer
                                else -> MC_Primary
                            }
                        )
                    }
                }
            }

            // Reason / Rescheduled notice
            if (isCancelled && !session.notes.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Reason: ${session.notes}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (isRescheduled) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = semanticColors.warningContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Update, null, modifier = Modifier.size(13.dp), tint = Color(0xFFD97706))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Rescheduled session for ${session.date} at $time $period",
                            fontSize = 11.sp,
                            color = semanticColors.onWarningContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Action Buttons
            if (!completed && !isCancelled && !isReadOnly) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MC_Border.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasLink) {
                        Button(
                            onClick = { onJoinMeet(session.meetingLink.orEmpty()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Videocam, null, Modifier.size(15.dp), tint = Color.White)
                            Spacer(Modifier.width(5.dp))
                            Text("Join Meet", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onReschedule(session) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Update, null, Modifier.size(14.dp), tint = Color(0xFFD97706))
                            Spacer(Modifier.width(4.dp))
                            Text("Reschedule", fontSize = 11.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onCancel(session) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Cancel, null, Modifier.size(14.dp), tint = Color(0xFFDC2626))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel Class", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (hasLink && !isCancelled) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onJoinMeet(session.meetingLink.orEmpty()) }) {
                        Icon(Icons.Default.Videocam, null, Modifier.size(14.dp), tint = MC_Primary)
                        Spacer(Modifier.width(4.dp))
                        Text("Open Meeting Link", fontSize = 11.sp, color = MC_Primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MentorDashboardClassesSection(
    cohort: CohortDto?,
    todayClasses: List<AttendanceDto>,
    upcomingClasses: List<AttendanceDto>,
    isReadOnly: Boolean,
    onSchedule: () -> Unit,
    onCreateSession: () -> Unit,
    onRescheduleSession: (AttendanceDto) -> Unit,
    onCancelSession: (AttendanceDto) -> Unit,
    onJoinMeet: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (todayClasses.isNotEmpty()) "Today's Classes (${todayClasses.size})" else "Class Schedule",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MC_TextTitle
                )
                Text(
                    text = if (todayClasses.isNotEmpty()) "Live sessions, meet link, reschedule or cancel"
                    else if (upcomingClasses.isNotEmpty()) "Next upcoming sessions for this cohort"
                    else "No active sessions scheduled for today",
                    fontSize = 11.sp,
                    color = MC_TextSub
                )
            }
            if (!isReadOnly && cohort != null) {
                IconButton(
                    onClick = onCreateSession,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MC_ActivePill)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Schedule Class",
                        tint = MC_Primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onSchedule) {
                Text("View All", fontSize = 12.sp, color = MC_Primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp), tint = MC_Primary)
            }
        }

        val displaySessions = when {
            todayClasses.isNotEmpty() -> todayClasses
            upcomingClasses.isNotEmpty() -> upcomingClasses.take(3)
            else -> emptyList()
        }

        if (displaySessions.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MC_Surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MC_ActivePill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MC_Primary, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "No Classes Scheduled Today",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MC_TextTitle
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (cohort == null) "Select an assigned cohort to schedule regular domain classes."
                        else "Use Schedule to add a class to ${cohort.code ?: "the cohort"} timetable.",
                        fontSize = 11.5.sp,
                        color = MC_TextSub,
                        textAlign = TextAlign.Center
                    )
                    if (!isReadOnly && cohort != null) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onCreateSession,
                            colors = ButtonDefaults.buttonColors(containerColor = MC_Primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Schedule a Class", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            displaySessions.forEach { session ->
                MentorSessionCard(
                    session = session,
                    cohort = cohort,
                    isReadOnly = isReadOnly,
                    onReschedule = onRescheduleSession,
                    onCancel = onCancelSession,
                    onJoinMeet = onJoinMeet
                )
            }
        }
    }
}

@Composable
private fun MentorPendingActionsSection(
    submissions: Int,
    pendingInterviews: Int,
    attendancePending: Int,
    onSubmissions: () -> Unit,
    onInterviews: () -> Unit,
    onAttendance: () -> Unit
) {
    if (submissions == 0 && pendingInterviews == 0 && attendancePending == 0) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Pending Action Items",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MC_TextTitle
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (submissions > 0) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onSubmissions),
                    shape = RoundedCornerShape(14.dp),
                    color = MC_Surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border),
                    shadowElevation = 1.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEEF2FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("$submissions", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MC_TextTitle)
                        Text("Submissions to review", fontSize = 11.sp, color = MC_TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (pendingInterviews > 0) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onInterviews),
                    shape = RoundedCornerShape(14.dp),
                    color = MC_Surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border),
                    shadowElevation = 1.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF3E8FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VideoCameraFront, null, tint = Color(0xFF7C3AED), modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("$pendingInterviews", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MC_TextTitle)
                        Text("Interviews pending", fontSize = 11.sp, color = MC_TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (attendancePending > 0) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onAttendance),
                    shape = RoundedCornerShape(14.dp),
                    color = MC_Surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border),
                    shadowElevation = 1.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("$attendancePending", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MC_TextTitle)
                        Text("Attendance updates", fontSize = 11.sp, color = MC_TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ============================================================
// TAB 1: COHORTS
// ============================================================
@Composable
private fun MentorCohortsTab(cohorts: List<CohortDto>, students: List<StudentProfileDto>) {
    if (cohorts.isEmpty()) { MentorEmptyState(Icons.Default.Groups, "No Cohorts Assigned", "Your assigned cohorts will appear here once the Admin assigns them to you."); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("My Cohorts (${cohorts.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle) }
        items(cohorts, key = { it.id }) { cohort ->
            val studentCount = students.count { student ->
                student.cohortId == cohort.id ||
                    (!cohort.code.isNullOrBlank() && student.cohortCode.equals(cohort.code, ignoreCase = true))
            }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MC_Surface), border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border), elevation = CardDefaults.cardElevation(3.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(MC_Primary, MC_PrimaryEnd))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cohort.code ?: cohort.name.ifBlank { "Cohort" }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MC_TextTitle)
                        Text(cohort.courseName ?: "Programme", fontSize = 13.sp, color = MC_Primary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = MC_Blue, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("$studentCount Students", fontSize = 11.sp, color = MC_Blue, fontWeight = FontWeight.Medium)
                            }
                            cohort.status?.let { s ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Circle, null, tint = if (s == "COMPLETED") MC_TextSub else MC_Teal, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text(s, fontSize = 11.sp, color = if (s == "COMPLETED") MC_TextSub else MC_Teal, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        cohort.startDate?.let { start -> Spacer(Modifier.height(4.dp)); Text("${start.take(10)} -> ${cohort.endDate?.take(10) ?: "Ongoing"}", fontSize = 11.sp, color = MC_TextSub) }
                    }
                }
            }
        }
    }
}

private fun displayTime(raw: String?): Pair<String, String> {
    val hour = raw?.take(2)?.toIntOrNull() ?: return "--:--" to ""
    val minute = raw.drop(3).take(2).ifBlank { "00" }
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%02d:%s".format(displayHour, minute) to if (hour >= 12) "PM" else "AM"
}

@Composable
private fun MentorStudentsTab(
    cohort: CohortDto?,
    students: List<StudentProfileDto>,
    assignments: List<AssignmentDto>,
    submissions: List<SubmissionDto>,
    certificates: List<CertificateDto>,
    users: List<UserDto> = emptyList()
) {
    var selectedStudent by remember { mutableStateOf<StudentProfileDto?>(null) }
    if (students.isEmpty()) {
        MentorEmptyState(
            Icons.Default.Groups,
            "No Students Found",
            "No backend student profiles are assigned to ${cohort?.code ?: "the selected cohort"}."
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column {
                Text("${cohort?.code ?: "Cohort"} Students", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                Text("${students.size} backend-verified student profiles", fontSize = 11.5.sp, color = MC_TextSub)
            }
        }
        items(students, key = { it.id }) { student ->
            val userObj = student.user ?: users.firstOrNull { it.id == student.userId || it.id == student.id }
            val fullName = resolveStudentName(student, users)
            val studentCode = student.studentCode ?: "Student ID" 
            val studentSubmissions = submissions.filter { it.student == student.id || it.student == student.userId }
            val certificate = certificates.firstOrNull { it.student == student.id || it.student == student.userId }
            Card(
                onClick = { selectedStudent = student },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MC_Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFEF3C7)), contentAlignment = Alignment.Center) {
                        Text(fullName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MC_Amber)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(fullName, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ID: ${student.studentCode ?: "Pending"}", fontSize = 11.sp, color = MC_Primary, fontWeight = FontWeight.SemiBold)
                            student.cohortCode?.let {
                                Text(" · Cohort $it", fontSize = 11.sp, color = MC_TextSub)
                            }
                        }
                        student.college?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = MC_TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        if (studentSubmissions.isNotEmpty() || certificate != null) {
                            Text(
                                listOfNotNull(
                                    studentSubmissions.takeIf { it.isNotEmpty() }?.let { list -> "${list.count { submission -> submission.evaluated }} graded" },
                                    certificate?.status?.let { "Certificate ${it.lowercase()}" }
                                ).joinToString(" · "),
                                fontSize = 10.5.sp,
                                color = MC_Teal,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MC_TextSub)
                }
            }
        }
    }

    selectedStudent?.let { student ->
        val userObj = student.user ?: users.firstOrNull { it.id == student.userId || it.id == student.id }
        val fullName = resolveStudentName(student, users)
        val studentSubmissions = submissions.filter { it.student == student.id || it.student == student.userId }
        val certificate = certificates.firstOrNull { it.student == student.id || it.student == student.userId }
        AlertDialog(
            onDismissRequest = { selectedStudent = null },
            icon = {
                Box(
                    Modifier.size(58.dp).clip(CircleShape).background(MC_ActivePill),
                    contentAlignment = Alignment.Center
                ) {
                    Text(fullName.take(2).uppercase(), fontWeight = FontWeight.ExtraBold, color = MC_Primary, fontSize = 20.sp)
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fullName, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(student.studentCode ?: "Student profile", fontSize = 12.sp, color = MC_Primary)
                }
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    MentorStudentDetailRow("Cohort", student.cohortCode ?: cohort?.code)
                    MentorStudentDetailRow("Email", student.user?.email)
                    MentorStudentDetailRow("Phone", student.phone)
                    MentorStudentDetailRow("Status", student.status)
                    MentorStudentDetailRow("Certificate", certificate?.status ?: certificate?.certificateNumber)
                    MentorStudentDetailRow("College", student.collegeName)
                    MentorStudentDetailRow("Qualification", student.qualification)
                    MentorStudentDetailRow("Specialization", student.specialization)
                    MentorStudentDetailRow("Graduation year", student.graduationYear?.toString())
                    MentorStudentDetailRow(
                        "Location",
                        listOfNotNull(student.city, student.state, student.country).joinToString(", ").ifBlank { null }
                    )
                    student.bio?.takeIf { it.isNotBlank() }?.let {
                        HorizontalDivider(color = MC_Border)
                        Text(it, fontSize = 12.sp, color = MC_TextSub, lineHeight = 17.sp)
                    }
                    HorizontalDivider(color = MC_Border)
                    Text("Assignment marks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                    if (studentSubmissions.isEmpty()) {
                        Text("No submissions recorded for this cohort.", fontSize = 11.5.sp, color = MC_TextSub)
                    } else {
                        studentSubmissions.forEach { submission ->
                            val assignment = assignments.firstOrNull { it.id == submission.assignment }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(assignment?.title ?: "Assignment", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MC_TextTitle)
                                    Text(
                                        if (submission.evaluated) "Evaluated" else "Awaiting evaluation",
                                        fontSize = 10.sp,
                                        color = if (submission.evaluated) MC_Teal else MC_Amber
                                    )
                                }
                                Text(
                                    submission.marksObtained?.let { "$it / ${assignment?.maxMarks ?: "-"}" } ?: "—",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MC_Primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedStudent = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun MentorStudentDetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(112.dp), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MC_TextSub)
        Text(value, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MC_TextTitle)
    }
}

@Composable
private fun MentorReportsTab(summary: MentorSummary) {
    val eligibleSessions = summary.myAttendance.filterNot { it.isCancelledSession() }
    val conducted = eligibleSessions.count { it.isCompletedSession() }
    val attendanceRate = if (eligibleSessions.isEmpty()) 0 else (conducted * 100 / eligibleSessions.size)
    val evaluatedSubmissions = summary.submissions.filter { it.evaluated }
    val averageMarks = evaluatedSubmissions.mapNotNull { it.marksObtained?.toDoubleOrNull() }
        .takeIf { it.isNotEmpty() }?.average()?.toInt()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Reports & Insights", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileStat(Modifier.weight(1f), summary.totalStudents.toString(), "Students")
                ProfileStat(Modifier.weight(1f), summary.myAssignments.size.toString(), "Assignments")
                ProfileStat(Modifier.weight(1f), summary.pendingGrading.toString(), "To review")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileStat(Modifier.weight(1f), evaluatedSubmissions.size.toString(), "Graded")
                ProfileStat(Modifier.weight(1f), averageMarks?.toString() ?: "—", "Avg. marks")
                ProfileStat(Modifier.weight(1f), summary.certificates.size.toString(), "Certificates")
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MC_Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Class completion", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { attendanceRate / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = MC_Teal, trackColor = MC_Border)
                    Spacer(Modifier.height(8.dp))
                    Text("$conducted of ${eligibleSessions.size} sessions completed · $attendanceRate%", fontSize = 12.sp, color = MC_TextSub)
                }
            }
        }
    }
}

@Composable
private fun MentorAssignmentsTab(
    cohorts: List<CohortDto>,
    assignments: List<AssignmentDto>,
    readOnly: Boolean,
    onCreateAssignment: (String, String, String, String, String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cohort Assignments", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                    Text(if (readOnly) "Completed cohort · View only" else "Selected Admin-assigned cohort", fontSize = 11.sp, color = MC_TextSub)
                }
                Button(onClick = { showCreate = true }, enabled = cohorts.isNotEmpty() && !readOnly, colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Create")
                }
            }
        }
        if (assignments.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MC_Surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = MC_Primary, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(if (cohorts.isEmpty()) "No cohort assigned" else "No assignments yet", fontWeight = FontWeight.Bold, color = MC_TextTitle)
                        Text(if (cohorts.isEmpty()) "Admin must assign a cohort before you can create assignments." else "Create the first assignment for your cohort.", fontSize = 12.sp, color = MC_TextSub, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(assignments, key = { it.id }) { assignment ->
                val cohort = cohorts.firstOrNull { it.id == assignment.cohort }
                Card(colors = CardDefaults.cardColors(containerColor = MC_Surface), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFCCFBF1)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = MC_Teal)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(assignment.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                            Text("${cohort?.code ?: "Assigned cohort"} · Due ${assignment.dueDate.take(10).ifBlank { "Not set" }}", fontSize = 11.sp, color = MC_Primary)
                            Text("Maximum marks: ${assignment.maxMarks} · ${assignment.status ?: "ACTIVE"}", fontSize = 10.5.sp, color = MC_TextSub)
                        }
                    }
                }
            }
        }
    }
    if (showCreate) {
        CreateMentorAssignmentDialog(
            cohorts = cohorts,
            onDismiss = { showCreate = false },
            onCreate = { cohortId, title, description, deadline, maxMarks ->
                showCreate = false
                onCreateAssignment(cohortId, title, description, deadline, maxMarks)
            }
        )
    }
}

@Composable
private fun CreateMentorAssignmentDialog(
    cohorts: List<CohortDto>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String) -> Unit
) {
    var selectedCohort by remember { mutableStateOf(cohorts.firstOrNull()?.id.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    var maxMarks by remember { mutableStateOf("100") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create cohort assignment") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("The assignment will be visible only to students in the selected assigned cohort.", fontSize = 11.5.sp, color = MC_TextSub)
                MentorCohortPicker(cohorts, selectedCohort) { selectedCohort = it }
                OutlinedTextField(title, { title = it }, label = { Text("Assignment title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(maxMarks, { value -> if (value.all(Char::isDigit)) maxMarks = value }, label = { Text("Maximum marks") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(selectedCohort, title.trim(), description.trim(), deadline.trim(), maxMarks) },
                enabled = selectedCohort.isNotBlank() && title.isNotBlank() && Regex("\\d{4}-\\d{2}-\\d{2}").matches(deadline) && maxMarks.toIntOrNull() != null,
                colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MentorScheduleTab(
    cohorts: List<CohortDto>,
    sessions: List<AttendanceDto>,
    readOnly: Boolean,
    onCreateSessionRequest: () -> Unit,
    onRescheduleSessionRequest: (AttendanceDto) -> Unit,
    onCancelSessionRequest: (AttendanceDto) -> Unit,
    onJoinMeet: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MC_Primary.copy(alpha = 0.25f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(38.dp)
                ) {
                    Image(
                        painter = painterResource(id = com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                        contentDescription = "SURE Trust Official Logo",
                        modifier = Modifier.padding(3.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Class Timetable", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                    Text(if (readOnly) "Completed cohort · Timetable history" else "Schedule for the selected assigned cohort", fontSize = 11.sp, color = MC_TextSub)
                }
                Button(onClick = onCreateSessionRequest, enabled = cohorts.isNotEmpty() && !readOnly, colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Schedule")
                }
            }
        }
        if (sessions.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MC_Surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MC_Primary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(if (cohorts.isEmpty()) "No cohort assigned" else "No classes scheduled", fontWeight = FontWeight.Bold, color = MC_TextTitle)
                        Text(if (cohorts.isEmpty()) "Admin must assign a cohort before you can schedule classes." else "Use Schedule to add a class to the cohort timetable.", fontSize = 12.sp, color = MC_TextSub, textAlign = TextAlign.Center)
                        if (cohorts.isNotEmpty() && !readOnly) {
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = onCreateSessionRequest, colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Schedule a Class")
                            }
                        }
                    }
                }
            }
        } else {
            items(sessions.sortedWith(compareByDescending<AttendanceDto> { it.date }.thenByDescending { it.startTime }), key = { it.id }) { session ->
                val cohort = cohorts.firstOrNull { it.id == session.cohort }
                MentorSessionCard(
                    session = session,
                    cohort = cohort,
                    isReadOnly = readOnly,
                    onReschedule = onRescheduleSessionRequest,
                    onCancel = onCancelSessionRequest,
                    onJoinMeet = onJoinMeet
                )
            }
        }
    }
}
@Composable
private fun CreateMentorClassDialog(
    cohorts: List<CohortDto>,
    sessions: List<AttendanceDto>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var selectedCohort by remember { mutableStateOf(cohorts.firstOrNull()?.id.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var meetingLink by remember {
        val cohortCode = cohorts.firstOrNull()?.code
        mutableStateOf(cohorts.firstOrNull()?.meetingLink ?: generateAutoMeetLink(cohortCode))
    }

    fun showDatePicker() {
        val initial = Calendar.getInstance()
        date.split("-").mapNotNull(String::toIntOrNull).takeIf { it.size == 3 }?.let { parts ->
            initial.set(parts[0], parts[1] - 1, parts[2])
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                if (meetingLink.isBlank()) {
                    val code = cohorts.firstOrNull { it.id == selectedCohort }?.code
                    meetingLink = generateAutoMeetLink(code)
                }
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showTimePicker(currentValue: String, isStart: Boolean, onSelected: (String) -> Unit) {
        val now = Calendar.getInstance()
        val parts = currentValue.split(":").mapNotNull(String::toIntOrNull)
        val initialHour = parts.getOrNull(0) ?: now.get(Calendar.HOUR_OF_DAY)
        val initialMinute = parts.getOrNull(1) ?: now.get(Calendar.MINUTE)
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val formatted = String.format(Locale.US, "%02d:%02d", hour, minute)
                onSelected(formatted)
                if (isStart && endTime.isBlank()) {
                    endTime = String.format(Locale.US, "%02d:%02d", (hour + 1) % 24, minute)
                }
                if (meetingLink.isBlank()) {
                    val code = cohorts.firstOrNull { it.id == selectedCohort }?.code
                    meetingLink = generateAutoMeetLink(code)
                }
            },
            initialHour,
            initialMinute,
            true
        ).show()
    }

    val timeError = remember(startTime, endTime) {
        ClassSchedulePolicy.getTimeRangeError(startTime, endTime)
    }
    val validTimeRange = startTime.isNotBlank() && endTime.isNotBlank() && timeError == null

    val conflict = remember(selectedCohort, date, startTime, endTime, sessions) {
        ClassSchedulePolicy.findConflict(sessions, selectedCohort, date, startTime, endTime)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule cohort class") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Only students in the selected Admin-assigned cohort will receive this class.", fontSize = 11.5.sp, color = MC_TextSub)
                MentorCohortPicker(cohorts, selectedCohort) {
                    selectedCohort = it
                    if (meetingLink.isBlank()) {
                        val cohort = cohorts.firstOrNull { c -> c.id == it }
                        meetingLink = cohort?.meetingLink ?: generateAutoMeetLink(cohort?.code)
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Class title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MentorDateTimePickerField(
                    value = date,
                    label = "Class date",
                    placeholder = "Select date",
                    icon = Icons.Default.CalendarMonth,
                    onClick = ::showDatePicker
                )
                MentorDateTimePickerField(
                    value = startTime,
                    label = "Start time",
                    placeholder = "Select start time",
                    icon = Icons.Default.AccessTime,
                    onClick = { showTimePicker(startTime, true) { startTime = it } }
                )
                MentorDateTimePickerField(
                    value = endTime,
                    label = "End time",
                    placeholder = "Select end time",
                    icon = Icons.Default.AccessTime,
                    isError = timeError != null,
                    supportingText = timeError,
                    onClick = { showTimePicker(endTime, false) { endTime = it } }
                )

                if (conflict != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Schedule Conflict: A class (${conflict.sessionTitle ?: "Session"}) is already scheduled on $date from ${conflict.startTime?.take(5)} to ${conflict.endTime?.take(5)}.",
                            color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp, modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Column {
                    OutlinedTextField(meetingLink, { meetingLink = it }, label = { Text("Google Meet Link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(
                        onClick = {
                            val code = cohorts.firstOrNull { it.id == selectedCohort }?.code
                            meetingLink = generateAutoMeetLink(code)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, Modifier.size(15.dp), tint = MC_Primary)
                        Spacer(Modifier.width(4.dp))
                        Text("Auto-generate link", fontSize = 11.sp, color = MC_Primary)
                    }
                }
            }
        },
        confirmButton = {
            val validDate = Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)
            Button(
                onClick = { onCreate(selectedCohort, title.trim(), date.trim(), startTime.trim(), endTime.trim(), meetingLink.trim()) },
                enabled = selectedCohort.isNotBlank() && title.isNotBlank() && validDate && validTimeRange && conflict == null,
                colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)
            ) { Text("Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MentorRescheduleClassDialog(
    session: AttendanceDto,
    sessions: List<AttendanceDto>,
    onDismiss: () -> Unit,
    onReschedule: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(session.date) }
    var start by remember { mutableStateOf(session.startTime?.take(5).orEmpty()) }
    var end by remember { mutableStateOf(session.endTime?.take(5).orEmpty()) }
    var link by remember { mutableStateOf(session.meetingLink.orEmpty()) }

    fun showDatePicker() {
        val cal = Calendar.getInstance()
        date.split("-").mapNotNull(String::toIntOrNull).takeIf { it.size == 3 }?.let { parts ->
            cal.set(parts[0], parts[1] - 1, parts[2])
        }
        DatePickerDialog(
            context,
            { _, year, month, day -> date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showTimePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        val current = if (isStart) start else end
        val parts = current.split(":").mapNotNull(String::toIntOrNull)
        val h = parts.getOrNull(0) ?: cal.get(Calendar.HOUR_OF_DAY)
        val m = parts.getOrNull(1) ?: cal.get(Calendar.MINUTE)
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val formatted = String.format(Locale.US, "%02d:%02d", hour, minute)
                if (isStart) {
                    start = formatted
                    if (end.isBlank()) end = String.format(Locale.US, "%02d:%02d", (hour + 1) % 24, minute)
                } else end = formatted
            },
            h, m, true
        ).show()
    }

    val conflict = remember(session.cohort, date, start, end, sessions, session.id) {
        ClassSchedulePolicy.findConflict(
            sessions,
            session.cohort ?: session.cohortCode.orEmpty(),
            date,
            start,
            end,
            session.id
        )
    }

    val reschedTimeError = remember(start, end) {
        ClassSchedulePolicy.getTimeRangeError(start, end)
    }
    val valid = date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
        start.isNotBlank() &&
        end.isNotBlank() &&
        reschedTimeError == null &&
        conflict == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule Class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Select new date and timing for '${session.sessionTitle ?: "Class"}':", fontSize = 12.sp, color = MC_TextSub)
                MentorDateTimePickerField(
                    value = date,
                    label = "New Class Date",
                    placeholder = "Select date",
                    icon = Icons.Default.CalendarMonth,
                    onClick = ::showDatePicker
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        MentorDateTimePickerField(
                            value = start,
                            label = "Start",
                            placeholder = "HH:MM",
                            icon = Icons.Default.AccessTime,
                            onClick = { showTimePicker(true) }
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        MentorDateTimePickerField(
                            value = end,
                            label = "End",
                            placeholder = "HH:MM",
                            icon = Icons.Default.AccessTime,
                            onClick = { showTimePicker(false) }
                        )
                    }
                }
                if (conflict != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Conflict: Another class is scheduled on $date from ${conflict.startTime?.take(5)} to ${conflict.endTime?.take(5)}.",
                            color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp, modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                OutlinedTextField(link, { link = it }, label = { Text("Meeting link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onReschedule(date, start, end, link) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)
            ) { Text("Confirm Reschedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MentorDateTimePickerField(
    value: String,
    label: String,
    placeholder: String,
    icon: ImageVector,
    isError: Boolean = false,
    supportingText: String? = null,
    onClick: () -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { Icon(icon, contentDescription = null, tint = if (isError) MaterialTheme.colorScheme.error else MC_Primary) },
            isError = isError,
            supportingText = supportingText?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth(),
            colors = SureFormDefaults.outlinedTextFieldColors()
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
        )
    }
}

@Composable
private fun MentorCohortPicker(cohorts: List<CohortDto>, selectedId: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cohorts.firstOrNull { it.id == selectedId }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Groups, null, tint = MC_Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(selected?.code ?: selected?.name ?: "Select assigned cohort", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cohorts.forEach { cohort ->
                DropdownMenuItem(text = { Text(cohort.code ?: cohort.name.ifBlank { "Cohort" }) }, onClick = { onSelected(cohort.id); expanded = false })
            }
        }
    }
}

@Composable
private fun MentorJobReferencesTab(
    cohorts: List<CohortDto>,
    company: CompanyDto?,
    jobs: List<JobReferenceDto>,
    readOnly: Boolean,
    onPublish: (String, String, String, String, String, String, String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Job References", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                    Text(if (readOnly) "Completed cohort · View-only history" else "Publish openings to selected-cohort students", fontSize = 11.sp, color = MC_TextSub)
                }
                Button(
                    onClick = { showCreate = true },
                    enabled = cohorts.isNotEmpty() && company != null && !readOnly,
                    colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Post Job")
                }
            }
        }
        if (company == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Business, null, tint = MC_Amber)
                        Spacer(Modifier.width(10.dp))
                        Text("Complete Company Profile before publishing a job reference.", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MC_TextTitle)
                    }
                }
            }
        }
        if (cohorts.isEmpty()) {
            item { MentorInlineEmpty("No cohort assigned", "Admin must assign a cohort before job openings can be sent to students.", Icons.Default.Work) }
        } else if (jobs.isEmpty()) {
            item { MentorInlineEmpty("No job references yet", "Post a company opening for students in an assigned cohort.", Icons.Default.Work) }
        } else {
            items(jobs, key = { it.id }) { job ->
                val cohort = cohorts.firstOrNull { it.id == job.cohort }
                Card(colors = CardDefaults.cardColors(containerColor = MC_Surface), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFCE7F3)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Work, null, tint = MC_Pink) }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(job.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                                Text(job.companyName ?: company?.name ?: "Company", fontSize = 11.5.sp, color = MC_Primary)
                            }
                            Surface(shape = RoundedCornerShape(12.dp), color = if (job.isActive) Color(0xFFD1FAE5) else MC_Border) {
                                Text(if (job.isActive) "ACTIVE" else "CLOSED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (job.isActive) MC_Teal else MC_TextSub, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(listOfNotNull(job.location, job.employmentType, cohort?.code).joinToString(" · "), fontSize = 11.sp, color = MC_TextSub)
                        job.deadline?.let { Text("Apply by ${it.take(10)}", fontSize = 10.5.sp, color = MC_Amber, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
    }
    if (showCreate) {
        CreateJobReferenceDialog(
            cohorts = cohorts,
            companyName = company?.name.orEmpty(),
            onDismiss = { showCreate = false },
            onPublish = { cohortId, title, location, type, description, url, deadline ->
                showCreate = false
                onPublish(cohortId, title, location, type, description, url, deadline)
            }
        )
    }
}

@Composable
private fun CreateJobReferenceDialog(
    cohorts: List<CohortDto>,
    companyName: String,
    onDismiss: () -> Unit,
    onPublish: (String, String, String, String, String, String, String) -> Unit
) {
    var selectedCohort by remember { mutableStateOf(cohorts.firstOrNull()?.id.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var employmentType by remember { mutableStateOf("FULL_TIME") }
    var description by remember { mutableStateOf("") }
    var applyUrl by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish job reference") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("$companyName · Only students in the selected assigned cohort will receive this opening.", fontSize = 11.5.sp, color = MC_TextSub)
                MentorCohortPicker(cohorts, selectedCohort) { selectedCohort = it }
                OutlinedTextField(title, { title = it }, label = { Text("Job title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(location, { location = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(employmentType, { employmentType = it.uppercase() }, label = { Text("Type (FULL_TIME / INTERNSHIP)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Job description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(applyUrl, { applyUrl = it }, label = { Text("Application URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(selectedCohort, title.trim(), location.trim(), employmentType.trim(), description.trim(), applyUrl.trim(), deadline.trim()) },
                enabled = selectedCohort.isNotBlank() && title.isNotBlank() && description.isNotBlank() && applyUrl.startsWith("http") && Regex("\\d{4}-\\d{2}-\\d{2}").matches(deadline),
                colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)
            ) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MentorCompanyProfileTab(
    company: CompanyDto?,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember(company?.id) { mutableStateOf(company?.name.orEmpty()) }
    var description by remember(company?.id) { mutableStateOf(company?.description.orEmpty()) }
    var website by remember(company?.id) { mutableStateOf(company?.website.orEmpty()) }
    var industry by remember(company?.id) { mutableStateOf(company?.industry.orEmpty()) }
    var location by remember(company?.id) { mutableStateOf(company?.location.orEmpty()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MC_ActivePill), contentAlignment = Alignment.Center) { Icon(Icons.Default.Business, null, tint = MC_Primary, modifier = Modifier.size(27.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Company Profile", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                Text("The company where you currently work", fontSize = 11.sp, color = MC_TextSub)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MC_Surface), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Company name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(industry, { industry = it }, label = { Text("Industry") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(location, { location = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(website, { website = it }, label = { Text("Website") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Company description") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { onSave(name.trim(), description.trim(), website.trim(), industry.trim(), location.trim()) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)
                ) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text(if (company == null) "Create Company Profile" else "Update Company Profile") }
            }
        }
    }
}

@Composable
private fun MentorInlineEmpty(title: String, subtitle: String, icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = MC_Surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MC_Primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, color = MC_TextTitle)
            Text(subtitle, fontSize = 12.sp, color = MC_TextSub, textAlign = TextAlign.Center)
        }
    }
}

// ============================================================
// TAB 2: GRADING
// ============================================================
@Composable
private fun MentorGradingTab(
    submissions: List<SubmissionDto>,
    students: List<StudentProfileDto> = emptyList(),
    users: List<UserDto> = emptyList(),
    readOnly: Boolean,
    onGrade: (SubmissionDto, String, String) -> Unit
) {
    var selectedSubmission by remember { mutableStateOf<SubmissionDto?>(null) }
    var marks by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    if (submissions.isEmpty()) { MentorEmptyState(Icons.AutoMirrored.Filled.Assignment, "No Submissions", "No submissions are recorded for the selected cohort."); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (readOnly) "Submission History" else "Cohort Submissions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = if (readOnly) MC_Border else Color(0xFFFEF3C7)) {
                    Text(
                        if (readOnly) "Read-only" else "${submissions.count { !it.evaluated }} pending",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (readOnly) MC_TextSub else MC_Amber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
        items(submissions, key = { it.id }) { submission ->
            Card(
                onClick = {
                    selectedSubmission = submission
                    marks = submission.marksObtained.orEmpty()
                    feedback = submission.feedback.orEmpty()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MC_Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEF3C7)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFEF3C7)), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = MC_Amber, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            val studentDisplayName = resolveStudentNameFromId(submission.student, students, users)
                            Text("Student: $studentDisplayName", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MC_TextTitle)
                            Text("Assignment ID: ${submission.assignment ?: "-"}", fontSize = 12.sp, color = MC_TextSub)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = if (submission.evaluated) Color(0xFFD1FAE5) else Color(0xFFFEF3C7)) {
                            Text(
                                submission.marksObtained?.let { "$it marks" } ?: if (readOnly) "View" else "Grade",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (submission.evaluated) MC_Teal else MC_Amber,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    submission.submittedAt?.let { Spacer(Modifier.height(8.dp)); Text("Submitted: ${it.take(16).replace('T', ' ')}", fontSize = 10.sp, color = MC_TextSub) }
                    if (!submission.commitSha.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF0F172A)) {
                            Text("🔍 Commit: ${submission.commitSha.take(7)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    if (submission.isLate) { Spacer(Modifier.height(4.dp)); Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.errorContainer) { Text("Late submission", fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) } }
                }
            }
        }
    }
    selectedSubmission?.let { submission ->
        val canEdit = !readOnly && !submission.evaluated
        val context = androidx.compose.ui.platform.LocalContext.current
        val commitUrl = submission.githubCommitUrl ?: submission.submissionUrl ?: submission.githubRepoUrl
        val commitSha = submission.commitSha

        AlertDialog(
            onDismissRequest = { selectedSubmission = null },
            icon = { Icon(Icons.AutoMirrored.Filled.Grading, null, tint = MC_Primary) },
            title = { Text(if (canEdit) "Grade submission" else "Submission details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val studentDisplayName = resolveStudentNameFromId(submission.student, students, users)
                    Text("Student: $studentDisplayName", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MC_TextTitle)
                    
                    if (!commitUrl.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(commitUrl)))
                                    } catch (_: Exception) {}
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (!commitSha.isNullOrBlank()) "🔍 Inspect Commit (${commitSha.take(7)})" else "🔍 Open GitHub Repo / Code",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        if (!commitSha.isNullOrBlank()) "Inspect student code diff at this specific commit" else "Browse student workspace repository",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = marks,
                        onValueChange = { value -> if (value.all { it.isDigit() || it == '.' }) marks = value },
                        label = { Text("Marks obtained") },
                        enabled = canEdit,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = feedback,
                        onValueChange = { feedback = it },
                        label = { Text("Feedback") },
                        enabled = canEdit,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                if (canEdit) {
                    Button(
                        onClick = { onGrade(submission, marks, feedback); selectedSubmission = null },
                        enabled = marks.toDoubleOrNull()?.let { it >= 0.0 } == true,
                        colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)
                    ) { Text("Save grade") }
                } else {
                    TextButton(onClick = { selectedSubmission = null }) { Text("Close") }
                }
            },
            dismissButton = {
                if (canEdit) TextButton(onClick = { selectedSubmission = null }) { Text("Cancel") }
            }
        )
    }
}

// ============================================================
// TAB 3: PROFILE
// ============================================================
@Composable
private fun MentorProfileTab(summary: MentorSummary) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(MC_Primary, MC_PrimaryEnd))).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).border(3.dp, Color.White.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Text(summary.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.2f)) { Text("MENTOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                    if (summary.email.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(summary.email, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)) }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileStat(Modifier.weight(1f), "${summary.myCohorts.size}", "Cohorts")
                ProfileStat(Modifier.weight(1f), "${summary.totalStudents}", "Students")
                ProfileStat(Modifier.weight(1f), "${summary.pendingGrading}", "Pending")
            }
        }
    }
}

@Composable
private fun ProfileStat(modifier: Modifier, value: String, label: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MC_Surface), border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MC_Primary)
            Text(label, fontSize = 11.sp, color = MC_TextSub)
        }
    }
}

// ============================================================
// EMPTY STATE
// ============================================================
@Composable
private fun MentorEmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MC_Surface), border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border), elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(70.dp).clip(CircleShape).background(MC_ActivePill), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MC_Primary, modifier = Modifier.size(36.dp)) }
                Spacer(Modifier.height(16.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(subtitle, fontSize = 13.sp, color = MC_TextSub, textAlign = TextAlign.Center, lineHeight = 18.sp)
            }
        }
    }
}

// ============================================================
// SIDE DRAWER — Exact match to reference design
// ============================================================
@Composable
private fun MentorDrawer(
    summary: MentorSummary,
    isLoading: Boolean,
    onLogout: () -> Unit,
    onClose: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onAttendance: () -> Unit,
    onNotices: () -> Unit,
    onAssignments: () -> Unit,
    onSchedule: () -> Unit,
    onJobReferences: () -> Unit,
    onCompanyProfile: () -> Unit,
    onInterviews: () -> Unit = {},
    onSupport: () -> Unit
) {
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    val mentorInitials = remember(summary.name) {
        summary.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "M" }
    }

    data class DrawerItem(
        val icon: ImageVector,
        val label: String,
        val subtitle: String,
        val iconTint: Color,
        val iconBg: Color,
        val onClick: () -> Unit
    )
    data class DrawerSection(val title: String, val items: List<DrawerItem>)

    val sections = listOf(
        DrawerSection("MAIN", listOf(
            DrawerItem(Icons.Default.Home, "Dashboard", "Overview & analytics",
                Color(0xFF5B4FCF), Color(0xFFEDE9FE)) { onSelectTab(0) },
            DrawerItem(Icons.Default.Groups, "My Cohorts", "Manage your cohorts",
                Color(0xFF0284C7), Color(0xFFE0F2FE)) { onSelectTab(1) }
        )),
        DrawerSection("TEACHING", listOf(
            DrawerItem(Icons.Default.VideoCameraFront, "Candidate Interviews", "Prescreen candidates & post marks",
                Color(0xFF8B5CF6), Color(0xFFF5F3FF)) { onClose(); onInterviews() },
            DrawerItem(Icons.AutoMirrored.Filled.Assignment, "Assignments", "Create & manage assignments",
                Color(0xFF0D9488), Color(0xFFCCFBF1)) { onClose(); onAssignments() },
                    DrawerItem(Icons.Default.Grade, "Grade Submissions", "Review & grade student work",
                Color(0xFFD97706), Color(0xFFFEF3C7)) { onSelectTab(2) },
            DrawerItem(Icons.Default.CalendarMonth, "Class Timetable", "Create & manage schedule",
                Color(0xFFDC2626), Color(0xFFFEE2E2)) { onClose(); onSchedule() },
            DrawerItem(Icons.Default.EventAvailable, "Attendance", "Mark & track attendance",
                Color(0xFF059669), Color(0xFFD1FAE5)) { onClose(); onAttendance() }
        )),
        DrawerSection("COMMUNICATION", listOf(
            DrawerItem(Icons.Default.Campaign, "Announcements", "Send updates to students",
                Color(0xFF7C3AED), Color(0xFFF3E8FF)) { onClose(); onNotices() },
            DrawerItem(Icons.Default.SupportAgent, "Request Form", "Request admin assistance",
                Color(0xFFB45309), Color(0xFFFFE4E6)) { onClose(); onSupport() },
            DrawerItem(Icons.AutoMirrored.Filled.Logout, "Log out", "Sign out of your mentor account",
                Color(0xFFE11D48), Color(0xFFFFE4E6)) { showLogoutConfirmation = true }
        ))
    )

    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        drawerContainerColor = MC_Surface,
        modifier = Modifier.fillMaxHeight()
    ) {
        // Gradient Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF3B0764), Color(0xFF5B21B6), Color(0xFF7C3AED)),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(
                            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY
                        )
                    )
                )
        ) {
            Image(
                painter = painterResource(com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(210.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 24.dp)
                    .graphicsLayer {
                        alpha = 0.14f
                        scaleX = 1.32f
                        scaleY = 1.32f
                        shape = MC_LogoDiamond
                        clip = true
                    }
            )

            if (isLoading) {
                ShimmerSweep(Modifier.matchParentSize())
            }

            // Decorative dots pattern (top right, like reference)
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(3) {
                            Box(
                                Modifier.size(5.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 18.dp)
            ) {
                // Settings gear top-right + avatar row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Avatar + green dot
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color.White, Color(0xFFEDE9FE))
                                    )
                                )
                                .border(3.dp, Color.White.copy(alpha = 0.82f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                mentorInitials,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF5B21B6),
                                letterSpacing = 0.5.sp
                            )
                        }
                        // Green online indicator
                        Box(
                            modifier = Modifier
                                .size(21.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                                .border(3.dp, Color(0xFF5B21B6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Online",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    // Settings icon — navigates to Profile/Settings screen
                }

                Spacer(Modifier.height(12.dp))

                // Name
                Text(
                    summary.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(5.dp))

                // MENTOR chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        "MENTOR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Spacer(Modifier.height(5.dp))

                // Email
                if (summary.email.isNotBlank()) {
                    Text(
                        summary.email,
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Stats row: Cohorts | Students | Pending Tasks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrawerStatChipNew(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Groups,
                        value = "${summary.myCohorts.size}",
                        label = "Cohorts"
                    )
                    DrawerStatChipNew(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.School,
                        value = "${summary.totalStudents}",
                        label = "Students"
                    )
                    DrawerStatChipNew(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        value = "${summary.pendingGrading}",
                        label = "Pending Tasks"
                    )
                }
            }
        }

        // â”€â”€ SCROLLABLE NAV ITEMS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp)
        ) {
            sections.forEach { section ->
                item {
                    Text(
                        section.title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF5B4FCF),
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(
                            start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp
                        )
                    )
                }
                section.items.forEach { item ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = item.onClick)
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Colored icon box
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(item.iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    item.icon, null,
                                    tint = item.iconTint,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            // Title + subtitle
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MC_TextTitle
                                )
                                Text(
                                    item.subtitle,
                                    fontSize = 12.sp,
                                    color = MC_TextSub,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight, null,
                                tint = MC_TextSub.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // â”€â”€ LOG OUT ROW â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // ── SURE PROED FOOTER ─ clearly visible ──────────────
        if (showLogoutConfirmation) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmation = false },
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = Color(0xFFE11D48)
                    )
                },
                title = { Text("Log out?") },
                text = { Text("Are you sure you want to sign out of your mentor account?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutConfirmation = false
                            onLogout()
                        }
                    ) {
                        Text("Log out", color = Color(0xFFE11D48))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun DrawerStatChipNew(modifier: Modifier, icon: ImageVector, value: String, label: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(
            label,
            fontSize = 8.5.sp,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 10.sp
        )
    }
}

// ============================================================
// PRE-SCREENING & INTERVIEWS OVERVIEW CARD
// ============================================================
@Composable
private fun MentorPrescreeningOverviewCard(
    scheduledInterviews: Int,
    pendingEvaluations: Int,
    evaluatedCount: Int,
    onManageInterviews: () -> Unit,
    onScheduleInterview: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF3B0764),
                            Color(0xFF581C87),
                            Color(0xFF6D28D9)
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.VideoCameraFront,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Candidate Pre-Screening",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Scheduled cohort interviews & marks",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                        }
                    }

                    if (pendingEvaluations > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFBBF24),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = "$pendingEvaluations Pending",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF78350F),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrescreeningStatPill(
                        count = scheduledInterviews,
                        label = "Upcoming",
                        icon = Icons.Default.Event,
                        modifier = Modifier.weight(1f)
                    )
                    PrescreeningStatPill(
                        count = pendingEvaluations,
                        label = "Needs Marks",
                        icon = Icons.Default.PendingActions,
                        modifier = Modifier.weight(1f)
                    )
                    PrescreeningStatPill(
                        count = evaluatedCount,
                        label = "Completed",
                        icon = Icons.Default.Verified,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1.3f)
                            .clickable(onClick = onManageInterviews),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MC_Primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "View & Post Marks",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MC_Primary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onScheduleInterview),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Schedule",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrescreeningStatPill(
    count: Int,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
                Text(
                    text = "$count",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================
// TAB 10: CANDIDATE INTERVIEWS & PRE-SCREENING TAB
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MentorInterviewsTab(
    cohorts: List<CohortDto>,
    interviews: List<PreScreeningInterviewDto>,
    applications: List<ApplicationDto>,
    students: List<StudentProfileDto> = emptyList(),
    users: List<UserDto> = emptyList(),
    readOnly: Boolean,
    onScheduleInterview: (appId: String, scheduledAt: String, meetingLink: String, notes: String) -> Unit,
    onEvaluateInterview: (interviewId: String, marks: String, status: String, feedback: String) -> Unit
) {
    var showScheduleDialog by remember { mutableStateOf(false) }
    var evaluatingInterview by remember { mutableStateOf<PreScreeningInterviewDto?>(null) }
    var reschedulingInterview by remember { mutableStateOf<PreScreeningInterviewDto?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filteredInterviews = remember(interviews, selectedFilter, searchQuery) {
        interviews.filter { interview ->
            val matchesFilter = when (selectedFilter) {
                "SCHEDULED" -> interview.status.equals("SCHEDULED", ignoreCase = true) || interview.status.equals("RESCHEDULED", ignoreCase = true)
                "PENDING_EVALUATION" -> interview.score.isNullOrBlank() || interview.status.equals("PENDING", ignoreCase = true) || interview.status.equals("SCHEDULED", ignoreCase = true)
                "PASSED" -> interview.status.equals("PASSED", ignoreCase = true)
                "FAILED" -> interview.status.equals("FAILED", ignoreCase = true)
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                interview.application.contains(searchQuery, ignoreCase = true) ||
                (interview.feedback ?: "").contains(searchQuery, ignoreCase = true) ||
                (interview.score ?: "").contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Candidate Pre-Screening",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MC_TextTitle
                    )
                    Text(
                        text = if (readOnly) "Completed cohort · Evaluation history" else "Schedule interviews & evaluate candidate applications",
                        fontSize = 11.5.sp,
                        color = MC_TextSub
                    )
                }
                Button(
                    onClick = { showScheduleDialog = true },
                    enabled = !readOnly,
                    colors = ButtonDefaults.buttonColors(containerColor = MC_Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Schedule", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by application ID or notes...", fontSize = 13.sp, color = MC_TextSub) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MC_Primary) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Clear search", tint = MC_TextSub)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = SureFormDefaults.outlinedTextFieldColors()
            )
        }

        // Filter Chips Row
        item {
            val filterOptions = listOf(
                "ALL" to "All (${interviews.size})",
                "SCHEDULED" to "Scheduled (${interviews.count { it.status.equals("SCHEDULED", true) }})",
                "PENDING_EVALUATION" to "Pending Evaluation (${interviews.count { it.score.isNullOrBlank() }})",
                "PASSED" to "Passed (${interviews.count { it.status.equals("PASSED", true) }})",
                "FAILED" to "Not Qualified (${interviews.count { it.status.equals("FAILED", true) }})"
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MC_Primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MC_Primary else MC_Border
                        ),
                        modifier = Modifier.clickable { selectedFilter = key }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MC_TextTitle,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // Empty state
        if (filteredInterviews.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MC_Surface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MC_ActivePill,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VideoCameraFront, null, tint = MC_Primary, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching candidate interviews" else "No interviews found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MC_TextTitle
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try a different search query or clear the filter." else "Schedule pre-screening interviews for cohort applicants to evaluate skills and post marks.",
                            fontSize = 12.sp,
                            color = MC_TextSub,
                            textAlign = TextAlign.Center
                        )
                        if (!readOnly && searchQuery.isBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { showScheduleDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MC_Primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Schedule First Interview", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredInterviews, key = { it.id }) { interview ->
                CandidateInterviewCard(
                    interview = interview,
                    applications = applications,
                    students = students,
                    users = users,
                    readOnly = readOnly,
                    onJoinCall = { link ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open meeting link: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEvaluate = { evaluatingInterview = interview },
                    onReschedule = { reschedulingInterview = interview }
                )
            }
        }
    }

    // Schedule Dialog
    if (showScheduleDialog) {
        ScheduleMentorInterviewDialog(
            cohorts = cohorts,
            applications = applications,
            students = students,
            users = users,
            onDismiss = { showScheduleDialog = false },
            onSchedule = { appId, scheduledAt, meetingLink, notes ->
                showScheduleDialog = false
                onScheduleInterview(appId, scheduledAt, meetingLink, notes)
            }
        )
    }

    // Reschedule Dialog
    reschedulingInterview?.let { target ->
        ScheduleMentorInterviewDialog(
            cohorts = cohorts,
            applications = applications,
            students = students,
            users = users,
            initialApplicationId = target.application,
            initialScheduledAt = target.scheduledAt.orEmpty(),
            initialMeetingLink = target.meetingLink.orEmpty(),
            initialNotes = target.feedback.orEmpty(),
            isReschedule = true,
            onDismiss = { reschedulingInterview = null },
            onSchedule = { appId, scheduledAt, meetingLink, notes ->
                reschedulingInterview = null
                onScheduleInterview(appId, scheduledAt, meetingLink, notes)
            }
        )
    }

    // Evaluate Dialog
    evaluatingInterview?.let { target ->
        EvaluateCandidateInterviewDialog(
            interview = target,
            applications = applications,
            students = students,
            users = users,
            onDismiss = { evaluatingInterview = null },
            onSubmit = { marks, status, feedback ->
                evaluatingInterview = null
                onEvaluateInterview(target.id, marks, status, feedback)
            }
        )
    }
}

private fun formatApplicationDisplay(
    appId: String,
    applications: List<ApplicationDto>,
    students: List<StudentProfileDto> = emptyList(),
    users: List<UserDto> = emptyList()
): String {
    if (appId.isBlank()) return "Candidate Application"
    val app = applications.firstOrNull { it.id.equals(appId, ignoreCase = true) }
    if (app != null) {
        val studentName = resolveStudentNameFromId(app.student, students, users)
        val appNum = if (!app.applicationNumber.isNullOrBlank()) "#${app.applicationNumber}" else ""
        if (studentName != "Student") {
            return if (appNum.isNotBlank()) "$studentName ($appNum)" else studentName
        }
        if (!app.applicationNumber.isNullOrBlank()) {
            return "Application #${app.applicationNumber}"
        }
    }
    val cleanId = if (appId.contains("-") || appId.length > 8) {
        "APP-" + appId.replace("-", "").take(8).uppercase(Locale.ROOT)
    } else {
        "APP-" + appId.uppercase(Locale.ROOT)
    }
    return "Application #$cleanId"
}

@Composable
private fun CandidateInterviewCard(
    interview: PreScreeningInterviewDto,
    applications: List<ApplicationDto> = emptyList(),
    students: List<StudentProfileDto> = emptyList(),
    users: List<UserDto> = emptyList(),
    readOnly: Boolean,
    onJoinCall: (String) -> Unit,
    onEvaluate: () -> Unit,
    onReschedule: () -> Unit
) {
    val semanticColors = sureSemanticColors()
    val isEvaluated = !interview.score.isNullOrBlank() || interview.status.equals("PASSED", true) || interview.status.equals("FAILED", true)
    val statusUpper = (interview.status ?: "SCHEDULED").uppercase(Locale.US)

    val (statusLabel, statusBg, statusColor) = when {
        statusUpper == "PASSED" -> Triple("QUALIFIED / PASSED", semanticColors.successContainer, semanticColors.onSuccessContainer)
        statusUpper == "FAILED" -> Triple("NOT QUALIFIED", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        statusUpper == "RESCHEDULED" -> Triple("RESCHEDULED", semanticColors.warningContainer, semanticColors.onWarningContainer)
        isEvaluated -> Triple("EVALUATED", semanticColors.infoContainer, semanticColors.onInfoContainer)
        else -> Triple("SCHEDULED", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MC_Surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Candidate Application Title + Status Badge
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
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MC_Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = formatApplicationDisplay(interview.application, applications, students, users),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MC_TextTitle
                        )
                        Text(
                            text = "Prescreening Interview",
                            fontSize = 11.sp,
                            color = MC_TextSub
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Date & Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MC_Primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = interview.scheduledAt?.takeIf { it.isNotBlank() } ?: "Date to be scheduled",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MC_TextTitle
                )
            }

            // Meeting Link Row (if available)
            if (!interview.meetingLink.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = semanticColors.successContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            tint = semanticColors.onSuccessContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = interview.meetingLink,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = semanticColors.onSuccessContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { onJoinCall(interview.meetingLink) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Join Call", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Evaluation / Score Box
            Spacer(Modifier.height(10.dp))
            if (!interview.score.isNullOrBlank() || !interview.feedback.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (!interview.score.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Grade, null, tint = Color(0xFFD97706), modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Score / Rating: ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MC_TextSub
                                )
                                Text(
                                    text = interview.score,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFD97706)
                                )
                            }
                        }
                        if (!interview.feedback.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Interviewer Notes: \"${interview.feedback}\"",
                                fontSize = 12.sp,
                                color = MC_TextTitle,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = semanticColors.warningContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PendingActions, null, tint = semanticColors.onWarningContainer, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Interview scheduled • Pending candidate evaluation",
                            fontSize = 11.5.sp,
                            color = semanticColors.onWarningContainer
                        )
                    }
                }
            }

            // Action Buttons
            if (!readOnly) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onEvaluate,
                        colors = ButtonDefaults.buttonColors(containerColor = MC_Primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isEvaluated) "Edit Evaluation" else "Evaluate Candidate",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onReschedule,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MC_Border),
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = MC_TextTitle, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reschedule", fontSize = 12.sp, color = MC_TextTitle)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleMentorInterviewDialog(
    cohorts: List<CohortDto>,
    applications: List<ApplicationDto> = emptyList(),
    students: List<StudentProfileDto> = emptyList(),
    users: List<UserDto> = emptyList(),
    initialApplicationId: String = "",
    initialScheduledAt: String = "",
    initialMeetingLink: String = "",
    initialNotes: String = "",
    isReschedule: Boolean = false,
    onDismiss: () -> Unit,
    onSchedule: (applicationId: String, scheduledAt: String, meetingLink: String, notes: String) -> Unit
) {
    var applicationId by remember { mutableStateOf(initialApplicationId) }
    val initialDate = initialScheduledAt.take(10).takeIf { it.length == 10 } ?: SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
    val initialTime = if (initialScheduledAt.length >= 16) initialScheduledAt.substring(11, 16) else "10:00"
    var date by remember { mutableStateOf(initialDate) }
    var time by remember { mutableStateOf(initialTime) }
    var meetingLink by remember { mutableStateOf(if (initialMeetingLink.isNotBlank()) initialMeetingLink else "https://meet.google.com/iup-ujwv-ryg") }
    var notes by remember { mutableStateOf(initialNotes) }
    var expandedAppDropdown by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun showDatePicker() {
        val initial = Calendar.getInstance()
        if (date.isNotBlank()) {
            runCatching {
                val parts = date.split("-").mapNotNull(String::toIntOrNull)
                if (parts.size == 3) initial.set(parts[0], parts[1] - 1, parts[2])
            }
        }
        DatePickerDialog(
            context,
            { _, year, month, day -> date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day) },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showTimePicker() {
        val now = Calendar.getInstance()
        val parts = time.split(":").mapNotNull(String::toIntOrNull)
        val initialHour = parts.getOrNull(0) ?: now.get(Calendar.HOUR_OF_DAY)
        val initialMinute = parts.getOrNull(1) ?: now.get(Calendar.MINUTE)
        TimePickerDialog(
            context,
            { _, hour, minute -> time = String.format(Locale.US, "%02d:%02d", hour, minute) },
            initialHour,
            initialMinute,
            false
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isReschedule) "Reschedule Candidate Interview" else "Schedule Candidate Pre-Screening",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MC_TextTitle
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Set up a video screening call with the applicant to verify background, technical fundamentals, and cohort fit.",
                    fontSize = 11.5.sp,
                    color = MC_TextSub
                )

                if (isReschedule) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, null, tint = MC_Primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Candidate Application",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MC_TextSub
                                )
                                Text(
                                    text = formatApplicationDisplay(applicationId, applications, students, users),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MC_TextTitle
                                )
                            }
                        }
                    }
                } else {
                    if (applications.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val selectedApp = applications.firstOrNull { it.id == applicationId }
                            val displayText = selectedApp?.let {
                                val num = it.applicationNumber ?: ("APP-" + it.id.take(8).uppercase())
                                "$num · ${it.student ?: "Applicant"}"
                            } ?: if (applicationId.isNotBlank()) formatApplicationDisplay(applicationId, applications, students, users) else ""

                            OutlinedTextField(
                                value = displayText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Candidate Application") },
                                placeholder = { Text("Choose applicant to schedule") },
                                trailingIcon = {
                                    IconButton(onClick = { expandedAppDropdown = !expandedAppDropdown }) {
                                        Icon(Icons.Default.ArrowDropDown, "Select applicant")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable { expandedAppDropdown = true }
                            )

                            DropdownMenu(
                                expanded = expandedAppDropdown,
                                onDismissRequest = { expandedAppDropdown = false }
                            ) {
                                applications.forEach { app ->
                                    val appNum = app.applicationNumber ?: ("APP-" + app.id.take(8).uppercase())
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(appNum, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MC_TextTitle)
                                                Text(app.student ?: "Applicant", fontSize = 11.5.sp, color = MC_TextSub)
                                            }
                                        },
                                        onClick = {
                                            applicationId = app.id
                                            expandedAppDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = applicationId,
                            onValueChange = { applicationId = it },
                            label = { Text("Candidate Application ID / Number") },
                            placeholder = { Text("e.g. APP-2026-001") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                MentorDateTimePickerField(
                    value = date,
                    label = "Interview Date",
                    placeholder = "Select date (YYYY-MM-DD)",
                    icon = Icons.Default.CalendarMonth,
                    onClick = ::showDatePicker
                )

                MentorDateTimePickerField(
                    value = time,
                    label = "Interview Time",
                    placeholder = "Select time (HH:MM)",
                    icon = Icons.Default.AccessTime,
                    onClick = ::showTimePicker
                )

                OutlinedTextField(
                    value = meetingLink,
                    onValueChange = { meetingLink = it },
                    label = { Text("Google Meet / Video Link") },
                    placeholder = { Text("https://meet.google.com/...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Videocam, null, tint = MC_Primary) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Topic / Screening Notes (Optional)") },
                    placeholder = { Text("e.g. Technical Screening & Problem Solving") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fullDateTime = if (date.isNotBlank()) "$date $time" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())
                    onSchedule(applicationId.trim(), fullDateTime, meetingLink.trim(), notes.trim())
                },
                enabled = applicationId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)
            ) {
                Text(if (isReschedule) "Update Schedule" else "Schedule Interview")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EvaluateCandidateInterviewDialog(
    interview: PreScreeningInterviewDto,
    applications: List<ApplicationDto> = emptyList(),
    students: List<StudentProfileDto> = emptyList(),
    users: List<UserDto> = emptyList(),
    onDismiss: () -> Unit,
    onSubmit: (marks: String, status: String, feedback: String) -> Unit
) {
    var marks by remember { mutableStateOf(interview.score.orEmpty()) }
    var status by remember { mutableStateOf(if (interview.status.equals("FAILED", true)) "FAILED" else if (interview.status.equals("PASSED", true)) "PASSED" else "PASSED") }
    var feedback by remember { mutableStateOf(interview.feedback.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Candidate Interview Evaluation", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MC_TextTitle)
                Text(
                    text = formatApplicationDisplay(interview.application, applications, students, users),
                    fontSize = 12.sp,
                    color = MC_Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Submit candidate score/rating, qualification outcome, and interview evaluation remarks.",
                    fontSize = 11.5.sp,
                    color = MC_TextSub
                )

                // Marks / Score input
                OutlinedTextField(
                    value = marks,
                    onValueChange = { marks = it },
                    label = { Text("Score / Rating Obtained") },
                    placeholder = { Text("e.g. 85/100, 8.5/10, or Grade A") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Grade, null, tint = Color(0xFFD97706)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Outcome / Status Selector
                Text("Qualification Outcome:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MC_TextTitle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusList = listOf(
                        "PASSED" to ("PASSED / QUALIFIED" to Color(0xFF059669)),
                        "FAILED" to ("NOT QUALIFIED" to Color(0xFFDC2626)),
                        "PENDING" to ("UNDER REVIEW" to Color(0xFFD97706))
                    )
                    statusList.forEach { (code, pair) ->
                        val (label, color) = pair
                        val isSelected = status == code
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) color else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (isSelected) color else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { status = code }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Qualitative Feedback / Notes
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = { Text("Interview Feedback & Remarks") },
                    placeholder = { Text("Candidate demonstrated strong problem-solving skills, basic SQL knowledge, communicative and eager to learn.") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(marks.trim(), status, feedback.trim()) },
                enabled = marks.isNotBlank() || feedback.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MC_Primary)
            ) {
                Text("Submit Evaluation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
