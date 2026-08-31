package com.example.suretouchapp.ui.screens.trustee

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.CohortDto
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private val DarkSlate @Composable get() = MaterialTheme.colorScheme.onSurface
private val GitHubBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val PurplePrimary @Composable get() = MaterialTheme.colorScheme.primary
private val PurpleSoft @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val BorderColor @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val GreenActive = Color(0xFF047857)
private val GreenSoft = Color(0xFFE8F8F1)
private val AmberGrace = Color(0xFFD97706)
private val AmberSoft = Color(0xFFFEF3C7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubRepoProvisioningDialog(
    tokenManager: TokenManager,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cohorts by remember { mutableStateOf<List<CohortDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var provisioningCohortId by remember { mutableStateOf<String?>(null) }
    var provisioningResult by remember { mutableStateOf<String?>(null) }
    var statusDialogCohort by remember { mutableStateOf<CohortDto?>(null) }
    var statusDialogMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    if (statusDialogCohort != null) {
        val cohort = statusDialogCohort!!
        val daysRemaining = getGraceDaysRemaining(cohort.trainingStartedAt)
        val isEligible = cohort.canProvisionGithubRepositories || (cohort.status.equals("TRAINING", true) && daysRemaining <= 0)

        AlertDialog(
            onDismissRequest = { statusDialogCohort = null; statusDialogMessage = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isEligible) GreenSoft else AmberSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEligible) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = if (isEligible) GreenActive else AmberGrace,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = if (isEligible) "Ready for Provisioning" else "15-Day Grace Period Active",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkSlate
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Cohort: ${cohort.code ?: cohort.name} (${cohort.courseName ?: "Course"})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = DarkSlate
                    )

                    if (!isEligible) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberSoft,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "⏳ $daysRemaining days remaining in 15-day grace window",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberGrace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Training started: ${formatDateDisplay(cohort.trainingStartedAt)}",
                                    fontSize = 11.sp,
                                    color = DarkSlate
                                )
                                Text(
                                    text = "Auto-trigger date: ${formatDateDisplay(cohort.githubRepositoryEligibleAt)}",
                                    fontSize = 11.sp,
                                    color = DarkSlate
                                )
                            }
                        }

                        Text(
                            text = "Why is it waiting?\nBackend policy enforces a 15-day grace period after a cohort enters TRAINING to ensure enrolled students connect their GitHub accounts before repositories are provisioned.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            text = "15-day grace period complete! The backend will create private student repositories with standard folders and mentor access.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GitHubBg,
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, tint = GreenActive, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Duplicate Prevention: Already provisioned student repositories are automatically skipped to guarantee zero duplicate repos.",
                                fontSize = 11.sp,
                                color = DarkSlate,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    if (statusDialogMessage != null) {
                        Text(
                            text = statusDialogMessage!!,
                            fontSize = 11.5.sp,
                            color = Color(0xFF9333EA),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { statusDialogCohort = null; statusDialogMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Got It")
                }
            }
        )
    }

    fun loadData() {
        isLoading = true
        scope.launch {
            val res = runCatching { ApiClient.getService(tokenManager).getCohorts() }.getOrNull()
            if (res?.isSuccessful == true) {
                cohorts = res.body()?.results.orEmpty()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    val filteredCohorts = remember(cohorts, selectedFilter) {
        when (selectedFilter) {
            "ELIGIBLE" -> cohorts.filter { it.canProvisionGithubRepositories || (it.status.equals("TRAINING", true) && getGraceDaysRemaining(it.trainingStartedAt) <= 0) }
            "GRACE" -> cohorts.filter { it.status.equals("TRAINING", true) && getGraceDaysRemaining(it.trainingStartedAt) > 0 }
            "OTHER" -> cohorts.filter { !it.status.equals("TRAINING", true) }
            else -> cohorts
        }
    }

    Dialog(
        onDismissRequest = { if (provisioningCohortId == null) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
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
                                .background(MaterialTheme.colorScheme.inverseSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "GitHub Repo Provisioning",
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkSlate
                            )
                            Text(
                                text = "Automated 15-day trigger & manual runner",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = provisioningCohortId == null
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BorderColor)

                // 15-Day Policy Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = GitHubBg,
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PurpleSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoMode, null, tint = PurplePrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "15-Day Grace Automation • Duplicate-Safe",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkSlate
                            )
                            Text(
                                text = "Repositories auto-create 15 days after entering Training status. Provisioning is idempotent and duplicate-safe: existing repositories are preserved and skipped.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ALL" to "All (${cohorts.size})",
                        "ELIGIBLE" to "Eligible",
                        "GRACE" to "Grace Period",
                        "OTHER" to "Other Status"
                    ).forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = key },
                            label = { Text(label, fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) MaterialTheme.colorScheme.primary else BorderColor
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cohort List
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                } else if (filteredCohorts.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No cohorts found in this filter.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredCohorts, key = { it.id }) { cohort ->
                            CohortProvisionCard(
                                cohort = cohort,
                                isProvisioning = provisioningCohortId == cohort.id,
                                onTriggerProvision = {
                                    provisioningCohortId = cohort.id
                                    scope.launch {
                                        val res = runCatching {
                                            ApiClient.getService(tokenManager).createCohortGithubRepositories(cohort.id)
                                        }.getOrNull()

                                        if (res?.isSuccessful == true) {
                                            val body = res.body()
                                            val created = body?.get("created_count") ?: body?.get("provisioned") ?: "Repositories provisioned"
                                            Toast.makeText(context, "GitHub repositories provisioned successfully ($created)!", Toast.LENGTH_LONG).show()
                                            statusDialogCohort = cohort
                                            statusDialogMessage = "Success: Repositories provisioned ($created) and collaborators invited."
                                            loadData()
                                            onUpdated()
                                        } else {
                                            val errorBody = res?.errorBody()?.string().orEmpty()
                                            statusDialogCohort = cohort
                                            if (errorBody.contains("grace period", ignoreCase = true) || errorBody.contains("eligible", ignoreCase = true)) {
                                                statusDialogMessage = "Backend Status: 15-Day Grace Period is currently active. The automated task will provision all student repositories when the 15 days conclude."
                                            } else if (errorBody.isNotBlank()) {
                                                statusDialogMessage = "Backend response: $errorBody"
                                            } else {
                                                statusDialogMessage = "Manual trigger request processed."
                                            }
                                        }
                                        provisioningCohortId = null
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BorderColor)

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Done", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CohortProvisionCard(
    cohort: CohortDto,
    isProvisioning: Boolean,
    onTriggerProvision: () -> Unit
) {
    val status = cohort.status?.uppercase() ?: "DRAFT"
    val isTraining = status == "TRAINING"
    val daysRemaining = getGraceDaysRemaining(cohort.trainingStartedAt)
    val isEligible = cohort.canProvisionGithubRepositories || (isTraining && daysRemaining <= 0)
    val hasBeenProvisioned = !cohort.githubRepositoriesLastProvisionedAt.isNullOrBlank()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isEligible) PurplePrimary.copy(0.5f) else BorderColor),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${cohort.code ?: cohort.name}",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate
                    )
                    Text(
                        text = cohort.courseName ?: "Specialization Cohort",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (status) {
                        "TRAINING" -> PurpleSoft
                        "ACTIVE" -> GreenSoft
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = status,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (status) {
                            "TRAINING" -> PurplePrimary
                            "ACTIVE" -> GreenActive
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(modifier = Modifier.height(10.dp))

            // Grace Period / Training Timeline
            if (isTraining) {
                if (isEligible) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GreenSoft,
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = GreenActive, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "15-day grace period complete! Ready to provision.",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenActive
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AmberSoft,
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassTop, null, tint = AmberGrace, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$daysRemaining days remaining until 15-day auto-provisioning.",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberGrace
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Cohort is in $status stage. 15-day grace countdown begins once switched to TRAINING.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hasBeenProvisioned) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Last provisioned: ${formatDateDisplay(cohort.githubRepositoriesLastProvisionedAt)}",
                    fontSize = 11.sp,
                    color = Color(0xFF059669),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button - Always available for manual trigger
            Button(
                onClick = onTriggerProvision,
                enabled = !isProvisioning,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isProvisioning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Provisioning Repositories...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEligible) "⚡ Provision Repositories" else "⚡ Manual Trigger Repo Creation",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

private fun getGraceDaysRemaining(trainingStartedAt: String?): Long {
    if (trainingStartedAt.isNullOrBlank()) return 15
    return runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val started = format.parse(trainingStartedAt.take(10)) ?: return 15
        val diffMs = System.currentTimeMillis() - started.time
        val daysElapsed = TimeUnit.MILLISECONDS.toDays(diffMs)
        val remaining = 15 - daysElapsed
        if (remaining < 0) 0 else remaining
    }.getOrDefault(15)
}

private fun formatDateDisplay(value: String?): String {
    if (value.isNullOrBlank()) return "Never"
    return runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val output = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.US)
        output.format(requireNotNull(input.parse(value.take(19))))
    }.getOrDefault(value.take(16))
}
