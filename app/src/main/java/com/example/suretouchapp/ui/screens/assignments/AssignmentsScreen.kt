package com.example.suretouchapp.ui.screens.assignments

import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AssignmentSubmissionRequest
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.theme.SureFormDefaults
import com.example.suretouchapp.ui.theme.sureSemanticColors
import kotlinx.coroutines.launch

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

enum class AssignmentStatus {
    PENDING, SUBMITTED, GRADED
}

data class AssignmentItem(
    val id: String,
    val submissionId: String? = null,
    val courseCode: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val maxMarks: Int,
    var status: AssignmentStatus,
    var submittedLink: String? = null,
    val score: Int? = null,
    val grade: String? = null,
    val feedback: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentsScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedAssignmentForSubmission by remember { mutableStateOf<AssignmentItem?>(null) }
    var selectedAssignmentForFeedback by remember { mutableStateOf<AssignmentItem?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var submissionInputLink by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val semanticColors = sureSemanticColors()

    val assignmentList = remember { mutableStateListOf<AssignmentItem>() }

    suspend fun loadAssignments() {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val api = ApiClient.getService(tokenManager)
            val response = api.getAssignments()
            val submissionsResponse = runCatching { api.getSubmissions() }.getOrNull()
            if (response.isSuccessful) {
                val rawAssignments = response.body()?.results.orEmpty()
                val rawSubmissions = submissionsResponse?.takeIf { it.isSuccessful }?.body()?.results.orEmpty()
                SureProEdNotificationManager.syncAssignments(context, rawAssignments)
                SureProEdNotificationManager.syncSubmissionsAndGrades(context, rawSubmissions, rawAssignments)
                
                val submissionMap = rawSubmissions.filter { !it.assignment.isNullOrBlank() }
                    .associateBy { it.assignment!! }

                assignmentList.clear()
                assignmentList.addAll(rawAssignments.map { assignment ->
                    val userSubmission = submissionMap[assignment.id]
                    val isGraded = userSubmission?.evaluated == true || 
                        assignment.status?.uppercase() in setOf("GRADED", "EVALUATED")
                    val isSubmitted = userSubmission != null || 
                        !assignment.submittedLink.isNullOrBlank() || 
                        assignment.status?.uppercase() == "SUBMITTED"

                    val status = when {
                        isGraded -> AssignmentStatus.GRADED
                        isSubmitted -> AssignmentStatus.SUBMITTED
                        else -> AssignmentStatus.PENDING
                    }
                    val submittedLink = userSubmission?.submissionUrl 
                        ?: userSubmission?.githubRepoUrl 
                        ?: userSubmission?.submissionText 
                        ?: assignment.submittedLink
                    val score = userSubmission?.marksObtained?.toDoubleOrNull()?.toInt() ?: assignment.score
                    val feedback = userSubmission?.feedback
                    val grade = when {
                        score != null && score >= 90 -> "A+"
                        score != null && score >= 80 -> "A"
                        score != null && score >= 70 -> "B"
                        score != null && score >= 60 -> "C"
                        score != null -> "Passed"
                        else -> assignment.grade
                    }

                    AssignmentItem(
                        id = assignment.id,
                        submissionId = userSubmission?.id,
                        courseCode = assignment.cohort ?: "Assigned cohort",
                        title = assignment.title,
                        description = assignment.description,
                        dueDate = assignment.dueDate,
                        maxMarks = assignment.maxMarks.toDoubleOrNull()?.toInt() ?: 100,
                        status = status,
                        submittedLink = submittedLink,
                        score = score,
                        grade = grade,
                        feedback = feedback
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
            assignmentList.clear()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadAssignments() }

    val filteredAssignments = remember(selectedFilter, assignmentList.toList()) {
        when (selectedFilter) {
            "Pending" -> assignmentList.filter { it.status == AssignmentStatus.PENDING }
            "Submitted" -> assignmentList.filter { it.status == AssignmentStatus.SUBMITTED }
            "Graded" -> assignmentList.filter { it.status == AssignmentStatus.GRADED }
            else -> assignmentList
        }
    }

    val pendingCount = assignmentList.count { it.status == AssignmentStatus.PENDING }
    val submittedCount = assignmentList.count { it.status == AssignmentStatus.SUBMITTED }
    val gradedCount = assignmentList.count { it.status == AssignmentStatus.GRADED }

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Assignments & Tasks",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ColorDarkHeader
                    )
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
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // =======================================================
                    // 1. HERO METRICS STATS BAR
                    // =======================================================
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Metric 1: Pending
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
                                    Text("Pending", fontSize = 11.5.sp, color = ColorTextSub, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$pendingCount Due",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }

                            // Metric 2: Submitted
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
                                        text = "$submittedCount Review",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }

                            // Metric 3: Graded
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
                                    Text("Graded", fontSize = 11.5.sp, color = ColorTextSub, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$gradedCount Done",
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
                        val filters = listOf("All", "Pending", "Submitted", "Graded")
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
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filter,
                                        fontSize = 13.5.sp,
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
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Assignment, null, tint = ColorPrimaryPurple, modifier = Modifier.size(34.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No assignments from the backend", fontWeight = FontWeight.Bold, color = ColorTextDark)
                                    Text("New cohort tasks will appear here automatically.", fontSize = 12.sp, color = ColorTextSub)
                                }
                            }
                        }
                    }
                    items(filteredAssignments, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ColorBorderHairline),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Header Row: Course Tag & Status Pill
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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

                                    val (statusBg, statusFg, statusText) = when (item.status) {
                                        AssignmentStatus.PENDING -> Triple(semanticColors.warningContainer, semanticColors.onWarningContainer, "PENDING")
                                        AssignmentStatus.SUBMITTED -> Triple(semanticColors.infoContainer, semanticColors.onInfoContainer, "SUBMITTED")
                                        AssignmentStatus.GRADED -> Triple(semanticColors.successContainer, semanticColors.onSuccessContainer, "GRADED")
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = statusBg
                                    ) {
                                        Text(
                                            text = statusText,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusFg,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Assignment Title
                                Text(
                                    text = item.title,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorTextDark
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Description
                                Text(
                                    text = item.description,
                                    fontSize = 12.5.sp,
                                    color = ColorTextSub,
                                    lineHeight = 17.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                HorizontalDivider(color = ColorBorderHairline, thickness = 0.8.dp)

                                Spacer(modifier = Modifier.height(10.dp))

                                // Footer Row: Due Date & Action Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Event,
                                            contentDescription = null,
                                            tint = ColorTextSub,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = item.dueDate,
                                            fontSize = 11.5.sp,
                                            color = ColorTextSub,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    when (item.status) {
                                        AssignmentStatus.PENDING -> {
                                            Button(
                                                onClick = {
                                                    selectedAssignmentForSubmission = item
                                                    submissionInputLink = item.submittedLink ?: ""
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Upload,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Submit Work", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        AssignmentStatus.SUBMITTED -> {
                                            OutlinedButton(
                                                onClick = {
                                                    selectedAssignmentForSubmission = item
                                                    submissionInputLink = item.submittedLink ?: ""
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, ColorPrimaryPurple),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Edit Link", fontSize = 12.sp, color = ColorPrimaryPurple, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        AssignmentStatus.GRADED -> {
                                            Button(
                                                onClick = { selectedAssignmentForFeedback = item },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Grade: ${item.score}/${item.maxMarks} (${item.grade})",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
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
        // SUBMISSION LINK DIALOG MODAL
        // =======================================================
        selectedAssignmentForSubmission?.let { target ->
            AlertDialog(
                onDismissRequest = { selectedAssignmentForSubmission = null },
                title = {
                    Text(
                        text = if (target.status == AssignmentStatus.SUBMITTED) "Edit Submission" else "Submit Assignment",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = target.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = ColorTextDark
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = submissionInputLink,
                            onValueChange = { submissionInputLink = it },
                            label = { Text("Submission Link (GitHub / Google Drive)") },
                            placeholder = { Text("https://github.com/username/project") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = SureFormDefaults.outlinedTextFieldColors()
                        )
                    }
                },
                confirmButton = {
                    val isConnected = com.example.suretouchapp.ui.components.LocalBackendConnected.current
                    Button(
                        onClick = {
                            if (!isConnected) return@Button
                            if (submissionInputLink.isBlank()) return@Button
                            isSubmitting = true
                            scope.launch {
                                val api = ApiClient.getService(tokenManager)
                                val cleanLink = submissionInputLink.trim()
                                val req = AssignmentSubmissionRequest(
                                    submissionLink = cleanLink,
                                    assignment = target.id,
                                    submissionText = cleanLink
                                )
                                val response = if (!target.submissionId.isNullOrBlank()) {
                                    val patchRes = runCatching {
                                        api.patchSubmission(target.submissionId, mapOf(
                                            "submission_url" to cleanLink,
                                            "submission_text" to cleanLink
                                        ))
                                    }.getOrNull()
                                    if (patchRes?.isSuccessful == true) patchRes
                                    else runCatching { api.submitAssignment(req) }.getOrNull()
                                } else {
                                    runCatching { api.submitAssignment(req) }.getOrNull()
                                }

                                if (response?.isSuccessful == true) {
                                    val newSub = response.body()
                                    target.submittedLink = cleanLink
                                    target.status = AssignmentStatus.SUBMITTED
                                    val idx = assignmentList.indexOfFirst { it.id == target.id }
                                    if (idx >= 0) {
                                        assignmentList[idx] = assignmentList[idx].copy(
                                            status = AssignmentStatus.SUBMITTED,
                                            submittedLink = cleanLink,
                                            submissionId = newSub?.id ?: target.submissionId
                                        )
                                    }
                                    selectedAssignmentForSubmission = null
                                    android.widget.Toast.makeText(context, "Assignment submitted successfully.", android.widget.Toast.LENGTH_SHORT).show()
                                    loadAssignments()
                                } else {
                                    android.widget.Toast.makeText(context, "Unable to submit the assignment. Please check connection and try again.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                isSubmitting = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isSubmitting && isConnected
                    ) {
                        Text(if (!isConnected) "Submit (Offline)" else if (isSubmitting) "Submitting..." else "Submit Link", fontWeight = FontWeight.Bold)
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
        // GRADED FEEDBACK DIALOG MODAL
        // =======================================================
        selectedAssignmentForFeedback?.let { graded ->
            AlertDialog(
                onDismissRequest = { selectedAssignmentForFeedback = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = semanticColors.success,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Evaluation & Feedback", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                },
                text = {
                    Column {
                        Text(graded.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextDark)
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = semanticColors.successContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Final Score", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = semanticColors.onSuccessContainer)
                                Text(
                                    "${graded.score} / ${graded.maxMarks} (${graded.grade})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = semanticColors.onSuccessContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Mentor Feedback:", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = ColorTextDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = graded.feedback ?: "Great effort!",
                            fontSize = 13.sp,
                            color = ColorTextSub,
                            lineHeight = 18.sp
                        )
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
