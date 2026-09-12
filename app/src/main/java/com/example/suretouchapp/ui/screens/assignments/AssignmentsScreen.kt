package com.example.suretouchapp.ui.screens.assignments

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AssignmentDto
import com.example.suretouchapp.data.model.AssignmentSubmissionRequest
import com.example.suretouchapp.data.model.SubmissionDto
import com.example.suretouchapp.data.repository.AssignmentLifecycleState
import com.example.suretouchapp.data.repository.AssignmentPolicy
import com.example.suretouchapp.data.repository.AssignmentValidator
import com.example.suretouchapp.data.repository.SubmissionMethod
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import com.example.suretouchapp.ui.theme.SureFormDefaults
import com.example.suretouchapp.ui.theme.sureSemanticColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Calendar
import java.util.Locale

private fun formatAssignmentDueDate(raw: String): String {
    if (raw.isBlank()) return "No deadline set"
    return try {
        val clean = raw.trim()
        val datePart = if (clean.contains("T")) clean.substringBefore("T") else clean.take(10)
        val parts = datePart.split("-")
        if (parts.size == 3) {
            val year = parts[0].toIntOrNull() ?: return clean
            val month = parts[1].toIntOrNull() ?: return clean
            val day = parts[2].toIntOrNull() ?: return clean
            val cal = Calendar.getInstance().apply { set(year, month - 1, day) }
            val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.US).format(cal.time)

            if (clean.contains("T")) {
                val timePart = clean.substringAfter("T").take(5)
                val timeParts = timePart.split(":")
                if (timeParts.size == 2) {
                    val hour = timeParts[0].toIntOrNull() ?: 0
                    val minute = timeParts[1].toIntOrNull() ?: 0
                    val timeCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                    }
                    val formattedTime = SimpleDateFormat("hh:mm a", Locale.US).format(timeCal.time)
                    "$formattedDate • $formattedTime"
                } else {
                    formattedDate
                }
            } else {
                formattedDate
            }
        } else {
            clean
        }
    } catch (_: Exception) {
        raw.take(10)
    }
}

// =======================================================
// ELEGANT COLOR TOKENS (MATCHING SURE TRUST THEME)
// =======================================================
private val ColorDarkHeader = Color(0xFF262626)
private val ColorCanvasBg @Composable get() = MaterialTheme.colorScheme.background
private val ColorPrimaryPurple @Composable get() = MaterialTheme.colorScheme.primary
private val ColorPurpleLight @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val ColorTextDark @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSub @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorBorderHairline @Composable get() = MaterialTheme.colorScheme.outlineVariant

data class AssignmentUiItem(
    val dto: AssignmentDto,
    val courseCode: String,
    val lifecycleState: AssignmentLifecycleState,
    val submissions: List<SubmissionDto>,
    val latestSubmission: SubmissionDto?,
    val attemptsCount: Int,
    val maxAttempts: Int,
    val canSubmit: Boolean,
    val formattedDueDate: String,
    val remainingTimeText: String
)

