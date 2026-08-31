package com.example.suretouchapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.StudentProfileDto
import com.example.suretouchapp.data.repository.StudentProfileRepository
import com.example.suretouchapp.ui.theme.SureLimeSecondary
import com.example.suretouchapp.ui.theme.SurePurpleDark
import com.example.suretouchapp.ui.theme.SurePurplePrimary
import com.example.suretouchapp.ui.theme.SureFormDefaults

private data class DrawerItemSpec(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun StudentDrawerContent(
    tokenManager: TokenManager,
    onNavigateToAttendance: () -> Unit = {},
    onNavigateToCourses: () -> Unit = {},
    onNavigateToAssignments: () -> Unit = {},
    onNavigateToScreening: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToLifeSkills: () -> Unit = {},
    onNavigateToSoftSkills: () -> Unit = {},
    cohortCode: String? = null,
    courseName: String? = null,
    onLogout: () -> Unit = {},
    onCloseDrawer: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    var profile by remember { mutableStateOf<StudentProfileDto?>(null) }
    LaunchedEffect(tokenManager) {
        profile = runCatching { StudentProfileRepository(tokenManager).load() }.getOrNull()
    }
    val apiName = listOfNotNull(profile?.user?.firstName, profile?.user?.lastName).joinToString(" ").trim()
    val userName = apiName.ifBlank { tokenManager.getUserName().ifBlank { "Student" } }
    val studentCode = profile?.studentCode?.takeIf(String::isNotBlank)
    val resolvedCohort = cohortCode?.takeIf(String::isNotBlank) ?: profile?.cohortCode?.takeIf(String::isNotBlank)
    val rawStudentCode = studentCode ?: tokenManager.getStudentCode().takeIf(String::isNotBlank)
    val cachedApp = tokenManager.getApplicationSnapshot()
    val appNumber = cachedApp?.applicationNumber
    val cleanStudentId = when {
        !rawStudentCode.isNullOrBlank() -> {
            val clean = rawStudentCode.removePrefix("STU-").removePrefix("ST-").removePrefix("ST_").trim()
            "STU-$clean"
        }
        !appNumber.isNullOrBlank() -> {
            val parts = appNumber.removePrefix("APP-").removePrefix("APP_").split("-")
            val cleanId = if (parts.size >= 2 && parts[0].length == 4 && parts[1].all { it.isDigit() }) {
                "${parts[0]}-${parts[1]}"
            } else if (parts.isNotEmpty()) {
                parts[0]
            } else {
                appNumber.take(10)
            }
            "STU-$cleanId"
        }
        else -> "STU-${String.format(java.util.Locale.US, "%05d", kotlin.math.abs(tokenManager.getUserEmail().hashCode()) % 100000)}"
    }
    val issuedStudentCode = "ID: $cleanStudentId"

    val menuItems = remember(
        onNavigateToAttendance,
        onNavigateToCourses,
        onNavigateToAssignments,
        onNavigateToScreening,
        onNavigateToNotifications,
        onNavigateToProfile,
        onNavigateToFeedback,
        onNavigateToLifeSkills,
        onNavigateToSoftSkills,
        onCloseDrawer
    ) {
        listOf(
            DrawerItemSpec("Attendance Record", Icons.Default.HowToReg) {
                onCloseDrawer()
                onNavigateToAttendance()
            },
            DrawerItemSpec("Courses & Skill Modules", Icons.AutoMirrored.Filled.MenuBook) {
                onCloseDrawer()
                onNavigateToCourses()
            },
            DrawerItemSpec("Assignments & Submissions", Icons.AutoMirrored.Filled.Assignment) {
                onCloseDrawer()
                onNavigateToAssignments()
            },
            DrawerItemSpec("Pre-Screening Assessments", Icons.Default.Quiz) {
                onCloseDrawer()
                onNavigateToScreening()
            },
            DrawerItemSpec("Life Skills Training (LST)", Icons.Default.Psychology) {
                onCloseDrawer()
                onNavigateToLifeSkills()
            },
            DrawerItemSpec("Soft Skills Training", Icons.Default.Groups) {
                onCloseDrawer()
                onNavigateToSoftSkills()
            },
            DrawerItemSpec("Notifications & News", Icons.Outlined.Notifications) {
                onCloseDrawer()
                onNavigateToNotifications()
            },
            DrawerItemSpec("Edit Student Profile", Icons.Default.Person) {
                onCloseDrawer()
                onNavigateToProfile()
            },
            DrawerItemSpec("Feedback & Support", Icons.Default.RateReview) {
                onCloseDrawer()
                onNavigateToFeedback()
            }
        )
    }

    val filteredMenuItems = remember(searchQuery, menuItems) {
        if (searchQuery.isBlank()) {
            menuItems
        } else {
            menuItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(310.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Drawer Header Profile Card (Gradient Background)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(SurePurplePrimary, SurePurpleDark)
                        )
                    )
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StudentProfileImage(
                        photo = profile?.profilePhoto ?: tokenManager.getProfilePhotoUrl(),
                        displayName = userName,
                        modifier = Modifier.size(72.dp).border(3.dp, SureLimeSecondary, CircleShape),
                        cornerRadius = 36
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = userName,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    val drawerTagline = profile?.tagline?.takeIf(String::isNotBlank) ?: tokenManager.getTagline().takeIf(String::isNotBlank)
                    if (!drawerTagline.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = drawerTagline,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = if (resolvedCohort != null) {
                            issuedStudentCode?.let { "$it • STUDENT" } ?: "STUDENT • VERIFIED"
                        } else {
                            issuedStudentCode?.let { "$it • ENROLLMENT PENDING" } ?: "STUDENT ID PENDING"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = SureLimeSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (resolvedCohort == null) {
                            "No cohort assigned"
                        } else {
                            "${courseName?.takeIf(String::isNotBlank) ?: "Assigned programme"} • Cohort $resolvedCohort"
                        },
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Drawer Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search quick access") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                colors = SureFormDefaults.outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // 3. Vertical Navigation Items List
            filteredMenuItems.forEach { item ->
                DrawerMenuItem(
                    title = item.title,
                    icon = item.icon,
                    onClick = item.onClick
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Logout Button (Red Pill Button)
            Button(
                onClick = {
                    onCloseDrawer()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("LOGOUT", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun DrawerMenuItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(title, fontWeight = FontWeight.SemiBold) },
        icon = { Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp)
    )
}
