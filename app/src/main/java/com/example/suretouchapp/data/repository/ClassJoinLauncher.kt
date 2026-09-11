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
        if (session == null || !ClassSchedulePolicy.canJoin(session)) {
            Toast.makeText(context, "Joining opens 15 minutes before class. Refresh the timetable if the class changed.", Toast.LENGTH_LONG).show()
            return
        }
        val accountSession = manager.getSessionId()
        try {
            val api = ApiClient.getService(manager)
            val latest = api.getAttendanceById(session.id).takeIf { it.isSuccessful }?.body()
                ?: error("Class could not be refreshed")
            manager.requireCurrentSession(accountSession)
            check(ClassSchedulePolicy.canJoin(latest))
            val uri = Uri.parse(latest.meetingLink.orEmpty())
            check(uri.scheme == "https" && uri.host == "meet.google.com")
            if (manager.getUserRole().equals("STUDENT", true)) {
                val response = api.recordPortalJoin(latest.id)
                check(response.isSuccessful)
            }
            manager.requireCurrentSession(accountSession)
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to join. Refresh the class and try again.", Toast.LENGTH_LONG).show()
        }
    }
}
