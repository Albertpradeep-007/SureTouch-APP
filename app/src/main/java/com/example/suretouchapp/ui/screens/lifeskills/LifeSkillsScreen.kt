package com.example.suretouchapp.ui.screens.lifeskills

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

private val ColorCanvasBg = Color(0xFFF8FAFC)
private val ColorCardSurface = Color(0xFFFFFFFF)
private val ColorPrimaryTeal = Color(0xFF0D9488)
private val ColorTealGradientStart = Color(0xFF0F766E)
private val ColorTealGradientEnd = Color(0xFF14B8A6)
private val ColorTextTitles = Color(0xFF0F172A)
private val ColorTextSubtext = Color(0xFF64748B)
private val ColorBorderHairline = Color(0xFFE2E8F0)
private val ColorGreenBadgeBg = Color(0xFFD1FAE5)
private val ColorGreenBadgeText = Color(0xFF047857)
private val ColorRedBadgeBg = Color(0xFFFEE2E2)
private val ColorRedBadgeText = Color(0xFFB91C1C)

data class LstSessionRecord(
    val id: String,
    val sessionNum: Int,
    val title: String,
    val date: String,
    val time: String,
    val trainer: String,
    val isAttended: Boolean,
    val notes: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeSkillsScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Sessions & Schedule", "LST Attendance", "LST Feedback")
    var training by remember { mutableStateOf<TrainingSnapshot?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    suspend fun loadTraining() {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val res = TrainingRepository(tokenManager).load(TrainingKind.LIFE_SKILLS)
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

    val lstSessions = training?.sessions.orEmpty().mapIndexed { index, session ->
        LstSessionRecord(
            id = session.id,
            sessionNum = index + 1,
            title = session.sessionTitle?.takeIf(String::isNotBlank) ?: "Life Skills Training",
            date = session.date.ifBlank { "Date pending" },
            time = listOfNotNull(session.startTime, session.endTime).joinToString(" - ").ifBlank { "Time pending" },
            trainer = session.conductedBy?.takeIf(String::isNotBlank) ?: "Trainer pending",
            isAttended = session.conducted && session.present,
            notes = session.notes
        )
    }
    val totalSessions = lstSessions.size
    val attendedSessions = lstSessions.count { it.isAttended }
    val conductedSessions = lstSessions.count { session -> session.isAttended || session.date.isNotBlank() }
    val attendancePct = if (conductedSessions == 0) 0.0 else (attendedSessions.toDouble() / conductedSessions) * 100.0

    BackendConnectionGate(
        isLoading = isLoading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Life Skills Portal...",
        onRetry = { scope.launch { loadTraining() } },
        onLogout = null
    ) {
        if (training?.cohortCode.isNullOrBlank()) {
            TrainingLockedScaffold("Life Skills Training (LST)", ColorPrimaryTeal, onBack)
        } else {
            Scaffold(
                containerColor = ColorCanvasBg,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Life Skills Training (LST)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitles)
                                Text("Personal development & emotional intelligence", fontSize = 12.sp, color = ColorTextSubtext)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ColorPrimaryTeal)
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
                                    colors = listOf(ColorTealGradientStart, ColorTealGradientEnd)
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
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Life Skills Program • Cohort ${training?.cohortCode}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                        contentColor = ColorPrimaryTeal
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                                }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> LstScheduleTab(lstSessions)
                        1 -> LstAttendanceTab(totalSessions = totalSessions, attendedSessions = attendedSessions, pct = attendancePct, sessions = lstSessions)
                        2 -> LstFeedbackTab(lstSessions)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingLoadingScaffold(title: String, onBack: () -> Unit) {
    Scaffold(
        containerColor = ColorCanvasBg,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
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
private fun TrainingLockedScaffold(title: String, accent: Color, onBack: () -> Unit) {
    Scaffold(
        containerColor = ColorCanvasBg,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = accent) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = accent)
            Spacer(Modifier.height(18.dp))
            Text("Training unlocks after cohort assignment", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Your cohort is checked from the student dashboard API. Refresh the dashboard after an administrator assigns it.", color = ColorTextSubtext, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LstScheduleTab(sessions: List<LstSessionRecord>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Upcoming & Past LST Modules", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
        }

        items(sessions) { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ColorPrimaryTeal.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${session.sessionNum}", fontWeight = FontWeight.Bold, color = ColorPrimaryTeal, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = ColorTextTitles)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Trainer: ${session.trainer}", fontSize = 12.sp, color = ColorTextSubtext)
                        Text("${session.date} • ${session.time}", fontSize = 11.5.sp, color = ColorTextSubtext)
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
            }
        }
    }
}

@Composable
private fun LstAttendanceTab(totalSessions: Int, attendedSessions: Int, pct: Double, sessions: List<LstSessionRecord>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // LST Attendance Summary Card
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
                            Text("LST Attendance Status", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                            Text("Separate tracking for Life Skills Training", fontSize = 12.sp, color = ColorTextSubtext)
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
                        color = ColorPrimaryTeal,
                        trackColor = ColorBorderHairline
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Attended: $attendedSessions / $totalSessions sessions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorTextSubtext)
                        Text("Min Required: 75%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorPrimaryTeal)
                    }
                }
            }
        }

        item {
            Text("Detailed LST Session Attendance", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
        }

        items(sessions.filter { it.sessionNum <= 4 }) { record ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LstFeedbackTab(sessions: List<LstSessionRecord>) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Feedback will be available after the backend schedules your first Life Skills session.", textAlign = TextAlign.Center, color = ColorTextSubtext)
        }
        return
    }
    var selectedSession by remember { mutableStateOf(sessions[0].title) }
    var rating by remember { mutableIntStateOf(5) }
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
            Text("Life Skills Session Feedback (LST Feedback)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
                border = BorderStroke(1.dp, ColorBorderHairline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Completed LST Session", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                    Spacer(modifier = Modifier.height(8.dp))

                    sessions.take(4).forEach { sess ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedSession == sess.title) ColorPrimaryTeal.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { selectedSession = sess.title }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSession == sess.title,
                                onClick = { selectedSession = sess.title },
                                colors = RadioButtonDefaults.colors(selectedColor = ColorPrimaryTeal)
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
                    Text("Session Value & Relevance Rating", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        for (i in 1..5) {
                            IconButton(onClick = { rating = i }) {
                                Icon(
                                    imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Star $i",
                                    tint = if (i <= rating) Color(0xFFF59E0B) else ColorTextSubtext,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Trainer Clarity & Interaction Rating", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        for (i in 1..5) {
                            IconButton(onClick = { trainerRating = i }) {
                                Icon(
                                    imageVector = if (i <= trainerRating) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                    contentDescription = "Rating $i",
                                    tint = if (i <= trainerRating) ColorPrimaryTeal else ColorTextSubtext,
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
                Text("LST Feedback Comments", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextTitles)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = feedbackComments,
                    onValueChange = { feedbackComments = it },
                    placeholder = { Text("What did you learn from this LST session? Any suggestions?") },
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
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryTeal)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SUBMIT LST FEEDBACK", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showSubmittedDialog) {
        AlertDialog(
            onDismissRequest = { showSubmittedDialog = false },
            title = { Text("LST Feedback Submitted", fontWeight = FontWeight.Bold) },
            text = { Text("Thank you for submitting feedback for '$selectedSession'. Your insights help us enhance our Life Skills Training module.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmittedDialog = false
                        feedbackComments = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryTeal)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
