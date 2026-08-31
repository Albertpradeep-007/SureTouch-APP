package com.example.suretouchapp.ui.screens.courses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.catalog.CourseCategory
import com.example.suretouchapp.data.catalog.officialCourses
import com.example.suretouchapp.data.model.ApplicationCreateRequest
import com.example.suretouchapp.data.model.ApplicationDto
import com.example.suretouchapp.data.model.CourseDto
import com.example.suretouchapp.data.model.CourseSelectionDto
import com.example.suretouchapp.data.model.ExamDto
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLogo
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import com.example.suretouchapp.ui.theme.SureFormDefaults
import com.example.suretouchapp.ui.theme.sureSemanticColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

private val CoursesPurple @Composable get() = MaterialTheme.colorScheme.primary
private val CoursesDeepPurple = Color(0xFF4C1D95)
private val CoursesCanvas @Composable get() = MaterialTheme.colorScheme.background
private val CoursesText @Composable get() = MaterialTheme.colorScheme.onSurface
private val CoursesSubtext @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val CoursesBorder @Composable get() = MaterialTheme.colorScheme.outlineVariant

private data class CourseUiModel(
    val websiteId: String,
    val title: String,
    val eligibility: String,
    val skillModules: List<String>,
    val websiteUrl: String,
    val backendId: String? = null,
    val category: CourseCategory? = null,
    val hasOpenCohort: Boolean = false
)

