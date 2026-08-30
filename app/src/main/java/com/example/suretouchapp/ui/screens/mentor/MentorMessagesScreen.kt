package com.example.suretouchapp.ui.screens.mentor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.UserResponse
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MessagePurple = Color(0xFF6D28D9)
private val MessagePink = Color(0xFFDB2777)
private val MessageInk = Color(0xFF172033)
private val MessageMuted = Color(0xFF64748B)
private val MessageBorder = Color(0xFFE2E8F0)

private data class RecipientRole(val value: String, val label: String)

private val mentorRecipientRoles = listOf(
    RecipientRole("STUDENT", "Students"),
    RecipientRole("ADMIN", "Admins"),
    RecipientRole("VOLUNTEER", "Volunteers"),
    RecipientRole("COMPANY", "Companies")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentorMessagesScreen(tokenManager: TokenManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedRole by remember { mutableStateOf(mentorRecipientRoles.first()) }
    var search by remember { mutableStateOf("") }
    var recipients by remember { mutableStateOf<List<UserResponse>>(emptyList()) }
    var selectedRecipient by remember { mutableStateOf<UserResponse?>(null) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSending by remember { mutableStateOf(false) }

    LaunchedEffect(selectedRole.value, search) {
        delay(250)
        isLoading = true
        val response = runCatching {
            ApiClient.getService(tokenManager).getUsers(
                role = selectedRole.value,
                search = search.trim().takeIf { it.isNotBlank() }
            )
        }.getOrNull()
        recipients = response?.takeIf { it.isSuccessful }?.body()?.results.orEmpty()
            .filter { it.id != null && !it.email.equals(tokenManager.getUserEmail(), ignoreCase = true) }
        if (selectedRecipient?.id !in recipients.mapNotNull { it.id }) selectedRecipient = null
        isLoading = false
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mentor Messages", fontWeight = FontWeight.Bold, color = MessageInk)
                        Text("Personalized, role-safe delivery", fontSize = 11.sp, color = MessageMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MessagePurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF3E8FF),
                border = BorderStroke(1.dp, Color(0xFFDDD0F8))
            ) {
                Text(
                    "Students are limited to your assigned cohorts. Volunteers must share one of those cohorts. Admin and Company recipients are selected explicitly.",
                    modifier = Modifier.padding(14.dp),
                    color = Color(0xFF4C1D95),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Text("Recipient role", fontWeight = FontWeight.Bold, color = MessageInk, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mentorRecipientRoles.forEach { role ->
                    FilterChip(
                        selected = selectedRole == role,
                        onClick = { selectedRole = role; selectedRecipient = null },
                        label = { Text(role.label) },
                        leadingIcon = if (selectedRole == role) {
                            { Icon(roleIcon(role.value), null, modifier = Modifier.size(17.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEDE9FE),
                            selectedLabelColor = MessagePurple,
                            selectedLeadingIconColor = MessagePurple
                        )
                    )
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search ${selectedRole.label.lowercase()}") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MessagePurple)
            )

            Text("Select recipient", fontWeight = FontWeight.Bold, color = MessageInk, fontSize = 13.sp)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, MessageBorder)
            ) {
                when {
                    isLoading -> Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                        SureTrustLoadingIndicator(message = "Finding recipients")
                    }
                    recipients.isEmpty() -> Text(
                        "No authorized ${selectedRole.label.lowercase()} found.",
                        modifier = Modifier.padding(18.dp),
                        color = MessageMuted,
                        fontSize = 12.sp
                    )
                    else -> Column {
                        recipients.take(10).forEachIndexed { index, recipient ->
                            RecipientRow(
                                recipient = recipient,
                                selected = selectedRecipient?.id == recipient.id,
                                onClick = { selectedRecipient = recipient }
                            )
                            if (index < recipients.take(10).lastIndex) HorizontalDivider(color = MessageBorder)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MessagePurple)
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MessagePurple)
            )

            Button(
                onClick = {
                    val recipient = selectedRecipient
                    if (recipient?.id == null || subject.isBlank() || message.isBlank()) {
                        scope.launch { snackbar.showSnackbar("Select a recipient and enter the subject and message") }
                        return@Button
                    }
                    scope.launch {
                        isSending = true
                        val response = runCatching {
                            ApiClient.getService(tokenManager).sendNotification(
                                mapOf(
                                    "user_id" to recipient.id,
                                    "title" to subject.trim(),
                                    "message" to message.trim(),
                                    "notification_type" to "INFO",
                                    "action_url" to "notifications"
                                )
                            )
                        }.getOrNull()
                        if (response?.isSuccessful == true) {
                            val name = recipientDisplayName(recipient)
                            subject = ""
                            message = ""
                            selectedRecipient = null
                            snackbar.showSnackbar("Message sent to $name")
                        } else {
                            snackbar.showSnackbar("Message could not be sent to this recipient")
                        }
                        isSending = false
                    }
                },
                enabled = !isSending,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MessagePink),
                shape = RoundedCornerShape(13.dp)
            ) {
                if (isSending) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send personalized message", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun RecipientRow(recipient: UserResponse, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = if (selected) Color(0xFFEDE9FE) else Color(0xFFF1F5F9)) {
            Icon(
                roleIcon(recipient.role.orEmpty()),
                null,
                modifier = Modifier.padding(10.dp).size(20.dp),
                tint = if (selected) MessagePurple else MessageMuted
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                recipientDisplayName(recipient),
                fontWeight = FontWeight.Bold,
                color = MessageInk,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(recipient.email, color = MessageMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = MessagePurple))
    }
}

private fun recipientDisplayName(user: UserResponse): String =
    listOfNotNull(user.firstName, user.lastName).joinToString(" ").trim().ifBlank { user.email.substringBefore('@') }

private fun roleIcon(role: String) = when (role.uppercase()) {
    "ADMIN" -> Icons.Default.Security
    "VOLUNTEER" -> Icons.Default.Groups
    "COMPANY" -> Icons.Default.Business
    else -> Icons.Default.Person
}
