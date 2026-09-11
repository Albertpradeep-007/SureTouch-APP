package com.example.suretouchapp.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.net.URL
import java.net.URLEncoder
import com.example.suretouchapp.ui.theme.sureSemanticColors
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private data class DocumentLoadResult(
    val pages: List<Bitmap>,
    val targetFile: File,
    val isWord: Boolean,
    val sizeBytes: Long
)

/**
 * High-fidelity in-app document and PDF viewer.
 * Uses native Android PdfRenderer for offline rendering and high-resolution display.
 * Also supports Microsoft Word (.docx/.doc) document inspection and external app launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppDocumentViewerDialog(
    documentUrl: String,
    documentTitle: String = "Student_Resume_CV.pdf",
    onDismiss: () -> Unit
) {
    val semanticColors = sureSemanticColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { com.example.suretouchapp.data.api.TokenManager(context) }

    val accountSession = remember { tokenManager.getSessionId() }
    val loadedFiles = remember { java.util.concurrent.ConcurrentLinkedQueue<File>() }
    var loadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(documentUrl, accountSession) {
        onDispose { loadJob?.cancel(); loadedFiles.forEach { it.delete() }; loadedFiles.clear() }
    }
    var isLoading by remember(documentUrl) { mutableStateOf(true) }
    var errorMessage by remember(documentUrl) { mutableStateOf<String?>(null) }
    var renderedBitmaps by remember(documentUrl) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isWordDoc by remember(documentUrl) { mutableStateOf(false) }
    var savedTargetFile by remember(documentUrl) { mutableStateOf<File?>(null) }
    var fileSizeBytes by remember(documentUrl) { mutableStateOf(0L) }

    fun loadDocument() {
        loadJob?.cancel()
        loadJob = scope.launch {
            isLoading = true
            errorMessage = null
            renderedBitmaps = emptyList()
            isWordDoc = false
            savedTargetFile = null
            fileSizeBytes = 0L

            try {
                val result = withContext(Dispatchers.IO) {
                    val resolvedUrl = com.example.suretouchapp.data.api.ApiClient.resolveServerUrl(documentUrl)
                    check(com.example.suretouchapp.data.repository.DocumentPolicy.trustedUrl(resolvedUrl)) { "Unsupported document location." }
                    tokenManager.requireCurrentSession(accountSession)
                    val response = com.example.suretouchapp.data.api.ApiClient.getService(tokenManager).downloadDocument(resolvedUrl)
                    if (!response.isSuccessful) {
                        response.errorBody()?.close()
                        throw java.io.IOException("The document is unavailable or access has expired. Refresh your profile.")
                    }
                    val bytes = response.body()?.use { body ->
                        body.byteStream().use { com.example.suretouchapp.data.repository.DocumentPolicy.readBounded(it) }
                    } ?: throw java.io.IOException("The server returned an empty document.")
                    val isWordByName = !bytes.take(5).toByteArray().toString(Charsets.US_ASCII).startsWith("%PDF")
                    coroutineContext.ensureActive()
                    val targetFile = tokenManager.withCurrentSession(accountSession) {
                        val directory = File(context.cacheDir, "private_documents").apply { mkdirs() }
                        File.createTempFile("document_", if (isWordByName) ".docx" else ".pdf", directory).also {
                            it.writeBytes(bytes)
                            loadedFiles.add(it)
                        }
                    }

                    // 3. Inspect document format
                    val isPdf = try {
                        targetFile.inputStream().use { stream ->
                            val header = ByteArray(5)
                            val read = stream.read(header)
                            read >= 4 && String(header, 0, read).startsWith("%PDF")
                        }
                    } catch (_: Exception) {
                        false
                    }

                    val isWord = !isPdf && runCatching {
                        java.util.zip.ZipFile(targetFile).use { zip ->
                            zip.getEntry("[Content_Types].xml") != null && zip.getEntry("word/document.xml") != null
                        }
                    }.getOrDefault(false)
                    check(isPdf || isWord) { "The server did not return a valid PDF or DOCX document." }

                    if (isWord) {
                        return@withContext DocumentLoadResult(
                            pages = emptyList(),
                            targetFile = targetFile,
                            isWord = true,
                            sizeBytes = targetFile.length()
                        )
                    }

                    // 4. Render PDF pages with Android PdfRenderer
                    val pages = mutableListOf<Bitmap>()
                    ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            check(renderer.pageCount in 1..10) { "Document has too many pages to preview." }
                            for (i in 0 until renderer.pageCount) {
                                coroutineContext.ensureActive()
                                renderer.openPage(i).use { page ->
                                    val scale = minOf(2f, 2048f / maxOf(page.width, page.height, 1))
                                    val bitmap = Bitmap.createBitmap(
                                        (page.width * scale).toInt().coerceAtLeast(1),
                                        (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
                                    )
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    pages.add(bitmap)
                                }
                            }
                        }
                    }

                    DocumentLoadResult(
                        pages = pages,
                        targetFile = targetFile,
                        isWord = false,
                        sizeBytes = targetFile.length()
                    )
                }

                tokenManager.withCurrentSession(accountSession) {
                    savedTargetFile = result.targetFile
                    renderedBitmaps = result.pages
                    isWordDoc = result.isWord
                    fileSizeBytes = result.sizeBytes
                    isLoading = false
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Unable to render resume"
                isLoading = false
            }
        }
    }

    LaunchedEffect(documentUrl, accountSession) {
        loadDocument()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top Navigation Bar ──
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isWordDoc) Color(0xFF2563EB).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isWordDoc) Icons.Default.Description else Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = if (isWordDoc) Color(0xFF2563EB) else Color(0xFFDC2626),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = documentTitle.ifBlank { if (isWordDoc) "Student_Resume_CV.docx" else "Student_Resume_CV.pdf" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Verified,
                                    null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        renderedBitmaps.isNotEmpty() -> "In-App Viewer • ${renderedBitmaps.size} Page${if (renderedBitmaps.size > 1) "s" else ""}"
                                        isWordDoc -> "Microsoft Word Document"
                                        else -> "Uploaded document"
                                    },
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // External Open Action
                        IconButton(
                            enabled = savedTargetFile != null && errorMessage == null && !isLoading,
                            onClick = {
                                try {
                                    tokenManager.requireCurrentSession(accountSession)
                                    if (savedTargetFile != null && savedTargetFile!!.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            savedTargetFile!!
                                        )
                                        val mime = if (isWordDoc) "application/vnd.openxmlformats-officedocument.wordprocessingml.document" else "application/pdf"
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mime)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Open Resume"))
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(documentUrl))
                                        context.startActivity(intent)
                                    }
                                } catch (e: Exception) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, documentUrl)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Resume"))
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in External App",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Close Action
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Resume",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // ── Document Content Area ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Loading Resume...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Retrieving verified candidate document",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        renderedBitmaps.isNotEmpty() -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(renderedBitmaps) { index, bitmap ->
                                    Card(
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Page ${index + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "SURE Trust Verified Candidate CV",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Page ${index + 1} of ${renderedBitmaps.size}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        isWordDoc && savedTargetFile != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color(0xFFEFF6FF),
                                            modifier = Modifier.size(68.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = Color(0xFF2563EB),
                                                    modifier = Modifier.size(38.dp)
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = documentTitle.ifBlank { "Student_Resume_CV.docx" },
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFDCFCE7)
                                            ) {
                                                Text(
                                                    text = "Microsoft Word Document • ${if (fileSizeBytes > 0) "${fileSizeBytes / 1024} KB" else "Ready"}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF16A34A),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "This resume is formatted as a Word document (.docx). You can open it in Microsoft Word, Google Docs, WPS Office, or share it.",
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 18.sp
                                        )

                                        Button(
                                            onClick = {
                                                try {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.provider",
                                                        savedTargetFile!!
                                                    )
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Open Resume With"))
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "Could not open document: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Open in Word / Google Docs", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.provider",
                                                        savedTargetFile!!
                                                    )
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share Resume"))
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "Could not share document: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().height(44.dp)
                                        ) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Share Word Document", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "Unable to Render Resume",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = errorMessage ?: "The resume document could not be retrieved from the server.",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { loadDocument() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Retry")
                                    }

                                    if (documentUrl.startsWith("http://", ignoreCase = true) || documentUrl.startsWith("https://", ignoreCase = true)) {
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(documentUrl))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "Could not open browser: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Open in Browser")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Bottom Action Bar ──
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = semanticColors.success,
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Verified Student Document • Ready for Review",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = semanticColors.success
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}
