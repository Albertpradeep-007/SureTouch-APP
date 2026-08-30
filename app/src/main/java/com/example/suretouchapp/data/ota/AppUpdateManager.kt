package com.example.suretouchapp.data.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.suretouchapp.BuildConfig
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AppVersionInfoDto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val currentVersionCode: Int
        get() = BuildConfig.VERSION_CODE

    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    /**
     * Checks if a new version is available on the remote server.
     */
    suspend fun checkForUpdates(
        context: Context,
        tokenManager: TokenManager,
        fallbackVersionUrl: String? = null
    ): UpdateState = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        try {
            var info: AppVersionInfoDto? = null

            // 1. Try backend API endpoint first
            try {
                val response = ApiClient.getService(tokenManager).checkAppVersion()
                if (response.isSuccessful && response.body() != null) {
                    info = response.body()
                }
            } catch (_: Exception) {
                // If backend endpoint is not yet configured, check fallback static version URL if provided
            }

            // 2. Fallback check from static version endpoint / CDN if primary API didn't succeed
            if (info == null && !fallbackVersionUrl.isNullOrBlank()) {
                try {
                    val request = Request.Builder().url(fallbackVersionUrl).build()
                    httpClient.newCall(request).execute().use { res ->
                        if (res.isSuccessful) {
                            val bodyString = res.body?.string()
                            if (!bodyString.isNullOrBlank()) {
                                info = Gson().fromJson(bodyString, AppVersionInfoDto::class.java)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val targetInfo = info
            if (targetInfo != null) {
                if (targetInfo.versionCode > currentVersionCode) {
                    if (!targetInfo.isMandatory && targetInfo.versionCode == userDismissedVersionCode) {
                        val state = UpdateState.UpToDate
                        _updateState.value = state
                        return@withContext state
                    }
                    val state = UpdateState.UpdateAvailable(targetInfo)
                    _updateState.value = state
                    return@withContext state
                } else {
                    val state = UpdateState.UpToDate
                    _updateState.value = state
                    return@withContext state
                }
            }

            val state = UpdateState.UpToDate
            _updateState.value = state
            state
        } catch (e: Exception) {
            val state = UpdateState.Error(e.localizedMessage ?: "Failed to check for updates")
            _updateState.value = state
            state
        }
    }

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

            httpClient.newCall(request).execute().use { response ->
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
                        val buffer = ByteArray(8 * 1024)
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
