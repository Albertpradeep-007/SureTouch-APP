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
import com.example.suretouchapp.data.repository.VolunteerRepository
import com.example.suretouchapp.ui.theme.SureFormDefaults
import com.example.suretouchapp.ui.theme.sureSemanticColors
import java.io.IOException

private data class AssignedPerson(
    val user: UserResponse,
    val roleType: String, // "MENTOR", "VOLUNTEER", "STUDENT", "MEMBER"
    val roleLabel: String,
    val cohortCodes: Set<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrusteePeopleScreen(
    tokenManager: TokenManager,
    initialFilter: String = "ALL",
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

    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            val api = ApiClient.getService(tokenManager)
            val profile = VolunteerRepository(tokenManager).loadProfile()
            val assignedIds = profile.assignedCohorts.map { it.id }.filter(String::isNotBlank).toSet()
            val cohortsResponse = api.getCohorts()
            val usersResponse = api.getUsers()
            val studentsResponse = api.getStudents()
            if (!cohortsResponse.isSuccessful) throw IOException("Cohorts request failed (${cohortsResponse.code()})")
            if (!usersResponse.isSuccessful) throw IOException("People request failed (${usersResponse.code()})")
            if (!studentsResponse.isSuccessful) throw IOException("Students request failed (${studentsResponse.code()})")

            val cohorts = cohortsResponse.body()?.results.orEmpty().filter { it.id in assignedIds }
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

            val assignedStudents: List<StudentProfileDto> = studentsResponse.body()?.results.orEmpty().filter { it.cohortId in assignedIds }
            assignedStudents.forEach { student ->
                student.userId?.let { userId ->
                    memberCohorts.getOrPut(userId) { mutableSetOf() }.add(codeById[student.cohortId].orEmpty())
                }
            }
            val studentIds = assignedStudents.mapNotNull { it.userId }.toSet()

            people = usersResponse.body()?.results.orEmpty().filter { it.id in memberCohorts.keys }.map { user ->
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
                AssignedPerson(
                    user = user,
                    roleType = roleType,
                    roleLabel = roleLabel,
                    cohortCodes = memberCohorts[user.id].orEmpty().filter(String::isNotBlank).toSet()
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
                actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF6726D9)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                    Surface(onClick = { selected = person }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(15.dp), shadowElevation = 2.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
    }

    selected?.let { person ->
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = {
                Icon(
                    when (person.roleType) {
                        "MENTOR" -> Icons.Default.School
                        "VOLUNTEER" -> Icons.Default.VolunteerActivism
                        else -> Icons.Default.Person
                    },
                    null,
                    tint = Color(0xFF6726D9)
                )
            },
            title = { Text(listOfNotNull(person.user.firstName, person.user.lastName).joinToString(" ").ifBlank { "Cohort member" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = when (person.roleType) {
                            "MENTOR" -> MaterialTheme.colorScheme.primaryContainer
                            "VOLUNTEER" -> semanticColors.infoContainer
                            else -> semanticColors.warningContainer
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            person.roleLabel.replace('_', ' '),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp
                        )
                    }
                    Text("Email: ${person.user.email}", fontSize = 13.sp)
                    person.user.phoneNumber?.takeIf(String::isNotBlank)?.let {
                        Text("Phone: $it", fontSize = 13.sp)
                    }
                    Text("Assigned Cohorts: ${person.cohortCodes.joinToString().ifBlank { "Assigned" }}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } }
        )
    }
}
