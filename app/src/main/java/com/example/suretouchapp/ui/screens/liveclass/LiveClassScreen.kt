package com.example.suretouchapp.ui.screens.liveclass

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.repository.LiveClassSelector
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager
import kotlinx.coroutines.launch

// =======================================================
// ELEGANT COLOR TOKENS (MATCHING SURE TRUST THEME)
// =======================================================
private val ColorDarkHeader = Color(0xFF262626)
private val ColorCanvasBg @Composable get() = MaterialTheme.colorScheme.background
private val ColorPrimaryPurple @Composable get() = MaterialTheme.colorScheme.primary
private val ColorPurpleLight @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val ColorTextDark @Composable get() = MaterialTheme.colorScheme.onSurface
private val ColorTextSub @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorBorderHairline @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val ColorLiveRed = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveClassScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var liveSession by remember { mutableStateOf<AttendanceDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val googleMeetUrl = liveSession?.meetingLink.orEmpty()

    LaunchedEffect(Unit) {
        liveSession = try {
            val response = ApiClient.getService(tokenManager).getAttendance()
            if (response.isSuccessful) {
                val list = response.body()?.results.orEmpty()
                SureProEdNotificationManager.syncTimetableAndClasses(context, list)
                val cohort = tokenManager.getCohortCode().takeIf(String::isNotBlank)
                LiveClassSelector.activeSession(
                    sessions = list,
                    allowedCohorts = cohort?.let(::setOf).orEmpty()
                )
            } else null
        } catch (_: Exception) { null }
        isLoading = false
    }

    var isAgreed by remember { mutableStateOf(false) }
    var showGuidelinesDialog by remember { mutableStateOf(false) }
    var dialogCheckboxChecked by remember { mutableStateOf(false) }

    // Pulsing animation for LIVE NOW indicator
    val infiniteTransition = rememberInfiniteTransition(label = "LivePulseTransition")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LivePulseAlpha"
    )

    val launchGoogleMeet = {
        if (googleMeetUrl.isBlank()) {
            Toast.makeText(context, "No live-class link is available from the backend.", Toast.LENGTH_LONG).show()
        } else try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(googleMeetUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Opening Google Meet: $googleMeetUrl", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Live Class Session",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ColorDarkHeader
                )
            )
        },
        containerColor = ColorCanvasBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // =======================================================
                // 1. HERO LIVE CLASS INFORMATION CARD
                // =======================================================
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, ColorBorderHairline),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Top Row: LIVE NOW Pulsing Badge & Cohort Code
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (googleMeetUrl.isBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (googleMeetUrl.isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .graphicsLayer { alpha = alphaPulse }
                                                    .clip(CircleShape)
                                                    .background(ColorLiveRed)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = if (googleMeetUrl.isBlank()) "NO LIVE CLASS" else "LIVE NOW",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (googleMeetUrl.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else ColorLiveRed
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Cohort: ${tokenManager.getCohortCode().ifBlank { "Pending" }}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Course Title & Code
                            Text(
                                text = liveSession?.sessionTitle ?: "No Live Class Scheduled",
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextDark
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Module Title / Notes
                            Text(
                                text = liveSession?.notes ?: "Live session details will update here once your mentor starts or schedules a class.",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = ColorTextSub
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider(color = ColorBorderHairline, thickness = 1.dp)

                            Spacer(modifier = Modifier.height(12.dp))

                            // Class Info Meta Grid (Mentor, Timings, Mode)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = ColorTextSub,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = liveSession?.conductedByName ?: "Trainer pending",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ColorTextSub
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = ColorTextSub,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = listOfNotNull(liveSession?.startTime, liveSession?.endTime).joinToString(" - ").ifBlank { "Time pending" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTextDark
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (googleMeetUrl.isBlank()) "Waiting for a live session link from trainer" else "Online class • Google Meet synchronized",
                                fontSize = 12.sp,
                                color = ColorTextSub
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // JOIN CLASS BUTTON (TRIGGERS GUIDELINES POP-UP IF NOT YET AGREED)
                            Button(
                                onClick = {
                                    if (isAgreed) {
                                        launchGoogleMeet()
                                    } else {
                                        dialogCheckboxChecked = false
                                        showGuidelinesDialog = true
                                    }
                                },
                                enabled = googleMeetUrl.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ColorPrimaryPurple,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoCall,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (googleMeetUrl.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (googleMeetUrl.isBlank()) "No Active Class to Join" else if (isAgreed) "Join Google Meet Class →" else "Join Live Class →",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (googleMeetUrl.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                // =======================================================
                // 2. OFFICIAL GOOGLE MEET LINK & COPY CARD (UNLOCKED UPON AGREEMENT)
                // =======================================================
                item {
                    AnimatedVisibility(
                        visible = isAgreed,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ColorBorderHairline),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Meet Link",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTextDark
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, ColorBorderHairline, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = googleMeetUrl.ifBlank { "No meeting link available" },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorPrimaryPurple,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(googleMeetUrl))
                                            Toast.makeText(context, "Link Copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp),
                                        enabled = googleMeetUrl.isNotBlank()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Link",
                                            tint = ColorPrimaryPurple,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (isLoading) {
                Box(
                    Modifier.fillMaxSize().background(ColorCanvasBg),
                    contentAlignment = Alignment.Center
                ) {
                    SureTrustLoadingIndicator(message = "Loading live class")
                }
            }
        }

        // =======================================================
        // POP-UP DIALOG MODAL FOR GUIDELINES & DISCIPLINARY RULES
        // =======================================================
        if (showGuidelinesDialog) {
            AlertDialog(
                onDismissRequest = { showGuidelinesDialog = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            tint = Color(0xFF1D4ED8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Class Guidelines & Rules",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "1. Stable Connection: Ensure a stable internet connection for smooth video.\n\n" +
                                           "2. Camera Mandatory: Keep your video/camera ON throughout the live class session.\n\n" +
                                           "3. Quiet Workspace: Do NOT travel while attending class; sit in a quiet, professional environment.\n\n" +
                                           "4. Mic & Doubts: Mute microphone upon entry; use 'Raise Hand' feature for Q&A.\n\n" +
                                           "5. Attendance: Attend at least 40% of the measured class duration to be marked present, as defined by the server attendance policy.\n\n" +
                                           "6. Strict Disciplinary Action: Any unfair practices, disruptive misconduct, or unprofessional behavior will result in immediate account suspension and complete termination from the cohort.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Checkbox Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { dialogCheckboxChecked = !dialogCheckboxChecked }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = dialogCheckboxChecked,
                                onCheckedChange = { dialogCheckboxChecked = it },
                                colors = CheckboxDefaults.colors(checkedColor = ColorPrimaryPurple)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "I accept & agree to follow all Live Class Guidelines & Rules",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextDark
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isAgreed = true
                            showGuidelinesDialog = false
                            launchGoogleMeet()
                        },
                        enabled = dialogCheckboxChecked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorPrimaryPurple,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Accept & Join Google Meet →",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGuidelinesDialog = false }) {
                        Text("Cancel", color = ColorTextSub)
                    }
                }
            )
        }
    }
}
