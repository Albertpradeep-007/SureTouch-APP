package com.example.suretouchapp.ui.screens.trustee

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.*
import com.example.suretouchapp.data.repository.VolunteerRepository
import com.example.suretouchapp.data.repository.ClassSchedulePolicy
import com.example.suretouchapp.data.repository.isCancelledSession
import com.example.suretouchapp.data.repository.isCompletedSession
import com.example.suretouchapp.ui.theme.SureFormDefaults
import com.example.suretouchapp.ui.theme.sureSemanticColors
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException
import java.util.Calendar
import java.util.Locale

private val WorkspaceBg @Composable get() = MaterialTheme.colorScheme.background
private val WorkspacePurple @Composable get() = MaterialTheme.colorScheme.primary
private val WorkspaceTeal = Color(0xFF0D9488)
private val WorkspaceInk @Composable get() = MaterialTheme.colorScheme.onSurface
private val WorkspaceMuted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val WorkspaceLine @Composable get() = MaterialTheme.colorScheme.outlineVariant

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

private data class VolunteerScope(
    val profile: VolunteerProfileDto,
    val cohorts: List<CohortDto>
) {
    val cohortIds = cohorts.map { it.id }.filter(String::isNotBlank).toSet()
    val cohortCodes = cohorts.mapNotNull { it.code }.filter(String::isNotBlank).toSet()
}

private suspend fun loadVolunteerScope(tokenManager: TokenManager): VolunteerScope {
    val profile = VolunteerRepository(tokenManager).loadProfile()
    val response = ApiClient.getService(tokenManager).getCohorts()
    if (!response.isSuccessful) throw IOException("Cohorts request failed (${response.code()})")
    val allCohorts = response.body()?.results.orEmpty()
    val assignedIds = profile.assignedCohorts.map { it.id }.filter(String::isNotBlank).toSet()
    val expanded = allCohorts.filter { it.id in assignedIds }
    val expandedIds = expanded.map { it.id }.toSet()
    val fallback = profile.assignedCohorts.filterNot { it.id in expandedIds }.map {
        CohortDto(id = it.id, code = it.code, name = it.name, course = it.course, courseName = it.course, meetingLink = it.meetingLink)
    }
    return VolunteerScope(profile, expanded + fallback)
}

private fun <T> Response<T>.failureMessage(action: String): String = when (code()) {
    401 -> "Your session expired. Sign in again to $action."
    403 -> "The backend has not granted this volunteer permission to $action. Ask an administrator to enable the role permission."
    400 -> "The backend rejected the supplied details. Check every required field."
    else -> "Unable to $action (server ${code()})."
}

fun generateAutoMeetLink(cohortCode: String? = null): String {
    val cleanCode = cohortCode?.filter { it.isLetterOrDigit() }?.lowercase()?.take(3)?.padEnd(3, 'a') ?: "sur"
    val chars = "abcdefghijklmnopqrstuvwxyz"
    val part1 = (1..4).map { chars.random() }.joinToString("")
    val part2 = (1..3).map { chars.random() }.joinToString("")
    return "https://meet.google.com/$cleanCode-$part1-$part2"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolunteerWorkspacePage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = WorkspaceBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.ExtraBold, color = WorkspaceInk)
                        Text(subtitle, fontSize = 11.sp, color = WorkspaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = WorkspacePurple) } },
                actions = {
                    onRefresh?.let { refresh -> IconButton(onClick = refresh) { Icon(Icons.Default.Refresh, "Refresh", tint = WorkspacePurple) } }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = floatingActionButton,
        content = content
    )
}

@Composable
private fun WorkspaceStateCard(icon: ImageVector, title: String, message: String, danger: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, WorkspaceLine),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (danger) Color(0xFFDC2626) else WorkspacePurple, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, color = WorkspaceInk)
            Spacer(Modifier.height(4.dp))
            Text(message, fontSize = 12.sp, color = WorkspaceMuted)
        }
    }
}

