package com.example.suretouchapp.ui.screens.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.api.fetchAllAttendancePages
import com.example.suretouchapp.data.model.AbsenceWarningDto
import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.model.CohortDto
import com.example.suretouchapp.data.model.StudentProfileDto
import com.example.suretouchapp.data.repository.VolunteerRepository
import com.example.suretouchapp.data.repository.StudentSessionAttendance
import com.example.suretouchapp.data.repository.StudentStatisticsRepository
import com.example.suretouchapp.data.repository.calculateStudentAttendancePercentage
import com.example.suretouchapp.data.repository.isCancelledSession
import com.example.suretouchapp.data.repository.isCompletedSession
import com.example.suretouchapp.data.repository.studentAttendance
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.theme.SureFormDefaults
import com.example.suretouchapp.ui.theme.sureSemanticColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val AttendanceCanvas @Composable get() = MaterialTheme.colorScheme.background
private val AttendancePurple @Composable get() = MaterialTheme.colorScheme.primary
private val AttendanceInk @Composable get() = MaterialTheme.colorScheme.onSurface
private val AttendanceMuted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val AttendanceBorder @Composable get() = MaterialTheme.colorScheme.outlineVariant

private fun resolveStudentName(student: StudentProfileDto): String {
    val userFullName = listOfNotNull(
        student.user?.firstName ?: student.userFirstName ?: student.firstName,
        student.user?.lastName ?: student.userLastName ?: student.lastName
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

    val email = student.user?.email ?: student.userEmail ?: student.email
    if (!email.isNullOrBlank()) {
        val handle = email.substringBefore("@").replace(".", " ").replace("_", " ")
        return handle.split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    val code = student.studentCode?.takeIf { it.isNotBlank() }
    return if (code != null) "Student ($code)" else "Student Profile"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(tokenManager: TokenManager, onNavigateBack: () -> Unit) {
    val isStudent = tokenManager.getUserRole().equals("STUDENT", ignoreCase = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    var records by remember { mutableStateOf<List<AttendanceDto>>(emptyList()) }
    var allStudents by remember { mutableStateOf<List<StudentProfileDto>>(emptyList()) }
    var allCohorts by remember { mutableStateOf<List<CohortDto>>(emptyList()) }
    var selectedSession by remember { mutableStateOf<AttendanceDto?>(null) }
    var warnings by remember { mutableStateOf<List<AbsenceWarningDto>>(emptyList()) }
    var selectedWarningForApology by remember { mutableStateOf<AbsenceWarningDto?>(null) }
    var apologyInputText by remember { mutableStateOf("") }
    var isSubmittingApology by remember { mutableStateOf(false) }
    var requestPermissionSession by remember { mutableStateOf<AttendanceDto?>(null) }
    var permissionReasonText by remember { mutableStateOf("") }
    var isSubmittingPermission by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val semanticColors = sureSemanticColors()
    var assignedCohortCount by remember { mutableIntStateOf(0) }
    var authoritativePercentage by remember { mutableStateOf<Double?>(null) }

    suspend fun loadAttendance() {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val api = ApiClient.getService(tokenManager)
            val assignedCohorts = when {
                isStudent -> emptyList()
                tokenManager.isMentor() -> api.getMentorProfiles()
                    .takeIf { it.isSuccessful }
                    ?.body()
                    ?.results
                    ?.firstOrNull()
                    ?.assignedCohorts
                    ?.map { it.id to it.code }
                    .orEmpty()
                else -> VolunteerRepository(tokenManager).loadProfile().assignedCohorts
                    .map { it.id to it.code }
            }
            val assignedIds = assignedCohorts.map { it.first }.filter(String::isNotBlank).toSet()
            val assignedCodes = assignedCohorts.mapNotNull { it.second }.filter(String::isNotBlank).toSet()
            assignedCohortCount = assignedCohorts.size

            val attendanceRecords: List<AttendanceDto>
            val studentsRes: retrofit2.Response<com.example.suretouchapp.data.model.PaginatedResponse<StudentProfileDto>>
            val cohortsRes: retrofit2.Response<com.example.suretouchapp.data.model.PaginatedResponse<CohortDto>>
            val warningsRes: retrofit2.Response<List<AbsenceWarningDto>>?

            coroutineScope {
                val a = async { api.fetchAllAttendancePages() }
                val s = async { api.getStudents() }
                val c = async { api.getCohorts() }
                val w = async { if (isStudent) runCatching { api.getAbsenceWarnings() }.getOrNull() else null }
                attendanceRecords = a.await()
                studentsRes = s.await()
                cohortsRes = c.await()
                warningsRes = w.await()
            }

            records = attendanceRecords.filter { record ->
                isStudent || record.cohort in assignedIds || record.cohortCode in assignedCodes
            }.sortedWith(compareByDescending<AttendanceDto> { it.date }.thenByDescending { it.startTime })
            allStudents = studentsRes.body()?.results.orEmpty()
            allCohorts = cohortsRes.body()?.results.orEmpty()
            warnings = warningsRes?.takeIf { it.isSuccessful }?.body().orEmpty().filter { !it.resolved }
            authoritativePercentage = if (isStudent) {
                runCatching { StudentStatisticsRepository(tokenManager).load()?.attendancePercentage }.getOrNull()
            } else null
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
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadAttendance() }

    val currentStudent = allStudents.firstOrNull {
        it.user?.email.equals(tokenManager.getUserEmail(), ignoreCase = true) ||
            it.studentCode.equals(tokenManager.getStudentCode(), ignoreCase = true)
    }
    val studentIdentifiers = setOfNotNull(
        currentStudent?.id?.takeIf(String::isNotBlank),
        currentStudent?.userId?.takeIf(String::isNotBlank),
        currentStudent?.studentCode?.takeIf(String::isNotBlank),
        tokenManager.getStudentCode().takeIf(String::isNotBlank)
    )
    val recordedStudentStates = records.map { it.studentAttendance(studentIdentifiers) }
        .filter {
            it == StudentSessionAttendance.PRESENT ||
                it == StudentSessionAttendance.BELOW_THRESHOLD ||
                it == StudentSessionAttendance.ABSENT
        }
    val presentCount = recordedStudentStates.count { it == StudentSessionAttendance.PRESENT }
    val calculatedPercentage = calculateStudentAttendancePercentage(records, studentIdentifiers)
    val percentage = if (!isStudent) 0 else (authoritativePercentage ?: calculatedPercentage ?: 0.0).toInt()
    val cohort = tokenManager.getCohortCode().ifBlank { "Pending assignment" }

    BackendConnectionGate(
        isLoading = isLoading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Attendance...",
        onRetry = { scope.launch { loadAttendance() } },
        onLogout = null
    ) {
        Scaffold(
            containerColor = AttendanceCanvas,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Attendance", fontWeight = FontWeight.Bold, color = AttendanceInk)
                            Text(
                                if (isStudent) "Cohort $cohort • Your Participation"
                                else "$assignedCohortCount Assigned Cohorts • Class Participation",
                                fontSize = 11.5.sp,
                                color = AttendanceMuted
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AttendancePurple)
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { loadAttendance() } }) {
                            Icon(Icons.Default.Refresh, "Refresh", tint = AttendancePurple)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    SureTrustLoadingIndicator(message = "Loading attendance records...")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Student Attendance Overview Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, AttendanceBorder)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(58.dp), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(if (isStudent) "$percentage%" else records.size.toString(), fontSize = 17.sp, fontWeight = FontWeight.Black, color = AttendancePurple)
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (isStudent) "Overall Attendance" else "Assigned Cohorts Overview",
                                        fontWeight = FontWeight.Bold,
                                        color = AttendanceInk,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        if (isStudent) {
                                            if (percentage >= 75) "Great job! Keep attending regularly."
                                            else "$presentCount attended of ${recordedStudentStates.size} sessions (Min. 75% required)"
                                        } else {
                                            "${records.count { it.isCompletedSession() }} completed • ${records.count { !it.isCompletedSession() && !it.isCancelledSession() }} upcoming sessions"
                                        },
                                        fontSize = 12.sp,
                                        color = AttendanceMuted
                                    )
                                }
                            }
                        }
                    }

                    // Absence Warning Banner for Students
                    if (isStudent && warnings.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Attendance Attention Required",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "You have ${warnings.size} absence notice(s). Please submit a brief explanation so your mentors can review and update your record.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    warnings.forEach { warn ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(warn.sessionTitle ?: "Missed Session", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = AttendanceInk)
                                                Text("Date: ${warn.classDate ?: "Past class"} • Status: ${warn.status}", fontSize = 11.sp, color = AttendanceMuted)
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    selectedWarningForApology = warn
                                                    apologyInputText = warn.apologyText.orEmpty()
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Explain", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (records.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, AttendanceBorder)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.EventAvailable, null, tint = AttendancePurple, modifier = Modifier.size(42.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("No Attendance Records Yet", fontWeight = FontWeight.Bold, color = AttendanceInk, fontSize = 15.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (isStudent) "You're all set! As soon as your classes begin, your attendance history will be recorded right here."
                                        else "No class sessions scheduled for your assigned cohorts yet.",
                                        fontSize = 12.sp,
                                        color = AttendanceMuted,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                if (isStudent) "Class Attendance History" else "Scheduled & Completed Classes",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = AttendanceInk
                            )
                        }
                    }

                    items(records, key = { it.id }) { record ->
                        val completed = record.isCompletedSession()
                        val isCancelled = record.isCancelledSession()
                        val isRescheduled = record.classStatus.equals("RESCHEDULED", true)
                        val studentState = record.studentAttendance(studentIdentifiers)
                        val positive = if (isStudent) studentState == StudentSessionAttendance.PRESENT else completed

                        val cohortObj = allCohorts.firstOrNull { it.id == record.cohort || it.code == record.cohortCode }
                        val cohortDisplayName = cohortObj?.name?.takeIf(String::isNotBlank)
                            ?: record.cohortCode
                            ?: "Assigned Cohort"
                        val cohortDisplayCode = cohortObj?.code ?: record.cohortCode ?: ""

                        val cohortStudents = allStudents.filter {
                            it.cohortId == record.cohort ||
                                (!cohortDisplayCode.isBlank() && it.cohortCode.equals(cohortDisplayCode, true))
                        }
                        val attendeeCount = record.attendees.size
                        val totalCount = cohortStudents.size.coerceAtLeast(attendeeCount)

                        Surface(
                            onClick = {
                                if (!isStudent) selectedSession = record
                            },
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AttendanceBorder),
                            shadowElevation = 1.dp
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when {
                                        isCancelled -> Icons.Default.Cancel
                                        positive -> Icons.Default.CheckCircle
                                        isRescheduled -> Icons.Default.Update
                                        else -> Icons.Default.Schedule
                                    },
                                    null,
                                    tint = when {
                                        isCancelled -> MaterialTheme.colorScheme.error
                                        positive -> semanticColors.success
                                        isRescheduled -> semanticColors.warning
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(record.sessionTitle ?: "Class session", fontWeight = FontWeight.Bold, color = AttendanceInk)
                                    Text(
                                        listOfNotNull(
                                            cohortDisplayName,
                                            if (cohortDisplayCode.isNotBlank() && cohortDisplayCode != cohortDisplayName) "($cohortDisplayCode)" else null,
                                            record.date,
                                            record.startTime?.take(5)
                                        ).joinToString(" • "),
                                        fontSize = 11.sp,
                                        color = AttendanceMuted
                                    )
                                    if (!isStudent) {
                                        Text(
                                            "Attendance: $attendeeCount / $totalCount present • Tap to view roster",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AttendancePurple
                                        )
                                    }
                                    record.notes?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = AttendanceMuted) }
                                }
                                if (isStudent) {
                                    val stateLabel = when (studentState) {
                                        StudentSessionAttendance.PRESENT -> "PRESENT"
                                        StudentSessionAttendance.BELOW_THRESHOLD -> "PARTIAL"
                                        StudentSessionAttendance.ABSENT -> "ABSENT"
                                        StudentSessionAttendance.CANCELLED -> "CANCELLED"
                                        StudentSessionAttendance.PENDING -> "UPCOMING"
                                    }
                                    val stateColor = when (studentState) {
                                        StudentSessionAttendance.PRESENT -> semanticColors.success
                                        StudentSessionAttendance.BELOW_THRESHOLD -> semanticColors.warning
                                        StudentSessionAttendance.ABSENT -> MaterialTheme.colorScheme.error
                                        StudentSessionAttendance.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                                        StudentSessionAttendance.PENDING -> AttendancePurple
                                    }
                                    val stateContainer = when (studentState) {
                                        StudentSessionAttendance.PRESENT -> semanticColors.successContainer
                                        StudentSessionAttendance.BELOW_THRESHOLD -> semanticColors.warningContainer
                                        StudentSessionAttendance.ABSENT -> MaterialTheme.colorScheme.errorContainer
                                        StudentSessionAttendance.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
                                        StudentSessionAttendance.PENDING -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Surface(
                                            color = stateContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                stateLabel,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = stateColor,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                        if (studentState == StudentSessionAttendance.ABSENT || studentState == StudentSessionAttendance.BELOW_THRESHOLD) {
                                            TextButton(
                                                onClick = {
                                                    requestPermissionSession = record
                                                    permissionReasonText = ""
                                                },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Request Review", fontSize = 10.sp, color = AttendancePurple)
                                            }
                                        }
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Surface(
                                            color = when {
                                                isCancelled -> MaterialTheme.colorScheme.errorContainer
                                                completed -> semanticColors.successContainer
                                                isRescheduled -> semanticColors.warningContainer
                                                else -> MaterialTheme.colorScheme.primaryContainer
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
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
                                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                        if (!completed && !isCancelled) {
                                            IconButton(onClick = {
                                                scope.launch {
                                                    val response = runCatching {
                                                        ApiClient.getService(tokenManager).patchAttendance(record.id, mapOf("conducted" to true, "class_status" to "COMPLETED"))
                                                    }.getOrNull()
                                                    if (response?.isSuccessful == true) {
                                                        records = records.map { if (it.id == record.id) it.copy(conducted = true, classStatus = "COMPLETED", effectiveStatus = "COMPLETED") else it }
                                                        snackbarHostState.showSnackbar("Class marked as completed.")
                                                    } else {
                                                        snackbarHostState.showSnackbar(if (response?.code() == 403) "Mentor authorization required to update attendance." else "Unable to update session status.")
                                                    }
                                                }
                                            }) { Icon(Icons.Default.CheckCircleOutline, "Mark Completed", tint = AttendancePurple) }
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

    // Student Apology Submission Dialog
    selectedWarningForApology?.let { warn ->
        AlertDialog(
            onDismissRequest = { if (!isSubmittingApology) selectedWarningForApology = null },
            icon = { Icon(Icons.Default.EditNote, null, tint = AttendancePurple) },
            title = { Text("Submit Absence Explanation", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Session: ${warn.sessionTitle ?: "Class"}\nPlease provide your sincere explanation or reason for missing this class. Mentors review requests promptly.",
                        fontSize = 12.sp,
                        color = AttendanceMuted
                    )
                    OutlinedTextField(
                        value = apologyInputText,
                        onValueChange = { apologyInputText = it },
                        placeholder = { Text("Write your reason or apology...") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SureFormDefaults.outlinedTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (apologyInputText.isNotBlank()) {
                            isSubmittingApology = true
                            scope.launch {
                                val res = runCatching {
                                    ApiClient.getService(tokenManager).resolveWarning(
                                        mapOf("warning_id" to warn.id, "apology_text" to apologyInputText.trim())
                                    )
                                }.getOrNull()
                                isSubmittingApology = false
                                if (res?.isSuccessful == true) {
                                    snackbarHostState.showSnackbar("Explanation submitted. Your mentor will review it shortly.")
                                    selectedWarningForApology = null
                                    loadAttendance()
                                } else {
                                    snackbarHostState.showSnackbar("Failed to submit explanation. Please try again.")
                                }
                            }
                        }
                    },
                    enabled = !isSubmittingApology && apologyInputText.isNotBlank()
                ) {
                    Text(if (isSubmittingApology) "Submitting..." else "Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedWarningForApology = null }, enabled = !isSubmittingApology) {
                    Text("Cancel")
                }
            }
        )
    }

    // Student Late-Join / Permission Request Dialog
    requestPermissionSession?.let { sess ->
        AlertDialog(
            onDismissRequest = { if (!isSubmittingPermission) requestPermissionSession = null },
            icon = { Icon(Icons.Default.HelpOutline, null, tint = AttendancePurple) },
            title = { Text("Request Attendance Review", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Class: ${sess.sessionTitle ?: "Session"}\nIf you attended or faced connectivity issues, submit your note here for the mentor's review.",
                        fontSize = 12.sp,
                        color = AttendanceMuted
                    )
                    OutlinedTextField(
                        value = permissionReasonText,
                        onValueChange = { permissionReasonText = it },
                        placeholder = { Text("Explain what happened or request permission...") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SureFormDefaults.outlinedTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (permissionReasonText.isNotBlank()) {
                            isSubmittingPermission = true
                            scope.launch {
                                val res = runCatching {
                                    ApiClient.getService(tokenManager).requestLateJoinPermission(
                                        mapOf("session_id" to sess.id, "reason" to permissionReasonText.trim())
                                    )
                                }.getOrNull()
                                isSubmittingPermission = false
                                if (res?.isSuccessful == true) {
                                    snackbarHostState.showSnackbar("Attendance review request submitted successfully.")
                                    requestPermissionSession = null
                                    loadAttendance()
                                } else {
                                    val err = res?.errorBody()?.string() ?: "Failed to submit request."
                                    snackbarHostState.showSnackbar(if (err.contains("already", true)) "A request has already been submitted." else "Unable to submit request.")
                                }
                            }
                        }
                    },
                    enabled = !isSubmittingPermission && permissionReasonText.isNotBlank()
                ) {
                    Text(if (isSubmittingPermission) "Submitting..." else "Submit Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { requestPermissionSession = null }, enabled = !isSubmittingPermission) {
                    Text("Cancel")
                }
            }
        )
    }

    // Mentor/Volunteer Student Attendance Roster Dialog
    selectedSession?.let { session ->
        val cohortObj = allCohorts.firstOrNull { it.id == session.cohort || it.code == session.cohortCode }
        val cohortCode = cohortObj?.code ?: session.cohortCode.orEmpty()
        val cohortStudents = remember(session, allStudents) {
            allStudents.filter {
                it.cohortId == session.cohort ||
                    (cohortCode.isNotBlank() && it.cohortCode.equals(cohortCode, true))
            }
        }
        var searchStudent by remember { mutableStateOf("") }
        val attendeeIdSet = remember(session) { session.attendees.toSet() }

        val filteredCohortStudents = remember(cohortStudents, searchStudent) {
            if (searchStudent.isBlank()) cohortStudents
            else cohortStudents.filter { s ->
                val name = resolveStudentName(s)
                val fn = (s.user?.firstName ?: s.userFirstName ?: s.firstName).orEmpty()
                val ln = (s.user?.lastName ?: s.userLastName ?: s.lastName).orEmpty()
                val em = (s.user?.email ?: s.userEmail ?: s.email).orEmpty()
                val sc = s.studentCode.orEmpty()
                name.contains(searchStudent, ignoreCase = true) ||
                    fn.contains(searchStudent, ignoreCase = true) ||
                    ln.contains(searchStudent, ignoreCase = true) ||
                    em.contains(searchStudent, ignoreCase = true) ||
                    sc.contains(searchStudent, ignoreCase = true)
            }
        }

        val presentCountInSession = cohortStudents.count {
            it.id in attendeeIdSet || it.userId in attendeeIdSet
        }.coerceAtLeast(session.attendees.size)
        val absentCountInSession = (cohortStudents.size - presentCountInSession).coerceAtLeast(0)

        AlertDialog(
            onDismissRequest = { selectedSession = null },
            icon = { Icon(Icons.Default.PeopleAlt, null, tint = AttendancePurple) },
            title = {
                Column {
                    Text(session.sessionTitle ?: "Class Attendance Details", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("${cohortObj?.name ?: cohortCode.ifBlank { "Cohort" }} • ${session.date}", fontSize = 12.sp, color = AttendanceMuted)
                }
            },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cohortStudents.size.toString(), fontWeight = FontWeight.Black, color = AttendancePurple, fontSize = 16.sp)
                                Text("Enrolled", fontSize = 10.sp, color = AttendanceMuted)
                            }
                        }
                        Surface(color = semanticColors.successContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(presentCountInSession.toString(), fontWeight = FontWeight.Black, color = semanticColors.success, fontSize = 16.sp)
                                Text("Present", fontSize = 10.sp, color = AttendanceMuted)
                            }
                        }
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(absentCountInSession.toString(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
                                Text("Absent", fontSize = 10.sp, color = AttendanceMuted)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchStudent,
                        onValueChange = { searchStudent = it },
                        placeholder = { Text("Search student name or email...") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = AttendancePurple) },
                        trailingIcon = {
                            if (searchStudent.isNotBlank()) {
                                IconButton(onClick = { searchStudent = "" }) { Icon(Icons.Default.Clear, null) }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SureFormDefaults.outlinedTextFieldColors()
                    )

                    if (cohortStudents.isEmpty()) {
                        Text("No students are mapped to this cohort in the records.", fontSize = 12.sp, color = AttendanceMuted, modifier = Modifier.padding(8.dp))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
                            items(filteredCohortStudents, key = { it.id }) { student ->
                                val isPresent = student.id in attendeeIdSet || student.userId in attendeeIdSet
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, AttendanceBorder)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(34.dp).background(if (isPresent) semanticColors.successContainer else MaterialTheme.colorScheme.errorContainer, CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(
                                                if (isPresent) Icons.Default.Check else Icons.Default.Close,
                                                null,
                                                tint = if (isPresent) semanticColors.success else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            val name = resolveStudentName(student)
                                            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = AttendanceInk)
                                            Spacer(Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = AttendancePurple.copy(alpha = 0.08f),
                                                    border = BorderStroke(1.dp, AttendancePurple.copy(alpha = 0.2f))
                                                ) {
                                                    Text(
                                                        text = student.studentCode ?: "STU-ID",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AttendancePurple,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                                val email = student.user?.email ?: student.userEmail ?: student.email
                                                if (!email.isNullOrBlank()) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(email, fontSize = 11.sp, color = AttendanceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                        Surface(
                                            color = if (isPresent) semanticColors.successContainer else MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                if (isPresent) "PRESENT" else "ABSENT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPresent) semanticColors.success else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedSession = null }) { Text("Close") } }
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
