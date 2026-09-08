package com.example.suretouchapp.ui.screens.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class NotificationSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tokenManager = TokenManager(context)
        if (!tokenManager.isLoggedIn()) {
            return@withContext Result.success()
        }
        val accountSession = tokenManager.getSessionId()

        try {
            SureProEdNotificationManager.createChannels(context)
            val api = ApiClient.getService(tokenManager)

            runCatching { MobilePushRegistration.register(context, tokenManager, accountSession) }

            val list = com.example.suretouchapp.data.repository.NotificationRepository(tokenManager).load()
            tokenManager.withCurrentSession(accountSession) {
                SureProEdNotificationManager.syncUnread(context, list, completeSnapshot = true)
            }
            val attendance = api.getAttendance(pageSize = 500)
            if (attendance.isSuccessful) {
                tokenManager.withCurrentSession(accountSession) {
                    SureProEdNotificationManager.syncTimetableAndClasses(context, attendance.body()?.results.orEmpty())
                }
            }

            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!tokenManager.isCurrentSession(accountSession) || runAttemptCount >= 4) Result.success() else Result.retry()
        }
    }

    companion object {
        const val TAG_PERIODIC_SYNC = "sure_proed_periodic_notification_sync"
        const val TAG_ONE_TIME_SYNC = "sure_proed_onetime_notification_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<NotificationSyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<NotificationSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                TAG_ONE_TIME_SYNC,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }
}