@Composable
private fun MetricTile(value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, WorkspaceLine), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = tint)
            Text(label, fontSize = 11.sp, color = WorkspaceMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerProgrammesScreen(tokenManager: TokenManager, onBack: () -> Unit, onOpenSchedule: () -> Unit) {
    var data by remember { mutableStateOf<VolunteerScope?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(refresh) {
        loading = true
        error = null
        runCatching { loadVolunteerScope(tokenManager) }
            .onSuccess { data = it }
            .onFailure { error = it.message ?: "Unable to load assigned programmes." }
        loading = false
    }

    VolunteerWorkspacePage("Assigned Programmes", "Only cohorts assigned by the backend", onBack, onRefresh = { refresh++ }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                loading -> item { WorkspaceStateCard(Icons.Default.Sync, "Syncing programmes", "Reading your live cohort assignments.") }
                error != null -> item { WorkspaceStateCard(Icons.Default.CloudOff, "Programmes unavailable", error.orEmpty(), true) }
                data?.cohorts.isNullOrEmpty() -> item { WorkspaceStateCard(Icons.Default.Groups, "No assigned cohorts", "An administrator must add this volunteer to a cohort in the backend.") }
                else -> {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile(data!!.cohorts.size.toString(), "Assigned cohorts", WorkspacePurple, Modifier.weight(1f))
                            val activeCohortCount = data!!.cohorts.count {
                                it.status?.uppercase() in setOf("ACTIVE", "TRAINING", "ONGOING", "IN_PROGRESS") ||
                                    (!it.endDate.isNullOrBlank() && it.endDate >= "2026")
                            }
                            MetricTile(activeCohortCount.toString(), "Active", WorkspaceTeal, Modifier.weight(1f))
                        }
                    }
                    items(data!!.cohorts, key = { it.id }) { cohort ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, WorkspaceLine), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Groups, null, tint = WorkspacePurple)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(cohort.name.ifBlank { cohort.code ?: "Assigned cohort" }, fontWeight = FontWeight.ExtraBold, color = WorkspaceInk)
                                        Text(listOfNotNull(cohort.code, cohort.courseName).filter(String::isNotBlank).joinToString(" • "), fontSize = 12.sp, color = WorkspaceMuted)
                                    }
                                    AssistChip(onClick = {}, label = { Text(cohort.status ?: "ASSIGNED", fontSize = 10.sp) })
                                }
                                Spacer(Modifier.height(12.dp))
                                Text("${cohort.startDate ?: "Start pending"}  →  ${cohort.endDate ?: "End pending"}", fontSize = 12.sp, color = WorkspaceMuted)
                                Text("${cohort.mentors.size} mentors • ${cohort.volunteers.size} volunteers • capacity ${cohort.maxStudents ?: 0}", fontSize = 12.sp, color = WorkspaceMuted)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onOpenSchedule, colors = ButtonDefaults.buttonColors(containerColor = WorkspacePurple)) {
                                        Icon(Icons.Default.Event, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Schedule")
                                    }
                                    cohort.meetingLink?.takeIf(String::isNotBlank)?.let { link ->
                                        OutlinedButton(onClick = { runCatching { uriHandler.openUri(link) } }) {
                                            Icon(Icons.Default.Link, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Meeting")
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerScheduleScreen(tokenManager: TokenManager, onBack: () -> Unit) {
    val api = remember(tokenManager) { ApiClient.getService(tokenManager) }
    val coroutineScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var scopeData by remember { mutableStateOf<VolunteerScope?>(null) }
    var sessions by remember { mutableStateOf<List<AttendanceDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<AttendanceDto?>(null) }
    var rescheduling by remember { mutableStateOf<AttendanceDto?>(null) }
    var cancelling by remember { mutableStateOf<AttendanceDto?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        error = null
        try {
            val assigned = loadVolunteerScope(tokenManager)
            val response = api.getAttendance()
            if (!response.isSuccessful) throw IOException("Class schedule request failed (${response.code()})")
            scopeData = assigned
            sessions = response.body()?.results.orEmpty().filter {
                it.cohort in assigned.cohortIds || it.cohortCode in assigned.cohortCodes
            }.sortedWith(compareByDescending<AttendanceDto> { it.date }.thenByDescending { it.startTime })
        } catch (failure: Exception) {
            error = failure.message ?: "Unable to load class schedule."
        } finally { loading = false }
    }

    LaunchedEffect(refresh) { reload() }

    Scaffold(
        containerColor = WorkspaceBg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Column { Text("Class Schedule", fontWeight = FontWeight.ExtraBold); Text("Create, reschedule and cancel cohort sessions", fontSize = 11.sp, color = WorkspaceMuted) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = WorkspacePurple) } },
                actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = WorkspacePurple) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (!scopeData?.cohorts.isNullOrEmpty()) FloatingActionButton(onClick = { editing = null; showEditor = true }, containerColor = WorkspacePurple, contentColor = Color.White) { Icon(Icons.Default.Add, "Schedule class") }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                loading -> item { WorkspaceStateCard(Icons.Default.Sync, "Syncing schedule", "Loading live class records.") }
                error != null -> item { WorkspaceStateCard(Icons.Default.CloudOff, "Schedule unavailable", error.orEmpty(), true) }
                scopeData?.cohorts.isNullOrEmpty() -> item { WorkspaceStateCard(Icons.Default.Groups, "No assigned cohort", "Class scheduling is enabled after backend cohort assignment.") }
                sessions.isEmpty() -> item { WorkspaceStateCard(Icons.Default.Event, "No classes scheduled", "Use + to create the first class for an assigned cohort.") }
                else -> items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onEdit = { editing = session; showEditor = true },
                        onReschedule = { rescheduling = session },
                        onCancel = { cancelling = session }
                    )
                }
            }
        }
    }

    if (showEditor && !scopeData?.cohorts.isNullOrEmpty()) {
        ClassSessionDialog(
            cohorts = scopeData!!.cohorts,
            existing = editing,
            existingSessions = sessions,
            onDismiss = { showEditor = false }
        ) { cohortId, title, date, start, end, link, notes, conducted ->
            coroutineScope.launch {
                val latestResponse = runCatching { api.getAttendance() }.getOrNull()
                if (latestResponse?.isSuccessful != true) {
                    snackbar.showSnackbar("Could not verify the latest timetable. Refresh and try again.")
                    return@launch
                }
                val latestSessions = latestResponse.body()?.results.orEmpty()
                val remoteConflict = ClassSchedulePolicy.findConflict(
                    sessions = latestSessions,
                    cohortId = cohortId,
                    date = date,
                    start = start,
                    end = end,
                    excludedSessionId = editing?.id
                )
                if (remoteConflict != null) {
                    snackbar.showSnackbar("This cohort already has a class during that time. Refresh and choose another slot.")
                    refresh++
                    return@launch
                }
                val body = mutableMapOf<String, Any?>(
                    "cohort" to cohortId, "title" to title.trim(), "class_date" to date.trim(),
                    "start_time" to start.trim(), "end_time" to end.trim(),
                    "meeting_link" to link.trim().takeIf(String::isNotBlank),
                    "notes" to notes.trim().takeIf(String::isNotBlank)
                )
                val wasCompleted = editing?.let { it.classStatus.equals("COMPLETED", true) || it.effectiveStatus.equals("COMPLETED", true) } ?: false
                if (editing != null && conducted != wasCompleted) {
                    body["conducted"] = conducted
                    body["class_status"] = if (conducted) "COMPLETED" else "SCHEDULED"
                }
                val response = runCatching {
                    editing?.let { api.patchAttendance(it.id, body) } ?: api.createAttendance(body)
                }.getOrNull()
                if (response?.isSuccessful == true) {
                    showEditor = false
                    snackbar.showSnackbar(if (editing == null) "Class scheduled successfully" else "Class updated")
                    refresh++
                } else snackbar.showSnackbar(response?.failureMessage(if (editing == null) "schedule classes" else "edit classes") ?: "Network error while saving the class.")
            }
        }
    }

    rescheduling?.let { session ->
        RescheduleClassDialog(
            session = session,
            existingSessions = sessions,
            onDismiss = { rescheduling = null },
            onReschedule = { newDate, newStart, newEnd, newLink ->
                coroutineScope.launch {
                    val latestResponse = runCatching { api.getAttendance() }.getOrNull()
                    if (latestResponse?.isSuccessful != true) {
                        snackbar.showSnackbar("Could not verify the latest timetable. Refresh and try again.")
                        return@launch
                    }
                    val latestSessions = latestResponse.body()?.results.orEmpty()
                    val remoteConflict = ClassSchedulePolicy.findConflict(
                        sessions = latestSessions,
                        cohortId = session.cohort ?: session.cohortCode.orEmpty(),
                        date = newDate,
                        start = newStart,
                        end = newEnd,
                        excludedSessionId = session.id
                    )
                    if (remoteConflict != null) {
                        snackbar.showSnackbar("That reschedule overlaps another class for this cohort.")
                        refresh++
                        return@launch
                    }
                    val body = mapOf<String, Any?>(
                        "class_date" to newDate,
                        "start_time" to newStart,
                        "end_time" to newEnd,
                        "meeting_link" to newLink.takeIf(String::isNotBlank),
                        "class_status" to "RESCHEDULED"
                    )
                    val res = runCatching { api.patchAttendance(session.id, body) }.getOrNull()
                    if (res?.isSuccessful == true) {
                        rescheduling = null
                        snackbar.showSnackbar("Class rescheduled to $newDate at $newStart")
                        refresh++
                    } else {
                        snackbar.showSnackbar(res?.failureMessage("reschedule class") ?: "Unable to reschedule class.")
                    }
                }
            }
        )
    }

    cancelling?.let { session ->
        var cancelReason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cancelling = null },
            icon = { Icon(Icons.Default.Cancel, null, tint = Color(0xFFDC2626)) },
            title = { Text("Cancel Class") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to cancel '${session.sessionTitle ?: "this class"}' scheduled on ${session.date}?")
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Cancellation Reason (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val body = mapOf<String, Any?>(
                                "class_status" to "CANCELLED",
                                "conducted" to false,
                                "notes" to cancelReason.trim().takeIf(String::isNotBlank)
                            )
                            val res = runCatching { api.patchAttendance(session.id, body) }.getOrNull()
                            if (res?.isSuccessful == true) {
                                cancelling = null
                                snackbar.showSnackbar("Class has been cancelled")
                                refresh++
                            } else {
                                snackbar.showSnackbar(res?.failureMessage("cancel class") ?: "Unable to cancel class.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Confirm Cancel") }
            },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun SessionCard(
    session: AttendanceDto,
    onEdit: () -> Unit,
    onReschedule: () -> Unit,
    onCancel: () -> Unit
) {
    val semanticColors = sureSemanticColors()
    val completed = session.isCompletedSession()
    val isCancelled = session.isCancelledSession()
    val isRescheduled = session.classStatus.equals("RESCHEDULED", true)

    val badgeText = when {
        isCancelled -> "CANCELLED"
        completed -> "COMPLETED"
        isRescheduled -> "RESCHEDULED"
        else -> "SCHEDULED"
    }

    val badgeColor = when {
        isCancelled -> MaterialTheme.colorScheme.onErrorContainer
        completed -> semanticColors.onSuccessContainer
        isRescheduled -> semanticColors.onWarningContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val badgeBg = when {
        isCancelled -> MaterialTheme.colorScheme.errorContainer
        completed -> semanticColors.successContainer
        isRescheduled -> semanticColors.warningContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, WorkspaceLine), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(badgeBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(
                        when {
                            isCancelled -> Icons.Default.Cancel
                            completed -> Icons.Default.CheckCircle
                            isRescheduled -> Icons.Default.Update
                            else -> Icons.Default.Schedule
                        },
                        null,
                        tint = badgeColor
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(session.sessionTitle ?: "Class session", fontWeight = FontWeight.Bold, color = WorkspaceInk)
                    Text(listOfNotNull(session.cohortCode, session.date, session.startTime?.take(5), session.endTime?.take(5)).joinToString(" • "), fontSize = 11.5.sp, color = WorkspaceMuted)
                    Text("${session.attendees.size} attendees", fontSize = 11.sp, color = WorkspaceMuted)
                }
                Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                    Text(badgeText, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = badgeColor, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                }
            }

            if (!completed && !isCancelled) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onReschedule) {
                        Icon(Icons.Default.Update, null, Modifier.size(15.dp), tint = Color(0xFFD97706))
                        Spacer(Modifier.width(4.dp))
                        Text("Reschedule", fontSize = 11.5.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, null, Modifier.size(15.dp), tint = Color(0xFFDC2626))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel", fontSize = 11.5.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "Edit", tint = WorkspacePurple, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassSessionDialog(
    cohorts: List<CohortDto>,
    existing: AttendanceDto?,
    existingSessions: List<AttendanceDto>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var cohortId by remember(existing, cohorts) { mutableStateOf(existing?.cohort ?: cohorts.first().id) }
    var title by remember(existing) { mutableStateOf(existing?.sessionTitle.orEmpty()) }
    var date by remember(existing) { mutableStateOf(existing?.date.orEmpty()) }
    var start by remember(existing) { mutableStateOf(existing?.startTime?.take(5).orEmpty()) }
    var end by remember(existing) { mutableStateOf(existing?.endTime?.take(5).orEmpty()) }
    var link by remember(existing) {
        val selectedCohort = cohorts.firstOrNull { it.id == (existing?.cohort ?: cohorts.first().id) }
        mutableStateOf(existing?.meetingLink ?: selectedCohort?.meetingLink.orEmpty())
    }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }
    var conducted by remember(existing) { mutableStateOf(existing?.classStatus.equals("COMPLETED", true) || existing?.effectiveStatus.equals("COMPLETED", true)) }
    var expanded by remember { mutableStateOf(false) }

    fun showDatePicker() {
        val cal = Calendar.getInstance()
        date.split("-").mapNotNull(String::toIntOrNull).takeIf { it.size == 3 }?.let {
            cal.set(it[0], it[1] - 1, it[2])
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                if (link.isBlank()) {
                    val code = cohorts.firstOrNull { it.id == cohortId }?.code
                    link = generateAutoMeetLink(code)
                }
            },
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
                    if (end.isBlank()) {
                        end = String.format(Locale.US, "%02d:%02d", (hour + 1) % 24, minute)
                    }
                    if (link.isBlank()) {
                        val code = cohorts.firstOrNull { it.id == cohortId }?.code
                        link = generateAutoMeetLink(code)
                    }
                } else {
                    end = formatted
                }
            },
            h, m, true
        ).show()
    }

    val conflict = remember(cohortId, date, start, end, existingSessions, existing?.id) {
        ClassSchedulePolicy.findConflict(existingSessions, cohortId, date, start, end, existing?.id)
    }

    val validTimeRange = ClassSchedulePolicy.isValidTimeRange(start, end)
    val valid = title.isNotBlank() &&
        date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
        start.matches(Regex("\\d{2}:\\d{2}")) &&
        end.matches(Regex("\\d{2}:\\d{2}")) &&
        validTimeRange &&
        conflict == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Schedule class" else "Edit class") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ExposedDropdownMenuBox(expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = cohorts.firstOrNull { it.id == cohortId }?.let { it.code ?: it.name } ?: "Assigned cohort",
                        onValueChange = {}, readOnly = true, label = { Text("Cohort") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        colors = SureFormDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        cohorts.forEach { cohort ->
                            DropdownMenuItem(
                                text = { Text(cohort.code ?: cohort.name) },
                                onClick = {
                                    cohortId = cohort.id
                                    expanded = false
                                    if (link.isBlank()) {
                                        link = cohort.meetingLink ?: generateAutoMeetLink(cohort.code)
                                    }
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(title, { title = it }, label = { Text("Class title") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = SureFormDefaults.outlinedTextFieldColors())

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = date, onValueChange = {}, readOnly = true, label = { Text("Class Date") },
                        placeholder = { Text("Select Date") }, trailingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = WorkspacePurple) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SureFormDefaults.outlinedTextFieldColors()
                    )
                    Box(Modifier.matchParentSize().clickable { showDatePicker() })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = start, onValueChange = {}, readOnly = true, label = { Text("Start Time") },
                            placeholder = { Text("HH:MM") }, trailingIcon = { Icon(Icons.Default.AccessTime, null, tint = WorkspacePurple) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )
                        Box(Modifier.matchParentSize().clickable { showTimePicker(true) })
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = end, onValueChange = {}, readOnly = true, label = { Text("End Time") },
                            placeholder = { Text("HH:MM") }, trailingIcon = { Icon(Icons.Default.AccessTime, null, tint = WorkspacePurple) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )
                        Box(Modifier.matchParentSize().clickable { showTimePicker(false) })
                    }
                }

                if (start.isNotBlank() && end.isNotBlank() && !validTimeRange) {
                    Text(
                        "End time must be after start time.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }

                if (conflict != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Schedule Conflict: A class (${conflict.sessionTitle ?: "Session"}) is already scheduled for this cohort on $date from ${conflict.startTime?.take(5)} to ${conflict.endTime?.take(5)}. Please choose a non-overlapping time.",
                            color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp, modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Column {
                    OutlinedTextField(link, { link = it }, label = { Text("Google Meet Link") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = SureFormDefaults.outlinedTextFieldColors())
                    TextButton(
                        onClick = {
                            val code = cohorts.firstOrNull { it.id == cohortId }?.code
                            link = generateAutoMeetLink(code)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, Modifier.size(15.dp), tint = WorkspacePurple)
                        Spacer(Modifier.width(4.dp))
                        Text("Auto-generate link", fontSize = 11.sp, color = WorkspacePurple)
                    }
                }

                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = SureFormDefaults.outlinedTextFieldColors())

                if (existing != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(conducted, { conducted = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Class completed")
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(cohortId, title, date, start, end, link, notes, conducted) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RescheduleClassDialog(
    session: AttendanceDto,
    existingSessions: List<AttendanceDto>,
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
        date.split("-").mapNotNull(String::toIntOrNull).takeIf { it.size == 3 }?.let {
            cal.set(it[0], it[1] - 1, it[2])
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

    val conflict = remember(session.cohort, date, start, end, existingSessions, session.id) {
        ClassSchedulePolicy.findConflict(
            existingSessions,
            session.cohort ?: session.cohortCode.orEmpty(),
            date,
            start,
            end,
            session.id
        )
    }

    val valid = date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
        start.matches(Regex("\\d{2}:\\d{2}")) &&
        end.matches(Regex("\\d{2}:\\d{2}")) &&
        ClassSchedulePolicy.isValidTimeRange(start, end) &&
        conflict == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule Class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Select the new date and timing for '${session.sessionTitle ?: "Class"}':", fontSize = 12.sp, color = WorkspaceMuted)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = date, onValueChange = {}, readOnly = true, label = { Text("New Date") },
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = WorkspacePurple) }, modifier = Modifier.fillMaxWidth(),
                        colors = SureFormDefaults.outlinedTextFieldColors()
                    )
                    Box(Modifier.matchParentSize().clickable { showDatePicker() })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = start, onValueChange = {}, readOnly = true, label = { Text("New Start") },
                            trailingIcon = { Icon(Icons.Default.AccessTime, null, tint = WorkspacePurple) }, modifier = Modifier.fillMaxWidth(),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )
                        Box(Modifier.matchParentSize().clickable { showTimePicker(true) })
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = end, onValueChange = {}, readOnly = true, label = { Text("New End") },
                            trailingIcon = { Icon(Icons.Default.AccessTime, null, tint = WorkspacePurple) }, modifier = Modifier.fillMaxWidth(),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )
                        Box(Modifier.matchParentSize().clickable { showTimePicker(false) })
                    }
                }
                if (conflict != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Conflict: Another class is already scheduled on $date from ${conflict.startTime?.take(5)} to ${conflict.endTime?.take(5)}.",
                            color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp, modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                OutlinedTextField(link, { link = it }, label = { Text("Google Meet link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = SureFormDefaults.outlinedTextFieldColors())
            }
        },
        confirmButton = {
            Button(
                onClick = { onReschedule(date, start, end, link) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = WorkspacePurple)
            ) { Text("Confirm Reschedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerTasksScreen(tokenManager: TokenManager, onBack: () -> Unit) {
    val api = remember(tokenManager) { ApiClient.getService(tokenManager) }
    val coroutineScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var assignedScope by remember { mutableStateOf<VolunteerScope?>(null) }
    var tasks by remember { mutableStateOf<List<VolunteerTaskDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true; error = null
        try {
            assignedScope = loadVolunteerScope(tokenManager)
            val response = api.getVolunteerTasks()
            if (!response.isSuccessful) throw IOException("Volunteer tasks request failed (${response.code()})")
            tasks = response.body()?.results.orEmpty().sortedBy { it.dueDate ?: "9999" }
        } catch (failure: Exception) { error = failure.message ?: "Unable to load tasks." }
        loading = false
    }
    LaunchedEffect(refresh) { reload() }

    Scaffold(
        containerColor = WorkspaceBg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Column { Text("Volunteer Tasks", fontWeight = FontWeight.ExtraBold); Text("Live backend assignments", fontSize = 11.sp, color = WorkspaceMuted) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = WorkspacePurple) } },
                actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = WorkspacePurple) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = { if (!assignedScope?.cohorts.isNullOrEmpty()) FloatingActionButton(onClick = { showCreate = true }, containerColor = WorkspacePurple, contentColor = Color.White) { Icon(Icons.Default.Add, "Create task") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            if (!loading && error == null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(tasks.count { it.status.uppercase() in setOf("PENDING", "OPEN", "ASSIGNED") }.toString(), "Open", Color(0xFFD97706), Modifier.weight(1f))
                    MetricTile(tasks.count { it.status.equals("IN_PROGRESS", true) }.toString(), "In progress", WorkspacePurple, Modifier.weight(1f))
                    MetricTile(tasks.count { it.status.uppercase() in setOf("DONE", "COMPLETED") }.toString(), "Complete", WorkspaceTeal, Modifier.weight(1f))
                }
            }
            when {
                loading -> item { WorkspaceStateCard(Icons.Default.Sync, "Syncing tasks", "Loading volunteer assignments.") }
                error != null -> item { WorkspaceStateCard(Icons.Default.CloudOff, "Tasks unavailable", error.orEmpty(), true) }
                tasks.isEmpty() -> item { WorkspaceStateCard(Icons.Default.TaskAlt, "No tasks", "No volunteer tasks have been assigned.") }
                else -> items(tasks, key = { it.id }) { task ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, WorkspaceLine), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(task.title, fontWeight = FontWeight.Bold, color = WorkspaceInk, modifier = Modifier.weight(1f))
                                AssistChip(onClick = {}, label = { Text(task.status.replace('_', ' '), fontSize = 9.sp) })
                            }
                            if (task.description.isNotBlank()) Text(task.description, fontSize = 12.sp, color = WorkspaceMuted)
                            Text(listOfNotNull(task.cohortName, task.priority, task.dueDate?.let { "Due ${it.take(16).replace('T', ' ')}" }).joinToString(" • "), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WorkspacePurple)
                            if (task.status.uppercase() !in setOf("COMPLETED", "CANCELLED")) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = {
                                    coroutineScope.launch {
                                        val next = if (task.status.equals("IN_PROGRESS", true)) "COMPLETED" else "IN_PROGRESS"
                                        val response = runCatching { api.patchVolunteerTask(task.id, mapOf("status" to next)) }.getOrNull()
                                        if (response?.isSuccessful == true) { snackbar.showSnackbar("Task status updated"); refresh++ }
                                        else snackbar.showSnackbar(response?.failureMessage("update volunteer tasks") ?: "Network error while updating task.")
                                    }
                                }) { Text(if (task.status.equals("IN_PROGRESS", true)) "Mark complete" else "Start task") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var due by remember { mutableStateOf("") }
        var cohortId by remember { mutableStateOf(assignedScope?.cohorts?.firstOrNull()?.id.orEmpty()) }
        var cohortMenu by remember { mutableStateOf(false) }

        fun showDueDatePicker() {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, day -> due = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day) },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog(
            onDismissRequest = { showCreate = false }, title = { Text("Create volunteer task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ExposedDropdownMenuBox(cohortMenu, { cohortMenu = !cohortMenu }) {
                        OutlinedTextField(assignedScope?.cohorts?.firstOrNull { it.id == cohortId }?.let { it.code ?: it.name }.orEmpty(), {}, readOnly = true, label = { Text("Assigned cohort") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cohortMenu) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                        ExposedDropdownMenu(cohortMenu, { cohortMenu = false }) { assignedScope?.cohorts.orEmpty().forEach { cohort -> DropdownMenuItem({ Text(cohort.code ?: cohort.name) }, { cohortId = cohort.id; cohortMenu = false }) } }
                    }
                    OutlinedTextField(title, { title = it }, label = { Text("Task title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(due, {}, readOnly = true, label = { Text("Due Date (optional)") }, trailingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = WorkspacePurple) }, modifier = Modifier.fillMaxWidth())
                        Box(Modifier.matchParentSize().clickable { showDueDatePicker() })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        val dueAt = due.trim().takeIf(String::isNotBlank)?.let { "${it}T23:59:00+05:30" }
                        val response = runCatching { api.createVolunteerTask(mapOf("title" to title.trim(), "description" to description.trim(), "cohort" to cohortId, "due_date" to dueAt, "assigned_to" to assignedScope?.profile?.user)) }.getOrNull()
                        if (response?.isSuccessful == true) { showCreate = false; snackbar.showSnackbar("Task created"); refresh++ }
                        else snackbar.showSnackbar(response?.failureMessage("create volunteer tasks") ?: "Network error while creating task.")
                    }
                }, enabled = title.isNotBlank() && cohortId.isNotBlank()) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerImpactScreen(tokenManager: TokenManager, onBack: () -> Unit) {
    val api = remember(tokenManager) { ApiClient.getService(tokenManager) }
    val coroutineScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var scopeData by remember { mutableStateOf<VolunteerScope?>(null) }
    var applications by remember { mutableStateOf<List<ApplicationDto>>(emptyList()) }
    var students by remember { mutableStateOf<List<StudentProfileDto>>(emptyList()) }
    var activities by remember { mutableStateOf<List<CommunityActivityDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var deciding by remember { mutableStateOf<CommunityActivityDto?>(null) }

    suspend fun reload() {
        loading = true; error = null
        try {
            val assigned = loadVolunteerScope(tokenManager)
            val appResponse = api.getMyApplications()
            val activityResponse = api.getCommunityActivities()
            val studentsResponse = api.getStudents()
            if (!activityResponse.isSuccessful) throw IOException("Community activities request failed (${activityResponse.code()})")
            scopeData = assigned
            applications = appResponse.body()?.results.orEmpty().filter { it.assignedCohort in assigned.cohortIds }
            students = studentsResponse.body()?.results.orEmpty().filter { it.cohortId in assigned.cohortIds }
            val appIds = applications.map { it.id }.toSet()
            activities = activityResponse.body()?.results.orEmpty().filter { it.cohort in assigned.cohortIds || it.application in appIds }.sortedByDescending { it.activityDate }
        } catch (failure: Exception) { error = failure.message ?: "Unable to load impact data." }
        loading = false
    }
    LaunchedEffect(refresh) { reload() }

    Scaffold(
        containerColor = WorkspaceBg, snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Column { Text("Impact & Activities", fontWeight = FontWeight.ExtraBold); Text("Assign community activity by Cohort or Student", fontSize = 11.sp, color = WorkspaceMuted) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = WorkspacePurple) } },
                actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = WorkspacePurple) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (!scopeData?.cohorts.isNullOrEmpty() || applications.isNotEmpty()) {
                FloatingActionButton(onClick = { showCreate = true }, containerColor = WorkspaceTeal, contentColor = Color.White) { Icon(Icons.Default.Add, "Create activity") }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            if (!loading && error == null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(activities.size.toString(), "Activities", WorkspacePurple, Modifier.weight(1f))
                    MetricTile(activities.count { it.status.equals("VERIFIED", true) }.toString(), "Verified", WorkspaceTeal, Modifier.weight(1f))
                    MetricTile(activities.count { it.status.equals("PENDING", true) }.toString(), "Pending", Color(0xFFD97706), Modifier.weight(1f))
                }
            }
            when {
                loading -> item { WorkspaceStateCard(Icons.Default.Sync, "Syncing impact", "Loading live community activities.") }
                error != null -> item { WorkspaceStateCard(Icons.Default.CloudOff, "Impact unavailable", error.orEmpty(), true) }
                activities.isEmpty() -> item { WorkspaceStateCard(Icons.Default.VolunteerActivism, "No activities", "Use + to assign a community activity to a cohort or individual student.") }
                else -> items(activities, key = { it.id }) { activity ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, WorkspaceLine), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolunteerActivism, null, tint = WorkspaceTeal)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(activity.title, fontWeight = FontWeight.Bold, color = WorkspaceInk)
                                    Text("${activity.activityType.replace('_', ' ')} • ${activity.activityDate}", fontSize = 11.sp, color = WorkspaceMuted)
                                }
                                AssistChip(onClick = {}, label = { Text(activity.status, fontSize = 9.sp) })
                            }
                            activity.description?.takeIf(String::isNotBlank)?.let { Text(it, fontSize = 12.sp, color = WorkspaceMuted, modifier = Modifier.padding(top = 8.dp)) }
                            activity.verificationRemarks?.takeIf(String::isNotBlank)?.let { Text("Decision: $it", fontSize = 11.sp, color = WorkspacePurple, modifier = Modifier.padding(top = 6.dp)) }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { deciding = activity }) { Icon(Icons.Default.Gavel, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Review decision") }
                        }
                    }
                }
            }
        }
    }

    if (showCreate && scopeData != null) {
        CommunityActivityAssignDialog(
            cohorts = scopeData!!.cohorts,
            applications = applications,
            students = students,
            onDismiss = { showCreate = false },
            onCreateSingle = { appId, cohortId, type, title, date, desc, evidence ->
                coroutineScope.launch {
                    val response = runCatching {
                        api.createCommunityActivity(mapOf(
                            "application" to appId,
                            "cohort" to cohortId,
                            "activity_type" to type,
                            "title" to title.trim(),
                            "activity_date" to date.trim(),
                            "description" to desc.trim().takeIf(String::isNotBlank),
                            "evidence_url" to evidence.trim().takeIf(String::isNotBlank)
                        ))
                    }.getOrNull()
                    if (response?.isSuccessful == true) {
                        showCreate = false
                        snackbar.showSnackbar("Community activity created")
                        refresh++
                    } else snackbar.showSnackbar(response?.failureMessage("create community activity") ?: "Unable to create activity.")
                }
            },
            onCreateCohort = { targetCohortId, type, title, date, desc, evidence ->
                coroutineScope.launch {
                    val cohortApps = applications.filter { it.assignedCohort == targetCohortId }
                    var successCount = 0
                    if (cohortApps.isNotEmpty()) {
                        cohortApps.forEach { app ->
                            val res = runCatching {
                                api.createCommunityActivity(mapOf(
                                    "application" to app.id,
                                    "cohort" to targetCohortId,
                                    "activity_type" to type,
                                    "title" to title.trim(),
                                    "activity_date" to date.trim(),
                                    "description" to desc.trim().takeIf(String::isNotBlank),
                                    "evidence_url" to evidence.trim().takeIf(String::isNotBlank)
                                ))
                            }.getOrNull()
                            if (res?.isSuccessful == true) successCount++
                        }
                    } else {
                        // Direct creation with cohort if application list is unpopulated
                        val res = runCatching {
                            api.createCommunityActivity(mapOf(
                                "cohort" to targetCohortId,
                                "activity_type" to type,
                                "title" to title.trim(),
                                "activity_date" to date.trim(),
                                "description" to desc.trim().takeIf(String::isNotBlank),
                                "evidence_url" to evidence.trim().takeIf(String::isNotBlank)
                            ))
                        }.getOrNull()
                        if (res?.isSuccessful == true) successCount++
                    }
                    showCreate = false
                    snackbar.showSnackbar("Assigned activity to cohort ($successCount created)")
                    refresh++
                }
            }
        )
    }

    deciding?.let { activity ->
        var status by remember(activity) { mutableStateOf(if (activity.status.equals("REJECTED", true)) "REJECTED" else "VERIFIED") }
        var remarks by remember(activity) { mutableStateOf(activity.verificationRemarks.orEmpty()) }
        AlertDialog(
            onDismissRequest = { deciding = null }, title = { Text("Activity decision") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row { FilterChip(status == "VERIFIED", { status = "VERIFIED" }, { Text("Verify") }); Spacer(Modifier.width(8.dp)); FilterChip(status == "REJECTED", { status = "REJECTED" }, { Text("Reject") }) }
                OutlinedTextField(remarks, { remarks = it }, label = { Text("Decision remarks") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { Button(onClick = {
                coroutineScope.launch {
                    val response = runCatching { api.verifyCommunityActivity(activity.id, mapOf("status" to status, "verification_remarks" to remarks.trim())) }.getOrNull()
                    if (response?.isSuccessful == true) { deciding = null; snackbar.showSnackbar("Activity decision saved"); refresh++ }
                    else snackbar.showSnackbar(response?.failureMessage("decide community activities") ?: "Network error while saving decision.")
                }
            }) { Text("Save decision") } },
            dismissButton = { TextButton(onClick = { deciding = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityActivityAssignDialog(
    cohorts: List<CohortDto>,
    applications: List<ApplicationDto>,
    students: List<StudentProfileDto>,
    onDismiss: () -> Unit,
    onCreateSingle: (String, String, String, String, String, String, String) -> Unit,
    onCreateCohort: (String, String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var isAssignToCohort by remember { mutableStateOf(true) }
    var selectedCohortId by remember { mutableStateOf(cohorts.firstOrNull()?.id.orEmpty()) }
    var selectedAppId by remember { mutableStateOf(applications.firstOrNull { it.assignedCohort == selectedCohortId }?.id ?: applications.firstOrNull()?.id.orEmpty()) }
    var type by remember { mutableStateOf("TREE_PLANTATION") }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }

    var cohortMenu by remember { mutableStateOf(false) }
    var appMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }

    val cohortApps = remember(selectedCohortId, applications) {
        applications.filter { it.assignedCohort == selectedCohortId }
    }

    fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day -> date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val valid = title.isNotBlank() && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
        (isAssignToCohort || selectedAppId.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Community Activity") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isAssignToCohort,
                        onClick = { isAssignToCohort = true },
                        label = { Text("Entire Cohort") },
                        leadingIcon = { Icon(Icons.Default.Groups, null, Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isAssignToCohort,
                        onClick = { isAssignToCohort = false },
                        label = { Text("Individual Student") },
                        leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                ExposedDropdownMenuBox(cohortMenu, { cohortMenu = !cohortMenu }) {
                    OutlinedTextField(
                        value = cohorts.firstOrNull { it.id == selectedCohortId }?.let { it.code ?: it.name } ?: "Select Cohort",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Cohort") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cohortMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(cohortMenu, { cohortMenu = false }) {
                        cohorts.forEach { cohort ->
                            DropdownMenuItem(
                                text = { Text(cohort.code ?: cohort.name) },
                                onClick = {
                                    selectedCohortId = cohort.id
                                    selectedAppId = applications.firstOrNull { it.assignedCohort == cohort.id }?.id.orEmpty()
                                    cohortMenu = false
                                }
                            )
                        }
                    }
                }

                if (!isAssignToCohort) {
                    ExposedDropdownMenuBox(appMenu, { appMenu = !appMenu }) {
                        val displayApp = cohortApps.firstOrNull { it.id == selectedAppId }
                        val matchingStudent = displayApp?.let { app -> students.firstOrNull { it.id == app.student || it.userId == app.student } }
                        val studentName = matchingStudent?.let { resolveStudentName(it) }
                        val displayTitle = when {
                            studentName != null -> "$studentName (${displayApp?.applicationNumber ?: displayApp?.id?.take(8)})"
                            displayApp?.applicationNumber != null -> displayApp.applicationNumber
                            selectedAppId.isNotBlank() -> "Student Application (${selectedAppId.take(8)})"
                            else -> "Select Student"
                        }
                        OutlinedTextField(
                            value = displayTitle,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Student in Cohort") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(appMenu) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(appMenu, { appMenu = false }) {
                            if (cohortApps.isEmpty()) {
                                DropdownMenuItem(text = { Text("No applications in this cohort") }, onClick = { appMenu = false })
                            } else {
                                cohortApps.forEach { app ->
                                    val st = students.firstOrNull { it.id == app.student || it.userId == app.student }
                                    val name = st?.let { resolveStudentName(it) }
                                    val title = if (name != null) "$name • ${app.applicationNumber ?: "App ${app.id.take(8)}"}" else (app.applicationNumber ?: "App ${app.id.take(8)}")
                                    DropdownMenuItem(
                                        text = { Text(title) },
                                        onClick = { selectedAppId = app.id; appMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }

                ExposedDropdownMenuBox(typeMenu, { typeMenu = !typeMenu }) {
                    OutlinedTextField(
                        type.replace('_', ' '), {},
                        readOnly = true, label = { Text("Activity type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(typeMenu, { typeMenu = false }) {
                        listOf("TREE_PLANTATION", "BLOOD_DONATION", "HELPING_SOCIETY", "BLOG", "OPEN_SOURCE", "MEETUP", "VOLUNTEER", "OTHER").forEach { value ->
                            DropdownMenuItem({ Text(value.replace('_', ' ')) }, { type = value; typeMenu = false })
                        }
                    }
                }

                OutlinedTextField(title, { title = it }, label = { Text("Activity Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        date, {}, readOnly = true, label = { Text("Activity Date") },
                        placeholder = { Text("Select Date") },
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = WorkspacePurple) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.matchParentSize().clickable { showDatePicker() })
                }

                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(evidence, { evidence = it }, label = { Text("Evidence URL (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isAssignToCohort) {
                        onCreateCohort(selectedCohortId, type, title, date, description, evidence)
                    } else {
                        onCreateSingle(selectedAppId, selectedCohortId, type, title, date, description, evidence)
                    }
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = WorkspaceTeal)
            ) { Text("Assign Activity") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerInterviewsScreen(tokenManager: TokenManager, onBack: () -> Unit) {
    val api = remember(tokenManager) { ApiClient.getService(tokenManager) }
    val coroutineScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var volunteer by remember { mutableStateOf<VolunteerProfileDto?>(null) }
    var applications by remember { mutableStateOf<List<ApplicationDto>>(emptyList()) }
    var students by remember { mutableStateOf<List<StudentProfileDto>>(emptyList()) }
    var interviews by remember { mutableStateOf<List<PreScreeningInterviewDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showSchedule by remember { mutableStateOf(false) }
    var evaluating by remember { mutableStateOf<PreScreeningInterviewDto?>(null) }
    val eligibleApplications = applications.filter {
        it.qualified == true && it.status.uppercase() in setOf("QUALIFIED", "WAITLISTED")
    }

    suspend fun reload() {
        loading = true; error = null
        try {
            val assigned = loadVolunteerScope(tokenManager)
            val appResponse = api.getMyApplications()
            val studentResponse = runCatching { api.getStudents() }.getOrNull()
            val interviewResponse = api.getPreScreeningInterviews()
            if (!appResponse.isSuccessful) throw IOException("Applications request failed (${appResponse.code()})")
            if (!interviewResponse.isSuccessful) throw IOException("Interviews request failed (${interviewResponse.code()})")
            volunteer = assigned.profile
            students = studentResponse?.takeIf { it.isSuccessful }?.body()?.results.orEmpty()
            applications = appResponse.body()?.results.orEmpty().filter { it.assignedCohort in assigned.cohortIds }
            val appIds = applications.map { it.id }.toSet()
            interviews = interviewResponse.body()?.results.orEmpty().filter { it.application in appIds }.sortedByDescending { it.scheduledAt }
        } catch (failure: Exception) { error = failure.message ?: "Unable to load interviews." }
        loading = false
    }
    LaunchedEffect(refresh) { reload() }

    Scaffold(
        containerColor = WorkspaceBg, snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Column { Text("Candidate Interviews", fontWeight = FontWeight.ExtraBold); Text("Schedule, score and decide assigned candidates", fontSize = 11.sp, color = WorkspaceMuted) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = WorkspacePurple) } },
                actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = WorkspacePurple) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = { if (eligibleApplications.isNotEmpty()) FloatingActionButton(onClick = { showSchedule = true }, containerColor = WorkspacePurple, contentColor = Color.White) { Icon(Icons.Default.Add, "Schedule interview") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            if (!loading && error == null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MetricTile(interviews.size.toString(), "Interviews", WorkspacePurple, Modifier.weight(1f))
                    MetricTile(interviews.count { it.status.equals("SCHEDULED", true) }.toString(), "Scheduled", Color(0xFFD97706), Modifier.weight(1f))
                    MetricTile(interviews.count { it.status?.uppercase() in setOf("PASSED", "FAILED", "COMPLETED") }.toString(), "Decided", WorkspaceTeal, Modifier.weight(1f))
                }
            }
            when {
                loading -> item { WorkspaceStateCard(Icons.Default.Sync, "Syncing interviews", "Loading candidates in assigned cohorts.") }
                error != null -> item { WorkspaceStateCard(Icons.Default.CloudOff, "Interviews unavailable", error.orEmpty(), true) }
                applications.isEmpty() -> item { WorkspaceStateCard(Icons.Default.Info, "No assigned candidates visible", "The backend must grant volunteers read access to applications in their assigned cohorts.") }
                eligibleApplications.isEmpty() && interviews.isEmpty() -> item { WorkspaceStateCard(Icons.Default.Info, "No interview-ready candidates", "Candidates appear here after they are qualified or waitlisted and their course requires an interview.") }
                interviews.isEmpty() -> item { WorkspaceStateCard(Icons.Default.HowToReg, "No interviews scheduled", "Use + to schedule an interview for an assigned candidate.") }
                else -> items(interviews, key = { it.id }) { interview ->
                    val application = applications.firstOrNull { it.id == interview.application }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, WorkspaceLine), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.HowToReg, null, tint = WorkspacePurple) }
                                Spacer(Modifier.width(10.dp))
                                val matchingStudent = application?.let { app -> students.firstOrNull { it.id == app.student || it.userId == app.student } }
                                val candidateName = matchingStudent?.let { resolveStudentName(it) } ?: application?.applicationNumber ?: "Candidate (${interview.application.take(8)})"
                                Column(Modifier.weight(1f)) {
                                    Text(candidateName, fontWeight = FontWeight.Bold, color = WorkspaceInk)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        application?.applicationNumber?.let { appNum ->
                                            Text(appNum, fontSize = 11.sp, color = WorkspacePurple, fontWeight = FontWeight.SemiBold)
                                            Text(" • ", fontSize = 11.sp, color = WorkspaceMuted)
                                        }
                                        Text(interview.scheduledAt ?: "Schedule pending", fontSize = 11.sp, color = WorkspaceMuted)
                                    }
                                }
                                AssistChip(onClick = {}, label = { Text(interview.status ?: "PENDING", fontSize = 9.sp) })
                            }
                            interview.score?.let { Text("Score: $it", fontWeight = FontWeight.SemiBold, color = WorkspacePurple, modifier = Modifier.padding(top = 8.dp)) }
                            interview.feedback?.takeIf(String::isNotBlank)?.let { Text(it, fontSize = 12.sp, color = WorkspaceMuted, modifier = Modifier.padding(top = 4.dp)) }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { evaluating = interview }) { Icon(Icons.Default.Edit, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Evaluate & decide") }
                        }
                    }
                }
            }
        }
    }

    if (showSchedule && eligibleApplications.isNotEmpty()) {
        InterviewScheduleDialog(eligibleApplications, onDismiss = { showSchedule = false }) { appId, dateTime, link, notes ->
            coroutineScope.launch {
                val response = runCatching { api.createPreScreeningInterview(mapOf(
                    "application" to appId, "interviewer" to volunteer?.user,
                    "scheduled_at" to dateTime.trim(), "meeting_link" to link.trim().takeIf(String::isNotBlank),
                    "status" to "SCHEDULED", "feedback" to notes.trim().takeIf(String::isNotBlank)
                )) }.getOrNull()
                if (response?.isSuccessful == true) { showSchedule = false; snackbar.showSnackbar("Interview scheduled"); refresh++ }
                else snackbar.showSnackbar(response?.failureMessage("schedule candidate interviews") ?: "Network error while scheduling interview.")
            }
        }
    }

    evaluating?.let { interview ->
        var score by remember(interview) { mutableStateOf(interview.score.orEmpty()) }
        var status by remember(interview) { mutableStateOf(interview.status ?: "COMPLETED") }
        var feedback by remember(interview) { mutableStateOf(interview.feedback.orEmpty()) }
        AlertDialog(
            onDismissRequest = { evaluating = null }, title = { Text("Evaluate candidate") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(score, { score = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Score") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("PASSED", "FAILED", "COMPLETED").forEach { value -> FilterChip(status == value, { status = value }, { Text(value.lowercase().replaceFirstChar(Char::uppercase), fontSize = 10.sp) }) }
                }
                OutlinedTextField(feedback, { feedback = it }, label = { Text("Feedback / decision reason") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { Button(onClick = {
                coroutineScope.launch {
                    val response = runCatching { api.patchPreScreeningInterview(interview.id, mapOf("score" to score.trim().takeIf(String::isNotBlank), "status" to status, "feedback" to feedback.trim())) }.getOrNull()
                    if (response?.isSuccessful == true) { evaluating = null; snackbar.showSnackbar("Interview decision saved"); refresh++ }
                    else snackbar.showSnackbar(response?.failureMessage("evaluate candidate interviews") ?: "Network error while saving evaluation.")
                }
            }, enabled = feedback.isNotBlank()) { Text("Save decision") } },
            dismissButton = { TextButton(onClick = { evaluating = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterviewScheduleDialog(applications: List<ApplicationDto>, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    val context = LocalContext.current
    var application by remember { mutableStateOf(applications.first().id) }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var link by remember { mutableStateOf(generateAutoMeetLink("int")) }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day -> date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showTimePicker() {
        val cal = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hour, minute -> time = String.format(Locale.US, "%02d:%02d:00", hour, minute) },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
        ).show()
    }

    val isoDateTime = if (date.isNotBlank() && time.isNotBlank()) "${date}T$time+05:30" else ""

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Schedule Candidate Interview") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(applications.firstOrNull { it.id == application }?.applicationNumber ?: application.take(8), {}, readOnly = true, label = { Text("Candidate application") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(expanded, { expanded = false }) { applications.forEach { app -> DropdownMenuItem({ Text(app.applicationNumber ?: app.id.take(8)) }, { application = app.id; expanded = false }) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(date, {}, readOnly = true, label = { Text("Interview Date") }, trailingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = WorkspacePurple) }, modifier = Modifier.fillMaxWidth())
                        Box(Modifier.matchParentSize().clickable { showDatePicker() })
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(time.take(5), {}, readOnly = true, label = { Text("Time") }, trailingIcon = { Icon(Icons.Default.AccessTime, null, tint = WorkspacePurple) }, modifier = Modifier.fillMaxWidth())
                        Box(Modifier.matchParentSize().clickable { showTimePicker() })
                    }
                }
                OutlinedTextField(link, { link = it }, label = { Text("Meeting link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Interview notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(application, isoDateTime, link, notes) }, enabled = isoDateTime.isNotBlank()) { Text("Schedule") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