private enum class CourseFilter(val label: String) {
    ALL("All"), NON_MEDICAL("Non Medical"), MEDICAL("Medical")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit,
    onApplicationSubmitted: () -> Unit
) {
    BackHandler {
        onBack()
    }

    var backendCourses by remember { mutableStateOf<List<CourseDto>>(emptyList()) }
    var applications by remember { mutableStateOf<List<ApplicationDto>>(emptyList()) }
    var courseSelection by remember { mutableStateOf<CourseSelectionDto?>(null) }
    var applyingCourseId by remember { mutableStateOf<String?>(null) }
    var optimisticApplication by remember { mutableStateOf<ApplicationDto?>(null) }
    var coursePendingConfirmation by remember { mutableStateOf<CourseUiModel?>(null) }
    var applicationSuccessCourse by remember { mutableStateOf<CourseUiModel?>(null) }
    var applicationError by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(CourseFilter.ALL) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val semanticColors = sureSemanticColors()

    val loadData = {
        scope.launch {
            isRefreshing = true
            message = null
            try {
                val api = ApiClient.getService(tokenManager)
                val payload = coroutineScope {
                    val coursesRequest = async { api.getCourses() }
                    val applicationsRequest = async { api.getMyApplications() }
                    val selectionRequest = async { runCatching { api.getCourseSelection() }.getOrNull() }
                    Triple(coursesRequest.await(), applicationsRequest.await(), selectionRequest.await())
                }
                val coursesResponse = payload.first
                val applicationsResponse = payload.second
                val selectionResponse = payload.third
                if (coursesResponse.isSuccessful) {
                    backendCourses = coursesResponse.body()?.results.orEmpty()
                        .filter { it.status.equals("PUBLISHED", ignoreCase = true) }
                    isConnected = true
                    hasLoadedOnce = true
                    isOffline = false
                    connectionError = null
                    errorTitle = null
                } else {
                    val errorInfo = NetworkUtils.getNetworkErrorInfo(context, null)
                    backendCourses = emptyList()
                    isConnected = false
                    isOffline = errorInfo.isOffline
                    errorTitle = errorInfo.title
                    connectionError = errorInfo.message
                }
            } catch (e: Exception) {
                val errorInfo = NetworkUtils.getNetworkErrorInfo(context, e)
                backendCourses = emptyList()
                isConnected = false
                isOffline = errorInfo.isOffline
                errorTitle = errorInfo.title
                connectionError = errorInfo.message
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    val fallbackBlockingApplication = applications.firstOrNull { it.blocksCourseSelection() }
    val blockingApplication = courseSelection?.blockingApplication
        ?: if (courseSelection?.canApply == true) null else fallbackBlockingApplication
    val canSelectCourse = courseSelection?.canApply ?: (fallbackBlockingApplication == null)
    val selectionMessage = if (canSelectCourse) null else courseSelection?.message ?: blockingApplication?.let {
        if (!it.assignedCohort.isNullOrBlank() || it.status.uppercase(Locale.US) in setOf("COHORT_ASSIGNED", "IN_PROGRESS")) {
            "You are already enrolled. You can view every published course, but Apply is locked until the current course is completed, discontinued, or cancelled."
        } else {
            "Only one course can be selected at a time. Other Apply buttons unlock if this selection is not qualified, verification fails, or it is cancelled."
        }
    }

    val mergedCourses = remember(backendCourses) {
        backendCourses.map { course ->
            val catalogueDetails = officialCourses.firstOrNull {
                normalizeTitle(it.title) == normalizeTitle(course.name)
            }
            CourseUiModel(
                websiteId = course.id,
                title = course.name,
                eligibility = course.prerequisites?.takeIf { it.isNotBlank() }
                    ?: catalogueDetails?.eligibility
                    ?: course.description,
                skillModules = catalogueDetails?.skillModules
                    ?: listOfNotNull(course.domain, course.subject).ifEmpty { listOf("Skill Development") },
                websiteUrl = catalogueDetails?.websiteUrl ?: "https://www.suretrustforruralyouth.com/courses/",
                backendId = course.id,
                category = catalogueDetails?.category ?: when (course.category?.lowercase()) {
                    "medical" -> CourseCategory.MEDICAL
                    else -> CourseCategory.NON_MEDICAL
                },
                hasOpenCohort = course.hasOpenCohort
            )
        }
    }

    val visibleCourses = remember(mergedCourses, searchQuery, selectedFilter) {
        val query = searchQuery.trim()
        mergedCourses.filter { course ->
            val matchesCategory = when (selectedFilter) {
                CourseFilter.ALL -> true
                CourseFilter.NON_MEDICAL -> course.category == CourseCategory.NON_MEDICAL || course.category == null
                CourseFilter.MEDICAL -> course.category == CourseCategory.MEDICAL
            }
            val matchesQuery = query.isBlank() ||
                course.title.contains(query, ignoreCase = true) ||
                course.eligibility.contains(query, ignoreCase = true) ||
                course.skillModules.any { it.contains(query, ignoreCase = true) }
            matchesCategory && matchesQuery
        }
    }

    val submitApplication: (CourseUiModel) -> Unit = submit@{ course ->
        val backendId = course.backendId
        when {
            backendId == null -> uriHandler.openUri(course.websiteUrl)
            else -> scope.launch {
                applyingCourseId = backendId
                val applicationNumber = "APP-${System.currentTimeMillis()}"
                try {
                    val response = ApiClient.getService(tokenManager)
                        .applyForCourse(
                            ApplicationCreateRequest(
                                applicationNumber = applicationNumber,
                                course = backendId
                            )
                        )
                    if (response.isSuccessful) {
                        val submittedApplication = response.body() ?: ApplicationDto(
                            id = "pending-$applicationNumber",
                            applicationNumber = applicationNumber,
                            course = backendId,
                            status = "APPLIED"
                        )
                        optimisticApplication = submittedApplication
                        applications = listOf(submittedApplication) + applications.filterNot {
                            it.id == submittedApplication.id ||
                                (!it.applicationNumber.isNullOrBlank() && it.applicationNumber == submittedApplication.applicationNumber)
                        }
                        courseSelection = CourseSelectionDto(
                            canApply = false,
                            reason = "ACTIVE_APPLICATION",
                            message = "Only one course can be selected at a time.",
                            blockingApplication = submittedApplication
                        )
                        tokenManager.markCourseApplied()
                        tokenManager.saveApplicationSnapshot(
                            submittedApplication.applicationNumber,
                            submittedApplication.status,
                            submittedApplication.course,
                            course.title,
                            submittedApplication.assignedCohort,
                            submittedApplication.qualified
                        )
                        SureProEdNotificationManager.showCourseApplicationSuccess(context, course.title)
                        message = null
                        applicationSuccessCourse = course
                        loadData()
                    } else {
                        applicationError = when (response.code()) {
                            400 -> "Only a published course can be selected. Please refresh the catalogue."
                            401, 403 -> "Your student session has expired. Sign out, sign in again, then apply."
                            409 -> "Another course is already selected. It must be not qualified, verification-failed, completed, discontinued, or cancelled before choosing a new one."
                            else -> "The application could not be submitted. Server response: ${response.code()}."
                        }
                        if (response.code() == 409) loadData()
                    }
                } catch (_: Exception) {
                    applicationError = "The student portal could not be reached. Check the connection and try again."
                } finally {
                    applyingCourseId = null
                }
            }
        }
    }

    coursePendingConfirmation?.let { course ->
        AlertDialog(
            onDismissRequest = { coursePendingConfirmation = null },
            title = { Text("Confirm course application", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Select ${course.title}? Only one course can be selected at a time. " +
                        "All other Apply buttons will be locked while this application or enrolment is active."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coursePendingConfirmation = null
                        submitApplication(course)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoursesPurple)
                ) { Text("Confirm & Apply") }
            },
            dismissButton = {
                TextButton(onClick = { coursePendingConfirmation = null }) { Text("Cancel") }
            }
        )
    }

    applicationSuccessCourse?.let { course ->
        AlertDialog(
            onDismissRequest = { applicationSuccessCourse = null },
            title = { Text("Course applied successfully", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your application for ${course.title} was submitted. Other courses remain visible, " +
                        "but Apply is now locked until this selection is closed by the backend."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        applicationSuccessCourse = null
                        onApplicationSubmitted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoursesPurple)
                ) { Text("View Screening") }
            },
            dismissButton = {
                TextButton(onClick = { applicationSuccessCourse = null }) { Text("Stay on Courses") }
            }
        )
    }

    applicationError?.let { error ->
        AlertDialog(
            onDismissRequest = { applicationError = null },
            title = { Text("Application not submitted", fontWeight = FontWeight.Bold) },
            text = { Text(error) },
            confirmButton = {
                Button(
                    onClick = { applicationError = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CoursesPurple)
                ) { Text("OK") }
            }
        )
    }

    BackendConnectionGate(
        isLoading = isRefreshing,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Loading SURE ProEd Course Catalog...",
        onRetry = { loadData() },
        onLogout = null
    ) {
        Scaffold(
            containerColor = CoursesCanvas,
            topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SureTrustLogo(size = 36.dp, showSubtext = false)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("My Courses", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Live published courses from SURE ProEd", fontSize = 11.sp, color = CoursesSubtext)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OfficialCatalogBanner(courseCount = mergedCourses.size)
            }

            selectionMessage?.let { lockMessage ->
                item {
                    Surface(
                        color = semanticColors.warningContainer,
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = semanticColors.onWarningContainer, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(9.dp))
                            Column {
                                Text(
                                    blockingApplication?.let { "Selected course: ${it.applicationNumber ?: "Active application"}" }
                                        ?: "Course selection locked",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = CoursesText
                                )
                                Text(lockMessage, fontSize = 11.sp, lineHeight = 15.sp, color = CoursesSubtext)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search courses or skill modules") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = SureFormDefaults.outlinedTextFieldColors()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CourseFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            leadingIcon = if (selectedFilter == filter) {
                                { Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF3E8FF),
                                selectedLabelColor = CoursesPurple,
                                selectedLeadingIconColor = CoursesPurple
                            )
                        )
                    }
                }
            }

            if (isRefreshing) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SureTrustLoadingIndicator(
                            size = 48.dp,
                            logoSize = 30.dp,
                            message = "Loading published courses"
                        )
                    }
                }
            }

            message?.let { statusMessage ->
                item {
                    Surface(
                        color = Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Text(
                            text = statusMessage,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFF92400E),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Available programmes", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = CoursesText)
                    Text("${visibleCourses.size} courses", fontSize = 12.sp, color = CoursesSubtext)
                }
            }

            if (visibleCourses.isEmpty()) {
                item {
                    Text(
                        if (searchQuery.isBlank()) "No published courses are currently available."
                        else "No published course matches \"$searchQuery\".",
                        color = CoursesSubtext,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(visibleCourses, key = { "${it.websiteId}-${it.backendId}" }) { course ->
                val courseApplications = course.backendId?.let { backendId ->
                    applications.filter { it.course == backendId || it.courseId == backendId }
                }.orEmpty()
                val activeApp = courseApplications.firstOrNull { it.blocksCourseSelection() }
                    ?: blockingApplication?.takeIf { it.course == course.backendId || it.courseId == course.backendId }
                val pastAttemptedApp = if (activeApp == null) {
                    courseApplications.firstOrNull { !it.blocksCourseSelection() }
                } else null

                CourseCatalogCard(
                    course = course,
                    activeApplication = activeApp,
                    pastAttemptedApplication = pastAttemptedApp,
                    isApplying = applyingCourseId == course.backendId,
                    canApply = canSelectCourse && applyingCourseId == null,
                    onViewOfficialCourse = { uriHandler.openUri(course.websiteUrl) },
                    onViewJourney = onApplicationSubmitted,
                    onApply = {
                        coursePendingConfirmation = course
                    }
                )
            }
        }
    }
}
}

