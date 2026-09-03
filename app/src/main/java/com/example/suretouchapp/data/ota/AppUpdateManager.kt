package com.example.suretouchapp.data.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.suretouchapp.BuildConfig
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.model.AppVersionInfoDto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val info: AppVersionInfoDto) : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val progress: Float, val bytesRead: Long, val totalBytes: Long) : UpdateState
    data class ReadyToInstall(val file: File, val info: AppVersionInfoDto) : UpdateState
    data class Error(val message: String) : UpdateState
}

object AppUpdateManager {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var userDismissedVersionCode: Int = -1
    private val updateCheckMutex = Mutex()
    private var lastUpdateCheckAtMillis: Long = 0L

    private val versionCheckClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(4, 3, TimeUnit.MINUTES))
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(4, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    val currentVersionCode: Int
        get() = BuildConfig.VERSION_CODE

    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    /**
     * Checks if a new version is available on the remote server.
     * The public version endpoint deliberately bypasses the authenticated API client so an
     * expired login token cannot add a token-refresh timeout to this startup-only request.
     */
    suspend fun checkForUpdates(
        fallbackVersionUrl: String? = null,
        force: Boolean = false
    ): UpdateState = withContext(Dispatchers.IO) {
        updateCheckMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            if (!force && lastUpdateCheckAtMillis > 0L && now - lastUpdateCheckAtMillis < 5 * 60_000L) {
                return@withLock _updateState.value
            }

            _updateState.value = UpdateState.Checking
            val primaryUrl = ApiClient.resolveServerUrl("app/version-check/")
            val info = fetchVersionInfo(primaryUrl)
                ?: fallbackVersionUrl?.takeIf(String::isNotBlank)?.let(::fetchVersionInfo)

            val state = try {
                if (info != null && info.versionCode > currentVersionCode) {
                    if (!info.isMandatory && info.versionCode == userDismissedVersionCode) {
                        UpdateState.UpToDate
                    } else {
                        UpdateState.UpdateAvailable(info)
                    }
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                UpdateState.Error(e.localizedMessage ?: "Failed to check for updates")
            }

            lastUpdateCheckAtMillis = SystemClock.elapsedRealtime()
            _updateState.value = state
            state
        }
    }

    private fun fetchVersionInfo(url: String): AppVersionInfoDto? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        versionCheckClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
                ?.takeIf(String::isNotBlank)
                ?.let { Gson().fromJson(it, AppVersionInfoDto::class.java) }
        }
    }.getOrNull()

    /**
     * Downloads the APK file to the app's cache/downloads directory and tracks progress.
     */
    suspend fun downloadUpdate(
        context: Context,
        info: AppVersionInfoDto
    ) = withContext(Dispatchers.IO) {
        try {
            if (info.downloadUrl.isBlank()) {
                _updateState.value = UpdateState.Error("Download URL is empty")
                return@withContext
            }

            val resolvedUrl = ApiClient.resolveServerUrl(info.downloadUrl)
            val request = Request.Builder().url(resolvedUrl).build()

            _updateState.value = UpdateState.Downloading(0f, 0L, info.fileSizeBytes)

            val destinationDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val destinationFile = File(destinationDir, "suretrust_v${info.versionCode}.apk")

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _updateState.value = UpdateState.Error("Download failed with HTTP ${response.code}")
                    return@withContext
                }

                val body = response.body ?: run {
                    _updateState.value = UpdateState.Error("Response body is null")
                    return@withContext
                }

                val totalBytes = if (body.contentLength() > 0) body.contentLength() else info.fileSizeBytes
                var bytesReadTotal = 0L

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var lastReportedPercent = -1

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesReadTotal += read

                            val progress = if (totalBytes > 0) (bytesReadTotal.toFloat() / totalBytes) else 0f
                            val currentPercent = (progress * 100).toInt()

                            if (currentPercent != lastReportedPercent) {
                                lastReportedPercent = currentPercent
                                _updateState.value = UpdateState.Downloading(progress, bytesReadTotal, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
            }

            _updateState.value = UpdateState.ReadyToInstall(destinationFile, info)
            // Attempt installation automatically
            withContext(Dispatchers.Main) {
                installApk(context, destinationFile)
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Failed to download update")
        }
    }

    /**
     * Prompts the Android OS package installer to install the downloaded APK.
     */
    fun installApk(context: Context, file: File) {
        try {
            if (!file.exists()) {
                _updateState.value = UpdateState.Error("APK file not found")
                return
            }

            // Android 8.0+ check for unknown app sources
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Unable to start installation: ${e.localizedMessage}")
        }
    }

    fun dismissUpdate(versionCode: Int? = null) {
        if (versionCode != null) {
            userDismissedVersionCode = versionCode
        }
        _updateState.value = UpdateState.Idle
    }
}
