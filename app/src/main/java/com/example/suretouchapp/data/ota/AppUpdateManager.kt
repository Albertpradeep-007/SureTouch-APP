package com.example.suretouchapp.data.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.suretouchapp.BuildConfig
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.model.AppVersionInfoDto
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
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
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val info: AppVersionInfoDto) : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(
        val progress: Float,
        val bytesRead: Long,
        val totalBytes: Long,
        val info: AppVersionInfoDto? = null
    ) : UpdateState
    data class ReadyToInstall(
        val file: File,
        val info: AppVersionInfoDto,
        val installMessage: String? = null
    ) : UpdateState
    data class Error(val message: String, val info: AppVersionInfoDto? = null) : UpdateState
}

private fun UpdateState.releaseInfo(): AppVersionInfoDto? = when (this) {
    is UpdateState.UpdateAvailable -> info
    is UpdateState.Downloading -> info
    is UpdateState.ReadyToInstall -> info
    is UpdateState.Error -> info
    else -> null
}

object AppUpdateManager {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var userDismissedVersionCode: Int = -1
    private val updateCheckMutex = Mutex()
    private val downloadMutex = Mutex()
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
        get() = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V")

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
            val previousState = _updateState.value
            if (previousState is UpdateState.Downloading || previousState is UpdateState.ReadyToInstall) {
                return@withLock previousState
            }
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
                } else if (info == null && previousState.releaseInfo()?.isMandatory == true) {
                    // A temporary network failure must not dismiss a known required update.
                    previousState
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
        if (!downloadMutex.tryLock()) return@withContext
        var destinationFile: File? = null
        try {
            if (info.downloadUrl.isBlank()) {
                throw IOException("The update download URL is missing. Please retry later.")
            }

            val resolvedUrl = ApiClient.resolveServerUrl(info.downloadUrl)
            val request = Request.Builder().url(resolvedUrl).build()

            if (!request.url.isHttps) {
                throw IOException("The update download must use a secure HTTPS connection.")
            }
            _updateState.value = UpdateState.Downloading(0f, 0L, info.fileSizeBytes, info)

            val destinationDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(destinationDir, "suretrust_v${info.versionCode}.apk")
            destinationFile = apkFile

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed with HTTP ${response.code}. Please retry.")
                }

                if (!response.request.url.isHttps) {
                    throw IOException("The update download redirected to an insecure connection.")
                }
                val body = response.body ?: throw IOException("The update response is empty. Please retry.")

                val responseBytes = body.contentLength()
                val totalBytes = if (info.fileSizeBytes > 0) info.fileSizeBytes else responseBytes
                var bytesReadTotal = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var lastReportedPercent = -1

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesReadTotal += read
                            if (info.fileSizeBytes > 0 && bytesReadTotal > info.fileSizeBytes) {
                                throw IOException("The update exceeds its published size. Please retry.")
                            }

                            val progress = if (totalBytes > 0) (bytesReadTotal.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                            val currentPercent = (progress * 100).toInt()

                            if (currentPercent != lastReportedPercent) {
                                lastReportedPercent = currentPercent
                                _updateState.value = UpdateState.Downloading(progress, bytesReadTotal, totalBytes, info)
                            }
                        }
                        output.flush()
                    }
                }
                downloadedSizeError(info.fileSizeBytes, apkFile.length(), responseBytes)?.let {
                    throw IOException(it)
                }
            }

            validateDownloadedApk(context, apkFile, info)
            _updateState.value = UpdateState.ReadyToInstall(apkFile, info)
            // Attempt installation automatically
            withContext(Dispatchers.Main) {
                installApk(context, apkFile, info)
            }
        } catch (e: CancellationException) {
            destinationFile?.delete()
            _updateState.value = UpdateState.Error("The update download was interrupted. Please retry.", info)
            throw e
        } catch (e: Exception) {
            destinationFile?.delete()
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Failed to download update. Please retry.", info)
        } finally {
            downloadMutex.unlock()
        }
    }

    @Suppress("DEPRECATION")
    private fun validateDownloadedApk(context: Context, file: File, info: AppVersionInfoDto) {
        downloadedSizeError(info.fileSizeBytes, file.length())?.let { throw IOException(it) }
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        val archiveVersion = archive?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
        }
        downloadedPackageError(
            expectedPackage = context.packageName,
            actualPackage = archive?.packageName,
            installedVersion = currentVersionCode.toLong(),
            expectedVersion = info.versionCode.toLong(),
            actualVersion = archiveVersion
        )?.let { throw IOException(it) }
    }

    /**
     * Prompts the Android OS package installer to install the downloaded APK.
     */
    fun installApk(context: Context, file: File, info: AppVersionInfoDto) {
        try {
            validateDownloadedApk(context, file, info)
        } catch (e: Exception) {
            file.delete()
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "The update file is invalid. Please download it again.", info)
            return
        }

        try {
            // Android 8.0+ check for unknown app sources
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    _updateState.value = UpdateState.ReadyToInstall(
                        file, info, "Allow updates from SURE ProEd in Settings, then return and tap INSTALL UPDATE."
                    )
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            _updateState.value = UpdateState.ReadyToInstall(file, info)
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
            _updateState.value = UpdateState.ReadyToInstall(
                file, info, "Unable to open the installer. Tap INSTALL UPDATE to try again."
            )
        }
    }

    fun dismissUpdate(versionCode: Int? = null) {
        val state = _updateState.value
        if (!canDismissUpdate(state.releaseInfo()?.isMandatory == true, state is UpdateState.Downloading)) return
        val dismissedVersion = versionCode ?: state.releaseInfo()?.versionCode
        if (dismissedVersion != null) {
            userDismissedVersionCode = dismissedVersion
        }
        _updateState.value = UpdateState.Idle
    }
}
