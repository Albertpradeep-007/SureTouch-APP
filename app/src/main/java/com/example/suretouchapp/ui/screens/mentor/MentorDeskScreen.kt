package com.example.suretouchapp.ui.screens.mentor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.NotificationDto
import com.example.suretouchapp.data.repository.DashboardRepository
import com.example.suretouchapp.data.repository.DashboardSnapshot
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.theme.SureFormDefaults
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

private val MentorHeader = Color(0xFF262626)
private val MentorCanvas @Composable get() = MaterialTheme.colorScheme.background
private val MentorPurple @Composable get() = MaterialTheme.colorScheme.primary
private val MentorPurpleLight @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val MentorText @Composable get() = MaterialTheme.colorScheme.onSurface
private val MentorSubtext @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val MentorBorder @Composable get() = MaterialTheme.colorScheme.outlineVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentorDeskScreen(tokenManager: TokenManager, onBack: () -> Unit) {
    val repository = remember(tokenManager) { DashboardRepository(tokenManager) }
    val coroutineScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var snapshot by remember {
        mutableStateOf(DashboardSnapshot(cohortCode = tokenManager.getCohortCode().ifBlank { null }))
    }
    var mentorMessages by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showDoubtDialog by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        snapshot = runCatching { repository.load(force = true) }.getOrElse { snapshot }
        mentorMessages = runCatching {
            ApiClient.getService(tokenManager).getNotifications().body()?.results.orEmpty().filter {
                "mentor" in "${it.title} ${it.message}".lowercase()
            }
        }.getOrDefault(emptyList())
        isLoading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mentor Desk", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { refreshKey += 1 }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, "Refresh mentor", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MentorHeader)
            )
        },
        floatingActionButton = {
            if (snapshot.cohortCode != null) {
                ExtendedFloatingActionButton(
                    onClick = { showDoubtDialog = true },
                    containerColor = MentorPurple,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.QuestionAnswer, null) },
                    text = { Text("Ask Mentor") }
                )
            }
        },
        containerColor = MentorCanvas
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SureTrustLoadingIndicator(message = "Loading mentor details")
                }
                snapshot.cohortCode == null -> NoMentorCohortState()
                else -> AssignedMentorContent(snapshot = snapshot, messages = mentorMessages)
            }
        }
    }

    if (showDoubtDialog) {
        var querySubject by remember { mutableStateOf("") }
        var queryMessage by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showDoubtDialog = false },
            icon = { Icon(Icons.Default.QuestionAnswer, null, tint = MentorPurple) },
            title = { Text("Ask Your Mentor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Submit your academic question or doubt to ${snapshot.mentorName ?: "your assigned mentor"}:", fontSize = 12.5.sp, color = MentorSubtext)
                    OutlinedTextField(
                        value = querySubject,
                        onValueChange = { querySubject = it },
                        label = { Text("Subject / Topic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SureFormDefaults.outlinedTextFieldColors()
                    )
                    OutlinedTextField(
                        value = queryMessage,
                        onValueChange = { queryMessage = it },
                        label = { Text("Detailed Doubt or Request") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SureFormDefaults.outlinedTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSubmitting = true
                            val api = ApiClient.getService(tokenManager)
                            val textType = "text/plain".toMediaTypeOrNull()
                            val description = buildString {
                                append(queryMessage.trim())
                                snapshot.cohortCode?.let { append("\n\nCohort: $it") }
                            }
                            val res = runCatching {
                                api.createUserRequest(
                                    "MENTOR_SUPPORT".toRequestBody(textType),
                                    querySubject.trim().toRequestBody(textType),
                                    description.toRequestBody(textType),
                                    null
                                )
                            }.getOrNull()
                            isSubmitting = false
                            if (res?.isSuccessful == true) {
                                showDoubtDialog = false
                                snackbar.showSnackbar("Query sent to mentor successfully!")
                            } else {
                                snackbar.showSnackbar("Unable to send the mentor request. Please try again.")
                            }
                        }
                    },
                    enabled = querySubject.isNotBlank() && queryMessage.isNotBlank() && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MentorPurple)
                ) {
                    if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Submit Query")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDoubtDialog = false }, enabled = !isSubmitting) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NoMentorCohortState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MentorBorder),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(70.dp).clip(CircleShape).background(MentorPurpleLight), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.HeadsetMic, null, tint = MentorPurple, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text("No cohort assigned", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MentorText)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your mentor details and mentor messages will appear automatically after the backend verifies your student role and assigns a cohort.",
                    fontSize = 13.sp,
                    color = MentorSubtext,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun AssignedMentorContent(snapshot: DashboardSnapshot, messages: List<NotificationDto>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MentorBorder),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(MentorPurpleLight), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.School, null, tint = MentorPurple, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(snapshot.mentorName ?: "Mentor assignment pending", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MentorText)
                        Text(snapshot.courseName ?: "SURE ProEd programme", fontSize = 12.5.sp, color = MentorPurple, fontWeight = FontWeight.SemiBold)
                        Text("Assigned cohort: ${snapshot.cohortCode}", fontSize = 11.5.sp, color = MentorSubtext)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, null, tint = MentorPurple, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Mentor Broadcasts & Messages", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MentorText)
            }
        }
        if (messages.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MentorBorder)
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsNone, null, tint = MentorPurple, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("No mentor broadcasts yet. Use 'Ask Mentor' to send a question.", fontSize = 12.5.sp, color = MentorSubtext)
                    }
                }
            }
        } else {
            items(messages, key = { it.id }) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MentorBorder)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(message.title, fontWeight = FontWeight.Bold, color = MentorText)
                        Spacer(Modifier.height(5.dp))
                        Text(message.message, fontSize = 12.5.sp, color = MentorSubtext, lineHeight = 17.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(message.createdAt.take(16).replace('T', ' '), fontSize = 10.5.sp, color = MentorPurple)
                    }
                }
            }
        }
    }
}