@Composable
private fun OfficialCatalogBanner(courseCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(listOf(CoursesPurple, CoursesDeepPurple))
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Published SURE ProEd Courses", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFFBEF264), modifier = Modifier.size(18.dp))
                }
                Text(
                    "$courseCount live courses available",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CourseCatalogCard(
    course: CourseUiModel,
    activeApplication: ApplicationDto?,
    pastAttemptedApplication: ApplicationDto?,
    isApplying: Boolean,
    canApply: Boolean,
    onViewOfficialCourse: () -> Unit,
    onViewJourney: () -> Unit,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, CoursesBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = course.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CoursesText
                )
                Spacer(modifier = Modifier.width(10.dp))
                Surface(color = Color(0xFFF3E8FF), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "6 MONTHS",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = CoursesPurple
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            // Cohort Admissions Status Badge
            if (course.hasOpenCohort) {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Cohort Open • Admissions Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF15803D)
                        )
                    }
                }
            } else {
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Admissions Closed • Next Cohort Soon",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Eligibility", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CoursesPurple)
            Text(
                text = course.eligibility,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = CoursesSubtext,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Skill modules", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CoursesText)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                course.skillModules.forEach { module ->
                    Surface(
                        color = Color(0xFFF8F5FF),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE9D5FF))
                    ) {
                        Text(
                            text = module,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = CoursesDeepPurple
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            when {
                activeApplication != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Status: ${activeApplication.status}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color(0xFF047857)
                        )
                        Button(
                            onClick = onViewJourney,
                            colors = ButtonDefaults.buttonColors(containerColor = CoursesPurple)
                        ) {
                            Text("View Journey")
                        }
                    }
                }
                pastAttemptedApplication != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Current Cohort Attempted",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                    Text(
                                        text = "A pre-screening result for this course is already recorded in the current cohort. You can apply to another published course now, or re-apply when the next cohort opens.",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFB45309),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onViewOfficialCourse) {
                                Text("Course details")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Button(
                                onClick = {},
                                enabled = false,
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = Color(0xFFF1F5F9),
                                    disabledContentColor = Color(0xFF94A3B8)
                                )
                            ) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Reapply in Next Cohort", fontSize = 12.5.sp)
                            }
                        }
                    }
                }
                course.backendId != null -> {
                    val isConnected = com.example.suretouchapp.ui.components.LocalBackendConnected.current
                    val isApplyEnabled = !isApplying && canApply && course.hasOpenCohort && isConnected
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onViewOfficialCourse) {
                            Text("Course details")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Button(
                            onClick = onApply,
                            enabled = isApplyEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CoursesPurple,
                                disabledContainerColor = Color(0xFFF1F5F9),
                                disabledContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            if (isApplying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(17.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(7.dp))
                            } else if (!course.hasOpenCohort) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            } else if (!canApply) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            } else if (!isConnected) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                when {
                                    isApplying -> "Applying"
                                    !course.hasOpenCohort -> "Opening Soon"
                                    !canApply -> "Locked"
                                    !isConnected -> "Apply (Offline)"
                                    else -> "Apply Now"
                                }
                            )
                        }
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = onViewOfficialCourse,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, CoursesPurple),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoursesPurple)
                    ) {
                        Text("View Official Course", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

private fun normalizeTitle(value: String): String = value
    .lowercase()
    .filter { it.isLetterOrDigit() }

internal fun ApplicationDto.allowsAnotherCourseApplication(exam: ExamDto?): Boolean {
    val applicationStatus = status.uppercase(Locale.US)
    val examStatus = exam?.status?.uppercase(Locale.US)
    val preScreeningStatus = preScreening?.status?.uppercase(Locale.US)

    if (applicationStatus in setOf("REJECTED", "DROPPED", "CANCELLED", "COMPLETED") || preScreeningStatus == "FAILED") {
        return true
    }

    val screeningResultPublished = examStatus == "EVALUATED" ||
        applicationStatus in setOf("PRESCREENING_COMPLETED", "EXAM_COMPLETED")
    val notQualified = qualified == false || exam?.qualified == false
    return screeningResultPublished && notQualified
}

internal fun ApplicationDto.blocksCourseSelection(): Boolean {
    val applicationStatus = status.uppercase(Locale.US)
    val preScreeningStatus = preScreening?.status?.uppercase(Locale.US)
    return applicationStatus !in setOf("REJECTED", "DROPPED", "CANCELLED", "COMPLETED") &&
        qualified != false && preScreeningStatus != "FAILED"
}
