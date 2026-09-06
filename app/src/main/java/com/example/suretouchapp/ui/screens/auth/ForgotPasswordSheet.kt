package com.example.suretouchapp.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.*
import kotlinx.coroutines.launch
import retrofit2.Response
import java.util.Locale

private enum class ResetStep { EMAIL, ACCOUNT, CODE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForgotPasswordSheet(
    tokenManager: TokenManager,
    initialEmail: String = "",
    onDismiss: () -> Unit,
    onSuccess: (String, String?) -> Unit,
    requestReset: suspend (ForgotPasswordRequest) -> Response<PasswordResetResponse> = {
        ApiClient.getService(tokenManager).requestPasswordReset(it)
    },
    confirmReset: suspend (ForgotPasswordConfirmRequest) -> Response<PasswordResetResponse> = {
        ApiClient.getService(tokenManager).confirmPasswordReset(it)
    }
) {
    var step by remember { mutableStateOf(ResetStep.EMAIL) }
    var email by remember { mutableStateOf(initialEmail.trim()) }
    var role by remember { mutableStateOf<String?>(null) }
    var roles by remember { mutableStateOf(emptyList<String>()) }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun requestCode(selectedRole: String? = role) {
        if (busy) return
        val address = email.trim().lowercase(Locale.US)
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
            error = "Enter your registered email address."
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val response = requestReset(ForgotPasswordRequest(address, selectedRole))
                if (response.isSuccessful) {
                    email = address
                    role = selectedRole
                    otp = ""
                    step = ResetStep.CODE
                    countdown = 45
                } else {
                    val raw = response.errorBody()?.string().orEmpty()
                    val choices = accountRoleChoices(raw)
                    if (choices.isNotEmpty()) {
                        roles = choices
                        step = ResetStep.ACCOUNT
                    } else {
                        error = resetError(raw, "Unable to send a code. Please try again.")
                    }
                }
            } catch (_: Exception) {
                error = "Could not connect. Check your connection and try again."
            } finally { busy = false }
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) { kotlinx.coroutines.delay(1000); countdown-- }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp).navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (step) { ResetStep.EMAIL -> "Reset password"; ResetStep.ACCOUNT -> "Choose your account"; ResetStep.CODE -> "Set a new password" },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                    )
                    Text(
                        when (step) {
                            ResetStep.EMAIL -> "Use the email linked to your account."
                            ResetStep.ACCOUNT -> "This email has more than one role."
                            ResetStep.CODE -> "Enter the code sent to your account’s email."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Default.Close, "Close password reset") }
            }

            if (step != ResetStep.EMAIL) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(email, style = MaterialTheme.typography.bodyMedium)
                            if (role != null && step == ResetStep.CODE) {
                                Text("${accountRoleLabel(role!!)} account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TextButton(onClick = { step = ResetStep.EMAIL; role = null; roles = emptyList(); otp = ""; error = null }, enabled = !busy) { Text("Change") }
                    }
                }
            }

            error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(it, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            when (step) {
                ResetStep.EMAIL -> {
                    OutlinedTextField(
                        value = email, onValueChange = { email = it; role = null; error = null }, enabled = !busy,
                        label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Button(onClick = { requestCode(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Continue")
                    }
                }
                ResetStep.ACCOUNT -> {
                    roles.forEach { option ->
                        OutlinedButton(
                            onClick = { requestCode(option) }, enabled = !busy,
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Icon(if (option == "STUDENT") Icons.Default.School else Icons.Default.Groups, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(accountRoleLabel(option), fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (option == "STUDENT") "Courses, attendance and grades" else "Your staff workspace",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                    Text("Choose a role to send its reset code. Other accounts stay unchanged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                ResetStep.CODE -> {
                    OutlinedTextField(
                        value = otp, onValueChange = { otp = it.filter(Char::isDigit).take(6); error = null },
                        label = { Text("6-digit code") }, enabled = !busy, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it; error = null }, enabled = !busy,
                        label = { Text("New password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show or hide password") } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    OutlinedTextField(
                        value = confirmation, onValueChange = { confirmation = it; error = null }, enabled = !busy,
                        label = { Text("Confirm new password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Text("Use at least 8 characters with uppercase, lowercase, a number and a symbol.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        when {
                            otp.length != 6 -> error = "Enter the 6-digit code from your email."
                            password.length < 8 -> error = "Use a password with at least 8 characters."
                            password != confirmation -> error = "The passwords do not match."
                            else -> {
                                busy = true; error = null
                                scope.launch {
                                    try {
                                        val response = confirmReset(ForgotPasswordConfirmRequest(email, otp, password, role))
                                        if (response.isSuccessful) onSuccess(email, role)
                                        else error = resetError(response.errorBody()?.string().orEmpty(), "The code is invalid or expired. Request a new code.")
                                    } catch (_: Exception) { error = "Could not connect. Please try again." }
                                    finally { busy = false }
                                }
                            }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Reset password")
                    }
                    TextButton(onClick = { requestCode(role) }, enabled = !busy && countdown == 0, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (countdown > 0) "Resend code in ${countdown}s" else "Resend code")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun resetError(raw: String, fallback: String): String = runCatching {
    val data = org.json.JSONObject(raw)
    data.optString("detail").takeIf { it.isNotBlank() }
        ?: data.optString("error").takeIf { it.isNotBlank() }
        ?: data.keys().asSequence().filter { it != "code" }.joinToString("\n") { key ->
            val value = data.opt(key)
            if (value is org.json.JSONArray) (0 until value.length()).joinToString(" ") { value.optString(it) } else value?.toString().orEmpty()
        }.ifBlank { fallback }
}.getOrDefault(fallback)

