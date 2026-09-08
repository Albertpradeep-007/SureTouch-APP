package com.example.suretouchapp.ui.screens.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClassScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = com.example.suretouchapp.data.api.TokenManager(context)
        val accountSession = intent.getStringExtra(EXTRA_ACCOUNT_SESSION) ?: return
        if (!manager.isLoggedIn() || !manager.isCurrentSession(accountSession)) return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val work = androidx.work.OneTimeWorkRequestBuilder<ClassReminderWorker>()
            .setInputData(androidx.work.workDataOf(EXTRA_ACCOUNT_SESSION to accountSession, EXTRA_SESSION_ID to sessionId))
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "class-reminder-$accountSession-$sessionId", androidx.work.ExistingWorkPolicy.REPLACE, work
        )
    }

    companion object {
        const val EXTRA_ACCOUNT_SESSION = "extra_account_session"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_SESSION_TITLE = "extra_session_title"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_MEETING_LINK = "extra_meeting_link"
    }
}
