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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.model.CohortDto
import com.example.suretouchapp.data.model.StudentProfileDto
import com.example.suretouchapp.data.repository.VolunteerRepository
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val AttendanceCanvas = Color(0xFFF6F7FC)
private val AttendancePurple = Color(0xFF6C2BD9)
private val AttendanceInk = Color(0xFF18213D)
private val AttendanceMuted = Color(0xFF64748B)
private val AttendanceBorder = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(tokenManager: TokenManager, onNavigateBack: () -> Unit) {
    val isStudent = tokenManager.getUserRole().equals("STUDENT", ignoreCase = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    var records by remember { mutableStateOf<List<AttendanceDto>>(emptyList()) }
    var allStudents by remember { mutableStateOf<List<StudentProfileDto>>(emptyList()) }
    var allCohorts by remember { mutableStateOf<List<CohortDto>>(emptyList()) }
    var selectedSession by remember { mutableStateOf<AttendanceDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var assignedCohortCount by remember { mutableIntStateOf(0) }

    suspend fun loadAttendance() {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val api = ApiClient.getService(tokenManager)
            val assignedProfile = if (isStudent) null else VolunteerRepository(tokenManager).loadProfile()
            val assignedIds = assignedProfile?.assignedCohorts?.map { it.id }?.filter(String::isNotBlank)?.toSet().orEmpty()
            val assignedCodes = assignedProfile?.assignedCohorts?.map { it.code }?.filter(String::isNotBlank)?.toSet().orEmpty()
            assignedCohortCount = assignedIds.size

            val (attendanceRes, studentsRes, cohortsRes) = coroutineScope {
                val a = async { api.getAttendance() }
                val s = async { api.getStudents() }
                val c = async { api.getCohorts() }
                Triple(a.await(), s.await(), c.await())
            }

            if (attendanceRes.isSuccessful) {
                records = attendanceRes.body()?.results.orEmpty().filter { record ->
                    isStudent || record.cohort in assignedIds || record.cohortCode in assignedCodes
                }.sortedWith(compareByDescending<AttendanceDto> { it.date }.thenByDescending { it.startTime })
                allStudents = studentsRes.body()?.results.orEmpty()
                allCohorts = cohortsRes.body()?.results.orEmpty()
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

    LaunchedEffect(Unit) { loadAttendance() }

    val presentCount = records.count { it.present }
    val percentage = if (!isStudent || records.isEmpty()) 0 else presentCount * 100 / records.size
    val cohort = tokenManager.getCohortCode().ifBlank { "Assignment pending" }

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
                            Text(if (isStudent) "Cohort $cohort" else "$assignedCohortCount assigned cohorts • live attendance metrics", fontSize = 11.sp, color = AttendanceMuted)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AttendancePurple)
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { loadAttendance() } }) {
                            Icon(Icons.Default.Refresh, "Refresh attendance", tint = AttendancePurple)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    SureTrustLoadingIndicator(message = "Loading attendance")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, AttendanceBorder)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(58.dp), CircleShape, color = Color(0xFFF3E8FF)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(if (isStudent) "$percentage%" else records.size.toString(), fontSize = 17.sp, fontWeight = FontWeight.Black, color = AttendancePurple)
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (isStudent) "Current attendance" else "Cohort attendance monitor", fontWeight = FontWeight.Bold, color = AttendanceInk)
                                    Text(
                                        if (isStudent) "$presentCount attended • ${records.size} recorded sessions"
                                        else "${records.count { it.classStatus.equals("COMPLETED", true) || it.effectiveStatus.equals("COMPLETED", true) || it.conducted }} completed • ${records.count { !it.classStatus.equals("COMPLETED", true) && !it.effectiveStatus.equals("COMPLETED", true) && !it.conducted }} scheduled",
                                        fontSize = 12.sp,
                                        color = AttendanceMuted
                                    )
                                }
                            }
                        }
                    }

                    if (records.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.EventAvailable, null, tint = AttendancePurple, modifier = Modifier.size(38.dp))
                                    Spacer(Modifier.height(9.dp))
                                    Text("No attendance records", fontWeight = FontWeight.Bold, color = AttendanceInk)
                                    Text("Records will appear after assigned cohort sessions begin.", fontSize = 12.sp, color = AttendanceMuted)
                                }
                            }
                        }
                    } else {
                        item {
                            Text(if (isStudent) "Session history" else "Cohort sessions (click to view student attendees)", fontWeight = FontWeight.Bold, color = AttendanceInk)
                        }
                    }

                    items(records, key = { it.id }) { record ->
                        val completed = record.classStatus.equals("COMPLETED", true) || record.effectiveStatus.equals("COMPLETED", true) || record.conducted
                        val isCancelled = record.classStatus.equals("CANCELLED", true)
                        val isRescheduled = record.classStatus.equals("RESCHEDULED", true)
                        val positive = if (isStudent) record.present else completed

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
                            color = Color.White,
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
                                        isCancelled -> Color(0xFFDC2626)
                                        positive -> Color(0xFF059669)
                                        isRescheduled -> Color(0xFFD97706)
                                        else -> Color(0xFF6C2BD9)
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
                                            "Attendance: $attendeeCount / $totalCount present • Click for student list",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AttendancePurple
                                        )
                                    }
                                    record.notes?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = AttendanceMuted) }
                                }
                                if (isStudent) {
                                    Surface(
                                        color = if (record.present) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            if (record.present) "PRESENT" else "ABSENT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (record.present) Color(0xFF059669) else Color(0xFFDC2626),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Surface(
                                            color = when {
                                                isCancelled -> Color(0xFFFEE2E2)
                                                completed -> Color(0xFFD1FAE5)
                                                isRescheduled -> Color(0xFFFEF3C7)
                                                else -> Color(0xFFF1E9FF)
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
                                                    isCancelled -> Color(0xFFDC2626)
                                                    completed -> Color(0xFF059669)
                                                    isRescheduled -> Color(0xFFD97706)
                                                    else -> AttendancePurple
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
                                                        snackbarHostState.showSnackbar("Class marked completed")
                                                    } else {
                                                        snackbarHostState.showSnackbar(if (response?.code() == 403) "Backend permission is required to update attendance." else "Unable to update attendance.")
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
                val fn = s.user?.firstName.orEmpty()
                val ln = s.user?.lastName.orEmpty()
                val em = s.user?.email.orEmpty()
                val sc = s.studentCode.orEmpty()
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
                        Surface(color = Color(0xFFF1E9FF), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cohortStudents.size.toString(), fontWeight = FontWeight.Black, color = AttendancePurple, fontSize = 16.sp)
                                Text("Enrolled", fontSize = 10.sp, color = AttendanceMuted)
                            }
                        }
                        Surface(color = Color(0xFFD1FAE5), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(presentCountInSession.toString(), fontWeight = FontWeight.Black, color = Color(0xFF059669), fontSize = 16.sp)
                                Text("Present", fontSize = 10.sp, color = AttendanceMuted)
                            }
                        }
                        Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(absentCountInSession.toString(), fontWeight = FontWeight.Black, color = Color(0xFFDC2626), fontSize = 16.sp)
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (cohortStudents.isEmpty()) {
                        Text("No students are mapped to this cohort in the backend database.", fontSize = 12.sp, color = AttendanceMuted, modifier = Modifier.padding(8.dp))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
                            items(filteredCohortStudents, key = { it.id }) { student ->
                                val isPresent = student.id in attendeeIdSet || student.userId in attendeeIdSet
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, AttendanceBorder)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(34.dp).background(if (isPresent) Color(0xFFD1FAE5) else Color(0xFFFEE2E2), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(
                                                if (isPresent) Icons.Default.Check else Icons.Default.Close,
                                                null,
                                                tint = if (isPresent) Color(0xFF059669) else Color(0xFFDC2626),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            val name = listOfNotNull(student.user?.firstName, student.user?.lastName).joinToString(" ").ifBlank { student.user?.email?.substringBefore('@') ?: student.studentCode ?: "Student" }
                                            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AttendanceInk)
                                            Text(student.user?.email.orEmpty().ifBlank { student.studentCode.orEmpty() }, fontSize = 11.sp, color = AttendanceMuted)
                                        }
                                        Surface(
                                            color = if (isPresent) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                if (isPresent) "PRESENT" else "ABSENT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPresent) Color(0xFF059669) else Color(0xFFDC2626),
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