data class SubmissionConfirmPayload(
    val assignment: AssignmentUiItem,
    val method: SubmissionMethod,
    val cleanUrl: String,
    val commitHash: String,
    val studentComment: String,
    val attemptNumber: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentsScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val semanticColors = sureSemanticColors()

    var selectedFilter by remember { mutableStateOf("All") }
    var selectedAssignmentDetails by remember { mutableStateOf<AssignmentUiItem?>(null) }
    var selectedAssignmentForSubmission by remember { mutableStateOf<AssignmentUiItem?>(null) }
    var submissionConfirmData by remember { mutableStateOf<SubmissionConfirmPayload?>(null) }
    var selectedAssignmentForFeedback by remember { mutableStateOf<AssignmentUiItem?>(null) }

    // Submission Form States
    var submissionMethod by remember { mutableStateOf(SubmissionMethod.GITHUB) }
    var submissionInputUrl by remember { mutableStateOf("") }
    var submissionCommitHash by remember { mutableStateOf("") }
    var submissionComments by remember { mutableStateOf("") }
    var submissionErrorMessage by remember { mutableStateOf<String?>(null) }

    var isSubmitting by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    val assignmentUiList = remember { mutableStateListOf<AssignmentUiItem>() }

    BackHandler {
        when {
            submissionConfirmData != null -> submissionConfirmData = null
            selectedAssignmentForSubmission != null -> selectedAssignmentForSubmission = null
            selectedAssignmentDetails != null -> selectedAssignmentDetails = null
            selectedAssignmentForFeedback != null -> selectedAssignmentForFeedback = null
            else -> onBack()
        }
    }

    suspend fun loadAssignments() {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val api = ApiClient.getService(tokenManager)
            val response = api.getAssignments()
            val submissionsResponse = runCatching { api.getSubmissions() }.getOrNull()
            val cohortsResponse = runCatching { api.getCohorts() }.getOrNull()
            val cohortMap = cohortsResponse?.takeIf { it.isSuccessful }?.body()?.results.orEmpty().associateBy { it.id }

            if (response.isSuccessful) {
                val rawAssignments = response.body()?.results.orEmpty()
                val rawSubmissions = submissionsResponse?.takeIf { it.isSuccessful }?.body()?.results.orEmpty()
                SureProEdNotificationManager.syncAssignments(context, rawAssignments)
                SureProEdNotificationManager.syncSubmissionsAndGrades(context, rawSubmissions, rawAssignments)

                val submissionsByAssignment = rawSubmissions.filter { !it.assignment.isNullOrBlank() || !it.assignmentId.isNullOrBlank() }
                    .groupBy { it.assignment ?: it.assignmentId ?: "" }

                val now = LocalDateTime.now()
                assignmentUiList.clear()
                assignmentUiList.addAll(rawAssignments.map { assignment ->
                    val userSubs = submissionsByAssignment[assignment.id].orEmpty().sortedBy { it.attemptNumber }
                    val latestSub = userSubs.lastOrNull()
                    val state = AssignmentPolicy.resolveState(assignment, userSubs, now)
                    val maxAttempts = assignment.maximumAttempts.takeIf { it > 0 } ?: 3
                    val attemptsCount = userSubs.size
                    val canSubmit = AssignmentPolicy.canSubmit(state, assignment, attemptsCount, maxAttempts, now)
                    val formattedDue = formatAssignmentDueDate(assignment.dueAt ?: assignment.dueDate)
                    val remainingTime = AssignmentPolicy.formatRemainingTime(assignment.dueAt ?: assignment.dueDate, now)

                    val cohortObj = cohortMap[assignment.cohort]
                    val resolvedCourseTag = when {
                        !assignment.cohortCode.isNullOrBlank() -> assignment.cohortCode
                        cohortObj?.code?.isNotBlank() == true -> cohortObj.code
                        cohortObj?.courseName?.isNotBlank() == true -> cohortObj.courseName
                        !assignment.courseName.isNullOrBlank() -> assignment.courseName
                        !assignment.moduleName.isNullOrBlank() -> assignment.moduleName
                        assignment.cohort?.matches(Regex("^[0-9a-fA-F-]{32,36}$")) == true -> "Course Assignment"
                        !assignment.cohort.isNullOrBlank() -> assignment.cohort
                        else -> "Course Assignment"
                    }

                    AssignmentUiItem(
                        dto = assignment,
                        courseCode = resolvedCourseTag,
                        lifecycleState = state,
                        submissions = userSubs,
                        latestSubmission = latestSub,
                        attemptsCount = attemptsCount,
                        maxAttempts = maxAttempts,
                        canSubmit = canSubmit,
                        formattedDueDate = formattedDue,
                        remainingTimeText = remainingTime
                    )
                })
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
            assignmentUiList.clear()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadAssignments() }

    val filteredAssignments = remember(selectedFilter, assignmentUiList.toList()) {
        when (selectedFilter) {
            "Open" -> assignmentUiList.filter {
                it.lifecycleState in setOf(
                    AssignmentLifecycleState.OPEN,
                    AssignmentLifecycleState.UPCOMING,
                    AssignmentLifecycleState.RESUBMISSION_REQUIRED
                )
            }
            "Submitted" -> assignmentUiList.filter {
                it.lifecycleState in setOf(
                    AssignmentLifecycleState.SUBMITTED,
                    AssignmentLifecycleState.LATE_SUBMITTED,
                    AssignmentLifecycleState.UNDER_REVIEW,
                    AssignmentLifecycleState.RESUBMITTED
                )
            }
            "Evaluated" -> assignmentUiList.filter { it.lifecycleState == AssignmentLifecycleState.EVALUATED }
            else -> assignmentUiList
        }
    }

    val openCount = assignmentUiList.count {
        it.lifecycleState in setOf(
            AssignmentLifecycleState.OPEN,
            AssignmentLifecycleState.UPCOMING,
            AssignmentLifecycleState.RESUBMISSION_REQUIRED
        )
    }
    val submittedCount = assignmentUiList.count {
        it.lifecycleState in setOf(
            AssignmentLifecycleState.SUBMITTED,
            AssignmentLifecycleState.LATE_SUBMITTED,
            AssignmentLifecycleState.UNDER_REVIEW,
            AssignmentLifecycleState.RESUBMITTED
        )
    }
    val evaluatedCount = assignmentUiList.count { it.lifecycleState == AssignmentLifecycleState.EVALUATED }

    BackendConnectionGate(
        isLoading = isLoading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Assignment Portal...",
        onRetry = { scope.launch { loadAssignments() } },
        onLogout = null
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Assignments & Tasks",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
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
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // =======================================================
                    // 1. STATS OVERVIEW CARDS
                    // =======================================================
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ColorBorderHairline),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Open", fontSize = 11.5.sp, color = ColorTextSub, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$openCount Active",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ColorBorderHairline),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Submitted", fontSize = 11.5.sp, color = ColorTextSub, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$submittedCount Submitted",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, ColorBorderHairline),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Evaluated", fontSize = 11.5.sp, color = ColorTextSub, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$evaluatedCount Evaluated",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                            }
                        }
                    }

                    // =======================================================
                    // 2. FILTER TABS ROW
                    // =======================================================
                    item {
                        val filters = listOf("All", "Open", "Submitted", "Evaluated")
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filters) { filter ->
                                val isSelected = filter == selectedFilter
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) ColorPurpleLight else MaterialTheme.colorScheme.surface)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) ColorPrimaryPurple else ColorBorderHairline,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedFilter = filter }
                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filter,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) ColorPrimaryPurple else ColorTextSub
                                    )
                                }
                            }
                        }
                    }

                    // =======================================================
                    // 3. ASSIGNMENT CARDS LIST
                    // =======================================================
                    if (isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                SureTrustLoadingIndicator(message = "Loading assignments")
                            }
                        }
                    } else if (filteredAssignments.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = ColorPrimaryPurple, modifier = Modifier.size(38.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("No assignments in this view", fontWeight = FontWeight.Bold, color = ColorTextDark)
                                    Text("Assignments published by your mentors will show up here.", fontSize = 12.sp, color = ColorTextSub)
                                }
                            }
                        }
                    }

                    items(filteredAssignments, key = { it.dto.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clickable { selectedAssignmentDetails = item },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ColorBorderHairline),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Header: Course Code + Status Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(modifier = Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ColorPurpleLight
                                        ) {
                                            Text(
                                                text = item.courseCode,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorPrimaryPurple,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        val typeTag = item.dto.assignmentType?.takeIf { it.isNotBlank() } ?: "Assignment"
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = typeTag,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ColorTextSub,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    // Lifecycle Badge
                                    val (badgeBg, badgeFg, badgeText) = when (item.lifecycleState) {
                                        AssignmentLifecycleState.OPEN -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Open")
                                        AssignmentLifecycleState.UPCOMING -> Triple(Color(0xFFE0E7FF), Color(0xFF4338CA), "Upcoming")
                                        AssignmentLifecycleState.SUBMITTED -> Triple(Color(0xFFEDE9FE), Color(0xFF6D28D9), "Submitted")
                                        AssignmentLifecycleState.LATE_SUBMITTED -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Late Submitted")
                                        AssignmentLifecycleState.UNDER_REVIEW -> Triple(Color(0xFFFEF08A), Color(0xFF854D0E), "Under Review")
                                        AssignmentLifecycleState.RESUBMISSION_REQUIRED -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), "Resubmission")
                                        AssignmentLifecycleState.RESUBMITTED -> Triple(Color(0xFFDBEAFE), Color(0xFF1D4ED8), "Resubmitted")
                                        AssignmentLifecycleState.EVALUATED -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Evaluated")
                                        AssignmentLifecycleState.CLOSED -> Triple(Color(0xFFF3F4F6), Color(0xFF4B5563), "Closed")
                                    }

                                    Surface(shape = RoundedCornerShape(6.dp), color = badgeBg) {
                                        Text(
                                            text = badgeText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeFg,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Assignment Title
                                Text(
                                    text = item.dto.title,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorTextDark
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Description
                                Text(
                                    text = item.dto.description.ifBlank { "No detailed description provided." },
                                    fontSize = 12.5.sp,
                                    color = ColorTextSub,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 17.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                HorizontalDivider(color = ColorBorderHairline, thickness = 0.8.dp)

                                Spacer(modifier = Modifier.height(10.dp))

                                // Footer: Deadlines & Action Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Event,
                                                contentDescription = null,
                                                tint = ColorTextSub,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = item.formattedDueDate,
                                                fontSize = 11.sp,
                                                color = ColorTextSub,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        val remaining = item.remainingTimeText
                                        if (remaining.isNotBlank() && item.lifecycleState == AssignmentLifecycleState.OPEN) {
                                            Text(
                                                text = "⏳ $remaining",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFFD97706),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    // Action Button
                                    when (item.lifecycleState) {
                                        AssignmentLifecycleState.EVALUATED -> {
                                            val scoreText = item.latestSubmission?.marksObtained
                                                ?: item.dto.score?.toString()
                                                ?: "Graded"
                                            Button(
                                                onClick = { selectedAssignmentForFeedback = item },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Grade: $scoreText/${item.dto.maxMarks.toDoubleOrNull()?.toInt() ?: 100}",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        AssignmentLifecycleState.RESUBMISSION_REQUIRED -> {
                                            Button(
                                                onClick = {
                                                    selectedAssignmentForSubmission = item
                                                    submissionMethod = SubmissionMethod.fromId(item.dto.submissionMethod)
                                                    submissionInputUrl = item.latestSubmission?.submissionUrl.orEmpty()
                                                    submissionCommitHash = item.latestSubmission?.commitSha ?: item.latestSubmission?.commitHash.orEmpty()
                                                    submissionComments = ""
                                                    submissionErrorMessage = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Resubmit (${item.attemptsCount + 1}/${item.maxAttempts})",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        AssignmentLifecycleState.OPEN -> {
                                            Button(
                                                onClick = {
                                                    selectedAssignmentForSubmission = item
                                                    submissionMethod = SubmissionMethod.fromId(item.dto.submissionMethod)
                                                    submissionInputUrl = item.latestSubmission?.submissionUrl.orEmpty()
                                                    submissionCommitHash = item.latestSubmission?.commitSha ?: item.latestSubmission?.commitHash.orEmpty()
                                                    submissionComments = ""
                                                    submissionErrorMessage = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = if (item.attemptsCount > 0) "Resubmit (${item.attemptsCount + 1}/${item.maxAttempts})" else "Submit Work",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        AssignmentLifecycleState.SUBMITTED,
                                        AssignmentLifecycleState.LATE_SUBMITTED,
                                        AssignmentLifecycleState.UNDER_REVIEW,
                                        AssignmentLifecycleState.RESUBMITTED -> {
                                            OutlinedButton(
                                                onClick = { selectedAssignmentDetails = item },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, ColorPrimaryPurple),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("View Details", fontSize = 11.5.sp, color = ColorPrimaryPurple, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        AssignmentLifecycleState.UPCOMING -> {
                                            Surface(
                                                color = Color(0xFFEEF2FF),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "Upcoming",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4338CA),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                        AssignmentLifecycleState.CLOSED -> {
                                            Surface(
                                                color = Color(0xFFF3F4F6),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "Closed",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF6B7280),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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

        // =======================================================
        // MODAL 1: ASSIGNMENT DETAILS & SUBMISSION HISTORY
        // =======================================================
        selectedAssignmentDetails?.let { detailItem ->
            AlertDialog(
                onDismissRequest = { selectedAssignmentDetails = null },
                title = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = detailItem.dto.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { selectedAssignmentDetails = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        Text(
                            text = "${detailItem.courseCode} • Max Marks: ${detailItem.dto.maxMarks.toDoubleOrNull()?.toInt() ?: 100} • Due: ${detailItem.formattedDueDate}",
                            fontSize = 12.sp,
                            color = ColorTextSub
                        )
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Mentor row
                        val mentor = AssignmentPolicy.formatMentorName(detailItem.dto.mentorName, detailItem.dto.createdBy)
                        item {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Person, null, tint = ColorPrimaryPurple, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Mentor: $mentor", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = ColorTextDark)
                                }
                            }
                        }

                        // Deadline Passed Banner
                        val deadlinePassed = detailItem.remainingTimeText.contains("passed", ignoreCase = true)
                        if (deadlinePassed && detailItem.lifecycleState != AssignmentLifecycleState.RESUBMISSION_REQUIRED) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.8.dp, ColorBorderHairline)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Lock, null, tint = ColorTextSub, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Deadline has passed. Submissions are closed.",
                                            fontSize = 12.sp,
                                            color = ColorTextSub,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // Resubmission Required Warning
                        if (detailItem.lifecycleState == AssignmentLifecycleState.RESUBMISSION_REQUIRED) {
                            item {
                                Surface(
                                    color = Color(0xFFFEF2F2),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Resubmission Required", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        val reason = detailItem.latestSubmission?.resubmissionReason
                                            ?: detailItem.latestSubmission?.feedback
                                            ?: "Mentor requested changes before the deadline."
                                        Text("Mentor Feedback: $reason", fontSize = 12.sp, color = Color(0xFF7F1D1D))
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Attempts Remaining: ${detailItem.maxAttempts - detailItem.attemptsCount}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB91C1C)
                                        )
                                    }
                                }
                            }
                        }

                        // Description
                        item {
                            Text("Description & Instructions", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ColorTextDark)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = detailItem.dto.description.ifBlank { "No description available." },
                                fontSize = 12.sp,
                                color = ColorTextSub,
                                lineHeight = 16.sp
                            )
                            if (!detailItem.dto.instructions.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Instructions: ${detailItem.dto.instructions}",
                                    fontSize = 12.sp,
                                    color = ColorTextSub,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Reference Material Link
                        if (!detailItem.dto.referenceUrl.isNullOrBlank()) {
                            item {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detailItem.dto.referenceUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        runCatching { context.startActivity(intent) }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open Reference Material", fontSize = 12.sp)
                                }
                            }
                        }

                        // Submission History Audit Trail
                        item {
                            Spacer(Modifier.height(6.dp))
                            val count = detailItem.submissions.size
                            val attemptsLabel = if (count == 1) "1 attempt recorded" else "$count attempts recorded"
                            Text(
                                text = "Submission History ($attemptsLabel)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ColorTextDark
                            )
                        }

                        if (detailItem.submissions.isEmpty()) {
                            item {
                                Text("No submissions recorded yet for this assignment.", fontSize = 12.sp, color = ColorTextSub)
                            }
                        } else {
                            items(detailItem.submissions) { sub ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.8.dp, ColorBorderHairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Attempt #${sub.attemptNumber}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = ColorPrimaryPurple
                                            )
                                            val isLate = AssignmentPolicy.isSubmissionLate(sub, detailItem.dto)
                                            val (subBadgeBg, subBadgeFg, subBadgeText) = when {
                                                sub.evaluated -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Evaluated")
                                                sub.submissionStatus?.equals("RESUBMISSION_REQUIRED", ignoreCase = true) == true ->
                                                    Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), "Revision Requested")
                                                isLate -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Late Submitted")
                                                else -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Submitted")
                                            }
                                            Surface(shape = RoundedCornerShape(6.dp), color = subBadgeBg) {
                                                Text(
                                                    text = subBadgeText,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = subBadgeFg,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        val subUrl = sub.submissionUrl ?: sub.githubRepoUrl ?: sub.submissionText.orEmpty()
                                        if (subUrl.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = ColorPurpleLight,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val cleanUrl = AssignmentValidator.normalizeUrl(subUrl)
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        runCatching { context.startActivity(intent) }
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Link, contentDescription = null, tint = ColorPrimaryPurple, modifier = Modifier.size(14.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = subUrl,
                                                            fontSize = 11.5.sp,
                                                            color = ColorPrimaryPurple,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Spacer(Modifier.width(4.dp))
                                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Link", tint = ColorPrimaryPurple, modifier = Modifier.size(13.dp))
                                                }
                                            }
                                        }

                                        val commit = sub.commitSha ?: sub.commitHash
                                        if (!commit.isNullOrBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text("Commit SHA: $commit", fontSize = 11.sp, color = ColorTextSub)
                                        }

                                        if (!sub.submittedAt.isNullOrBlank()) {
                                            val formattedDate = AssignmentPolicy.formatSubmissionDateTime(sub.submittedAt)
                                            Spacer(Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Schedule, contentDescription = null, tint = ColorTextSub, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Submitted: $formattedDate", fontSize = 11.sp, color = ColorTextSub)
                                            }
                                        }

                                        if (sub.evaluated) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Evaluated: ${sub.marksObtained ?: "Passed"} • Feedback: ${sub.feedback ?: "None"}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF15803D)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (detailItem.canSubmit) {
                        Button(
                            onClick = {
                                selectedAssignmentDetails = null
                                selectedAssignmentForSubmission = detailItem
                                submissionMethod = SubmissionMethod.fromId(detailItem.dto.submissionMethod)
                                submissionInputUrl = detailItem.latestSubmission?.submissionUrl.orEmpty()
                                submissionCommitHash = detailItem.latestSubmission?.commitSha ?: detailItem.latestSubmission?.commitHash.orEmpty()
                                submissionComments = ""
                                submissionErrorMessage = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (detailItem.attemptsCount > 0) "Resubmit (${detailItem.attemptsCount + 1}/${detailItem.maxAttempts})" else "Submit Assignment",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(onClick = { selectedAssignmentDetails = null }, shape = RoundedCornerShape(8.dp)) {
                            Text("Close")
                        }
                    }
                },
                dismissButton = {
                    if (detailItem.canSubmit) {
                        TextButton(onClick = { selectedAssignmentDetails = null }) {
                            Text("Close", color = ColorTextSub)
                        }
                    }
                }
            )
        }

        // =======================================================
        // MODAL 2: STUDENT SUBMISSION FORM (URL-BASED)
        // =======================================================
        selectedAssignmentForSubmission?.let { target ->
            val nextAttempt = target.attemptsCount + 1
            val commitRequired = target.dto.commitHashRequired || submissionMethod == SubmissionMethod.GITHUB

            AlertDialog(
                onDismissRequest = { selectedAssignmentForSubmission = null },
                title = {
                    Column {
                        Text(
                            text = if (target.attemptsCount > 0) "Resubmit Assignment" else "Submit Assignment",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "${target.dto.title} • Attempt $nextAttempt of ${target.maxAttempts}",
                            fontSize = 12.sp,
                            color = ColorTextSub
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Submission Method Picker
                        Text("Select Submission Method", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorTextDark)
                        val methods = listOf(
                            SubmissionMethod.GITHUB,
                            SubmissionMethod.GOOGLE_DRIVE,
                            SubmissionMethod.ONEDRIVE,
                            SubmissionMethod.GOOGLE_DOCS,
                            SubmissionMethod.DEPLOYMENT,
                            SubmissionMethod.OTHER
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(methods) { method ->
                                FilterChip(
                                    selected = submissionMethod == method,
                                    onClick = {
                                        submissionMethod = method
                                        submissionErrorMessage = null
                                    },
                                    label = { Text(method.displayName, fontSize = 11.sp) }
                                )
                            }
                        }

                        // URL Input
                        val urlLabel = when (submissionMethod) {
                            SubmissionMethod.GITHUB -> "GitHub Repository URL"
                            SubmissionMethod.GOOGLE_DRIVE -> "Google Drive Share Link"
                            SubmissionMethod.ONEDRIVE -> "OneDrive Share Link"
                            SubmissionMethod.GOOGLE_DOCS -> "Google Docs / Sheets Link"
                            SubmissionMethod.DEPLOYMENT -> "Live Deployment URL"
                            else -> "Submission URL"
                        }
                        OutlinedTextField(
                            value = submissionInputUrl,
                            onValueChange = {
                                submissionInputUrl = it
                                submissionErrorMessage = null
                            },
                            label = { Text(urlLabel) },
                            placeholder = { Text(submissionMethod.defaultPlaceholder) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = SureFormDefaults.outlinedTextFieldColors(),
                            isError = submissionErrorMessage != null
                        )

                        // Commit Hash Input (if GitHub or required)
                        if (commitRequired) {
                            Column {
                                OutlinedTextField(
                                    value = submissionCommitHash,
                                    onValueChange = {
                                        submissionCommitHash = it.trim()
                                        submissionErrorMessage = null
                                    },
                                    label = { Text("GitHub Commit Hash / SHA") },
                                    placeholder = { Text("e.g. a72f10c or 40-char SHA") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = SureFormDefaults.outlinedTextFieldColors()
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Identifies exact submitted code version for audit & auto-grading.",
                                    fontSize = 10.5.sp,
                                    color = ColorTextSub
                                )
                            }
                        }

                        // Student Comments
                        OutlinedTextField(
                            value = submissionComments,
                            onValueChange = { submissionComments = it },
                            label = { Text("Student Comments (Optional)") },
                            placeholder = { Text("e.g. Completed module and testbench verification.") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(8.dp),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )

                        // Inline Error Alert
                        if (!submissionErrorMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = submissionErrorMessage!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val validation = AssignmentValidator.validate(
                                rawUrl = submissionInputUrl,
                                method = submissionMethod,
                                commitHashRequired = commitRequired,
                                rawCommitHash = submissionCommitHash
                            )
                            if (!validation.isValid) {
                                submissionErrorMessage = validation.errorMessage
                                return@Button
                            }

                            // Open Two-step Confirmation Dialog
                            submissionConfirmData = SubmissionConfirmPayload(
                                assignment = target,
                                method = submissionMethod,
                                cleanUrl = AssignmentValidator.normalizeUrl(submissionInputUrl),
                                commitHash = submissionCommitHash.trim(),
                                studentComment = submissionComments.trim(),
                                attemptNumber = nextAttempt
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Review Submission", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedAssignmentForSubmission = null }) {
                        Text("Cancel", color = ColorTextSub)
                    }
                }
            )
        }

        // =======================================================
        // MODAL 3: EXPLICIT SUBMISSION CONFIRMATION DIALOG
        // =======================================================
        submissionConfirmData?.let { confirmData ->
            AlertDialog(
                onDismissRequest = { if (!isSubmitting) submissionConfirmData = null },
                icon = {
                    Icon(Icons.Default.UploadFile, null, tint = ColorPrimaryPurple, modifier = Modifier.size(28.dp))
                },
                title = {
                    Text("Confirm Assignment Submission", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Are you sure you want to submit this assignment?", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = ColorTextDark)

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Assignment: ${confirmData.assignment.dto.title}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Method: ${confirmData.method.displayName}", fontSize = 12.sp)
                                Text("URL: ${confirmData.cleanUrl}", fontSize = 11.5.sp, color = ColorPrimaryPurple)
                                if (confirmData.commitHash.isNotBlank()) {
                                    Text("Commit: ${confirmData.commitHash}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text("Attempt: ${confirmData.attemptNumber} of ${confirmData.assignment.maxAttempts}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "⚠️ Once submitted, this attempt will be permanently recorded in the audit trail.",
                            fontSize = 11.5.sp,
                            color = Color(0xFFB45309),
                            lineHeight = 15.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isSubmitting = true
                            scope.launch {
                                val api = ApiClient.getService(tokenManager)
                                val isGithub = confirmData.method == SubmissionMethod.GITHUB
                                val req = AssignmentSubmissionRequest(
                                    submissionLink = confirmData.cleanUrl,
                                    assignment = confirmData.assignment.dto.id,
                                    assignmentId = confirmData.assignment.dto.id,
                                    submissionText = confirmData.studentComment.ifBlank { confirmData.cleanUrl },
                                    studentComment = confirmData.studentComment,
                                    githubRepoUrl = if (isGithub) confirmData.cleanUrl else null,
                                    repositoryUrl = if (isGithub) confirmData.cleanUrl else null,
                                    commitSha = if (confirmData.commitHash.isNotBlank()) confirmData.commitHash else null,
                                    commitHash = if (confirmData.commitHash.isNotBlank()) confirmData.commitHash else null,
                                    submissionMethod = confirmData.method.id,
                                    attemptNumber = confirmData.attemptNumber
                                )

                                var response = runCatching { api.submitAssignment(req) }.getOrNull()

                                // If submission returns duplicate/already exists error, fallback to PATCH
                                if (response == null || !response.isSuccessful) {
                                    val errStr = runCatching { response?.errorBody()?.string() }.getOrNull()
                                    val isDuplicate = (response?.code() in setOf(400, 409))
                                    if (isDuplicate) {
                                        val existingSubs = runCatching { api.getSubmissions() }.getOrNull()?.body()?.results.orEmpty()
                                        val matching = existingSubs.firstOrNull { it.assignment == confirmData.assignment.dto.id || it.assignmentId == confirmData.assignment.dto.id }
                                        if (matching != null) {
                                            val patchMap = mutableMapOf<String, Any>(
                                                "submission_url" to confirmData.cleanUrl,
                                                "submission_text" to confirmData.studentComment.ifBlank { confirmData.cleanUrl },
                                                "submission_method" to confirmData.method.id,
                                                "attempt_number" to confirmData.attemptNumber
                                            )
                                            if (confirmData.studentComment.isNotBlank()) patchMap["student_comment"] = confirmData.studentComment
                                            if (isGithub) {
                                                patchMap["github_repo_url"] = confirmData.cleanUrl
                                                patchMap["repository_url"] = confirmData.cleanUrl
                                                if (confirmData.commitHash.isNotBlank()) {
                                                    patchMap["commit_sha"] = confirmData.commitHash
                                                    patchMap["commit_hash"] = confirmData.commitHash
                                                }
                                            }
                                            val patchRes = runCatching { api.patchSubmission(matching.id, patchMap) }.getOrNull()
                                            if (patchRes?.isSuccessful == true) {
                                                response = patchRes
                                            }
                                        }
                                    }
                                }

                                if (response?.isSuccessful == true) {
                                    Toast.makeText(context, "Assignment submitted successfully (Attempt #${confirmData.attemptNumber})", Toast.LENGTH_SHORT).show()
                                    submissionConfirmData = null
                                    selectedAssignmentForSubmission = null
                                    loadAssignments()
                                } else {
                                    val errorBodyStr = runCatching { response?.errorBody()?.string() }.getOrNull()
                                    val backendMessage = NetworkUtils.parseBackendErrorMessage(errorBodyStr)
                                    val finalError = when {
                                        !backendMessage.isNullOrBlank() -> backendMessage
                                        response != null && response.code() == 400 -> "Invalid submission link or format. Please check required fields."
                                        response != null && (response.code() == 401 || response.code() == 403) -> "Authorization expired. Please re-login."
                                        response != null && response.code() >= 500 -> "Server temporary error (${response.code()}). Please retry."
                                        else -> "Submission could not be completed. Please check your network and retry."
                                    }
                                    submissionErrorMessage = finalError
                                    Toast.makeText(context, finalError, Toast.LENGTH_LONG).show()
                                    submissionConfirmData = null
                                }
                                isSubmitting = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isSubmitting
                    ) {
                        Text(if (isSubmitting) "Submitting..." else "Confirm Submission", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { submissionConfirmData = null }, enabled = !isSubmitting) {
                        Text("Back", color = ColorTextSub)
                    }
                }
            )
        }

        // =======================================================
        // MODAL 4: EVALUATION & FEEDBACK DIALOG
        // =======================================================
        selectedAssignmentForFeedback?.let { evaluatedItem ->
            val sub = evaluatedItem.latestSubmission
            val scoreText = sub?.marksObtained ?: evaluatedItem.dto.score?.toString() ?: "--"
            val maxMarks = evaluatedItem.dto.maxMarks.toDoubleOrNull()?.toInt() ?: 100

            AlertDialog(
                onDismissRequest = { selectedAssignmentForFeedback = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = semanticColors.success, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Evaluation & Grade", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(evaluatedItem.dto.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = ColorTextDark)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = semanticColors.successContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Final Marks Awarded", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = semanticColors.onSuccessContainer)
                                Text(
                                    "$scoreText / $maxMarks",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = semanticColors.onSuccessContainer
                                )
                            }
                        }

                        Text("Mentor Feedback:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ColorTextDark)
                        val feedback = sub?.feedback ?: evaluatedItem.dto.grade ?: "Good effort and submission!"
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = feedback,
                                fontSize = 12.5.sp,
                                color = ColorTextDark,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 17.sp
                            )
                        }

                        if (!sub?.evaluatedBy.isNullOrBlank() || !sub?.evaluatedAt.isNullOrBlank()) {
                            Text(
                                text = "Evaluated by: ${sub?.evaluatedBy ?: "Mentor"} • ${sub?.evaluatedAt ?: ""}",
                                fontSize = 11.sp,
                                color = ColorTextSub
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { selectedAssignmentForFeedback = null },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
