package com.example.suretouchapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.ChangePasswordRequest
import kotlinx.coroutines.launch

private val DialogPurple = Color(0xFF6C2BD9)
private val DialogInk = Color(0xFF101A3C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordDialog(
    tokenManager: TokenManager,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isCurrentVisible by remember { mutableStateOf(false) }
    var isNewVisible by remember { mutableStateOf(false) }
    var isConfirmVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = DialogPurple.copy(alpha = 0.12f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = DialogPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Change Password",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = DialogInk
                            )
                            Text(
                                text = "Revokes all other active sessions",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Error Banner
                errorMessage?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Current Password Field
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; errorMessage = null },
                    label = { Text("Current Password") },
                    placeholder = { Text("••••••••") },
                    visualTransformation = if (isCurrentVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    trailingIcon = {
                        IconButton(onClick = { isCurrentVisible = !isCurrentVisible }) {
                            Icon(
                                imageVector = if (isCurrentVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isCurrentVisible) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(12.dp))

                // New Password Field
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; errorMessage = null },
                    label = { Text("New Password") },
                    placeholder = { Text("••••••••") },
                    supportingText = {
                        Text(
                            text = "Min 8 chars, uppercase, lowercase, number & symbol.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    visualTransformation = if (isNewVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    trailingIcon = {
                        IconButton(onClick = { isNewVisible = !isNewVisible }) {
                            Icon(
                                imageVector = if (isNewVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isNewVisible) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Confirm Password Field
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = null },
                    label = { Text("Confirm New Password") },
                    placeholder = { Text("••••••••") },
                    visualTransformation = if (isConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    trailingIcon = {
                        IconButton(onClick = { isConfirmVisible = !isConfirmVisible }) {
                            Icon(
                                imageVector = if (isConfirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isConfirmVisible) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val cleanCurrent = currentPassword.trim()
                            val cleanNew = newPassword.trim()
                            val cleanConfirm = confirmPassword.trim()

                            if (cleanCurrent.isBlank()) {
                                errorMessage = "Please enter your current password."
                                return@Button
                            }
                            if (cleanNew.length < 8) {
                                errorMessage = "New password must be at least 8 characters long."
                                return@Button
                            }
                            if (!cleanNew.any { it.isUpperCase() }) {
                                errorMessage = "New password must contain at least one uppercase letter (A-Z)."
                                return@Button
                            }
                            if (!cleanNew.any { it.isLowerCase() }) {
                                errorMessage = "New password must contain at least one lowercase letter (a-z)."
                                return@Button
                            }
                            if (!cleanNew.any { it.isDigit() }) {
                                errorMessage = "New password must contain at least one number (0-9)."
                                return@Button
                            }
                            val specialSymbols = "!@#$%^&*()_+-=[]{};':\"\\|,.<>/?~`"
                            if (!cleanNew.any { it in specialSymbols }) {
                                errorMessage = "New password must contain at least one special character (!@#$%^&*)."
                                return@Button
                            }
                            if (cleanNew != cleanConfirm) {
                                errorMessage = "New passwords do not match. Please recheck."
                                return@Button
                            }

                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val api = ApiClient.getService(tokenManager)
                                    val res = api.changePassword(
                                        ChangePasswordRequest(
                                            oldPassword = cleanCurrent,
                                            newPassword = cleanNew,
                                            confirmPassword = cleanConfirm
                                        )
                                    )
                                    if (res.isSuccessful && res.body() != null) {
                                        val body = res.body()!!
                                        if (!body.access.isNullOrBlank()) {
                                            tokenManager.saveToken(body.access, body.refresh.orEmpty())
                                        }
                                        onSuccess(body.detail ?: "Password updated successfully! All other sessions have been logged out.")
                                    } else {
                                        val rawError = res.errorBody()?.string().orEmpty()
                                        val parsedMsg = runCatching {
                                            val json = org.json.JSONObject(rawError)
                                            json.optString("detail").takeIf { it.isNotBlank() }
                                                ?: json.optString("error").takeIf { it.isNotBlank() }
                                                ?: json.optJSONArray("old_password")?.optString(0)
                                                ?: json.optJSONArray("new_password")?.optString(0)
                                        }.getOrNull()
                                        errorMessage = parsedMsg ?: "Failed to update password. Please check your current password and retry."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Network connection failed. Please check your internet and retry."
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DialogPurple),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Update", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
