package com.example.suretouchapp.ui.screens.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClassScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = com.example.suretouchapp.data.api.TokenManager(context)
        val accountSession = intent.getStringExtra(EXTRA_ACCOUNT_SESSION) ?: return
        if (!manager.isLoggedIn() || !manager.isCurrentSession(accountSession)) return
        val title = intent.getStringExtra(EXTRA_SESSION_TITLE) ?: "Live Class"
        val startTime = intent.getStringExtra(EXTRA_START_TIME) ?: "Soon"
        val meetingLink = intent.getStringExtra(EXTRA_MEETING_LINK)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: System.currentTimeMillis().toString()

        manager.withCurrentSession(accountSession) { SureProEdNotificationManager.showUpcomingClassReminder(
            context = context,
            sessionId = sessionId,
            title = title,
            startTime = startTime,
            meetingLink = meetingLink
        ) }
    }

    companion object {
        const val EXTRA_ACCOUNT_SESSION = "extra_account_session"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_SESSION_TITLE = "extra_session_title"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_MEETING_LINK = "extra_meeting_link"
    }
}
