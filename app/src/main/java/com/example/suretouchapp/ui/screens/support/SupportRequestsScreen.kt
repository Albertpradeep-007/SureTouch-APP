package com.example.suretouchapp.ui.screens.support

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.UserRequestDto
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Locale

private val SupportPurple = Color(0xFF6C2BD9)
private val SupportInk = Color(0xFF0F172A)
private val SupportMuted = Color(0xFF64748B)
private val SupportBorder = Color(0xFFE2E8F0)

private data class RequestCategory(val value: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportRequestsScreen(tokenManager: TokenManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var requests by remember { mutableStateOf<List<UserRequestDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val categories = remember(tokenManager.getUserRole()) { categoriesForRole(tokenManager.getUserRole()) }
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var subject by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var attachmentName by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        attachmentUri = uri
        attachmentName = uri?.let { displayName(context, it) }
    }

    suspend fun loadRequests() {
        isLoading = true
        val response = runCatching { ApiClient.getService(tokenManager).getUserRequests() }.getOrNull()
        requests = response?.takeIf { it.isSuccessful }?.body()?.results.orEmpty()
        isLoading = false
    }

    LaunchedEffect(refreshKey) { loadRequests() }
    DisposableEffect(lifecycleOwner) {
        var hasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasPaused = true
                Lifecycle.Event.ON_RESUME -> if (hasPaused) {
                    hasPaused = false
                    refreshKey += 1
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Request Form", fontWeight = FontWeight.Bold, color = SupportInk)
                        Text("Send and track your requests", fontSize = 11.sp, color = SupportMuted)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SupportPurple) } },
                actions = { IconButton(onClick = { refreshKey += 1 }) { Icon(Icons.Default.Refresh, "Refresh", tint = SupportPurple) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.White, contentColor = SupportPurple) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("New request") }, icon = { Icon(Icons.Default.AddCircle, null) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("My requests (${requests.size})") }, icon = { Icon(Icons.Default.ConfirmationNumber, null) })
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF3E8FF), border = BorderStroke(1.dp, Color(0xFFDDD0F8))) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SupportAgent, null, tint = SupportPurple)
                                Spacer(Modifier.width(10.dp))
                                Text("Send a request to the SURE ProEd administration team. You can follow its status and resolution notes here.", fontSize = 12.sp, color = Color(0xFF4C1D95), lineHeight = 17.sp)
                            }
                        }
                    }
                    item {
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = !categoryExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedCategory.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                    .fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SupportPurple)
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                categories.forEach { item ->
                                    DropdownMenuItem(text = { Text(item.label) }, onClick = { selectedCategory = item; categoryExpanded = false })
                                }
                            }
                        }
                    }
                    item { OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(description, { description = it }, label = { Text("Describe your request") }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), minLines = 5) }
                    item {
                        OutlinedButton(onClick = { filePicker.launch(arrayOf("image/*", "application/pdf", "text/plain")) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, SupportPurple)) {
                            Icon(Icons.Default.AttachFile, null)
                            Spacer(Modifier.width(8.dp))
                            Text(attachmentName ?: "Add an optional attachment", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    if (subject.isBlank() || description.isBlank()) {
                                        snackbar.showSnackbar("Enter a subject and description")
                                        return@launch
                                    }
                                    isSubmitting = true
                                    val textType = "text/plain".toMediaTypeOrNull()
                                    val filePart = attachmentUri?.let { uri ->
                                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        bytes?.let {
                                            val mediaType = context.contentResolver.getType(uri)?.toMediaTypeOrNull()
                                            MultipartBody.Part.createFormData("attachment", attachmentName ?: "attachment", it.toRequestBody(mediaType))
                                        }
                                    }
                                    val response = runCatching {
                                        ApiClient.getService(tokenManager).createUserRequest(
                                            selectedCategory.value.toRequestBody(textType),
                                            subject.trim().toRequestBody(textType),
                                            description.trim().toRequestBody(textType),
                                            filePart
                                        )
                                    }.getOrNull()
                                    if (response?.isSuccessful == true) {
                                        subject = ""; description = ""; attachmentUri = null; attachmentName = null
                                        loadRequests(); selectedTab = 1
                                        snackbar.showSnackbar("Request submitted successfully")
                                    } else {
                                        snackbar.showSnackbar("Unable to submit request. Please try again.")
                                    }
                                    isSubmitting = false
                                }
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SupportPurple),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else { Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("Submit request", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            } else if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SureTrustLoadingIndicator(message = "Loading requests") }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (requests.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.TaskAlt, null, tint = SupportPurple, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(10.dp)); Text("No requests yet", fontWeight = FontWeight.Bold, color = SupportInk)
                                Text("Your submitted helpdesk requests will appear here.", fontSize = 12.sp, color = SupportMuted)
                            }
                        }
                    }
                    items(requests, key = { it.id }) { request -> RequestTrackerCard(request) }
                }
            }
        }
    }
}

