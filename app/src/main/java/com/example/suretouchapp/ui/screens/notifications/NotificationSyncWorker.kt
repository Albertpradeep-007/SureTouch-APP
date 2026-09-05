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

        try {
            SureProEdNotificationManager.createChannels(context)
            val api = ApiClient.getService(tokenManager)

            // 1. Fetch & sync announcements
            runCatching {
                val annResp = api.getAnnouncements()
                if (annResp.isSuccessful) {
                    val list = annResp.body()?.results.orEmpty()
                    SureProEdNotificationManager.syncAnnouncements(context, list)
                }
            }

            // 2. Fetch & sync notifications
            runCatching {
                val notifResp = api.getNotifications()
                if (notifResp.isSuccessful) {
                    val list = notifResp.body()?.results.orEmpty()
                    SureProEdNotificationManager.syncUnread(context, list)
                }
            }

            // 3. Fetch & sync attendance / class schedules & exact 15m alarms
            runCatching {
                val attResp = api.getAttendance(pageSize = 500)
                if (attResp.isSuccessful) {
                    val sessions = attResp.body()?.results.orEmpty()
                    SureProEdNotificationManager.syncTimetableAndClasses(context, sessions)
                }
            }

            // 4. Fetch & sync assignments & grades
            runCatching {
                val assignResp = api.getAssignments()
                if (assignResp.isSuccessful) {
                    val assignments = assignResp.body()?.results.orEmpty()
                    SureProEdNotificationManager.syncAssignments(context, assignments)

                    val subResp = api.getSubmissions()
                    if (subResp.isSuccessful) {
                        val submissions = subResp.body()?.results.orEmpty()
                        SureProEdNotificationManager.syncSubmissionsAndGrades(context, submissions, assignments)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
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
