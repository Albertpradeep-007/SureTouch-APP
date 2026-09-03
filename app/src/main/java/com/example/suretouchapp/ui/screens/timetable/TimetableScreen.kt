package com.example.suretouchapp.ui.screens.timetable

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.repository.*
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import com.example.suretouchapp.ui.theme.sureSemanticColors
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val ColorDarkHeader = Color(0xFF6C2BD9)
private val ColorCardTopBanner = Color(0xFF4C1D95)
private val ColorCanvasBackground @Composable get() = MaterialTheme.colorScheme.background
private val ColorTextDark @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSub @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

private enum class TimetableMode { UPCOMING, HISTORY }

private data class TimetableSlot(
    val id: String,
    val rawDate: LocalDate,
    val timeSlot: String,
    val classType: String,
    val courseDetails: String,
    val state: TimetableClassStatus,
    val notes: String? = null,
    val hasMeetingLink: Boolean = false,
    val meetingLink: String? = null
)

private fun parseApiTime(value: String?): LocalTime? = ClassSchedulePolicy.parseLocalTime(value)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit,
    onNavigateToLiveClass: () -> Unit = {}
) {
    val context = LocalContext.current
    val semanticColors = sureSemanticColors()
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000L)
        }
    }

    val currentWeekDays = remember(now.toLocalDate()) {
        val monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        (0L..6L).map { monday.plusDays(it) }
    }

    var selectedDate by remember {
        val today = LocalDate.now()
        val initial = if (today in currentWeekDays) today else currentWeekDays.first()
        mutableStateOf(initial)
    }

    var showAllWeek by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(TimetableMode.UPCOMING) }
    var selectedHistoryDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var attendance by remember { mutableStateOf<List<AttendanceDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val liveAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "live_alpha"
    )

    fun openLink(link: String?) {
        val clean = link?.trim().orEmpty()
        if (clean.isBlank()) {
            Toast.makeText(context, "No meeting link available", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Could not launch link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(refreshTrigger) {
        isLoading = attendance.isEmpty()
        loadError = null
        errorTitle = null
        try {
            val api = ApiClient.getService(tokenManager)
            val response = api.getAttendance()
            if (response.isSuccessful) {
                val results = response.body()?.results.orEmpty()
                if (results.isNotEmpty()) {
                    attendance = results
                } else {
                    val stats = runCatching { api.getStudentStatistics() }.getOrNull()?.body()
                    val statsSessions = stats?.upcomingSessions.orEmpty()
                    if (statsSessions.isNotEmpty()) {
                        attendance = statsSessions
                    } else {
                        attendance = results
                    }
                }
                SureProEdNotificationManager.syncTimetableAndClasses(context, attendance)
                isConnected = true
                hasLoadedOnce = true
                isOffline = false
                loadError = null
            } else {
                val stats = runCatching { api.getStudentStatistics() }.getOrNull()?.body()
                val statsSessions = stats?.upcomingSessions.orEmpty()
                if (statsSessions.isNotEmpty()) {
                    attendance = statsSessions
                    isConnected = true
                    hasLoadedOnce = true
                    loadError = null
                } else {
                    val errorInfo = NetworkUtils.getNetworkErrorInfo(context, null)
                    isConnected = false
                    isOffline = errorInfo.isOffline
                    errorTitle = errorInfo.title
                    loadError = errorInfo.message
                }
            }
        } catch (e: Exception) {
            val stats = runCatching { ApiClient.getService(tokenManager).getStudentStatistics() }.getOrNull()?.body()
            val statsSessions = stats?.upcomingSessions.orEmpty()
            if (statsSessions.isNotEmpty()) {
                attendance = statsSessions
                isConnected = true
                hasLoadedOnce = true
                loadError = null
            } else {
                val errorInfo = NetworkUtils.getNetworkErrorInfo(context, e)
                isConnected = false
                isOffline = errorInfo.isOffline
                errorTitle = errorInfo.title
                loadError = errorInfo.message
            }
        } finally {
            isLoading = false
            hasLoadedOnce = true
        }
    }

    val (nextSession, nextSessionStatus) = remember(attendance, now) {
        TimetableSessionPolicy.findNextActiveSession(attendance, now)
    }

    val slots = remember(attendance, now) {
        attendance.mapNotNull { session ->
            val date = parseSessionLocalDate(session.date) ?: return@mapNotNull null
            val status = TimetableSessionPolicy.resolveStatus(session, now)
            val statusLabel = when (status) {
                TimetableClassStatus.ONGOING -> "Live now"
                TimetableClassStatus.UPCOMING -> "Upcoming soon"
                TimetableClassStatus.AWAITING_UPCOMING -> "Scheduled"
                TimetableClassStatus.ENDED -> "Ended"
                TimetableClassStatus.CANCELLED -> "Cancelled"
                TimetableClassStatus.RESCHEDULED -> "Rescheduled"
                TimetableClassStatus.NO_CLASS_SCHEDULED -> "No Class"
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
                    sessionTitle.takeIf { it.isNotBlank() && !genericTitle && !it.equals(courseName, ignoreCase = true) },
                    session.notes?.takeIf(String::isNotBlank)
                ).joinToString("\n").ifBlank { "Class details pending" },
                state = status,
                notes = session.notes,
                hasMeetingLink = !session.meetingLink.isNullOrBlank(),
                meetingLink = session.meetingLink
            )
        }.sortedWith(compareBy<TimetableSlot> { it.rawDate }.thenBy { it.timeSlot })
    }

    val weekRange = remember(now.toLocalDate()) {
        TimetableSessionPolicy.getWeekDateRange(now.toLocalDate())
    }

    val weekSlots = remember(slots, weekRange) {
        slots.filter { it.rawDate in weekRange }
    }

    val activeSlots = remember(slots, weekSlots, mode, selectedDate, showAllWeek, selectedHistoryDate, now, weekRange) {
        when (mode) {
            TimetableMode.UPCOMING -> {
                if (showAllWeek) {
                    weekSlots
                } else {
                    weekSlots.filter { it.rawDate == selectedDate }
                }
            }
            TimetableMode.HISTORY -> {
                slots.filter { slot ->
                    val isPastWeek = slot.rawDate.isBefore(weekRange.start)
                    val isCompletedOrPast = slot.state == TimetableClassStatus.ENDED ||
                        slot.state == TimetableClassStatus.CANCELLED ||
                        slot.state == TimetableClassStatus.RESCHEDULED ||
                        slot.rawDate.isBefore(now.toLocalDate())
                    (isPastWeek || isCompletedOrPast) &&
                        (selectedHistoryDate == null || slot.rawDate == selectedHistoryDate)
                }.sortedWith(compareByDescending<TimetableSlot> { it.rawDate }.thenByDescending { it.timeSlot })
            }
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
            refreshTrigger++
        },
        onLogout = null
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                                contentDescription = "SURE Trust Official Logo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(2.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Class Timetable", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
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
                    // Segmented Button: Current Week vs Timetable History
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        TimetableMode.entries.forEachIndexed { index, item ->
                            SegmentedButton(
                                selected = mode == item,
                                onClick = { mode = item },
                                shape = SegmentedButtonDefaults.itemShape(index, TimetableMode.entries.size)
                            ) { Text(if (item == TimetableMode.UPCOMING) "Weekly Timetable" else "Timetable History") }
                        }
                    }

                    // HERO: Next Upcoming Class Banner / Active Transition
                    if (mode == TimetableMode.UPCOMING) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = when (nextSessionStatus) {
                                TimetableClassStatus.ONGOING -> Color(0xFF15803D)
                                TimetableClassStatus.UPCOMING -> Color(0xFF4338CA)
                                TimetableClassStatus.AWAITING_UPCOMING -> Color(0xFF6C2BD9)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                                        contentDescription = "SURE Trust Logo",
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))

                                if (nextSessionStatus == TimetableClassStatus.ONGOING) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .graphicsLayer { alpha = liveAlpha }
                                            .clip(CircleShape)
                                            .background(Color.Red)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when (nextSessionStatus) {
                                            TimetableClassStatus.ONGOING -> "ONGOING • LIVE NOW"
                                            TimetableClassStatus.UPCOMING -> "STARTING SOON"
                                            TimetableClassStatus.AWAITING_UPCOMING -> "AWAITING UPCOMING CLASS"
                                            else -> "NO CLASS SCHEDULED"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (nextSessionStatus == TimetableClassStatus.NO_CLASS_SCHEDULED) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    if (nextSession != null) {
                                        Text(
                                            text = nextSession.sessionTitle ?: nextSession.courseName ?: "Next class",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${nextSession.date} • ${formatTimeRange(nextSession.startTime, nextSession.endTime)}",
                                            fontSize = 11.5.sp,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    } else {
                                        Text(
                                            text = "All scheduled sessions for the current week have ended.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (nextSessionStatus == TimetableClassStatus.ONGOING && !nextSession?.meetingLink.isNullOrBlank()) {
                                    Button(
                                        onClick = { openLink(nextSession?.meetingLink) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Videocam, null, tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Join Meet", fontSize = 11.5.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Week Day Filter Chips (All Week + Monday through Sunday)
                    if (mode == TimetableMode.UPCOMING) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = showAllWeek,
                                    onClick = { showAllWeek = true },
                                    label = {
                                        Text(
                                            text = "All Week (${weekSlots.size})",
                                            fontWeight = if (showAllWeek) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ViewWeek, null, modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                            items(currentWeekDays) { dayDate ->
                                val isSelected = !showAllWeek && dayDate == selectedDate
                                val isToday = dayDate.isEqual(now.toLocalDate())
                                val dayName = dayDate.format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.US))
                                val dayCount = weekSlots.count { it.rawDate == dayDate }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedDate = dayDate
                                        showAllWeek = false
                                    },
                                    label = {
                                        Text(
                                            text = if (isToday) "$dayName (Today - $dayCount)" else "$dayName ($dayCount)",
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = if (isToday) {
                                        { Icon(Icons.Default.Today, null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    } else {
                        // History Date Filter
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
                        text = if (mode == TimetableMode.UPCOMING) {
                            if (showAllWeek) "All Week Schedule (${activeSlots.size} classes)"
                            else selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.US))
                        } else if (selectedHistoryDate != null) {
                            "Archived: ${selectedHistoryDate!!.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.US))} (${activeSlots.size})"
                        } else "Archived Sessions & History (${activeSlots.size})",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTextDark,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        textAlign = TextAlign.Start
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isLoading) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SureTrustLoadingIndicator(message = "Loading timetable") }
                        } else if (activeSlots.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EventBusy,
                                        contentDescription = null,
                                        tint = ColorTextSub,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        loadError ?: if (mode == TimetableMode.HISTORY) {
                                            if (selectedHistoryDate != null) "No completed classes found for ${selectedHistoryDate!!.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}."
                                            else "No completed or archived classes found."
                                        } else {
                                            "No classes scheduled for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE", Locale.US))}."
                                        },
                                        textAlign = TextAlign.Center,
                                        color = ColorTextSub,
                                        fontSize = 13.5.sp
                                    )
                                    if (mode == TimetableMode.UPCOMING && !showAllWeek && weekSlots.isNotEmpty()) {
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = { showAllWeek = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorDarkHeader),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("View All Week Classes (${weekSlots.size})", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        items(activeSlots, key = { it.id }) { slot ->
                            val isClickable = (slot.state == TimetableClassStatus.ONGOING || slot.state == TimetableClassStatus.UPCOMING) && slot.hasMeetingLink
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp)
                                    .clickable(enabled = isClickable) {
                                        if (slot.state == TimetableClassStatus.ONGOING) {
                                            openLink(slot.meetingLink)
                                        } else {
                                            onNavigateToLiveClass()
                                        }
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    1.dp,
                                    when (slot.state) {
                                        TimetableClassStatus.ONGOING -> Color(0xFF15803D)
                                        TimetableClassStatus.CANCELLED -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                        TimetableClassStatus.RESCHEDULED -> Color(0xFFD97706).copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    // Header banner with time slot and state badge
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(
                                                when (slot.state) {
                                                    TimetableClassStatus.ONGOING -> Color(0xFF15803D)
                                                    TimetableClassStatus.CANCELLED -> Color(0xFFDC2626)
                                                    TimetableClassStatus.RESCHEDULED -> Color(0xFFD97706)
                                                    TimetableClassStatus.ENDED -> Color(0xFF64748B)
                                                    else -> ColorCardTopBanner
                                                }
                                            )
                                            .padding(vertical = 8.dp, horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(slot.timeSlot, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Surface(
                                                color = Color.White.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = when (slot.state) {
                                                        TimetableClassStatus.ONGOING -> "LIVE"
                                                        TimetableClassStatus.UPCOMING -> "SOON"
                                                        TimetableClassStatus.AWAITING_UPCOMING -> "SCHEDULED"
                                                        TimetableClassStatus.ENDED -> "ENDED"
                                                        TimetableClassStatus.CANCELLED -> "CANCELLED"
                                                        TimetableClassStatus.RESCHEDULED -> "RESCHEDULED"
                                                        else -> "SCHEDULED"
                                                    },
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(
                                        Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 10.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            slot.rawDate.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ColorDarkHeader
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(slot.classType, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = ColorTextDark)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            slot.courseDetails,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = ColorTextSub,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (slot.state == TimetableClassStatus.CANCELLED && !slot.notes.isNullOrBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    "Reason: ${slot.notes}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(6.dp),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        if (slot.state == TimetableClassStatus.ONGOING && slot.hasMeetingLink) {
                                            Spacer(Modifier.height(8.dp))
                                            Button(
                                                onClick = { openLink(slot.meetingLink) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.fillMaxWidth().height(30.dp)
                                            ) {
                                                Icon(Icons.Default.Videocam, null, Modifier.size(13.dp), tint = Color.White)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Join Now", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
}
