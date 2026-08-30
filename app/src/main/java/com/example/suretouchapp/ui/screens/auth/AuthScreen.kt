package com.example.suretouchapp.ui.screens.auth

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.ForgotPasswordConfirmRequest
import com.example.suretouchapp.data.model.ForgotPasswordRequest
import com.example.suretouchapp.data.model.TokenObtainRequest
import com.example.suretouchapp.ui.components.SureTrustLogo
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.theme.SureBackgroundDark
import com.example.suretouchapp.ui.theme.SurePurpleDark
import com.example.suretouchapp.data.ota.AppUpdateManager
import com.example.suretouchapp.data.ota.UpdateState
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch

private fun formatApiErrorMessage(raw: String, fallback: String = "Could not send verification OTP. Check details and try again."): String {
    if (raw.isBlank()) return fallback
    val trimmed = raw.trim()
    return try {
        val json = org.json.JSONObject(trimmed)
        val messages = mutableListOf<String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val optVal = json.opt(key)
            val fieldName = key.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            when (optVal) {
                is org.json.JSONArray -> {
                    val items = (0 until optVal.length()).map { optVal.getString(it) }
                    messages.add("$fieldName: ${items.joinToString(", ")}")
                }
                is String -> {
                    if (key == "detail" || key == "error") messages.add(optVal)
                    else messages.add("$fieldName: $optVal")
                }
                else -> messages.add("$fieldName: $optVal")
            }
        }
        if (messages.isNotEmpty()) messages.joinToString("\n") else fallback
    } catch (_: Exception) {
        trimmed.replace('"', ' ').replace('{', ' ').replace('}', ' ').replace('[', ' ').replace(']', ' ').replace("error:", "").replace("detail:", "").trim().ifBlank { fallback }
    }
}

private fun signupGenderApiValue(value: String): String? = when (value.trim()) {
    "Male" -> "MALE"
    "Female" -> "FEMALE"
    "Other" -> "OTHER"
    else -> null
}

