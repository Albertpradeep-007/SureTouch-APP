package com.example.suretouchapp.ui.screens.courses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.ApplicationDto
import com.example.suretouchapp.data.model.CourseDto
import com.example.suretouchapp.data.model.ExamDto
import com.example.suretouchapp.data.repository.DashboardRepository
import com.example.suretouchapp.data.repository.DashboardSnapshot
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrolledCourseScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit,
    onBrowseCourses: () -> Unit,
    onViewJourney: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var application by remember { mutableStateOf<ApplicationDto?>(null) }
    var course by remember { mutableStateOf<CourseDto?>(null) }
    var exam by remember { mutableStateOf<ExamDto?>(null) }
    var dashboard by remember { mutableStateOf(DashboardSnapshot()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscontinueDialog by remember { mutableStateOf(false) }
    var discontinuing by remember { mutableStateOf(false) }
    var discontinueError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadCourseData() {
        loading = true
        error = null
        errorTitle = null
        try {
            val api = ApiClient.getService(tokenManager)
            val (applications, courses, exams, loadedDashboard) = coroutineScope {
                val applicationRead = async { api.getMyApplications().body()?.results.orEmpty() }
                val courseRead = async { api.getCourses().body()?.results.orEmpty() }
                val examRead = async { api.getScreeningResults().body()?.results.orEmpty() }
                val dashboardRead = async { DashboardRepository(tokenManager).load(force = true) }
                Quadruple(
                    applicationRead.await(),
                    courseRead.await(),
                    examRead.await(),
                    dashboardRead.await()
                )
            }
            application = applications.firstOrNull { it.blocksCourseSelection() }
            course = courses.firstOrNull { it.id == application?.course && it.status.equals("PUBLISHED", ignoreCase = true) }
            exam = exams.firstOrNull { it.application == application?.id }
            dashboard = loadedDashboard
            isConnected = true
            hasLoadedOnce = true
            isOffline = false
            error = null
            errorTitle = null
        } catch (e: Exception) {
            val errorInfo = NetworkUtils.getNetworkErrorInfo(context, e)
            isConnected = false
            isOffline = errorInfo.isOffline
            errorTitle = errorInfo.title
            error = errorInfo.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(tokenManager) { loadCourseData() }

    if (showDiscontinueDialog) {
        AlertDialog(
            onDismissRequest = { if (!discontinuing) showDiscontinueDialog = false },
            title = { Text("Discontinue this course?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your current application/enrolment will be closed. After confirmation, you can select one published course again."
                )
            },
            confirmButton = {
                    val isConnected = com.example.suretouchapp.ui.components.LocalBackendConnected.current
                    Button(
                        onClick = {
                            if (!isConnected) return@Button
                            val applicationId = application?.id ?: return@Button
                            scope.launch {
                                discontinuing = true
                                discontinueError = null
                                val response = runCatching {
                                    ApiClient.getService(tokenManager).discontinueCourse(
                                        applicationId,
                                        mapOf("reason" to "Student discontinued from the Android app.")
                                    )
                                }.getOrNull()
                                if (response?.isSuccessful == true) {
                                    application = response.body()?.application ?: application?.copy(status = "DROPPED")
                                    showDiscontinueDialog = false
                                    onBrowseCourses()
                                } else {
                                    discontinueError = "The course could not be discontinued. Please try again or contact support."
                                }
                                discontinuing = false
                            }
                        },
                        enabled = !discontinuing && isConnected,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
                    ) {
                        if (discontinuing) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (!isConnected) "Discontinue (Offline)" else "Confirm Discontinue")
                    }
            },
            dismissButton = { TextButton(onClick = { showDiscontinueDialog = false }, enabled = !discontinuing) { Text("Keep Course") } }
        )
    }

    BackendConnectionGate(
        isLoading = loading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = error,
        loadingMessage = "Connecting to SURE Trust Enrolment Portal...",
        onRetry = { scope.launch { loadCourseData() } },
        onLogout = null
    ) {
        Scaffold(
            containerColor = Color(0xFFF8FAFC),
            topBar = {
            TopAppBar(
                title = { Text("My Enrolled Course", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                SureTrustLoadingIndicator(message = "Loading your course")
            }
            application == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(68.dp), tint = Color(0xFF6C2BD9))
                Spacer(Modifier.height(16.dp))
                Text("No course selected yet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(error ?: "Choose a published programme from the live SURE ProEd catalogue.", color = Color(0xFF64748B))
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBrowseCourses) { Text("Browse Courses") }
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color(0xFFD8B4FE))
                ) {
                    Column(
                        Modifier.background(Brush.linearGradient(listOf(Color(0xFF6D28D9), Color(0xFF4C1D95)))).padding(22.dp)
                    ) {
                        Box(Modifier.size(48.dp).background(Color.White.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.School, null, tint = Color.White)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(course?.name ?: dashboard.courseName ?: "Selected programme", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Application status: ${application?.status ?: "APPLIED"}", color = Color.White.copy(alpha = .85f))
                        Text(
                            dashboard.cohortCode?.let { "Cohort $it" } ?: "Cohort not assigned yet",
                            color = Color(0xFFBEF264),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AssignmentTurnedIn, null, tint = Color(0xFF6C2BD9))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Screening marks & grade", fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    exam?.percentage != null -> "${exam?.percentage}% • ${if (exam?.qualified == true) "Qualified" else "Result published"}"
                                    exam?.marksObtained != null -> "${exam?.marksObtained}/${exam?.totalMarks ?: "--"} • ${exam?.status ?: "Evaluated"}"
                                    else -> "Your evaluated result will appear here from the backend"
                                },
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Button(
                    onClick = onViewJourney,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("View Student Journey") }
                OutlinedButton(
                    onClick = { showDiscontinueDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = BorderStroke(1.dp, Color(0xFFB91C1C)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB91C1C))
                ) {
                    Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (application?.assignedCohort.isNullOrBlank()) "Cancel Course Selection" else "Discontinue Course")
                }
                discontinueError?.let { Text(it, color = Color(0xFFB91C1C), fontSize = 12.sp) }
            }
        }
    }
}
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
