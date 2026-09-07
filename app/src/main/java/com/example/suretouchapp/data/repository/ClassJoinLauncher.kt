package com.example.suretouchapp.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AttendanceDto

object ClassJoinLauncher {
    suspend fun join(context: Context, manager: TokenManager, session: AttendanceDto?) {
        if (session == null || TimetableSessionPolicy.resolveStatus(session) != TimetableClassStatus.ONGOING) {
            Toast.makeText(context, "Joining opens 10 minutes before class. Refresh the timetable if the class changed.", Toast.LENGTH_LONG).show()
            return
        }
        val accountSession = manager.getSessionId()
        try {
            val uri = Uri.parse(session.meetingLink.orEmpty())
            check(uri.scheme == "https" && uri.host == "meet.google.com")
            if (manager.getUserRole().equals("STUDENT", true)) {
                val response = ApiClient.getService(manager).recordPortalJoin(session.id)
                check(response.isSuccessful)
            }
            manager.requireCurrentSession(accountSession)
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to join. Refresh the class and try again.", Toast.LENGTH_LONG).show()
        }
    }
}
