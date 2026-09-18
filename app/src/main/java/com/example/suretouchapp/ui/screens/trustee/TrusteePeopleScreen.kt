package com.example.suretouchapp.ui.screens.trustee

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.StudentProfileDto
import com.example.suretouchapp.data.model.UserResponse
import com.example.suretouchapp.data.repository.AccountPageLoader
import com.example.suretouchapp.data.model.CohortDto
import com.example.suretouchapp.data.repository.VolunteerRepository
import com.example.suretouchapp.ui.theme.SureFormDefaults
import com.example.suretouchapp.ui.theme.sureSemanticColors
import com.example.suretouchapp.data.model.ApplicationDto
import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.io.IOException

private data class AssignedPerson(
    val user: UserResponse,
    val roleType: String, // "MENTOR", "VOLUNTEER", "STUDENT", "MEMBER"
    val roleLabel: String,
    val cohortCodes: Set<String>,
    val studentProfile: StudentProfileDto? = null,
    val application: ApplicationDto? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrusteePeopleScreen(
    tokenManager: TokenManager,
    initialFilter: String = "ALL",
    mentorsOnly: Boolean = false,
    onBack: () -> Unit
) {
    var people by remember { mutableStateOf<List<AssignedPerson>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<AssignedPerson?>(null) }
    var selectedFilter by remember { mutableStateOf(initialFilter.uppercase()) }
    var searchQuery by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val semanticColors = sureSemanticColors()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var studentToSuspend by remember { mutableStateOf<AssignedPerson?>(null) }
    var studentToUnsuspend by remember { mutableStateOf<AssignedPerson?>(null) }
    var isProcessingStatus by remember { mutableStateOf(false) }
    var selectedReasonChip by remember { mutableStateOf("Low Attendance (<75%)") }
    var customReasonNotes by remember { mutableStateOf("") }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedStudentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchSuspendDialog by remember { mutableStateOf(false) }
    var showBatchUnsuspendDialog by remember { mutableStateOf(false) }
    var isProcessingBatch by remember { mutableStateOf(false) }

    val canManageStatus = tokenManager.canManageStudentStatus()
    val canUnsuspend = tokenManager.canUnsuspendStudent()

    val selectedStudentsList = remember(people, selectedStudentIds) {
        people.filter { (it.user.id ?: it.user.email) in selectedStudentIds && it.roleType == "STUDENT" }
    }
    val selectedActiveList = remember(selectedStudentsList) {
        selectedStudentsList.filter { p ->
            val isSusp = p.application?.status.equals("SUSPENDED", true) || p.studentProfile?.status.equals("SUSPENDED", true)
            !isSusp
        }
    }
    val selectedSuspendedList = remember(selectedStudentsList) {
        selectedStudentsList.filter { p ->
            val isSusp = p.application?.status.equals("SUSPENDED", true) || p.studentProfile?.status.equals("SUSPENDED", true)
            isSusp
        }
    }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            val api = ApiClient.getService(tokenManager)
            val profile = VolunteerRepository(tokenManager).loadProfile()
            val assignedIds = profile.assignedCohorts.map { it.id }.filter(String::isNotBlank).toSet()
            val pages = AccountPageLoader(tokenManager)
            val cohorts = pages.load("cohorts", { it: CohortDto -> it.id }) { api.getCohorts(page = it) }.filter { it.id in assignedIds }
            val users = pages.load("people", { it: UserResponse -> it.id.orEmpty() }) { api.getUsers(role = if (mentorsOnly) "MENTOR" else null, page = it) }
            val students = if (mentorsOnly) emptyList() else pages.load("students", { it: StudentProfileDto -> it.id }) { api.getStudents(page = it) }
            val applications = if (mentorsOnly) emptyList() else pages.load("applications", { it: ApplicationDto -> it.id }) { api.getMyApplications(page = it) }
            val codeById = profile.assignedCohorts.associate { it.id to it.code } + cohorts.associate { it.id to (it.code ?: it.name) }
            
            val mentorUserIds = mutableSetOf<String>()
            val volunteerUserIds = mutableSetOf<String>()
            val memberCohorts = mutableMapOf<String, MutableSet<String>>()

            cohorts.forEach { cohort ->
                val code = codeById[cohort.id].orEmpty()
                cohort.mentors.forEach { userId ->
                    mentorUserIds.add(userId)
                    memberCohorts.getOrPut(userId) { mutableSetOf() }.add(code)
                }
                cohort.volunteers.forEach { userId ->
                    volunteerUserIds.add(userId)
                    memberCohorts.getOrPut(userId) { mutableSetOf() }.add(code)
                }
            }

            val assignedStudents: List<StudentProfileDto> = students.filter { it.cohortId in assignedIds }
            assignedStudents.forEach { student ->
                student.userId?.let { userId ->
                    memberCohorts.getOrPut(userId) { mutableSetOf() }.add(codeById[student.cohortId].orEmpty())
                }
            }
            val studentIds = assignedStudents.mapNotNull { it.userId }.toSet()

            people = users.filter { it.id in memberCohorts.keys }.map { user ->
                val roleType = when {
                    user.id in mentorUserIds || user.role.equals("MENTOR", true) || user.role.equals("INSTRUCTOR", true) -> "MENTOR"
                    user.id in volunteerUserIds || user.role.equals("VOLUNTEER", true) || user.role.equals("VOLUNTEER_TRUSTEE", true) -> "VOLUNTEER"
                    user.id in studentIds || user.role.equals("STUDENT", true) -> "STUDENT"
                    else -> "MEMBER"
                }
                val roleLabel = when (roleType) {
                    "MENTOR" -> "MENTOR"
                    "VOLUNTEER" -> "VOLUNTEER"
                    "STUDENT" -> "STUDENT"
                    else -> user.role.orEmpty().ifBlank { "COHORT MEMBER" }
                }
                val studentProfile = assignedStudents.firstOrNull { it.userId == user.id }
                val studentApp = if (roleType == "STUDENT") {
                    applications.firstOrNull { it.student == studentProfile?.id || it.student == user.id }
                } else null
                AssignedPerson(
                    user = user,
                    roleType = roleType,
                    roleLabel = roleLabel,
                    cohortCodes = memberCohorts[user.id].orEmpty().filter(String::isNotBlank).toSet(),
                    studentProfile = studentProfile,
                    application = studentApp
                )
            }.sortedWith(compareBy<AssignedPerson> { it.roleType }.thenBy { it.user.firstName.orEmpty() })
        } catch (failure: Exception) {
            error = failure.message ?: "Unable to load assigned-cohort people."
        }
        loading = false
    }

    val mentorCount = remember(people) { people.count { it.roleType == "MENTOR" } }
    val volunteerCount = remember(people) { people.count { it.roleType == "VOLUNTEER" } }
    val studentCount = remember(people) { people.count { it.roleType == "STUDENT" } }

    val filteredPeople = remember(people, selectedFilter, searchQuery) {
        people.filter { person ->
            if (mentorsOnly && person.roleType != "MENTOR") return@filter false
            val matchesFilter = when (selectedFilter) {
                "MENTORS" -> person.roleType == "MENTOR"
                "VOLUNTEERS" -> person.roleType == "VOLUNTEER"
                "STUDENTS" -> person.roleType == "STUDENT"
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                person.user.firstName?.contains(searchQuery, ignoreCase = true) == true ||
                person.user.lastName?.contains(searchQuery, ignoreCase = true) == true ||
                person.user.email.contains(searchQuery, ignoreCase = true) ||
                person.cohortCodes.any { it.contains(searchQuery, ignoreCase = true) }
            matchesFilter && matchesSearch
        }
    }

    val headerTitle = when (selectedFilter) {
        "MENTORS" -> "Mentor Network"
        "VOLUNTEERS" -> "Volunteer Network"
        "STUDENTS" -> "Cohort Students"
        else -> "Cohort Network"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(headerTitle, fontWeight = FontWeight.Bold)
                        Text(
                            when (selectedFilter) {
                                "MENTORS" -> "$mentorCount assigned mentors across cohorts"
                                "VOLUNTEERS" -> "$volunteerCount assigned volunteers across cohorts"
                                "STUDENTS" -> "$studentCount enrolled students across cohorts"
                                else -> "${people.size} total network members"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF6726D9)) } },
                actions = {
                    if (canManageStatus) {
                        IconButton(onClick = {
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) selectedStudentIds = emptySet()
                        }) {
                            Icon(
                                if (isMultiSelectMode) Icons.Default.Close else Icons.Default.DoneAll,
                                "Multi-Select",
                                tint = if (isMultiSelectMode) MaterialTheme.colorScheme.primary else Color(0xFF6726D9)
                            )
                        }
                    }
                    IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF6726D9)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (isMultiSelectMode && selectedStudentIds.isNotEmpty()) 90.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF6525D7)), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).background(Color.White.copy(.16f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(
                                    when (selectedFilter) {
                                        "MENTORS" -> Icons.Default.School
                                        "VOLUNTEERS" -> Icons.Default.VolunteerActivism
                                        "STUDENTS" -> Icons.Default.Groups
                                        else -> Icons.Default.Groups
                                    },
                                    null, tint = Color.White, modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(headerTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(
                                    "Mentors: $mentorCount • Volunteers: $volunteerCount • Students: $studentCount",
                                    color = Color.White.copy(.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name, email, or cohort...") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF6726D9)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = SureFormDefaults.outlinedTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedFilter == "ALL",
                                onClick = { selectedFilter = "ALL" },
                                label = { Text("All (${people.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF6726D9),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "MENTORS",
                                onClick = { selectedFilter = "MENTORS" },
                                label = { Text("Mentors ($mentorCount)") },
                                leadingIcon = { Icon(Icons.Default.School, null, Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF6726D9),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "VOLUNTEERS",
                                onClick = { selectedFilter = "VOLUNTEERS" },
                                label = { Text("Volunteers ($volunteerCount)") },
                                leadingIcon = { Icon(Icons.Default.VolunteerActivism, null, Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0D9488),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == "STUDENTS",
                                onClick = { selectedFilter = "STUDENTS" },
                                label = { Text("Students ($studentCount)") },
                                leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFD97706),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                if (isMultiSelectMode) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val allActiveStudentIds = remember(people) {
                                people.filter {
                                    it.roleType == "STUDENT" &&
                                    !(it.application?.status.equals("SUSPENDED", true) || it.studentProfile?.status.equals("SUSPENDED", true))
                                }.map { it.user.id ?: it.user.email }.toSet()
                            }
                            val allSuspendedStudentIds = remember(people) {
                                people.filter {
                                    it.roleType == "STUDENT" &&
                                    (it.application?.status.equals("SUSPENDED", true) || it.studentProfile?.status.equals("SUSPENDED", true))
                                }.map { it.user.id ?: it.user.email }.toSet()
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFD97706).copy(alpha = 0.12f),
                                modifier = Modifier.clickable { selectedStudentIds = allActiveStudentIds }
                            ) {
                                Text(
                                    "Select Active (${allActiveStudentIds.size})",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF16A34A).copy(alpha = 0.12f),
                                modifier = Modifier.clickable { selectedStudentIds = allSuspendedStudentIds }
                            ) {
                                Text(
                                    "Select Suspended (${allSuspendedStudentIds.size})",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }

                            if (selectedStudentIds.isNotEmpty()) {
                                TextButton(
                                    onClick = { selectedStudentIds = emptySet() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                when {
                    loading -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF6726D9)) } }
                    error != null -> item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
                    filteredPeople.isEmpty() -> item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PersonSearch, null, tint = Color(0xFF6726D9), modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No matching people found", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("No records match '$selectedFilter' in your assigned cohorts.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    else -> items(filteredPeople, key = { it.user.id ?: it.user.email }) { person ->
                        val roleBadgeColor = when (person.roleType) {
                            "MENTOR" -> MaterialTheme.colorScheme.primary
                            "VOLUNTEER" -> semanticColors.info
                            "STUDENT" -> semanticColors.warning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val roleBadgeBg = when (person.roleType) {
                            "MENTOR" -> MaterialTheme.colorScheme.primaryContainer
                            "VOLUNTEER" -> semanticColors.infoContainer
                            "STUDENT" -> semanticColors.warningContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val personKey = person.user.id ?: person.user.email
                        val isSelected = personKey in selectedStudentIds
                        val isStudent = person.roleType == "STUDENT"

                        Surface(
                            onClick = {
                                if (isMultiSelectMode && isStudent) {
                                    selectedStudentIds = if (isSelected) selectedStudentIds - personKey else selectedStudentIds + personKey
                                } else {
                                    selected = person
                                }
                            },
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(15.dp),
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isMultiSelectMode && isStudent) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedStudentIds = if (checked) selectedStudentIds + personKey else selectedStudentIds - personKey
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Box(Modifier.size(44.dp).background(roleBadgeBg, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(
                                        when (person.roleType) {
                                            "MENTOR" -> Icons.Default.School
                                            "VOLUNTEER" -> Icons.Default.VolunteerActivism
                                            "STUDENT" -> Icons.Default.Person
                                            else -> Icons.Default.Person
                                        },
                                        null, tint = roleBadgeColor
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    val fullName = listOfNotNull(person.user.firstName, person.user.lastName).joinToString(" ").trim().ifBlank {
                                        person.user.email.substringBefore('@').replace(".", " ").replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                                    }
                                    Text(
                                        fullName.ifBlank { "Cohort Member" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (person.roleType == "STUDENT") {
                                            val isSuspended = person.application?.status.equals("SUSPENDED", true) || person.studentProfile?.status.equals("SUSPENDED", true)
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFFEF3C7),
                                                border = BorderStroke(1.dp, Color(0xFFFDE68A))
                                            ) {
                                                Text(
                                                    text = "STUDENT",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFD97706),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            if (isSuspended) {
                                                Spacer(Modifier.width(4.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFDC2626).copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        text = "SUSPENDED",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFFDC2626),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(person.user.email, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text("Cohorts: ${person.cohortCodes.joinToString().ifBlank { "Assigned cohort" }}", fontSize = 11.sp, color = roleBadgeColor, fontWeight = FontWeight.Medium)
                                }
                                Surface(color = roleBadgeBg, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        person.roleLabel.replace('_', ' '),
                                        fontSize = 9.5.sp,
                                        color = roleBadgeColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Batch Floating Action Bar
            if (isMultiSelectMode && selectedStudentIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${selectedStudentIds.size} Selected", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text(
                                "${selectedActiveList.size} active · ${selectedSuspendedList.size} suspended",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (selectedSuspendedList.isNotEmpty() && canUnsuspend) {
                                Button(
                                    onClick = { showBatchUnsuspendDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    enabled = !isProcessingBatch
                                ) {
                                    Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Unsuspend (${selectedSuspendedList.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (selectedActiveList.isNotEmpty() && canManageStatus) {
                                Button(
                                    onClick = {
                                        selectedReasonChip = "Low Attendance (<75%)"
                                        customReasonNotes = ""
                                        showBatchSuspendDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    enabled = !isProcessingBatch
                                ) {
                                    Icon(Icons.Default.Block, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Suspend (${selectedActiveList.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { person ->
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = {
                Icon(
                    when (person.roleType) {
                        "MENTOR" -> Icons.Default.School
                        "VOLUNTEER" -> Icons.Default.VolunteerActivism
                        "STUDENT" -> Icons.Default.Person
                        else -> Icons.Default.Person
                    },
                    null,
                    tint = Color(0xFF6726D9),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    listOfNotNull(person.user.firstName, person.user.lastName).joinToString(" ").ifBlank { person.user.email },
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Role: ${person.roleLabel}", fontWeight = FontWeight.SemiBold, color = Color(0xFF6726D9), fontSize = 13.sp)
                    Text("Email: ${person.user.email}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                    person.user.phoneNumber?.let {
                        Text("Phone: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                    }
                    Text("Assigned Cohorts: ${person.cohortCodes.joinToString().ifBlank { "Assigned" }}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (person.roleType == "STUDENT") {
                        val isSuspended = person.application?.status.equals("SUSPENDED", true) || person.studentProfile?.status.equals("SUSPENDED", true)
                        if (isSuspended && canUnsuspend) {
                            Button(
                                onClick = {
                                    studentToUnsuspend = person
                                    selected = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Unsuspend", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (!isSuspended && canManageStatus) {
                            Button(
                                onClick = {
                                    studentToSuspend = person
                                    selectedReasonChip = "Low Attendance (<75%)"
                                    customReasonNotes = ""
                                    selected = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Suspend Student", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            }
        )
    }

    // --- BATCH SUSPEND DIALOG ---
    if (showBatchSuspendDialog && canManageStatus) {
        val reasons = listOf(
            "Low Attendance (<75%)",
            "Incomplete Module Assignments",
            "Violation of Code of Conduct",
            "Prolonged Inactivity",
            "Requested by Academic Trustee"
        )
        AlertDialog(
            onDismissRequest = { if (!isProcessingBatch) showBatchSuspendDialog = false },
            icon = {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Block, null, tint = Color(0xFFDC2626), modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Suspend ${selectedActiveList.size} Student(s)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Are you sure you want to suspend the ${selectedActiveList.size} selected active student(s)? They will lose cohort access until reinstated.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Select Primary Reason:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        reasons.forEach { reason ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedReasonChip == reason) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (selectedReasonChip == reason) MaterialTheme.colorScheme.primary else Color.Transparent),
                                modifier = Modifier.fillMaxWidth().clickable { selectedReasonChip = reason }
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selectedReasonChip == reason,
                                        onClick = { selectedReasonChip = reason },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(reason, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customReasonNotes,
                        onValueChange = { customReasonNotes = it },
                        label = { Text("Detailed Reason / Notes (Optional)") },
                        placeholder = { Text("e.g. Batch suspension due to attendance audit") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalReason = if (customReasonNotes.isNotBlank()) {
                            "$selectedReasonChip: ${customReasonNotes.trim()}"
                        } else selectedReasonChip

                        scope.launch {
                            isProcessingBatch = true
                            var successCount = 0
                            val api = ApiClient.getService(tokenManager)
                            for (person in selectedActiveList) {
                                val appId = person.application?.id ?: person.studentProfile?.id ?: person.user.id.orEmpty()
                                val res = runCatching {
                                    val direct = api.suspendApplication(appId, mapOf("reason" to finalReason))
                                    if (!direct.isSuccessful) {
                                        api.patchApplication(appId, mapOf("status" to "SUSPENDED", "suspension_reason" to finalReason))
                                    } else direct
                                }.getOrNull()
                                if (res?.isSuccessful == true) {
                                    successCount++
                                    val sName = listOfNotNull(person.user.firstName, person.user.lastName).joinToString(" ").ifBlank { "Student" }
                                    SureProEdNotificationManager.showStudentSuspendedNotification(
                                        context, sName, person.cohortCodes.firstOrNull(), finalReason
                                    )
                                }
                            }
                            isProcessingBatch = false
                            showBatchSuspendDialog = false
                            selectedStudentIds = emptySet()
                            isMultiSelectMode = false
                            snackbarHostState.showSnackbar("Successfully suspended $successCount of ${selectedActiveList.size} student(s).")
                            refresh++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isProcessingBatch
                ) {
                    if (isProcessingBatch) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Confirm Suspension (${selectedActiveList.size})", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchSuspendDialog = false },
                    enabled = !isProcessingBatch
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- BATCH UNSUSPEND DIALOG ---
    if (showBatchUnsuspendDialog && canUnsuspend) {
        AlertDialog(
            onDismissRequest = { if (!isProcessingBatch) showBatchUnsuspendDialog = false },
            icon = {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LockOpen, null, tint = Color(0xFF16A34A), modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Unsuspend ${selectedSuspendedList.size} Student(s)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to unsuspend and reinstate ${selectedSuspendedList.size} student(s)?")
                    Text(
                        "Their application status will be restored to active learning.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isProcessingBatch = true
                            var successCount = 0
                            val api = ApiClient.getService(tokenManager)
                            for (person in selectedSuspendedList) {
                                val appId = person.application?.id ?: person.studentProfile?.id ?: person.user.id.orEmpty()
                                val res = runCatching {
                                    api.unsuspendApplication(
                                        appId,
                                        mapOf("reason" to "Batch unsuspended by volunteer/trustee")
                                    )
                                }.getOrNull()
                                if (res?.isSuccessful == true) {
                                    successCount++
                                    val sName = listOfNotNull(person.user.firstName, person.user.lastName).joinToString(" ").ifBlank { "Student" }
                                    SureProEdNotificationManager.showStudentUnsuspendedNotification(
                                        context, sName, person.cohortCodes.firstOrNull()
                                    )
                                }
                            }
                            isProcessingBatch = false
                            showBatchUnsuspendDialog = false
                            selectedStudentIds = emptySet()
                            isMultiSelectMode = false
                            snackbarHostState.showSnackbar("Successfully unsuspended $successCount of ${selectedSuspendedList.size} student(s).")
                            refresh++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isProcessingBatch
                ) {
                    if (isProcessingBatch) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Confirm Reinstatement (${selectedSuspendedList.size})", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchUnsuspendDialog = false },
                    enabled = !isProcessingBatch
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- SUSPEND DIALOG WITH CUSTOM REASONS ---
    studentToSuspend?.let { person ->
        val fullName = listOfNotNull(person.user.firstName, person.user.lastName).joinToString(" ").ifBlank { "Student" }
        val predefinedReasons = listOf(
            "Low Attendance (<75%)",
            "Missed Assignments",
            "Prolonged Inactivity (14+ Days)",
            "Disciplinary / Policy Violation",
            "Other Reason"
        )
        AlertDialog(
            onDismissRequest = { if (!isProcessingStatus) studentToSuspend = null },
            icon = {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Block, null, tint = Color(0xFFDC2626), modifier = Modifier.size(26.dp))
                }
            },
            title = { Text("Suspend Student", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Are you sure you want to suspend $fullName?", fontSize = 13.sp)
                    Text("Select Suspension Reason:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    predefinedReasons.forEach { reason ->
                        val isSelected = selectedReasonChip == reason
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFDC2626) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReasonChip = reason }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedReasonChip = reason },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFDC2626))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    reason,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customReasonNotes,
                        onValueChange = { customReasonNotes = it },
                        label = { Text("Detailed Reason / Notes (Optional)") },
                        placeholder = { Text("e.g. Inactivity or missed attendance") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalReason = if (customReasonNotes.isNotBlank()) {
                            "$selectedReasonChip: ${customReasonNotes.trim()}"
                        } else selectedReasonChip

                        scope.launch {
                            isProcessingStatus = true
                            val appId = person.application?.id ?: person.studentProfile?.id ?: person.user.id.orEmpty()
                            val res = runCatching {
                                val api = ApiClient.getService(tokenManager)
                                val direct = api.suspendApplication(appId, mapOf("reason" to finalReason))
                                if (!direct.isSuccessful) {
                                    api.patchApplication(appId, mapOf("status" to "SUSPENDED", "suspension_reason" to finalReason))
                                } else direct
                            }.getOrNull()
                            isProcessingStatus = false
                            studentToSuspend = null
                            if (res?.isSuccessful == true) {
                                SureProEdNotificationManager.showStudentSuspendedNotification(
                                    context, fullName, person.cohortCodes.firstOrNull(), finalReason
                                )
                                snackbarHostState.showSnackbar("$fullName has been suspended.")
                                refresh++
                            } else {
                                snackbarHostState.showSnackbar("Failed to suspend student.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isProcessingStatus
                ) {
                    if (isProcessingStatus) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Confirm Suspension", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToSuspend = null }, enabled = !isProcessingStatus) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- UNSUSPEND DIALOG ---
    studentToUnsuspend?.let { person ->
        val fullName = listOfNotNull(person.user.firstName, person.user.lastName).joinToString(" ").ifBlank { "Student" }
        AlertDialog(
            onDismissRequest = { if (!isProcessingStatus) studentToUnsuspend = null },
            icon = {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LockOpen, null, tint = Color(0xFF16A34A), modifier = Modifier.size(26.dp))
                }
            },
            title = { Text("Unsuspend Student", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to unsuspend $fullName?")
                    Text(
                        "Their application will be reinstated and restored to active learning.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isProcessingStatus = true
                            val appId = person.application?.id ?: person.studentProfile?.id ?: person.user.id.orEmpty()
                            val res = runCatching {
                                ApiClient.getService(tokenManager).unsuspendApplication(
                                    appId,
                                    mapOf("reason" to "Unsuspended by volunteer")
                                )
                            }.getOrNull()
                            isProcessingStatus = false
                            studentToUnsuspend = null
                            if (res?.isSuccessful == true) {
                                SureProEdNotificationManager.showStudentUnsuspendedNotification(
                                    context, fullName, person.cohortCodes.firstOrNull()
                                )
                                snackbarHostState.showSnackbar("$fullName has been unsuspended and reinstated.")
                                refresh++
                            } else {
                                snackbarHostState.showSnackbar("Failed to unsuspend student.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isProcessingStatus
                ) {
                    if (isProcessingStatus) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Confirm Reinstatement", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToUnsuspend = null }, enabled = !isProcessingStatus) {
                    Text("Cancel")
                }
            }
        )
    }
}
