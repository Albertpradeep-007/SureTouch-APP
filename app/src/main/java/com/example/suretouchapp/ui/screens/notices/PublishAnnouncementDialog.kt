package com.example.suretouchapp.ui.screens.notices

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.CohortDto
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private val PurplePrimary = Color(0xFF6821A8)
private val PurpleSoft = Color(0xFFF3E8FF)
private val DarkText = Color(0xFF1E293B)
private val SubText = Color(0xFF64748B)
private val BorderColor = Color(0xFFE2E8F0)

enum class TargetAudienceOption(val value: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ALL("ALL", "All Users", Icons.Default.Public),
    STUDENTS("STUDENTS", "All Students", Icons.Default.School),
    MENTORS("MENTORS", "All Mentors", Icons.Default.Person),
    VOLUNTEERS("VOLUNTEERS", "All Volunteers", Icons.Default.VolunteerActivism),
    COHORT("COHORT", "Specific Cohort", Icons.Default.Groups)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishAnnouncementDialog(
    tokenManager: TokenManager,
    onDismiss: () -> Unit,
    onAnnouncementCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedAudience by remember { mutableStateOf(TargetAudienceOption.ALL) }
    var selectedCohort by remember { mutableStateOf<CohortDto?>(null) }
    var cohortsList by remember { mutableStateOf<List<CohortDto>>(emptyList()) }
    var cohortsLoading by remember { mutableStateOf(false) }
    var cohortDropdownExpanded by remember { mutableStateOf(false) }

    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var attachmentName by remember { mutableStateOf<String?>(null) }
    var linkUrl by remember { mutableStateOf("") }
    var isPinned by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        attachmentUri = uri
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        attachmentName = it.getString(nameIndex)
                    }
                }
            }
            if (attachmentName == null) {
                attachmentName = uri.lastPathSegment ?: "attachment"
            }
        } else {
            attachmentName = null
        }
    }

    LaunchedEffect(Unit) {
        cohortsLoading = true
        runCatching {
            val api = ApiClient.getService(tokenManager)
            val res = api.getCohorts()
            if (res.isSuccessful) {
                cohortsList = res.body()?.results.orEmpty()
            }
        }
        cohortsLoading = false
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(PurpleSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = PurplePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Add Announcement",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Text(
                                text = "Broadcast updates to your network",
                                fontSize = 12.sp,
                                color = SubText
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SubText)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = BorderColor
                )

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFFB91C1C),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Title
                    Column {
                        Text(
                            text = "Title *",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = { Text("e.g., Guest Lecture on Saturday / AI Workshop", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = BorderColor
                            )
                        )
                    }

                    // Message
                    Column {
                        Text(
                            text = "Message *",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            placeholder = { Text("Write your announcement message here...", fontSize = 13.sp) },
                            minLines = 4,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = BorderColor
                            )
                        )
                    }

                    // Target Audience
                    Column {
                        Text(
                            text = "Target Audience *",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TargetAudienceOption.values().forEach { option ->
                                val isSelected = selectedAudience == option
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedAudience = option
                                        if (option != TargetAudienceOption.COHORT) {
                                            selectedCohort = null
                                        }
                                    },
                                    label = { Text(option.label, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = option.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isSelected) PurplePrimary else SubText
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PurpleSoft,
                                        selectedLabelColor = PurplePrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) PurplePrimary else BorderColor
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }
                    }

                    // Specific Cohort Picker (Visible when COHORT is selected)
                    if (selectedAudience == TargetAudienceOption.COHORT) {
                        Column {
                            Text(
                                text = "Cohort *",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ExposedDropdownMenuBox(
                                expanded = cohortDropdownExpanded,
                                onExpandedChange = { cohortDropdownExpanded = !cohortDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedCohort?.let { "${it.name} (${it.code ?: "Cohort"})" } ?: "Select a cohort...",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cohortDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PurplePrimary,
                                        unfocusedBorderColor = BorderColor
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = cohortDropdownExpanded,
                                    onDismissRequest = { cohortDropdownExpanded = false }
                                ) {
                                    if (cohortsList.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No active cohorts found") },
                                            onClick = { cohortDropdownExpanded = false }
                                        )
                                    } else {
                                        cohortsList.forEach { cohort ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(cohort.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text(cohort.code ?: cohort.courseName ?: "", fontSize = 11.sp, color = SubText)
                                                    }
                                                },
                                                onClick = {
                                                    selectedCohort = cohort
                                                    cohortDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Attachment
                    Column {
                        Text(
                            text = "Attachment",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (attachmentUri == null) {
                            OutlinedButton(
                                onClick = { filePicker.launch("*/*") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Document, Image, or PDF", color = DarkText, fontSize = 12.5.sp)
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.InsertDriveFile, null, tint = PurplePrimary, modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = attachmentName ?: "File attached",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = DarkText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            attachmentUri = null
                                            attachmentName = null
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Text(
                            text = "Optional document, image, or PDF attachment.",
                            fontSize = 11.sp,
                            color = SubText,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    // Link URL
                    Column {
                        Text(
                            text = "Link URL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = linkUrl,
                            onValueChange = { linkUrl = it },
                            placeholder = { Text("https://meet.google.com/... or resource URL", fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = SubText, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = BorderColor
                            )
                        )
                        Text(
                            text = "Optional external or internal link URL.",
                            fontSize = 11.sp,
                            color = SubText,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    // Is Pinned & Is Active Toggles
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Is Pinned
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PushPin, null, tint = PurplePrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Is pinned", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                                    }
                                    Text(
                                        "Pinned announcements stay at the top of student dashboards.",
                                        fontSize = 11.sp,
                                        color = SubText
                                    )
                                }
                                Switch(
                                    checked = isPinned,
                                    onCheckedChange = { isPinned = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                                )
                            }

                            HorizontalDivider(color = BorderColor)

                            // Is Active
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF047857), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Is active", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkText)
                                    }
                                    Text(
                                        "Active announcements are immediately published.",
                                        fontSize = 11.sp,
                                        color = SubText
                                    )
                                }
                                Switch(
                                    checked = isActive,
                                    onCheckedChange = { isActive = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                                )
                            }
                        }
                    }

                    // Created By badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccountCircle, null, tint = SubText, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Publishing as: ${tokenManager.getUserName().ifBlank { tokenManager.getUserEmail() }} (${tokenManager.getUserRole()})",
                                fontSize = 11.5.sp,
                                color = SubText
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = BorderColor
                )

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        enabled = !isSubmitting
                    ) {
                        Text("Cancel", color = SubText, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                errorMessage = "Please enter an announcement title."
                                return@Button
                            }
                            if (message.isBlank()) {
                                errorMessage = "Please enter the announcement message."
                                return@Button
                            }
                            if (selectedAudience == TargetAudienceOption.COHORT && selectedCohort == null) {
                                errorMessage = "Please select the cohort for this announcement."
                                return@Button
                            }

                            isSubmitting = true
                            errorMessage = null

                            scope.launch {
                                val textType = "text/plain".toMediaTypeOrNull()
                                val api = ApiClient.getService(tokenManager)

                                val res = if (attachmentUri != null) {
                                    val bytes = runCatching {
                                        context.contentResolver.openInputStream(attachmentUri!!)?.use { it.readBytes() }
                                    }.getOrNull()

                                    val filePart = bytes?.let {
                                        val mediaType = context.contentResolver.getType(attachmentUri!!)?.toMediaTypeOrNull()
                                        MultipartBody.Part.createFormData(
                                            "attachment",
                                            attachmentName ?: "attachment",
                                            it.toRequestBody(mediaType)
                                        )
                                    }

                                    runCatching {
                                        api.createAnnouncementMultipart(
                                            title = title.trim().toRequestBody(textType),
                                            message = message.trim().toRequestBody(textType),
                                            targetAudience = selectedAudience.value.toRequestBody(textType),
                                            cohort = selectedCohort?.id?.takeIf { it.isNotBlank() }?.toRequestBody(textType),
                                            linkUrl = linkUrl.trim().takeIf { it.isNotBlank() }?.toRequestBody(textType),
                                            isPinned = isPinned.toString().toRequestBody(textType),
                                            isActive = isActive.toString().toRequestBody(textType),
                                            attachment = filePart
                                        )
                                    }.getOrNull()
                                } else {
                                    val body = mutableMapOf<String, Any?>(
                                        "title" to title.trim(),
                                        "message" to message.trim(),
                                        "target_audience" to selectedAudience.value,
                                        "is_pinned" to isPinned,
                                        "is_active" to isActive
                                    )
                                    if (selectedCohort != null && selectedCohort!!.id.isNotBlank()) {
                                        body["cohort"] = selectedCohort!!.id
                                    }
                                    if (linkUrl.isNotBlank()) {
                                        body["link_url"] = linkUrl.trim()
                                    }
                                    runCatching {
                                        api.createAnnouncement(body)
                                    }.getOrNull()
                                }

                                if (res?.isSuccessful == true) {
                                    Toast.makeText(context, "Announcement published successfully!", Toast.LENGTH_SHORT).show()
                                    onAnnouncementCreated()
                                    onDismiss()
                                } else {
                                    val errorBody = res?.errorBody()?.string() ?: "Server returned error"
                                    errorMessage = "Failed to publish: $errorBody"
                                }
                                isSubmitting = false
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publishing...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publish", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
