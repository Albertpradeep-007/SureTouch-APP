package com.example.suretouchapp.ui.screens.liveclass

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.repository.LiveClassSelector
import com.example.suretouchapp.data.repository.LiveClassUiState
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.suretouchapp.data.repository.ClassJoinLauncher
import java.time.LocalDateTime

private val ColorDarkHeader = Color(0xFF262626)
private val ColorCanvasBg @Composable get() = MaterialTheme.colorScheme.background
private val ColorPrimaryPurple @Composable get() = MaterialTheme.colorScheme.primary
private val ColorTextDark @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSub @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorBorderHairline @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val ColorLiveRed = Color(0xFFDC2626)
private val ColorAmberLive = Color(0xFFD97706)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveClassScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit,
    onNavigateToTimetable: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val accountSession = remember { tokenManager.getSessionId() }
    var sessions by remember { mutableStateOf<List<AttendanceDto>>(emptyList()) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(com.example.suretouchapp.data.repository.ClassSchedulePolicy.now()) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val orderedSessions = LiveClassSelector.orderedSessions(sessions, now)
    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId && com.example.suretouchapp.data.repository.TimetableSessionPolicy.resolveStatus(it, now) != com.example.suretouchapp.data.repository.TimetableClassStatus.ENDED }
    val liveState = LiveClassSelector.resolveLiveClassState(
        selectedSession?.let(::listOf) ?: orderedSessions, now = now
    )

    val activeSession: AttendanceDto? = when (val s = liveState) {
        is LiveClassUiState.Ongoing -> s.session
        is LiveClassUiState.StartingSoon -> s.session
        is LiveClassUiState.AwaitingUpcoming -> s.nextSession
        is LiveClassUiState.Cancelled -> s.session
        else -> null
    }

    val googleMeetUrl = when (val s = liveState) {
        is LiveClassUiState.Ongoing -> s.session.meetingLink.orEmpty()
        is LiveClassUiState.StartingSoon -> s.session.meetingLink.orEmpty()
        is LiveClassUiState.AwaitingUpcoming -> s.nextSession.meetingLink.orEmpty()
        else -> activeSession?.meetingLink.orEmpty()
    }

    suspend fun refreshLiveState() {
        try {
            val api = ApiClient.getService(tokenManager)
            val list = runCatching {
                com.example.suretouchapp.data.repository.AttendanceRepository(tokenManager).load()
            }.getOrDefault(emptyList())

            val resolvedSessions = if (list.isNotEmpty()) {
                list
            } else {
                val stats = runCatching { api.getStudentStatistics() }.getOrNull()
                stats?.takeIf { it.isSuccessful }?.body()?.upcomingSessions.orEmpty()
            }

            tokenManager.withCurrentSession(accountSession) {
                sessions = resolvedSessions
                if (resolvedSessions.none { it.id == selectedSessionId }) {
                    selectedSessionId = resolvedSessions.firstOrNull {
                        val status = com.example.suretouchapp.data.repository.TimetableSessionPolicy.resolveStatus(it, now)
                        status == com.example.suretouchapp.data.repository.TimetableClassStatus.ONGOING ||
                            status == com.example.suretouchapp.data.repository.TimetableClassStatus.UPCOMING
                    }?.id
                }
                SureProEdNotificationManager.syncTimetableAndClasses(context, resolvedSessions)
                refreshError = null
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (tokenManager.isCurrentSession(accountSession)) {
                val fallback = runCatching {
                    ApiClient.getService(tokenManager).getStudentStatistics().takeIf { it.isSuccessful }?.body()?.upcomingSessions.orEmpty()
                }.getOrDefault(emptyList())
                if (fallback.isNotEmpty()) {
                    tokenManager.withCurrentSession(accountSession) {
                        sessions = fallback
                        refreshError = null
                    }
                } else {
                    refreshError = "Unable to refresh classes. Please retry before joining."
                }
            }
        } finally { isLoading = false }
    }

    LaunchedEffect(accountSession) {
        while (true) {
            refreshLiveState()
            delay(20_000L)
        }
    }
    LaunchedEffect(accountSession) {
        while (true) { now = com.example.suretouchapp.data.repository.ClassSchedulePolicy.now(); delay(1_000L) }
    }
    var isAgreed by remember(accountSession) { mutableStateOf(tokenManager.isLiveClassGuidelinesAgreed()) }
    var showGuidelinesDialog by remember(activeSession?.id) { mutableStateOf(false) }
    var dialogCheckboxChecked by remember(activeSession?.id) { mutableStateOf(tokenManager.isLiveClassGuidelinesAgreed()) }

    // Pulsing animation for LIVE NOW / STARTING SOON indicator
    val infiniteTransition = rememberInfiniteTransition(label = "LivePulseTransition")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LivePulseAlpha"
    )

    val joinScope = rememberCoroutineScope()
    val launchGoogleMeet: () -> Unit = {
        joinScope.launch { ClassJoinLauncher.join(context, tokenManager, activeSession) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Live Class Session",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorDarkHeader)
            )
        },
        containerColor = ColorCanvasBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    refreshError?.let {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    val availableChoices = orderedSessions.filter {
                        val status = com.example.suretouchapp.data.repository.TimetableSessionPolicy.resolveStatus(it, now)
                        status != com.example.suretouchapp.data.repository.TimetableClassStatus.ENDED &&
                            status != com.example.suretouchapp.data.repository.TimetableClassStatus.CANCELLED
                    }

                    if (availableChoices.size > 1) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ColorBorderHairline),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = null,
                                            tint = ColorPrimaryPurple,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Select Class Session",
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorTextDark
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ColorPrimaryPurple.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "${availableChoices.size} Classes",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorPrimaryPurple,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    availableChoices.forEach { session ->
                                        val isSelected = session.id == activeSession?.id
                                        val sessionStatus = com.example.suretouchapp.data.repository.TimetableSessionPolicy.resolveStatus(session, now)
                                        val startFormatted = formatClassTime(session.startTime)
                                        val endFormatted = formatClassTime(session.endTime)
                                        val timeText = if (startFormatted != "--:--" && endFormatted != "--:--") {
                                            "$startFormatted - $endFormatted"
                                        } else {
                                            listOfNotNull(session.startTime, session.endTime).joinToString(" - ")
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                            },
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) ColorPrimaryPurple else ColorBorderHairline
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { selectedSessionId = session.id }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .border(
                                                            width = 2.dp,
                                                            color = if (isSelected) ColorPrimaryPurple else ColorTextSub.copy(alpha = 0.5f),
                                                            shape = CircleShape
                                                        )
                                                        .background(if (isSelected) ColorPrimaryPurple else Color.Transparent),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Selected",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = session.sessionTitle ?: session.courseName ?: "Live Class",
                                                            fontSize = 13.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = ColorTextDark,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))

                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = when (sessionStatus) {
                                                                com.example.suretouchapp.data.repository.TimetableClassStatus.ONGOING -> MaterialTheme.colorScheme.errorContainer
                                                                com.example.suretouchapp.data.repository.TimetableClassStatus.UPCOMING -> Color(0xFFFEF3C7)
                                                                else -> MaterialTheme.colorScheme.primaryContainer
                                                            }
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                if (sessionStatus == com.example.suretouchapp.data.repository.TimetableClassStatus.ONGOING) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(6.dp)
                                                                            .graphicsLayer { alpha = alphaPulse }
                                                                            .clip(CircleShape)
                                                                            .background(ColorLiveRed)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                }
                                                                Text(
                                                                    text = when (sessionStatus) {
                                                                        com.example.suretouchapp.data.repository.TimetableClassStatus.ONGOING -> "LIVE"
                                                                        com.example.suretouchapp.data.repository.TimetableClassStatus.UPCOMING -> "SOON"
                                                                        else -> "UPCOMING"
                                                                    },
                                                                    fontSize = 9.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = when (sessionStatus) {
                                                                        com.example.suretouchapp.data.repository.TimetableClassStatus.ONGOING -> ColorLiveRed
                                                                        com.example.suretouchapp.data.repository.TimetableClassStatus.UPCOMING -> ColorAmberLive
                                                                        else -> MaterialTheme.colorScheme.primary
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(3.dp))

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.Schedule,
                                                                contentDescription = null,
                                                                tint = ColorTextSub,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                text = timeText,
                                                                fontSize = 11.5.sp,
                                                                color = ColorTextSub,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }

                                                        if (!session.conductedByName.isNullOrBlank()) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Person,
                                                                    contentDescription = null,
                                                                    tint = ColorTextSub,
                                                                    modifier = Modifier.size(13.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text(
                                                                    text = session.conductedByName,
                                                                    fontSize = 11.5.sp,
                                                                    color = ColorTextSub,
                                                                    maxLines = 1,
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
                    }
                }
                // HERO CARD
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, ColorBorderHairline),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Top Row: Status Badge & Cohort Code
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (liveState) {
                                        is LiveClassUiState.Ongoing -> MaterialTheme.colorScheme.errorContainer
                                        is LiveClassUiState.StartingSoon -> Color(0xFFFEF3C7)
                                        is LiveClassUiState.AwaitingUpcoming -> MaterialTheme.colorScheme.primaryContainer
                                        is LiveClassUiState.Cancelled -> MaterialTheme.colorScheme.errorContainer
                                        is LiveClassUiState.NoClassScheduled -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (liveState is LiveClassUiState.Ongoing) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .graphicsLayer { alpha = alphaPulse }
                                                    .clip(CircleShape)
                                                    .background(ColorLiveRed)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        } else if (liveState is LiveClassUiState.StartingSoon) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .graphicsLayer { alpha = alphaPulse }
                                                    .clip(CircleShape)
                                                    .background(ColorAmberLive)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }

                                        Text(
                                            text = when (val s = liveState) {
                                                is LiveClassUiState.Ongoing -> "LIVE NOW"
                                                is LiveClassUiState.StartingSoon -> "STARTING SOON (${s.minutesUntil} MINS)"
                                                is LiveClassUiState.AwaitingUpcoming -> "AWAITING UPCOMING CLASS"
                                                is LiveClassUiState.Cancelled -> "CLASS CANCELLED"
                                                is LiveClassUiState.NoClassScheduled -> "NO CLASS SCHEDULED"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (liveState) {
                                                is LiveClassUiState.Ongoing -> ColorLiveRed
                                                is LiveClassUiState.StartingSoon -> ColorAmberLive
                                                is LiveClassUiState.AwaitingUpcoming -> MaterialTheme.colorScheme.primary
                                                is LiveClassUiState.Cancelled -> MaterialTheme.colorScheme.error
                                                is LiveClassUiState.NoClassScheduled -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Cohort: ${tokenManager.getCohortCode().ifBlank { activeSession?.cohortCode ?: "Assigned" }}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Course Title & Code
                            Text(
                                text = activeSession?.sessionTitle ?: activeSession?.courseName ?: "No Live Class Scheduled",
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextDark
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Module Title / Notes
                            Text(
                                text = when (val s = liveState) {
                                    is LiveClassUiState.Ongoing -> s.session.notes ?: "Live session is currently in progress."
                                    is LiveClassUiState.StartingSoon -> "Class starts at ${s.session.startTime}. Early access is active so you can join and test audio/video."
                                    is LiveClassUiState.AwaitingUpcoming -> "Next live session is scheduled on ${s.nextSession.date}. Google Meet link activates 10 minutes before start."
                                    is LiveClassUiState.Cancelled -> "Notice: ${s.reason ?: "This class session was cancelled by the mentor."}"
                                    is LiveClassUiState.NoClassScheduled -> "There are no live classes currently scheduled for your cohort."
                                },
                                fontSize = 13.sp,
                                color = ColorTextSub
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = ColorBorderHairline, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Class Info Meta Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = ColorTextSub,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = activeSession?.conductedByName ?: "Trainer assigned",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ColorTextSub
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = ColorTextSub,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (activeSession != null) {
                                            val startFormatted = formatClassTime(activeSession.startTime)
                                            val endFormatted = formatClassTime(activeSession.endTime)
                                            if (startFormatted != "--:--" && endFormatted != "--:--") {
                                                "$startFormatted - $endFormatted"
                                            } else {
                                                listOfNotNull(activeSession.startTime, activeSession.endTime).joinToString(" - ")
                                            }
                                        } else "Time pending",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTextDark
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Action button
                            val effectiveMeetUrl = (googleMeetUrl.ifBlank { activeSession?.meetingLink.orEmpty() }).trim()
                            if (effectiveMeetUrl.isNotBlank()) {
                                Button(
                                    onClick = {
                                        if (isAgreed) {
                                            launchGoogleMeet()
                                        } else {
                                            dialogCheckboxChecked = false
                                            showGuidelinesDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (liveState is LiveClassUiState.Ongoing) Color(0xFF15803D) else ColorPrimaryPurple
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VideoCall,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (liveState) {
                                            is LiveClassUiState.Ongoing -> "Join Live Class →"
                                            is LiveClassUiState.StartingSoon -> "Join Class Early →"
                                            else -> "Join Google Meet →"
                                        },
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onNavigateToTimetable,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("View Complete Timetable", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // MEET LINK & LAPTOP CODE CARD
                val effectiveMeetUrl = (googleMeetUrl.ifBlank { activeSession?.meetingLink.orEmpty() }).trim()
                if (effectiveMeetUrl.isNotBlank()) {
                    item {
                        val meetCode = remember(effectiveMeetUrl) {
                            val clean = effectiveMeetUrl.substringBefore("?").substringBefore("#")
                            clean.substringAfterLast("/")
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ColorBorderHairline),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Google Meet Link & Code",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorTextDark
                                        )
                                    }

                                    Surface(
                                        color = ColorPrimaryPurple.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Code: $meetCode",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ColorPrimaryPurple,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, ColorBorderHairline, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = effectiveMeetUrl,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ColorPrimaryPurple,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(meetCode))
                                                Toast.makeText(context, "Meet Code Copied: $meetCode", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tag,
                                                contentDescription = "Copy Code",
                                                tint = ColorPrimaryPurple,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(effectiveMeetUrl))
                                                Toast.makeText(context, "Full Link Copied!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Link",
                                                tint = ColorPrimaryPurple,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (isAgreed) {
                                                launchGoogleMeet()
                                            } else {
                                                dialogCheckboxChecked = false
                                                showGuidelinesDialog = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) {
                                        Icon(Icons.Default.VideoCall, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Open Meet", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val uri = ClassJoinLauncher.normalizeMeetingUri(effectiveMeetUrl)
                                            if (uri != null) {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                runCatching { context.startActivity(browserIntent) }
                                                    .onFailure {
                                                        clipboardManager.setText(AnnotatedString(effectiveMeetUrl))
                                                        Toast.makeText(context, "Link copied: $effectiveMeetUrl", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp), tint = ColorPrimaryPurple)
                                        Spacer(Modifier.width(6.dp))
                                        Text("In Browser", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorPrimaryPurple)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "💻 Join on Laptop: Open meet.google.com and enter code: $meetCode",
                                        fontSize = 11.sp,
                                        color = ColorTextSub,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (activeSession != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ColorBorderHairline),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Meeting Link Pending",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorTextDark
                                        )
                                    }

                                    Surface(
                                        color = Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Scheduled",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB45309),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "The trainer will publish the Google Meet link shortly before the session starts. Please refresh or check back closer to class time.",
                                            fontSize = 12.sp,
                                            color = ColorTextSub,
                                            lineHeight = 17.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val startFormatted = formatClassTime(activeSession.startTime)
                                        Text(
                                            text = "📅 Scheduled: ${activeSession.date} at ${if (startFormatted != "--:--") startFormatted else activeSession.startTime ?: "Scheduled Time"}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ColorPrimaryPurple
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    Modifier.fillMaxSize().background(ColorCanvasBg),
                    contentAlignment = Alignment.Center
                ) {
                    SureTrustLoadingIndicator(message = "Loading live class")
                }
            }
        }

        // Guidelines Dialog
        if (showGuidelinesDialog) {
            AlertDialog(
                onDismissRequest = { showGuidelinesDialog = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            tint = Color(0xFF1D4ED8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Class Guidelines & Rules",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "1. Stable Connection: Ensure a stable internet connection for smooth video.\n\n" +
                                           "2. Camera Mandatory: Keep your video/camera ON throughout the live class session.\n\n" +
                                           "3. Quiet Workspace: Do NOT travel while attending class; sit in a quiet, professional environment.\n\n" +
                                           "4. Mic & Doubts: Mute microphone upon entry; use 'Raise Hand' feature for Q&A.\n\n" +
                                           "5. Attendance: Attend at least 40% of the measured class duration to be marked present, as defined by the server attendance policy.\n\n" +
                                           "6. Strict Disciplinary Action: Any unfair practices, disruptive misconduct, or unprofessional behavior will result in immediate account suspension and complete termination from the cohort.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { dialogCheckboxChecked = !dialogCheckboxChecked }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = dialogCheckboxChecked,
                                onCheckedChange = { dialogCheckboxChecked = it },
                                colors = CheckboxDefaults.colors(checkedColor = ColorPrimaryPurple)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "I accept & agree to follow all Live Class Guidelines & Rules",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextDark
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isAgreed = true
                            showGuidelinesDialog = false
                            launchGoogleMeet()
                        },
                        enabled = dialogCheckboxChecked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorPrimaryPurple,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Accept & Join Google Meet →",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGuidelinesDialog = false }) {
                        Text("Cancel", color = ColorTextSub)
                    }
                }
            )
        }
    }
}

private fun formatClassTime(value: String?): String {
    if (value.isNullOrBlank()) return "--:--"
    val clean = value.trim()
    val raw = if (clean.length >= 5) clean.substring(0, 5) else clean
    return runCatching {
        val sdf24 = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
        val sdf12 = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
        sdf12.format(requireNotNull(sdf24.parse(raw)))
    }.getOrDefault(raw)
}