private fun signupDateApiValue(value: String): String? {
    val trimmed = value.trim()
    val match = Regex("^(\\d{2})-(\\d{2})-(\\d{4})$").matchEntire(trimmed)
        ?: Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(trimmed)?.let {
            return signupDateApiValue("${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}")
        }
        ?: return null
    val day = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val year = match.groupValues[3].toInt()
    return runCatching {
        Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
            time
        }
        String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    tokenManager: TokenManager,
    onAuthSuccess: () -> Unit
) {
    var isLoginTab by remember { mutableStateOf(true) }
    
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    var showAdminRedirectDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var isRegPasswordVisible by remember { mutableStateOf(false) }
    var isRegConfirmPasswordVisible by remember { mutableStateOf(false) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Select Gender") }
    var dob by remember { mutableStateOf("") }
    var education by remember { mutableStateOf("Select Education / Degree") }
    var isGenderDropdownExpanded by remember { mutableStateOf(false) }
    var isEducationDropdownExpanded by remember { mutableStateOf(false) }
    var isEmailVerified by remember { mutableStateOf(false) }
    var showLinkedInWebSheet by remember { mutableStateOf(false) }
    var linkedInAuthUrl by remember { mutableStateOf<String?>(null) }
    var isOtpVerificationPending by remember { mutableStateOf(false) }
    var emailVerificationOtp by remember { mutableStateOf("") }
    var isResendingOtp by remember { mutableStateOf(false) }
    var otpResendCountdown by remember { mutableIntStateOf(0) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val updateState by AppUpdateManager.updateState.collectAsState()

    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdates(context, tokenManager)
    }

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedDay = String.format(Locale.getDefault(), "%02d", dayOfMonth)
            val formattedMonth = String.format(Locale.getDefault(), "%02d", month + 1)
            dob = "$formattedDay-$formattedMonth-$year"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(otpResendCountdown) {
        if (otpResendCountdown > 0) {
            kotlinx.coroutines.delay(1000L)
            otpResendCountdown -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SurePurpleDark,
                        SureBackgroundDark
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // SURE TRUST Official Logo Header
            SureTrustLogo(size = 110.dp, showSubtext = true)

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SURE ProEd",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Skill Upgradation For Rural Youth Empowerment",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 20.dp),
                        lineHeight = 16.sp
                    )

                    // Tab Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip(
                            selected = isLoginTab,
                            onClick = {
                                isLoginTab = true
                                errorMessage = null
                            },
                            label = { Text("Log In", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = !isLoginTab,
                            onClick = {
                                isLoginTab = false
                                errorMessage = null
                            },
                            label = { Text("Sign Up", fontWeight = FontWeight.Bold) }
                        )
                    }

                    if (isLoginTab) {
                        // 1. Log In Form
                        OutlinedTextField(
                            value = loginEmail,
                            onValueChange = { loginEmail = it },
                            label = { Text("Email Address") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            label = { Text("Password") },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        TextButton(
                            onClick = {
                                successMessage = null
                                showForgotPassword = true
                            },
                            modifier = Modifier.align(Alignment.End),
                            enabled = !isLoading
                        ) {
                            Text("Forgot password?", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (loginEmail.isBlank() || loginPassword.isBlank()) {
                                    errorMessage = "Please enter both Email and Password."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val cleanEmail = loginEmail.trim()
                                    val cleanPass = loginPassword.trim()
                                    try {
                                        val api = ApiClient.getService(tokenManager)
                                        val req = TokenObtainRequest(email = cleanEmail, password = cleanPass)
                                        val response = api.login(req)
                                        if (response.isSuccessful && response.body() != null) {
                                            val tokens = response.body()!!
                                            tokenManager.saveToken(tokens.access, tokens.refresh ?: "")
                                            tokenManager.saveUserInfo(cleanEmail.substringBefore("@"), cleanEmail)
                                            // Fetch user profile — backend returns only the caller's own record
                                            // for non-admin users (UserViewSet.get_queryset returns filter(id=user.id))
                                            try {
                                                val profileApi = ApiClient.getService(tokenManager)
                                                val usersList = profileApi.getUsers().body()?.results.orEmpty()
                                                val me = usersList.find { it.email.equals(cleanEmail, ignoreCase = true) }
                                                    ?: if (usersList.size == 1) usersList.firstOrNull() else null
                                                if (me != null) {
                                                    val role = me.role ?: "STUDENT"
                                                    val isStaff = me.isStaff == true
                                                    val isAdmin = role.equals("ADMIN", ignoreCase = true) ||
                                                            role.equals("SUPERADMIN", ignoreCase = true) ||
                                                            isStaff
                                                    if (isAdmin) {
                                                        tokenManager.clear()
                                                        isLoading = false
                                                        showAdminRedirectDialog = true
                                                        return@launch
                                                    }
                                                    val name = listOfNotNull<String>(me.firstName, me.lastName)
                                                        .joinToString(" ")
                                                        .ifBlank { cleanEmail.substringBefore("@") }
                                                    tokenManager.saveUserRole(role)
                                                    tokenManager.saveUserInfo(name, cleanEmail)
                                                    val mePhone = me.phoneNumber
                                                    if (!mePhone.isNullOrBlank()) tokenManager.savePhone(mePhone)
                                                    val meGender = me.gender
                                                    if (!meGender.isNullOrBlank()) tokenManager.saveGender(meGender)
                                                    val meDob = me.dateOfBirth
                                                    if (!meDob.isNullOrBlank()) tokenManager.saveDob(meDob)
                                                }
                                            } catch (_: Exception) {}
                                            onAuthSuccess()

                                        } else {
                                            errorMessage = "Invalid credentials. Please verify your email and password."
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "The SURE ProEd server is unavailable. Sign-in requires a connection."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                SureTrustLoadingIndicator(size = 28.dp, logoSize = 17.dp, spinnerColor = Color.White)
                            } else {
                                Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Divider OR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            Text(
                                text = "  OR SOCIAL LOGIN  ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Tabs In-App LinkedIn Social Auth Button
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        var targetUrl: String? = null
                                        try {
                                            val api = ApiClient.getService(tokenManager)
                                            val res = api.getLinkedInAuthUrl()
                                            if (res.isSuccessful && res.body() != null) {
                                                val fetchedUrl = res.body()?.authorizationUrl ?: res.body()?.authUrl ?: res.body()?.url
                                                if (!fetchedUrl.isNullOrBlank()) {
                                                    targetUrl = fetchedUrl
                                                }
                                            }
                                        } catch (_: Exception) {}

                                        if (!targetUrl.isNullOrBlank()) {
                                            linkedInAuthUrl = targetUrl
                                            showLinkedInWebSheet = true
                                        } else {
                                            errorMessage = "Could not start LinkedIn login. Please check server connection."
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Failed to launch LinkedIn login."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A66C2))
                        ) {
                            Text("in", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Continue with LinkedIn", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        // 2. Sign Up Form with Email OTP Activation
                        if (isOtpVerificationPending) {
                            // OTP Verification Mode (Account Details Frozen)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF3E8FF),
                                border = BorderStroke(1.dp, Color(0xFFD8B4FE))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = SurePurpleDark,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Details Locked for Verification",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SurePurpleDark
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                isOtpVerificationPending = false
                                                emailVerificationOtp = ""
                                                errorMessage = null
                                                successMessage = null
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Edit Email",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SurePurpleDark
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Email: $regEmail",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (firstName.isNotBlank() || lastName.isNotBlank()) {
                                        Text(
                                            text = "Name: ${"$firstName $lastName".trim()}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Enter 6-Digit Verification OTP",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "We sent an activation code to $regEmail",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = emailVerificationOtp,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                        emailVerificationOtp = it
                                    }
                                },
                                label = { Text("6-Digit OTP *") },
                                placeholder = { Text("123456") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 4.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    val cleanEmail = regEmail.trim().lowercase()
                                    val cleanOtp = emailVerificationOtp.trim()
                                    val cleanPass = regPassword.trim()
                                    val cleanFirst = firstName.trim().ifBlank { cleanEmail.substringBefore("@") }
                                    val cleanLast = lastName.trim()
                                    val fullName = "$cleanFirst $cleanLast".trim()
                                    val cleanPhone = phoneNumber.trim()
                                    val cleanDob = dob.trim()
                                    val cleanGender = gender.trim()

                                    if (cleanOtp.length != 6) {
                                        errorMessage = "Please enter the complete 6-digit OTP code."
                                        return@Button
                                    }
                                    isLoading = true
                                    errorMessage = null
                                    successMessage = null
                                    scope.launch {
                                        try {
                                            val api = ApiClient.getService(tokenManager)
                                            val verifyRes = api.verifyEmailOtp(
                                                com.example.suretouchapp.data.model.VerifyEmailOtpRequest(
                                                    email = cleanEmail,
                                                    otp = cleanOtp
                                                )
                                            )
                                            if (verifyRes.isSuccessful) {
                                                val resBody = verifyRes.body()
                                                successMessage = "Email verified! Setting up your profile..."
                                                tokenManager.markNewAccountOnboarding()
                                                val access = resBody?.access
                                                val refresh = resBody?.refresh
                                                if (!access.isNullOrBlank()) {
                                                    tokenManager.saveToken(access, refresh ?: "")
                                                    tokenManager.saveUserInfo(fullName, cleanEmail)
                                                    tokenManager.saveUserRole(resBody.user?.role ?: "STUDENT")
                                                    if (cleanPhone.isNotBlank()) tokenManager.savePhone(cleanPhone)
                                                    if (gender.isNotBlank() && gender != "Select Gender") tokenManager.saveGender(gender)
                                                    if (cleanDob.isNotBlank()) tokenManager.saveDob(cleanDob)
                                                    if (education.isNotBlank() && education != "Select Education / Degree") {
                                                        tokenManager.saveStudentProfileDetails(qualification = education)
                                                    }
                                                    onAuthSuccess()
                                                } else {
                                                    // Fallback: Login via password
                                                    val loginRes = api.login(
                                                        com.example.suretouchapp.data.model.LoginRequest(cleanEmail, cleanPass)
                                                    )
                                                    if (loginRes.isSuccessful && loginRes.body() != null) {
                                                        val tokens = loginRes.body()!!
                                                        tokenManager.saveToken(tokens.access, tokens.refresh ?: "")
                                                        tokenManager.saveUserInfo(fullName, cleanEmail)
                                                        tokenManager.saveUserRole("STUDENT")
                                                        if (cleanPhone.isNotBlank()) tokenManager.savePhone(cleanPhone)
                                                        if (gender.isNotBlank() && gender != "Select Gender") tokenManager.saveGender(gender)
                                                        if (cleanDob.isNotBlank()) tokenManager.saveDob(cleanDob)
                                                        onAuthSuccess()
                                                    } else {
                                                        isOtpVerificationPending = false
                                                        isLoginTab = true
                                                        loginEmail = cleanEmail
                                                        loginPassword = cleanPass
                                                        successMessage = "Email verified successfully! Please log in with your password."
                                                    }
                                                }
                                            } else {
                                                errorMessage = "Invalid or expired OTP code. Please verify the code or request a new one."
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Could not verify OTP. Please check your internet connection and retry."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading && emailVerificationOtp.length == 6
                            ) {
                                if (isLoading) {
                                    SureTrustLoadingIndicator(size = 28.dp, logoSize = 17.dp, spinnerColor = Color.White)
                                } else {
                                    Text("Verify Email & Activate Account", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        isOtpVerificationPending = false
                                        emailVerificationOtp = ""
                                        errorMessage = null
                                    },
                                    enabled = !isLoading
                                ) {
                                    Text("Change Email Address", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                TextButton(
                                    onClick = {
                                        val cleanEmail = regEmail.trim().lowercase()
                                        isResendingOtp = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                val api = ApiClient.getService(tokenManager)
                                                val res = api.sendEmailVerificationOtp(
                                                    com.example.suretouchapp.data.model.SendEmailVerificationOtpRequest(
                                                        email = cleanEmail,
                                                        password = regPassword.trim(),
                                                        firstName = firstName.trim(),
                                                        lastName = lastName.trim(),
                                                        phoneNumber = phoneNumber.trim(),
                                                        gender = signupGenderApiValue(gender),
                                                        dateOfBirth = signupDateApiValue(dob),
                                                        role = "STUDENT"
                                                    )
                                                )
                                                if (res.isSuccessful) {
                                                    otpResendCountdown = 60
                                                    successMessage = "New verification OTP dispatched to $cleanEmail."
                                                } else {
                                                    errorMessage = "Could not resend OTP. Please try again in a moment."
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Network error while resending OTP code."
                                            } finally {
                                                isResendingOtp = false
                                            }
                                        }
                                    },
                                    enabled = !isLoading && !isResendingOtp && otpResendCountdown == 0
                                ) {
                                    Text(
                                        text = if (otpResendCountdown > 0) "Resend in ${otpResendCountdown}s" else if (isResendingOtp) "Sending..." else "Resend OTP",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            // Standard Registration Form Entry
                            Text(
                                text = "Fill in your details and verify your email to create your account.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            )

                            // First Name *
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                label = { Text("First Name *") },
                                placeholder = { Text("John") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Last Name *
                            OutlinedTextField(
                                value = lastName,
                                onValueChange = { lastName = it },
                                label = { Text("Last Name *") },
                                placeholder = { Text("Doe") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Email Address * with note and Verify Email button
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = regEmail,
                                    onValueChange = {
                                        regEmail = it
                                        if (isEmailVerified) isEmailVerified = false
                                    },
                                    label = { Text("Email Address *") },
                                    placeholder = { Text("john.doe@example.com") },
                                    supportingText = {
                                        Text(
                                            text = "— fill all fields & set password first, then click Verify Email",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    ),
                                    trailingIcon = {
                                        if (isEmailVerified) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Email Verified",
                                                tint = Color(0xFF16A34A)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                if (isEmailVerified) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFDCFCE7),
                                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF15803D),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Email Verified",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF15803D)
                                            )
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            val cleanEmail = regEmail.trim().lowercase()
                                            val cleanPass = regPassword.trim()
                                            val cleanConfirm = regConfirmPassword.trim()
                                            val cleanFirst = firstName.trim()
                                            val cleanLast = lastName.trim()
                                            val cleanPhone = phoneNumber.trim()
                                            val cleanDob = dob.trim()
                                            val apiDob = signupDateApiValue(cleanDob)

                                            if (cleanFirst.isBlank()) {
                                                errorMessage = "Please enter your First Name."
                                                return@OutlinedButton
                                            }
                                            if (cleanLast.isBlank()) {
                                                errorMessage = "Please enter your Last Name."
                                                return@OutlinedButton
                                            }
                                            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                                errorMessage = "Please enter a valid Email Address."
                                                return@OutlinedButton
                                            }
                                            if (cleanPhone.isBlank()) {
                                                errorMessage = "Please enter your Phone Number."
                                                return@OutlinedButton
                                            }
                                            if (gender == "Select Gender" || gender.isBlank()) {
                                                errorMessage = "Please select your Gender."
                                                return@OutlinedButton
                                            }
                                            if (cleanDob.isBlank()) {
                                                errorMessage = "Please select or enter your Date of Birth (dd-mm-yyyy)."
                                                return@OutlinedButton
                                            }
                                            if (apiDob == null) {
                                                errorMessage = "Please enter a valid Date of Birth in dd-mm-yyyy format."
                                                return@OutlinedButton
                                            }
                                            if (education == "Select Education / Degree" || education.isBlank()) {
                                                errorMessage = "Please select your Highest Education / Degree."
                                                return@OutlinedButton
                                            }
                                            if (cleanPass.length < 8) {
                                                errorMessage = "Password must be at least 8 characters long."
                                                return@OutlinedButton
                                            }
                                            if (cleanPass != cleanConfirm) {
                                                errorMessage = "Passwords do not match. Please recheck confirm password."
                                                return@OutlinedButton
                                            }

                                            isLoading = true
                                            errorMessage = null
                                            successMessage = null
                                            scope.launch {
                                                try {
                                                    val api = ApiClient.getService(tokenManager)
                                                    val sendOtpReq = com.example.suretouchapp.data.model.SendEmailVerificationOtpRequest(
                                                        email = cleanEmail,
                                                        password = cleanPass,
                                                        firstName = cleanFirst,
                                                        lastName = cleanLast,
                                                        phoneNumber = cleanPhone,
                                                        gender = signupGenderApiValue(gender),
                                                        dateOfBirth = apiDob,
                                                        role = "STUDENT"
                                                    )
                                                    val otpRes = api.sendEmailVerificationOtp(sendOtpReq)
                                                    if (otpRes.isSuccessful) {
                                                        isOtpVerificationPending = true
                                                        otpResendCountdown = 60
                                                        successMessage = "Verification OTP sent to $cleanEmail."
                                                    } else {
                                                        val errorText = otpRes.errorBody()?.string().orEmpty()
                                                        errorMessage = if (errorText.contains("already verified", ignoreCase = true)) {
                                                            "An account with this email is already verified and registered. Please log in."
                                                        } else {
                                                            formatApiErrorMessage(errorText, "Could not send verification OTP. Check details and try again.")
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "The SURE ProEd server is unavailable. Sign-up requires an active connection."
                                                } finally {
                                                    isLoading = false
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        enabled = !isLoading,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SurePurpleDark),
                                        border = BorderStroke(1.dp, SurePurpleDark)
                                    ) {
                                        if (isLoading) {
                                            SureTrustLoadingIndicator(size = 24.dp, logoSize = 15.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Sending OTP...", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MarkEmailRead,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Verify Email", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    errorMessage?.let { message ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = message,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Phone Number *
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
                                        phoneNumber = input
                                    }
                                },
                                label = { Text("Phone Number *") },
                                placeholder = { Text("9876543210") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Gender * (Dropdown Menu)
                            ExposedDropdownMenuBox(
                                expanded = isGenderDropdownExpanded,
                                onExpandedChange = { isGenderDropdownExpanded = !isGenderDropdownExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = gender,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Gender *") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isGenderDropdownExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = isGenderDropdownExpanded,
                                    onDismissRequest = { isGenderDropdownExpanded = false }
                                ) {
                                    listOf("Select Gender", "Male", "Female", "Other", "Prefer not to say").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                gender = option
                                                isGenderDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Date of Birth * (Field with Calendar icon picker)
                            OutlinedTextField(
                                value = dob,
                                onValueChange = { dob = it },
                                label = { Text("Date of Birth *") },
                                placeholder = { Text("dd-mm-yyyy") },
                                trailingIcon = {
                                    IconButton(onClick = { datePickerDialog.show() }) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = "Select Date of Birth",
                                            tint = SurePurpleDark
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Education / Highest Degree * (Dropdown Menu)
                            ExposedDropdownMenuBox(
                                expanded = isEducationDropdownExpanded,
                                onExpandedChange = { isEducationDropdownExpanded = !isEducationDropdownExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = education,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Highest Education / Degree *") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isEducationDropdownExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = isEducationDropdownExpanded,
                                    onDismissRequest = { isEducationDropdownExpanded = false }
                                ) {
                                    listOf(
                                        "Select Education / Degree",
                                        "B.Tech / B.E (Engineering)",
                                        "B.Sc / BCA (Computer Science / IT)",
                                        "B.Com / BBA / BBM",
                                        "B.A / Humanities",
                                        "M.Tech / M.E / MS",
                                        "MCA / M.Sc",
                                        "MBA / PGDM",
                                        "Diploma / Polytechnic",
                                        "Intermediate / 12th Grade",
                                        "Other Degree / Graduate"
                                    ).forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                education = option
                                                isEducationDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Password *
                            OutlinedTextField(
                                value = regPassword,
                                onValueChange = { regPassword = it },
                                label = { Text("Password *") },
                                placeholder = { Text("••••••••") },
                                visualTransformation = if (isRegPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { isRegPasswordVisible = !isRegPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isRegPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (isRegPasswordVisible) "Hide Password" else "Show Password"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Confirm Password *
                            OutlinedTextField(
                                value = regConfirmPassword,
                                onValueChange = { regConfirmPassword = it },
                                label = { Text("Confirm Password *") },
                                placeholder = { Text("••••••••") },
                                visualTransformation = if (isRegConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { isRegConfirmPasswordVisible = !isRegConfirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isRegConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (isRegConfirmPasswordVisible) "Hide Password" else "Show Password"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Warning Banner if email is not yet verified
                            if (!isEmailVerified) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFFBEB),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⚠️",
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = "Please verify your email above before creating your account.",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB45309),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Create Account Button
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    val cleanEmail = regEmail.trim().lowercase()
                                    val cleanPass = regPassword.trim()
                                    val cleanConfirm = regConfirmPassword.trim()
                                    val cleanFirst = firstName.trim()
                                    val cleanLast = lastName.trim()
                                    val cleanPhone = phoneNumber.trim()
                                    val cleanDob = dob.trim()
                                    val apiDob = signupDateApiValue(cleanDob)

                                    if (cleanFirst.isBlank()) {
                                        errorMessage = "Please enter your First Name."
                                        return@Button
                                    }
                                    if (cleanLast.isBlank()) {
                                        errorMessage = "Please enter your Last Name."
                                        return@Button
                                    }
                                    if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                        errorMessage = "Please enter a valid Email Address."
                                        return@Button
                                    }
                                    if (cleanPhone.isBlank()) {
                                        errorMessage = "Please enter your Phone Number."
                                        return@Button
                                    }
                                    if (gender == "Select Gender" || gender.isBlank()) {
                                        errorMessage = "Please select your Gender."
                                        return@Button
                                    }
                                    if (cleanDob.isBlank()) {
                                        errorMessage = "Please select or enter your Date of Birth (dd-mm-yyyy)."
                                        return@Button
                                    }
                                    if (apiDob == null) {
                                        errorMessage = "Please enter a valid Date of Birth in dd-mm-yyyy format."
                                        return@Button
                                    }
                                    if (education == "Select Education / Degree" || education.isBlank()) {
                                        errorMessage = "Please select your Highest Education / Degree."
                                        return@Button
                                    }
                                    if (cleanPass.length < 8) {
                                        errorMessage = "Password must be at least 8 characters long."
                                        return@Button
                                    }
                                    if (cleanPass != cleanConfirm) {
                                        errorMessage = "Passwords do not match. Please recheck confirm password."
                                        return@Button
                                    }

                                    if (!isEmailVerified) {
                                        // Email not yet verified - send OTP and prompt user
                                        isLoading = true
                                        errorMessage = null
                                        successMessage = null
                                        scope.launch {
                                            try {
                                                val api = ApiClient.getService(tokenManager)
                                                val sendOtpReq = com.example.suretouchapp.data.model.SendEmailVerificationOtpRequest(
                                                    email = cleanEmail,
                                                    password = cleanPass,
                                                    firstName = cleanFirst,
                                                    lastName = cleanLast,
                                                    phoneNumber = cleanPhone,
                                                    gender = signupGenderApiValue(gender),
                                                    dateOfBirth = apiDob,
                                                    role = "STUDENT"
                                                )
                                                val otpRes = api.sendEmailVerificationOtp(sendOtpReq)
                                                if (otpRes.isSuccessful) {
                                                    isOtpVerificationPending = true
                                                    otpResendCountdown = 60
                                                    successMessage = "Verification OTP sent to $cleanEmail. Please enter the OTP to create your account."
                                                } else {
                                                    val errorText = otpRes.errorBody()?.string().orEmpty()
                                                    errorMessage = if (errorText.contains("already verified", ignoreCase = true)) {
                                                        "An account with this email is already verified and registered. Please log in."
                                                    } else {
                                                        formatApiErrorMessage(errorText, "Could not send verification OTP. Check details and try again.")
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "The SURE ProEd server is unavailable. Sign-up requires an active connection."
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    } else {
                                        // Email already verified - proceed to login/onboarding
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                val api = ApiClient.getService(tokenManager)
                                                val loginRes = api.login(
                                                    com.example.suretouchapp.data.model.LoginRequest(cleanEmail, cleanPass)
                                                )
                                                if (loginRes.isSuccessful && loginRes.body() != null) {
                                                    val tokens = loginRes.body()!!
                                                    tokenManager.saveToken(tokens.access, tokens.refresh ?: "")
                                                    tokenManager.saveUserInfo("$cleanFirst $cleanLast".trim(), cleanEmail)
                                                    tokenManager.saveUserRole("STUDENT")
                                                    if (cleanPhone.isNotBlank()) tokenManager.savePhone(cleanPhone)
                                                    if (gender.isNotBlank() && gender != "Select Gender") tokenManager.saveGender(gender)
                                                    if (cleanDob.isNotBlank()) tokenManager.saveDob(cleanDob)
                                                    if (education.isNotBlank() && education != "Select Education / Degree") {
                                                        tokenManager.saveStudentProfileDetails(qualification = education)
                                                    }
                                                    onAuthSuccess()
                                                } else {
                                                    errorMessage = "Could not sign in automatically. Please log in with your credentials."
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Network error. Please try logging in."
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    SureTrustLoadingIndicator(size = 28.dp, logoSize = 17.dp, spinnerColor = Color.White)
                                } else {
                                    Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    errorMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    successMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = msg,
                            color = Color(0xFF15803D),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Dynamic Update Banner on Login/Signup Card
                    if (updateState is UpdateState.UpdateAvailable) {
                        val updateInfo = (updateState as UpdateState.UpdateAvailable).info
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF3E8FF),
                            border = BorderStroke(1.dp, Color(0xFFA855F7)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clickable {
                                    scope.launch {
                                        AppUpdateManager.checkForUpdates(context, tokenManager)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Update Available",
                                    tint = Color(0xFF7E22CE),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "New Update Available: v${updateInfo.versionName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF581C87)
                                    )
                                    Text(
                                        text = "Tap to install the latest version now",
                                        fontSize = 11.sp,
                                        color = Color(0xFF7E22CE)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF7E22CE),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Version Footer
            Row(
                modifier = Modifier.padding(top = 14.dp, bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (updateState is UpdateState.UpdateAvailable) {
                            "⚠️ Installed: v${AppUpdateManager.currentVersionName} • Latest: v${(updateState as UpdateState.UpdateAvailable).info.versionName}"
                        } else {
                            "SURE ProEd v${AppUpdateManager.currentVersionName} • Latest version"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (showForgotPassword) {
            ForgotPasswordSheet(
                tokenManager = tokenManager,
                initialEmail = loginEmail.trim(),
                onDismiss = { showForgotPassword = false },
                onSuccess = { updatedEmail ->
                    loginEmail = updatedEmail
                    loginPassword = ""
                    showForgotPassword = false
                    successMessage = "Password reset successfully. You can now log in with your new password."
                }
            )
        }

        if (showAdminRedirectDialog) {
            AlertDialog(
                onDismissRequest = { showAdminRedirectDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Admin Login Notice",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Admin Portal Required",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Admin accounts must log in via the Web Admin Portal (Django Admin) and cannot access the mobile application.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Would you like to open the Admin Web Portal in your browser now?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAdminRedirectDialog = false
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sureproed.com/secure-admin/"))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    ) {
                        Text("Open Admin Portal")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAdminRedirectDialog = false
                        }
                    ) {
                        Text("Stay on Login")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (showLinkedInWebSheet && !linkedInAuthUrl.isNullOrBlank()) {
            LinkedInOAuthSheet(
                initialUrl = linkedInAuthUrl!!,
                onDismiss = { showLinkedInWebSheet = false },
                onCodeReceived = { code, access, refresh ->
                    showLinkedInWebSheet = false
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            if (!access.isNullOrBlank()) {
                                tokenManager.saveToken(access, refresh ?: "")
                                runCatching { com.example.suretouchapp.data.repository.StudentProfileRepository(tokenManager).load() }
                                onAuthSuccess()
                            } else if (!code.isNullOrBlank()) {
                                val api = ApiClient.getService(tokenManager)
                                val res = api.connectLinkedInCallback(com.example.suretouchapp.data.model.LinkedInCallbackRequest(code = code))
                                if (res.isSuccessful && res.body() != null) {
                                    val body = res.body()!!
                                    val photoUrl = body.profilePhoto
                                        ?: body.profilePicture
                                        ?: body.photoUrl
                                        ?: body.avatar
                                        ?: body.picture
                                        ?: body.linkedinProfilePhotoUrl
                                        ?: body.user?.effectiveProfilePhoto
                                    if (!photoUrl.isNullOrBlank()) {
                                        tokenManager.saveProfilePhotoUrl(photoUrl)
                                    }
                                    if (!body.linkedinUrl.isNullOrBlank()) {
                                        tokenManager.saveStudentProfileDetails(linkedinUrl = body.linkedinUrl)
                                    }
                                    if (!body.access.isNullOrBlank()) {
                                        tokenManager.saveToken(body.access, body.refresh ?: "")
                                        val userName = listOfNotNull<String>(body.user?.firstName, body.user?.lastName)
                                            .joinToString(" ")
                                            .ifBlank { body.user?.email?.substringBefore("@") ?: "Student" }
                                        tokenManager.saveUserInfo(userName, body.user?.email ?: "")
                                        tokenManager.saveUserRole(body.user?.role ?: "STUDENT")
                                        runCatching { com.example.suretouchapp.data.repository.StudentProfileRepository(tokenManager).load() }
                                        onAuthSuccess()
                                    } else {
                                        try {
                                            val usersList = api.getUsers().body()?.results.orEmpty()
                                            val me = usersList.firstOrNull()
                                            if (me != null) {
                                                val name = listOfNotNull<String>(me.firstName, me.lastName).joinToString(" ").ifBlank { me.email.substringBefore("@") }
                                                tokenManager.saveUserInfo(name, me.email)
                                                tokenManager.saveUserRole(me.role ?: "STUDENT")
                                                val mePhoto = me.effectiveProfilePhoto
                                                if (!mePhoto.isNullOrBlank()) tokenManager.saveProfilePhotoUrl(mePhoto)
                                            }
                                        } catch (_: Exception) {}
                                        runCatching { com.example.suretouchapp.data.repository.StudentProfileRepository(tokenManager).load() }
                                        onAuthSuccess()
                                    }
                                } else {
                                    val errText = res.errorBody()?.string().orEmpty()
                                    errorMessage = if (errText.isNotBlank()) {
                                        errText.replace("\"", "").replace("{", "").replace("}", "").replace("error:", "").trim()
                                    } else {
                                        "LinkedIn authentication failed on server. Please try logging in with email and password."
                                    }
                                }
                            } else {
                                errorMessage = "LinkedIn authentication completed without code."
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error during LinkedIn verification: ${e.localizedMessage ?: "Network error"}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkedInOAuthSheet(
    initialUrl: String,
    onDismiss: () -> Unit,
    onCodeReceived: (code: String?, access: String?, refresh: String?) -> Unit
) {
    var isLoadingWeb by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0A66C2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("in", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "LinkedIn Sign In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (isLoadingWeb) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0A66C2)
                )
            } else {
                HorizontalDivider(color = Color(0xFFE2E8F0))
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoadingWeb = true
                                checkAndIntercept(url, onCodeReceived)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoadingWeb = false
                                checkAndIntercept(url, onCodeReceived)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val reqUrl = request?.url?.toString().orEmpty()
                                return checkAndIntercept(reqUrl, onCodeReceived)
                            }
                        }
                        loadUrl(initialUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun checkAndIntercept(
    url: String?,
    onCodeReceived: (code: String?, access: String?, refresh: String?) -> Unit
): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = Uri.parse(url)
    val access = uri.getQueryParameter("access") ?: uri.getQueryParameter("access_token") ?: uri.getQueryParameter("token")
    val refresh = uri.getQueryParameter("refresh") ?: uri.getQueryParameter("refresh_token")
    val code = uri.getQueryParameter("code")

    // 1. If redirected with tokens (either via suretrust:// deep link or frontend redirect)
    if (!access.isNullOrBlank()) {
        onCodeReceived(null, access, refresh)
        return true
    }

    // 2. If it is the suretrust:// custom scheme deep link
    if (uri.scheme.equals("suretrust", ignoreCase = true)) {
        onCodeReceived(code, access, refresh)
        return true
    }

    // 3. Do not intercept server callback GET request — let the WebView load it so backend exchanges code cleanly!
    if (url.contains("/api/auth/linkedin/callback") && !code.isNullOrBlank()) {
        return false
    }

    // 4. If intercepted on custom frontend redirect that has error
    val error = uri.getQueryParameter("error") ?: uri.getQueryParameter("error_description")
    if (!error.isNullOrBlank() && (url.contains("error=") || uri.scheme == "suretrust")) {
        onCodeReceived(null, null, null)
        return true
    }

    // 5. Fallback for external oauth callback
    if (!code.isNullOrBlank() && !url.contains("/api/auth/")) {
        onCodeReceived(code, access, refresh)
        return true
    }

    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgotPasswordSheet(
    tokenManager: TokenManager,
    initialEmail: String = "",
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var resendCountdown by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            kotlinx.coroutines.delay(1000L)
            resendCountdown -= 1
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row with Icon and Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF3E8FF),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isOtpSent) Icons.Default.MarkEmailRead else Icons.Default.LockReset,
                            contentDescription = null,
                            tint = SurePurpleDark,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                }
            }

            // Title & Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isOtpSent) "Enter Verification Code" else "Reset Password",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = if (isOtpSent) {
                        "We've sent a 6-digit code to ${email.trim()}"
                    } else {
                        "Enter your registered email address and we'll send you a 6-digit OTP to reset your password."
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 18.sp
                )
            }

            // Step Indicator Badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isOtpSent) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                border = BorderStroke(1.dp, if (isOtpSent) Color(0xFFA7F3D0) else Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isOtpSent) Color(0xFF10B981) else SurePurpleDark)
                    )
                    Text(
                        text = if (isOtpSent) "Step 2 of 2: Verify OTP & Choose New Password" else "Step 1 of 2: Registered Email",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOtpSent) Color(0xFF065F46) else Color(0xFF475569)
                    )
                }
            }

            // Error / Info banners
            errorMessage?.let { error ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = Color(0xFFDC2626),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            infoMessage?.let { info ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = info,
                        color = Color(0xFF16A34A),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // Input Fields
            if (!isOtpSent) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Registered Email Address") },
                    placeholder = { Text("e.g. yourname@example.com") },
                    leadingIcon = { Icon(Icons.Default.Email, null, tint = SurePurpleDark) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SurePurpleDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedLabelColor = SurePurpleDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        val targetEmail = email.trim()
                        if (targetEmail.isBlank() || !targetEmail.contains("@")) {
                            errorMessage = "Please enter a valid registered email address."
                            return@Button
                        }
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            infoMessage = null
                            try {
                                val response = ApiClient.getService(tokenManager)
                                    .requestPasswordReset(ForgotPasswordRequest(targetEmail))
                                if (response.isSuccessful) {
                                    isOtpSent = true
                                    resendCountdown = 45
                                    infoMessage = "Verification OTP sent! Please check your inbox (and spam folder)."
                                } else {
                                    errorMessage = "Could not send password reset OTP. Please check the email and try again."
                                }
                            } catch (_: Exception) {
                                errorMessage = "Unable to connect to SURE ProEd server. Please check your network."
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurePurpleDark)
                ) {
                    if (isLoading) {
                        SureTrustLoadingIndicator(size = 24.dp, logoSize = 14.dp, spinnerColor = Color.White)
                    } else {
                        Text("Send Verification Code", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Step 2: OTP Entry + New Password
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "6-Digit Verification Code",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )

                    // Modern OTP Box Component
                    ResetOtpCodeField(
                        value = otp,
                        onValueChange = { value ->
                            otp = value.filter(Char::isDigit).take(6)
                            errorMessage = null
                        }
                    )

                    // Resend Code Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { isOtpSent = false; otp = "" },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("← Change email", fontSize = 12.sp, color = Color(0xFF64748B))
                        }

                        if (resendCountdown > 0) {
                            Text(
                                "Resend code in ${resendCountdown}s",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        infoMessage = null
                                        try {
                                            val response = ApiClient.getService(tokenManager)
                                                .requestPasswordReset(ForgotPasswordRequest(email.trim()))
                                            if (response.isSuccessful) {
                                                otp = ""
                                                resendCountdown = 45
                                                infoMessage = "A fresh 6-digit OTP has been sent to your email."
                                            } else {
                                                errorMessage = "Could not resend OTP. Please try again later."
                                            }
                                        } catch (_: Exception) {
                                            errorMessage = "Connection error. Please try again."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                enabled = !isLoading,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Resend Code", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SurePurpleDark)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // New Password Fields
                    Text(
                        "Create New Password",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; errorMessage = null },
                        label = { Text("New Password") },
                        placeholder = { Text("At least 8 characters") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = SurePurpleDark) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SurePurpleDark,
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedLabelColor = SurePurpleDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = null },
                        label = { Text("Confirm New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = SurePurpleDark) },
                        trailingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    if (isConfirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SurePurpleDark,
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedLabelColor = SurePurpleDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Password match indicator
                    if (newPassword.isNotBlank() && confirmPassword.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val matches = newPassword == confirmPassword
                            Icon(
                                if (matches) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (matches) Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                if (matches) "Passwords match" else "Passwords do not match",
                                fontSize = 11.5.sp,
                                color = if (matches) Color(0xFF16A34A) else Color(0xFFDC2626)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            when {
                                otp.length != 6 -> {
                                    errorMessage = "Please enter the complete 6-digit OTP code."
                                }
                                newPassword.length < 8 -> {
                                    errorMessage = "Password must be at least 8 characters."
                                }
                                newPassword != confirmPassword -> {
                                    errorMessage = "The passwords do not match."
                                }
                                else -> {
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        infoMessage = null
                                        try {
                                            val response = ApiClient.getService(tokenManager)
                                                .confirmPasswordReset(
                                                    ForgotPasswordConfirmRequest(
                                                        email = email.trim(),
                                                        otp = otp.trim(),
                                                        newPassword = newPassword
                                                    )
                                                )
                                            if (response.isSuccessful) {
                                                onSuccess(email.trim())
                                            } else {
                                                errorMessage = "The OTP code is invalid or has expired. Please request a new code."
                                            }
                                        } catch (_: Exception) {
                                            errorMessage = "Unable to connect to server. Please try again."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurePurpleDark)
                    ) {
                        if (isLoading) {
                            SureTrustLoadingIndicator(size = 24.dp, logoSize = 14.dp, spinnerColor = Color.White)
                        } else {
                            Text("Reset Password & Sign In", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResetOtpCodeField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = LocalTextStyle.current.copy(color = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.size(1.dp)) { innerTextField() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(6) { index ->
                        val digit = value.getOrNull(index)?.toString().orEmpty()
                        val isFilled = digit.isNotEmpty()
                        val isActive = index == value.length.coerceAtMost(5)

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                isFilled -> Color(0xFFF3E8FF)
                                isActive -> Color(0xFFFAF5FF)
                                else -> Color(0xFFF8FAFC)
                            },
                            border = BorderStroke(
                                width = if (isActive) 2.dp else 1.dp,
                                color = when {
                                    isActive -> SurePurpleDark
                                    isFilled -> SurePurpleDark.copy(alpha = 0.5f)
                                    else -> Color(0xFFCBD5E1)
                                }
                            ),
                            shadowElevation = if (isActive) 3.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = digit.ifEmpty { "•" },
                                    color = if (isFilled) SurePurpleDark else Color(0xFF94A3B8),
                                    fontSize = if (isFilled) 22.sp else 16.sp,
                                    fontWeight = if (isFilled) FontWeight.Black else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
