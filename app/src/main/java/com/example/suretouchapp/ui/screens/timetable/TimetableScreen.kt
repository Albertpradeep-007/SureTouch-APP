package com.example.suretouchapp.ui.screens.timetable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.platform.LocalContext

private val ColorDarkHeader = Color(0xFF6C2BD9)
private val ColorCardTopBanner = Color(0xFF4C1D95)
private val ColorCanvasBackground @Composable get() = MaterialTheme.colorScheme.background
private val ColorTextDark @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSub @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

private enum class TimetableMode { UPCOMING, HISTORY }
private enum class SlotState { UPCOMING, LIVE, COMPLETED }

private data class TimetableSlot(
    val id: String,
    val rawDate: LocalDate,
    val timeSlot: String,
    val classType: String,
    val courseDetails: String,
    val state: SlotState,
    val hasMeetingLink: Boolean
)

private fun parseApiTime(value: String?): LocalTime? {
    val normalized = value?.trim()?.takeIf(String::isNotBlank) ?: return null
    return runCatching { LocalTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_TIME) }
        .recoverCatching { LocalTime.parse(normalized.take(5), DateTimeFormatter.ofPattern("HH:mm")) }
        .getOrNull()
}

private fun displayTime(value: String?): String = parseApiTime(value)
    ?.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    ?: "Time pending"

private fun formatTimeRange(startValue: String?, endValue: String?): String {
    val start = parseApiTime(startValue)
    val end = parseApiTime(endValue)
    if (start == null && end == null) return "Time pending"
    if (start != null && end == null) return start.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    if (start == null && end != null) return end.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))

    val startFmt = start!!.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    val endFmt = end!!.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))

    if (!end.isAfter(start)) {
        val correctedEnd = start.plusHours(1)
        val correctedFmt = correctedEnd.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
        return "$startFmt – $correctedFmt"
    }

    return "$startFmt – $endFmt"
}

