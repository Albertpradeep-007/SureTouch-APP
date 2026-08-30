package com.example.suretouchapp.ui.screens.certificates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.NetworkUtils
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.CertificateDto
import com.example.suretouchapp.ui.components.BackendConnectionGate
import com.example.suretouchapp.ui.components.SureTrustLoadingIndicator
import java.util.Locale

private val CertificateHeader = Color(0xFF262626)
private val CertificateCanvas = Color(0xFFFAFAFA)
private val CertificatePurple = Color(0xFF6821A8)
private val CertificatePurpleLight = Color(0xFFF3E8FF)
private val CertificateText = Color(0xFF1E293B)
private val CertificateSubtext = Color(0xFF475569)
private val CertificateBorder = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificatesScreen(tokenManager: TokenManager, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var certificates by remember { mutableStateOf<List<CertificateDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val cohortCode = tokenManager.getCohortCode().ifBlank { null }

    LaunchedEffect(refreshKey) {
        isLoading = true
        connectionError = null
        errorTitle = null
        try {
            val res = ApiClient.getService(tokenManager).getCertificates()
            if (res.isSuccessful) {
                certificates = res.body()?.results.orEmpty()
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

    BackendConnectionGate(
        isLoading = isLoading,
        isConnected = isConnected,
        hasData = hasLoadedOnce,
        isOffline = isOffline,
        errorTitle = errorTitle,
        errorMessage = connectionError,
        loadingMessage = "Connecting to SURE Trust Certificate Registry...",
        onRetry = { refreshKey += 1 },
        onLogout = null
    ) {
        Scaffold(
            topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Official Certificates", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey += 1 }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, "Refresh certificates", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CertificateHeader)
            )
        },
        containerColor = CertificateCanvas
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Image(
                painter = painterResource(com.example.suretouchapp.R.drawable.sure_trust_official_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(280.dp).align(Alignment.Center).graphicsLayer { alpha = 0.08f }
            )

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SureTrustLoadingIndicator(message = "Loading certificates")
                }
                certificates.isEmpty() -> CertificateEmptyState(cohortCode = cohortCode, onBack = onBack)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("${certificates.size} certificate${if (certificates.size == 1) "" else "s"} issued",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CertificateSubtext)
                    }
                    items(certificates, key = { it.id }) { certificate ->
                        BackendCertificateCard(certificate)
                    }
                }
            }
        }
    }
}
}

@Composable
private fun CertificateEmptyState(cohortCode: String?, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CertificateBorder),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(CertificatePurpleLight), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = CertificatePurple, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    if (cohortCode == null) "Certificates are not available yet" else "No certificate issued yet",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CertificateText,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (cohortCode == null) {
                        "Certificates become available after student verification, cohort assignment, and completion of programme requirements."
                    } else {
                        "An official certificate will appear here when the backend confirms all course, attendance, assessment, and project requirements."
                    },
                    fontSize = 13.sp,
                    color = CertificateSubtext,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(18.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = CertificatePurpleLight) {
                    Text(
                        cohortCode?.let { "COHORT $it • CERTIFICATE PENDING" } ?: "NO COHORT ASSIGNED",
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = CertificatePurple
                    )
                }
                Spacer(Modifier.height(22.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CertificatePurple)) {
                    Text("Back to Dashboard", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BackendCertificateCard(certificate: CertificateDto) {
    val status = certificate.status?.uppercase(Locale.US) ?: "ISSUED"
    val valid = status !in setOf("REVOKED", "CANCELLED")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CertificateBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(CertificatePurpleLight), contentAlignment = Alignment.Center) {
                    Icon(if (valid) Icons.Default.Verified else Icons.Default.WorkspacePremium, null, tint = CertificatePurple, modifier = Modifier.size(27.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        certificate.certificateType?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase() }
                            ?: "SURE ProEd Certificate",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CertificateText
                    )
                    Text(certificate.certificateNumber ?: "Certificate record", fontSize = 11.5.sp, color = CertificateSubtext)
                }
                Surface(shape = CircleShape, color = if (valid) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)) {
                    Text(status, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (valid) Color(0xFF15803D) else Color(0xFFB91C1C))
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CertificateBorder)
            Spacer(Modifier.height(10.dp))
            Text("Issued: ${certificate.issuedAt?.take(10) ?: "Pending backend date"}", fontSize = 12.sp, color = CertificateSubtext)
            certificate.verificationCode?.takeIf { it.isNotBlank() }?.let {
                Text("Verification code: $it", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CertificatePurple)
            }
            certificate.revocationReason?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = Color(0xFFB91C1C))
            }
        }
    }
}
