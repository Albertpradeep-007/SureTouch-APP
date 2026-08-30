package com.example.suretouchapp.ui.screens.notifications

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.NotificationDto
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import kotlinx.coroutines.launch
import java.util.Locale

private val NotificationHeader = Color(0xFF262626)
private val NotificationCanvas = Color(0xFFFAFAFA)
private val NotificationPurple = Color(0xFF6821A8)
private val NotificationPurpleLight = Color(0xFFF3E8FF)
private val NotificationText = Color(0xFF1E293B)
private val NotificationSubtext = Color(0xFF475569)
private val NotificationBorder = Color(0xFFE2E8F0)

private data class StudentAlert(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val category: String,
    val isRead: Boolean,
    val actionUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit,
    onNavigateAction: (String?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var mobileNotificationsEnabled by remember {
        mutableStateOf(SureProEdNotificationManager.canPost(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        mobileNotificationsEnabled = SureProEdNotificationManager.canPost(context)
        if (mobileNotificationsEnabled) SureProEdNotificationManager.syncUnread(context, notifications)
    }
    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mobileNotificationsEnabled = SureProEdNotificationManager.canPost(context)
                refreshKey += 1
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val openNotificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        )
    }
    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    LaunchedEffect(refreshKey) {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val api = ApiClient.getService(tokenManager)
            val res = api.getNotifications()
            if (res.isSuccessful) {
                val remoteNotifications = res.body()?.results.orEmpty().sortedByDescending { it.createdAt }
                notifications = remoteNotifications
                tokenManager.markCourseApplicationNoticeRead()
                mobileNotificationsEnabled = SureProEdNotificationManager.canPost(context)
                if (mobileNotificationsEnabled) {
                    SureProEdNotificationManager.syncUnread(context, remoteNotifications)
                    val announcements = runCatching {
                        api.getAnnouncements().takeIf { it.isSuccessful }?.body()?.results.orEmpty()
                    }.getOrDefault(emptyList())
                    if (announcements.isNotEmpty()) {
                        SureProEdNotificationManager.syncAnnouncements(context, announcements)
                    }
                }
                isConnected = true
                hasLoadedOnce = true
                isOffline = false
                connectionError = null
                errorTitle = null
            } else {
                val errorInfo = NetworkUtils.getNetworkErrorInfo(context, null)
                isConnected = false
                isOffline = errorInfo.isOffline
                errorTitle = errorInfo.title
                connectionError = errorInfo.message
            }
        } catch (e: Exception) {
            val errorInfo = NetworkUtils.getNetworkErrorInfo(context, e)
            isConnected = false
            isOffline = errorInfo.isOffline
            errorTitle = errorInfo.title
            connectionError = errorInfo.message
        } finally {
            isLoading = false
        }
    }

    val alerts = notifications.map { it.toStudentAlert() }
    BackendConnectionGate(
        isLoading = isLoading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Notifications...",
        onRetry = { refreshKey += 1 },
        onLogout = null
    ) {
        Scaffold(
            topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Account Notifications", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (notifications.any { !it.isRead }) {
                        IconButton(onClick = {
                            notifications = notifications.map { it.copy(isRead = true) }
                            scope.launch {
                                runCatching { ApiClient.getService(tokenManager).markAllNotificationsRead() }
                            }
                        }) {
                            Icon(Icons.Default.DoneAll, "Mark all as read", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { refreshKey += 1 }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, "Refresh notifications", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NotificationHeader)
            )
        },
        containerColor = NotificationCanvas
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Image(
                painter = painterResource(com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(280.dp).align(Alignment.Center).graphicsLayer { alpha = 0.08f }
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!mobileNotificationsEnabled) {
                    item {
                        MobileNotificationPermissionCard(
                            enabled = false,
                            onEnable = requestNotificationPermission,
                            onSettings = openNotificationSettings
                        )
                    }
                }
                when {
                    isLoading -> item {
                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            SureTrustLoadingIndicator(message = "Loading notifications")
                        }
                    }
                    alerts.isEmpty() -> item { NotificationEmptyState() }
                    else -> items(alerts, key = { it.id }) { alert ->
                        NotificationCard(
                            alert = alert,
                            onClick = {
                                val remote = notifications.firstOrNull { it.id == alert.id }
                                if (remote != null && !remote.isRead) {
                                    notifications = notifications.map {
                                        if (it.id == remote.id) it.copy(isRead = true) else it
                                    }
                                    scope.launch {
                                        runCatching {
                                            ApiClient.getService(tokenManager).markNotificationRead(remote.id)
                                        }
                                    }
                                }
                                onNavigateAction(alert.actionUrl)
                            }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun MobileNotificationPermissionCard(
    enabled: Boolean,
    onEnable: () -> Unit,
    onSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xFFECFDF5) else Color.White),
        border = BorderStroke(1.dp, if (enabled) Color(0xFFA7F3D0) else Color(0xFFE9D5FF)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (enabled) Color(0xFFD1FAE5) else NotificationPurpleLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (enabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        null,
                        tint = if (enabled) Color(0xFF059669) else NotificationPurple,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (enabled) "Mobile notifications enabled" else "Enable mobile notifications",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NotificationText
                    )
                    Text(
                        if (enabled) "SURE ProEd updates can appear in your phone notification panel."
                        else "Allow alerts for cohort, classes, assignments, activities, and certificates.",
                        fontSize = 11.5.sp,
                        color = NotificationSubtext,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!enabled) {
                    Button(
                        onClick = onEnable,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NotificationPurple),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Enable", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, NotificationPurple)
                ) {
                    Icon(Icons.Default.Settings, null, tint = NotificationPurple, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Settings", color = NotificationPurple, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NotificationEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NotificationBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.NotificationsNone, null, tint = NotificationPurple, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(10.dp))
            Text("No notifications yet", fontWeight = FontWeight.Bold, color = NotificationText)
            Text(
                "Backend messages will appear here after your application, screening, cohort, or learning activity changes.",
                fontSize = 12.sp,
                color = NotificationSubtext,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun NotificationCard(alert: StudentAlert, onClick: () -> Unit) {
    val (icon, tint, badgeBackground, badgeText) = when (alert.category) {
        "GRADE" -> AlertStyle(Icons.Default.AssignmentTurnedIn, Color(0xFFD97706), Color(0xFFFEF3C7), "GRADE")
        "ACADEMIC" -> AlertStyle(Icons.Default.School, Color(0xFF16A34A), Color(0xFFDCFCE7), "ACADEMIC")
        "ATTENDANCE" -> AlertStyle(Icons.Default.EventAvailable, Color(0xFF2563EB), Color(0xFFDBEAFE), "ATTENDANCE")
        else -> AlertStyle(Icons.Default.VerifiedUser, NotificationPurple, NotificationPurpleLight, "ACCOUNT")
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (alert.isRead) Color.White else Color(0xFFFCFAFF)),
        border = BorderStroke(if (alert.isRead) 1.dp else 1.5.dp, if (alert.isRead) NotificationBorder else Color(0xFFD8B4FE)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(badgeBackground), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = badgeBackground) {
                    Text(badgeText, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint)
                }
                Spacer(Modifier.weight(1f))
                Text(alert.timestamp, fontSize = 10.5.sp, color = NotificationSubtext)
            }
            Spacer(Modifier.height(10.dp))
            Text(alert.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NotificationText)
            Spacer(Modifier.height(4.dp))
            Text(alert.message, fontSize = 12.5.sp, color = NotificationSubtext, lineHeight = 17.sp)
            if (!alert.actionUrl.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Tap to open", fontSize = 11.sp, color = NotificationPurple, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class AlertStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val background: Color,
    val label: String
)

private fun NotificationDto.toStudentAlert(): StudentAlert {
    val searchable = "$title $message".uppercase(Locale.US)
    val category = when {
        "GRADE" in searchable || "MARK" in searchable || "TEST" in searchable -> "GRADE"
        "ATTENDANCE" in searchable -> "ATTENDANCE"
        listOf(
            "COHORT", "SCREENING", "COURSE", "INTERVIEW", "CLASS", "TIMETABLE",
            "ASSIGNMENT", "TRAINING", "CERTIFICATE", "COMMUNITY"
        ).any(searchable::contains) -> "ACADEMIC"
        else -> "ACCOUNT"
    }
    val timestamp = createdAt.takeIf { it.isNotBlank() }
        ?.take(16)
        ?.replace('T', ' ')
        ?: "Just now"
    return StudentAlert(id, title, message, timestamp, category, isRead, actionUrl)
}