private fun stateFor(session: AttendanceDto, now: LocalDateTime): SlotState {
    val backendStatus = session.effectiveStatus ?: session.classStatus
    if (backendStatus in setOf("COMPLETED", "CANCELLED", "RESCHEDULED")) return SlotState.COMPLETED
    val date = runCatching { LocalDate.parse(session.date.take(10)) }.getOrNull() ?: return SlotState.UPCOMING
    val start = parseApiTime(session.startTime) ?: LocalTime.MIN
    val end = parseApiTime(session.endTime) ?: start
    val startAt = LocalDateTime.of(date, start)
    val endAt = LocalDateTime.of(date, end)
    return when {
        !now.isBefore(endAt) -> SlotState.COMPLETED
        now.isBefore(startAt) -> SlotState.UPCOMING
        else -> SlotState.LIVE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit,
    onNavigateToLiveClass: () -> Unit = {}
) {
    val context = LocalContext.current
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val todayName = remember { LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US) }
    var selectedDay by remember { mutableStateOf(todayName.takeIf { it in days } ?: "Monday") }
    var mode by remember { mutableStateOf(TimetableMode.UPCOMING) }
    var selectedHistoryDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var attendance by remember { mutableStateOf<List<AttendanceDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000L)
        }
    }

    LaunchedEffect(selectedHistoryDate) {
        isLoading = true
        loadError = null
        errorTitle = null
        try {
            val response = ApiClient.getService(tokenManager).getAttendance(
                classDate = selectedHistoryDate?.toString()
            )
            if (response.isSuccessful) {
                attendance = response.body()?.results.orEmpty()
                SureProEdNotificationManager.syncTimetableAndClasses(context, attendance)
                isConnected = true
                hasLoadedOnce = true
                isOffline = false
                loadError = null
                errorTitle = null
            } else {
                val errorInfo = NetworkUtils.getNetworkErrorInfo(context, null)
                isConnected = false
                isOffline = errorInfo.isOffline
                errorTitle = errorInfo.title
                loadError = errorInfo.message
            }
        } catch (e: Exception) {
            val errorInfo = NetworkUtils.getNetworkErrorInfo(context, e)
            isConnected = false
            isOffline = errorInfo.isOffline
            errorTitle = errorInfo.title
            loadError = errorInfo.message
        } finally {
            isLoading = false
        }
    }

    val slots = remember(attendance, now) {
        attendance.mapNotNull { session ->
            val date = runCatching { LocalDate.parse(session.date.take(10)) }.getOrNull() ?: return@mapNotNull null
            val state = stateFor(session, now)
            val statusLabel = when (state) {
                SlotState.LIVE -> "Live now"
                SlotState.UPCOMING -> "Scheduled"
                SlotState.COMPLETED -> "Completed"
            }
            val sessionTitle = session.sessionTitle?.trim().orEmpty()
            val genericTitle = sessionTitle.uppercase(Locale.US).let {
                it.startsWith("DOMAIN SESSION") || it.startsWith("CLASS SESSION")
            }
            val courseName = session.courseName?.trim()?.takeIf(String::isNotBlank)
            TimetableSlot(
                id = session.id,
                rawDate = date,
                timeSlot = formatTimeRange(session.startTime, session.endTime),
                classType = listOfNotNull(statusLabel, session.conductedByName?.takeIf(String::isNotBlank)).joinToString(" • "),
                courseDetails = listOfNotNull(
                    courseName,
                    sessionTitle.takeIf {
                        it.isNotBlank() && !genericTitle && !it.equals(courseName, ignoreCase = true)
                    },
                    session.notes?.replace("Sechdule", "Schedule", ignoreCase = true)?.takeIf(String::isNotBlank)
                ).joinToString("\n").ifBlank { "Class details pending" },
                state = state,
                hasMeetingLink = !session.meetingLink.isNullOrBlank()
            )
        }.sortedWith(compareBy<TimetableSlot> { it.rawDate }.thenBy { it.timeSlot })
    }

    val activeSlots = remember(slots, mode, selectedDay, selectedHistoryDate) {
        when (mode) {
            TimetableMode.UPCOMING -> slots.filter {
                it.state != SlotState.COMPLETED &&
                    it.rawDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US) == selectedDay
            }
            TimetableMode.HISTORY -> slots.filter {
                it.state == SlotState.COMPLETED &&
                    (selectedHistoryDate == null || it.rawDate == selectedHistoryDate)
            }.sortedWith(compareByDescending<TimetableSlot> { it.rawDate }.thenByDescending { it.timeSlot })
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (selectedHistoryDate ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedHistoryDate = pickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    BackendConnectionGate(
        isLoading = isLoading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = loadError,
        loadingMessage = "Connecting to SURE Trust Timetable...",
        onRetry = {
            isLoading = true
            selectedHistoryDate = selectedHistoryDate
        },
        onLogout = null
    ) {
        Scaffold(
            topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Class Timetable", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorDarkHeader)
            )
        },
        containerColor = ColorCanvasBackground
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Image(
                painter = painterResource(id = com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                contentDescription = "SURE Trust Official Logo Watermark",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = 0.04f
                        scaleX = 1.35f
                        scaleY = 1.35f
                    }
            )
            Column(Modifier.fillMaxSize()) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    TimetableMode.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = mode == item,
                            onClick = { mode = item },
                            shape = SegmentedButtonDefaults.itemShape(index, TimetableMode.entries.size)
                        ) { Text(if (item == TimetableMode.UPCOMING) "Upcoming" else "History") }
                    }
                }

                if (mode == TimetableMode.UPCOMING) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(days) { day ->
                            FilterChip(
                                selected = day == selectedDay,
                                onClick = { selectedDay = day },
                                label = { Text(day) }
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(selectedHistoryDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "Filter history by date")
                        }
                        if (selectedHistoryDate != null) {
                            IconButton(onClick = { selectedHistoryDate = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear date filter")
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = if (mode == TimetableMode.UPCOMING) selectedDay else "Class History",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextDark,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    textAlign = TextAlign.Center
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isLoading) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SureTrustLoadingIndicator(message = "Loading timetable") }
                    } else if (activeSlots.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                loadError ?: if (mode == TimetableMode.HISTORY) "No completed classes match this date." else "No upcoming classes are scheduled for $selectedDay.",
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                                color = ColorTextSub
                            )
                        }
                    }
                    items(activeSlots, key = { it.id }) { slot ->
                        val titleSize = when {
                            slot.courseDetails.length > 85 -> 10.sp
                            slot.courseDetails.length > 55 -> 11.sp
                            else -> 12.sp
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp).clickable(
                                enabled = slot.state != SlotState.COMPLETED && slot.hasMeetingLink,
                                onClick = onNavigateToLiveClass
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Box(
                                    Modifier.fillMaxWidth().background(
                                        if (slot.state == SlotState.COMPLETED) Color(0xFF64748B) else ColorCardTopBanner
                                    ).padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(slot.timeSlot, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 9.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        slot.rawDate.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ColorDarkHeader
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(slot.classType, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = ColorTextDark, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        slot.courseDetails,
                                        fontSize = titleSize,
                                        fontWeight = FontWeight.Medium,
                                        color = ColorTextSub,
                                        textAlign = TextAlign.Center,
                                        lineHeight = (titleSize.value + 3).sp,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
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
}
