package com.example.suretouchapp.ui.screens.softskills

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.repository.TrainingKind
import com.example.suretouchapp.data.repository.TrainingRepository
import com.example.suretouchapp.data.repository.TrainingSnapshot
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import kotlinx.coroutines.launch

private val ColorCanvasBg @Composable get() = MaterialTheme.colorScheme.background
private val ColorCardSurface @Composable get() = MaterialTheme.colorScheme.surface
private val ColorPrimaryIndigo @Composable get() = MaterialTheme.colorScheme.primary
private val ColorIndigoGradientStart = Color(0xFF3730A3)
private val ColorIndigoGradientEnd = Color(0xFF4F46E5)
private val ColorTextTitles @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSubtext @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorBorderHairline @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val ColorGreenBadgeBg = Color(0xFFD1FAE5)
private val ColorGreenBadgeText = Color(0xFF047857)
private val ColorRedBadgeBg = Color(0xFFFEE2E2)
private val ColorRedBadgeText = Color(0xFFB91C1C)

data class SoftSkillSessionRecord(
    val id: String,
    val moduleName: String,
    val title: String,
    val date: String,
    val time: String,
    val trainer: String,
    val isAttended: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftSkillsScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Modules & Schedule", "Soft Skills Attendance", "Soft Skills Feedback")
    var training by remember { mutableStateOf<TrainingSnapshot?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    suspend fun loadTraining() {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val res = TrainingRepository(tokenManager).load(TrainingKind.SOFT_SKILLS)
            training = res
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

    LaunchedEffect(tokenManager) { loadTraining() }

    val softSkillSessions = training?.sessions.orEmpty().map { session ->
        SoftSkillSessionRecord(
            id = session.id,
            moduleName = session.notes?.substringBefore(":")?.takeIf(String::isNotBlank) ?: "Soft Skills",
            title = session.sessionTitle?.takeIf(String::isNotBlank) ?: "Soft Skills Training",
            date = session.date.ifBlank { "Date pending" },
            time = listOfNotNull(session.startTime, session.endTime).joinToString(" - ").ifBlank { "Time pending" },
            trainer = session.conductedBy?.takeIf(String::isNotBlank) ?: "Trainer pending",
            isAttended = session.conducted && session.present
        )
    }
    val totalSessions = softSkillSessions.size
    val attendedSessions = softSkillSessions.count { it.isAttended }
    val attendancePct = if (totalSessions == 0) 0.0 else (attendedSessions.toDouble() / totalSessions) * 100.0

    BackendConnectionGate(
        isLoading = isLoading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Soft Skills Portal...",
        onRetry = { scope.launch { loadTraining() } },
        onLogout = null
    ) {
        if (training?.cohortCode.isNullOrBlank()) {
            SoftSkillsLockedScaffold(onBack)
        } else {
            Scaffold(
                containerColor = ColorCanvasBg,
                topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Soft Skills Training", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitles)
                        Text("Communication, resume building & mock interviews", fontSize = 12.sp, color = ColorTextSubtext)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ColorPrimaryIndigo)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorCardSurface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Banner Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(ColorIndigoGradientStart, ColorIndigoGradientEnd)
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Soft Skills • Cohort ${training?.cohortCode}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            if (training?.completed == true) "Training completed • verified by student journey API"
                            else "Schedule and attendance synced from the student API",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ColorCardSurface,
                contentColor = ColorPrimaryIndigo
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium, fontSize = 12.5.sp)
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> SoftSkillsScheduleTab(softSkillSessions)
                1 -> SoftSkillsAttendanceTab(totalSessions = totalSessions, attendedSessions = attendedSessions, pct = attendancePct, sessions = softSkillSessions)
                2 -> SoftSkillsFeedbackTab(softSkillSessions)
            }
        }
    }
}
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoftSkillsLoadingScaffold(onBack: () -> Unit) {
    Scaffold(
        containerColor = ColorCanvasBg,
        topBar = {
            TopAppBar(
                title = { Text("Soft Skills Training", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            SureTrustLoadingIndicator(message = "Checking cohort and training records")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoftSkillsLockedScaffold(onBack: () -> Unit) {
    Scaffold(
        containerColor = ColorCanvasBg,
        topBar = {
            TopAppBar(
                title = { Text("Soft Skills Training", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ColorPrimaryIndigo) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = ColorPrimaryIndigo)
            Spacer(Modifier.height(18.dp))
            Text("Training unlocks after cohort assignment", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Your cohort is checked from the student dashboard API. Refresh the dashboard after an administrator assigns it.", color = ColorTextSubtext, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SoftSkillsScheduleTab(sessions: List<SoftSkillSessionRecord>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Soft Skills Modules & Workshops", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
        }

        items(sessions) { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ColorPrimaryIndigo.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(session.moduleName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ColorPrimaryIndigo)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (session.isAttended) ColorGreenBadgeBg else Color(0xFFF3F4F6))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (session.isAttended) "Completed" else "Scheduled",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (session.isAttended) ColorGreenBadgeText else ColorTextSubtext
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(session.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = ColorTextTitles)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Trainer: ${session.trainer}", fontSize = 12.sp, color = ColorTextSubtext)
                    Text("${session.date} • ${session.time}", fontSize = 11.5.sp, color = ColorTextSubtext)
                }
            }
        }
    }
}

@Composable
private fun SoftSkillsAttendanceTab(totalSessions: Int, attendedSessions: Int, pct: Double, sessions: List<SoftSkillSessionRecord>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Soft Skills Attendance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Soft Skills Attendance Record", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                            Text("Separate tracking for Soft Skills Program", fontSize = 12.sp, color = ColorTextSubtext)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (pct >= 75.0) ColorGreenBadgeBg else ColorRedBadgeBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = String.format("%.1f%%", pct),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (pct >= 75.0) ColorGreenBadgeText else ColorRedBadgeText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { (pct / 100.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ColorPrimaryIndigo,
                        trackColor = ColorBorderHairline
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Attended: $attendedSessions / $totalSessions sessions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorTextSubtext)
                        Text("Min Required: 75%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorPrimaryIndigo)
                    }
                }
            }
        }

        item {
            Text("Detailed Soft Skills Attendance Log", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
        }

        items(sessions.take(4)) { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (record.isAttended) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (record.isAttended) Color(0xFF059669) else Color(0xFFDC2626),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(record.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextTitles)
                        Text("${record.date} • ${record.time}", fontSize = 11.5.sp, color = ColorTextSubtext)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (record.isAttended) ColorGreenBadgeBg else ColorRedBadgeBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (record.isAttended) "PRESENT" else "ABSENT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (record.isAttended) ColorGreenBadgeText else ColorRedBadgeText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoftSkillsFeedbackTab(sessions: List<SoftSkillSessionRecord>) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Feedback will be available after the backend schedules your first Soft Skills session.", textAlign = TextAlign.Center, color = ColorTextSubtext)
        }
        return
    }
    var selectedSession by remember { mutableStateOf(sessions[0].title) }
    var contentRating by remember { mutableIntStateOf(5) }
    var trainerRating by remember { mutableIntStateOf(5) }
    var feedbackComments by remember { mutableStateOf("") }
    var showSubmittedDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Soft Skills Workshop Feedback", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Soft Skill Module", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                    Spacer(modifier = Modifier.height(8.dp))

                    sessions.take(4).forEach { sess ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedSession == sess.title) ColorPrimaryIndigo.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { selectedSession = sess.title }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSession == sess.title,
                                onClick = { selectedSession = sess.title },
                                colors = RadioButtonDefaults.colors(selectedColor = ColorPrimaryIndigo)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(sess.title, fontSize = 13.sp, fontWeight = if (selectedSession == sess.title) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Quality & Practical Relevance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        for (i in 1..5) {
                            IconButton(onClick = { contentRating = i }) {
                                Icon(
                                    imageVector = if (i <= contentRating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Star $i",
                                    tint = if (i <= contentRating) Color(0xFFF59E0B) else ColorTextSubtext,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Trainer Communication & Guidance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        for (i in 1..5) {
                            IconButton(onClick = { trainerRating = i }) {
                                Icon(
                                    imageVector = if (i <= trainerRating) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                    contentDescription = "Rating $i",
                                    tint = if (i <= trainerRating) ColorPrimaryIndigo else ColorTextSubtext,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text("Soft Skills Feedback Comments", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = feedbackComments,
                    onValueChange = { feedbackComments = it },
                    placeholder = { Text("How will this soft skill workshop help in your interviews or career?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        item {
            Button(
                onClick = { showSubmittedDialog = true },
                enabled = feedbackComments.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryIndigo)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SUBMIT SOFT SKILLS FEEDBACK", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showSubmittedDialog) {
        AlertDialog(
            onDismissRequest = { showSubmittedDialog = false },
            title = { Text("Soft Skills Feedback Submitted", fontWeight = FontWeight.Bold) },
            text = { Text("Thank you for submitting feedback for '$selectedSession'. Your feedback helps us customize your career readiness training.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmittedDialog = false
                        feedbackComments = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryIndigo)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
