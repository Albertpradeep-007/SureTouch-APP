package com.example.suretouchapp.ui.screens.notices

import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.api.ApiClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// =======================================================
// ELEGANT COLOR TOKENS (MATCHING SURE TRUST THEME)
// =======================================================
private val ColorDarkHeader = Color(0xFF262626)
private val ColorCanvasBg = Color(0xFFFAFAFA)
private val ColorPrimaryPurple = Color(0xFF6821A8)
private val ColorPurpleLight = Color(0xFFF3E8FF)
private val ColorTextDark = Color(0xFF1E293B)
private val ColorTextSub = Color(0xFF475569)
private val ColorBorderHairline = Color(0xFFE2E8F0)

data class NoticeItem(
    val id: String,
    val title: String,
    val description: String,
    val dateStr: String,
    val category: String, // HOLIDAY, ACADEMIC, REQUIREMENT, SYSTEM
    val isImportant: Boolean = false,
    val attachment: String? = null,
    val linkUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedNotice by remember { mutableStateOf<NoticeItem?>(null) }
    var remoteNotices by remember { mutableStateOf<List<NoticeItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showPublishDialog by remember { mutableStateOf(false) }
    var readNoticeIds by remember { mutableStateOf(tokenManager.getReadNoticeIds()) }

    val userRole = tokenManager.getUserRole().trim().uppercase()
    val canPublish = tokenManager.isVolunteerTrustee() || tokenManager.isMentor() || userRole == "ADMIN" || userRole == "TRUSTEE" || userRole == "VOLUNTEER"

    suspend fun loadNotices() {
        isLoading = true
        val response = runCatching { ApiClient.getService(tokenManager).getAnnouncements() }.getOrNull()
        if (response?.isSuccessful == true) {
            val rawResults = response.body()?.results.orEmpty()
            SureProEdNotificationManager.syncAnnouncements(context, rawResults)
            remoteNotices = rawResults
                .filter { it.isActive }
                .sortedWith(compareByDescending<com.example.suretouchapp.data.model.AnnouncementDto> { it.isPinned }.thenByDescending { it.createdAt })
                .map { item ->
                    NoticeItem(
                        id = item.id,
                        title = item.title,
                        description = item.message,
                        dateStr = formatNoticeDate(item.createdAt),
                        category = if (item.isPinned) "PINNED" else item.targetAudience,
                        isImportant = item.isPinned,
                        attachment = item.attachment,
                        linkUrl = item.linkUrl
                    )
                }
            loadError = null
        } else {
            // Keep the last successful live result visible during transient failures.
            loadError = "Could not refresh live announcements."
        }
        hasLoadedOnce = true
        isLoading = false
    }

    LaunchedEffect(Unit) { loadNotices() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (selectedNotice != null) "Notice Detail" else "Notices & Broadcasts",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedNotice != null) {
                                selectedNotice = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (selectedNotice == null) {
                        if (remoteNotices.any { it.id !in readNoticeIds }) {
                            IconButton(onClick = {
                                tokenManager.markAllNoticesRead(remoteNotices.map { it.id })
                                readNoticeIds = tokenManager.getReadNoticeIds()
                                remoteNotices.forEach { SureProEdNotificationManager.dismissAnnouncement(context, it.id) }
                            }) {
                                Icon(Icons.Default.DoneAll, "Mark all as read", tint = Color.White)
                            }
                        }
                        if (canPublish) {
                            IconButton(onClick = { showPublishDialog = true }) {
                                Icon(Icons.Default.AddCircleOutline, "Add Announcement", tint = Color.White)
                            }
                        }
                        IconButton(onClick = { scope.launch { loadNotices() } }) {
                            Icon(Icons.Default.Refresh, "Refresh announcements", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorDarkHeader
                )
            )
        },
        floatingActionButton = {
            if (canPublish && selectedNotice == null) {
                ExtendedFloatingActionButton(
                    onClick = { showPublishDialog = true },
                    containerColor = ColorPrimaryPurple,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                    text = { Text("Add Announcement", fontWeight = FontWeight.Bold, fontSize = 13.5.sp) },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        containerColor = ColorCanvasBg
    ) { innerPadding ->
        if (showPublishDialog) {
            PublishAnnouncementDialog(
                tokenManager = tokenManager,
                onDismiss = { showPublishDialog = false },
                onAnnouncementCreated = {
                    scope.launch { loadNotices() }
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Translucent SURE TRUST Official Logo Watermark
            Image(
                painter = painterResource(id = com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.Center)
                    .padding(top = 40.dp)
                    .graphicsLayer { alpha = 0.08f }
            )

            val activeNotice = selectedNotice
            if (activeNotice == null) {
                // =======================================================
                // LIST VIEW (ALL NOTICES & BROADCASTS)
                // =======================================================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Live Announcements & Broadcasts",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E3A8A)
                                    )
                                    Text(
                                        text = when {
                                            isLoading && remoteNotices.isEmpty() -> "Loading live announcements…"
                                            isLoading -> "Refreshing ${remoteNotices.size} live announcements…"
                                            loadError != null && remoteNotices.isEmpty() -> requireNotNull(loadError)
                                            else -> "${remoteNotices.size} active announcements for your role and cohort."
                                        },
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF1E40AF)
                                    )
                                }
                            }
                        }
                    }

                    if (hasLoadedOnce && remoteNotices.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, ColorBorderHairline)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = if (loadError == null) Icons.Default.Campaign else Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = ColorTextSub,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (loadError == null) {
                                            "No active announcements"
                                        } else {
                                            "Live announcements unavailable"
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTextDark
                                    )
                                    Text(
                                        text = if (loadError == null) {
                                            "New broadcasts from SURE ProEd will appear here."
                                        } else {
                                            "Check your connection and try refreshing."
                                        },
                                        fontSize = 12.sp,
                                        color = ColorTextSub,
                                        textAlign = TextAlign.Center
                                    )
                                    if (loadError != null) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        TextButton(onClick = { scope.launch { loadNotices() } }) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(remoteNotices, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                selectedNotice = item
                                if (item.id !in readNoticeIds) {
                                    tokenManager.markNoticeRead(item.id)
                                    readNoticeIds = tokenManager.getReadNoticeIds()
                                    SureProEdNotificationManager.dismissAnnouncement(context, item.id)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, ColorBorderHairline),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val (icon, iconTint, badgeBg, badgeText) = when (item.category) {
                                        "PINNED" -> NoticeMeta(Icons.Default.PushPin, ColorPrimaryPurple, ColorPurpleLight, "PINNED")
                                        "HOLIDAY" -> NoticeMeta(Icons.Default.Celebration, Color(0xFFDC2626), Color(0xFFFEE2E2), "HOLIDAY NOTICE")
                                        "ACADEMIC" -> NoticeMeta(Icons.Default.School, ColorPrimaryPurple, ColorPurpleLight, "ACADEMIC")
                                        "REQUIREMENT" -> NoticeMeta(Icons.Default.Assignment, Color(0xFFD97706), Color(0xFFFEF3C7), "REQUIREMENT")
                                        else -> NoticeMeta(Icons.Default.Build, Color(0xFF475569), Color(0xFFF1F5F9), "SYSTEM NOTICE")
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(badgeBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = badgeBg
                                        ) {
                                            Text(
                                                text = badgeText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = iconTint,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (item.id !in readNoticeIds) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFDC2626)
                                            ) {
                                                Text(
                                                    text = "NEW",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = item.dateStr,
                                            fontSize = 11.sp,
                                            color = ColorTextSub
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = item.title,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorTextDark
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = item.description.take(110) + "...",
                                    fontSize = 12.5.sp,
                                    color = ColorTextSub,
                                    lineHeight = 17.5.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Tap to view full notice →",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorPrimaryPurple
                                )
                            }
                        }
                    }
                }
            } else {
                // =======================================================
                // FULL-SCREEN NOTICE DETAIL VIEW
                // =======================================================
                val (icon, iconTint, badgeBg, badgeText) = when (activeNotice.category) {
                    "PINNED" -> NoticeMeta(Icons.Default.PushPin, ColorPrimaryPurple, ColorPurpleLight, "PINNED")
                    "HOLIDAY" -> NoticeMeta(Icons.Default.Celebration, Color(0xFFDC2626), Color(0xFFFEE2E2), "HOLIDAY NOTICE")
                    "ACADEMIC" -> NoticeMeta(Icons.Default.School, ColorPrimaryPurple, ColorPurpleLight, "ACADEMIC")
                    "REQUIREMENT" -> NoticeMeta(Icons.Default.Assignment, Color(0xFFD97706), Color(0xFFFEF3C7), "REQUIREMENT")
                    else -> NoticeMeta(Icons.Default.Build, Color(0xFF475569), Color(0xFFF1F5F9), "SYSTEM NOTICE")
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, ColorBorderHairline),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = badgeBg
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = iconTint,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                Text(
                                    text = "Posted: ${activeNotice.dateStr}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorTextSub
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = activeNotice.title,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextDark,
                                lineHeight = 25.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider(color = ColorBorderHairline, thickness = 1.dp)

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = activeNotice.description,
                                fontSize = 14.sp,
                                color = ColorTextDark,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            activeNotice.attachment?.takeIf { it.isNotBlank() }?.let { attachment ->
                                OutlinedButton(
                                    onClick = { uriHandler.openUri(ApiClient.resolveServerUrl(attachment)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, ColorPrimaryPurple)
                                ) {
                                    Icon(Icons.Default.Download, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Download attachment")
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            activeNotice.linkUrl?.takeIf { it.isNotBlank() }?.let { link ->
                                Button(
                                    onClick = { uriHandler.openUri(ApiClient.resolveServerUrl(link)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple)
                                ) {
                                    Icon(Icons.Default.OpenInNew, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open resource link")
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, ColorBorderHairline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = ColorPrimaryPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Issued by: SURE ProEd Administration Desk",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorPrimaryPurple
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = { selectedNotice = null },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryPurple),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "← Back to All Notices",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class NoticeMeta<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatNoticeDate(value: String?): String = runCatching {
    if (value.isNullOrBlank()) return@runCatching "RECENT"
    val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    val output = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
    val parsed = input.parse(value.take(19))
    if (parsed != null) output.format(parsed).uppercase(Locale.US) else value.take(10).uppercase(Locale.US)
}.getOrDefault(value?.take(10)?.uppercase(Locale.US) ?: "RECENT")