@Composable
private fun RequestTrackerCard(request: UserRequestDto) {
    val statusColor = when (request.status) {
        "RESOLVED", "CLOSED" -> Color(0xFF047857)
        "REJECTED" -> Color(0xFFB91C1C)
        "IN_PROGRESS" -> Color(0xFF0369A1)
        else -> Color(0xFFB45309)
    }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, SupportBorder)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(request.requestNumber.ifBlank { "REQUEST" }, color = SupportPurple, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(12.dp), color = statusColor.copy(alpha = 0.1f)) {
                    Text(request.status.replace('_', ' '), color = statusColor, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Text(request.subject, fontWeight = FontWeight.Bold, color = SupportInk, fontSize = 14.sp)
            Text(categoryLabel(request.category), color = SupportMuted, fontSize = 11.sp)
            Text(request.description, color = SupportMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            request.adminRemarks?.takeIf { it.isNotBlank() }?.let {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF0FDF4)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("ADMIN RESPONSE", color = Color(0xFF047857), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(it, color = SupportInk, fontSize = 11.5.sp)
                    }
                }
            }
            if (request.adminRemarks.isNullOrBlank() && request.status in setOf("RESOLVED", "REJECTED", "CLOSED")) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF7ED)) {
                    Text(
                        "No admin response was recorded for this closed request. Please ask the administration team to reopen it and add their remarks.",
                        modifier = Modifier.padding(10.dp),
                        color = Color(0xFF9A3412),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            request.resolvedAt?.takeIf { it.isNotBlank() }?.let {
                Text("Responded ${formatDate(it)}", color = SupportMuted, fontSize = 10.5.sp)
            }
            Text(formatDate(request.createdAt), color = SupportMuted, fontSize = 10.5.sp)
        }
    }
}

private fun categoriesForRole(role: String): List<RequestCategory> {
    val common = listOf(
        RequestCategory("ATTENDANCE", "Attendance correction"),
        RequestCategory("TECHNICAL_ISSUE", "Technical issue / bug"),
        RequestCategory("LEAVE_REQUEST", "Leave request"),
        RequestCategory("OTHER", "Other general request")
    )
    return when (role.uppercase(Locale.US)) {
        "MENTOR" -> listOf(
            RequestCategory("MENTOR_SUPPORT", "Mentor support / class reschedule"),
            RequestCategory("COURSE_INQUIRY", "Course / curriculum inquiry"),
            RequestCategory("ASSIGNMENT_EXAM", "Assignment / marks query")
        ) + common
        "VOLUNTEER", "TRUSTEE", "VOLUNTEER_TRUSTEE" -> listOf(
            RequestCategory("VOLUNTEER_SUPPORT", "Volunteer support / session log"),
            RequestCategory("VOLUNTEER_JOINING", "Volunteer joining request")
        ) + common
        else -> listOf(
            RequestCategory("OFFER_LETTER", "Offer letter request"),
            RequestCategory("CERTIFICATE", "Certificate issue request"),
            RequestCategory("VOLUNTEER_JOINING", "Volunteer joining request"),
            RequestCategory("ASSIGNMENT_EXAM", "Assignment / marks query"),
            RequestCategory("COURSE_INQUIRY", "Course / curriculum inquiry")
        ) + common
    }
}

private fun categoryLabel(value: String): String = value.lowercase(Locale.US).split('_').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.US) } }

private fun formatDate(value: String?): String = runCatching {
    val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    val output = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
    output.format(requireNotNull(input.parse(requireNotNull(value).take(19))))
}.getOrDefault(value?.take(10).orEmpty())

private fun displayName(context: android.content.Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    val name = cursor?.use { if (it.moveToFirst()) it.getString(0) ?: "" else "" }.orEmpty()
    return name.ifBlank { "attachment" }
}
