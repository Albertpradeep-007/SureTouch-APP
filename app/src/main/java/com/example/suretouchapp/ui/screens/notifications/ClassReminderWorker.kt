package com.example.suretouchapp.ui.screens.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.repository.isCancelledSession

/** Alarm extras are hints only: never display cached account or class content. */
class ClassReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val account = inputData.getString(ClassScheduleAlarmReceiver.EXTRA_ACCOUNT_SESSION) ?: return Result.success()
        val id = inputData.getString(ClassScheduleAlarmReceiver.EXTRA_SESSION_ID) ?: return Result.success()
        val tokens = TokenManager(applicationContext)
        if (!tokens.isLoggedIn() || !tokens.isCurrentSession(account)) return Result.success()
        return try {
            val response = ApiClient.getService(tokens).getAttendanceById(id)
            if (!response.isSuccessful) {
                if (response.code() >= 500 && runAttemptCount < 3) Result.retry() else Result.success()
            } else {
                val session = response.body() ?: return Result.success()
                val start = SureProEdNotificationManager.parseClassStartTimeMillis(session.date, session.startTime)
                if (!session.isCancelledSession() && start != null && start - System.currentTimeMillis() in 0..600_000L) {
                    tokens.withCurrentSession(account) {
                        SureProEdNotificationManager.showUpcomingClassReminder(applicationContext, id,
                            session.sessionTitle ?: "Live Class", session.startTime ?: "Soon", session.meetingLink)
                    }
                }
                Result.success()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (tokens.isCurrentSession(account) && runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
