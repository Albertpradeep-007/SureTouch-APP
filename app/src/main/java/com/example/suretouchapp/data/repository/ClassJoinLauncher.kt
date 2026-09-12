package com.example.suretouchapp.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AttendanceDto

object ClassJoinLauncher {
    fun normalizeMeetingUri(rawLink: String?): Uri? {
        if (rawLink.isNullOrBlank()) return null
        var clean = rawLink.trim()
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            clean = "https://$clean"
        }
        return runCatching { Uri.parse(clean) }.getOrNull()
    }

    suspend fun join(context: Context, manager: TokenManager, session: AttendanceDto?) {
        if (session == null) {
            Toast.makeText(context, "No class session selected.", Toast.LENGTH_SHORT).show()
            return
        }

        // Try to fetch latest session details, but gracefully fall back to local session if network fails
        val latestSession = runCatching {
            val api = ApiClient.getService(manager)
            api.getAttendanceById(session.id).takeIf { it.isSuccessful }?.body()
        }.getOrNull() ?: session

        val rawMeetingLink = latestSession.meetingLink?.takeIf(String::isNotBlank)
            ?: session.meetingLink?.takeIf(String::isNotBlank)

        if (rawMeetingLink.isNullOrBlank()) {
            Toast.makeText(context, "Meeting link has not been published for this session yet.", Toast.LENGTH_LONG).show()
            return
        }

        val uri = normalizeMeetingUri(rawMeetingLink)
        if (uri == null || uri.host.isNullOrBlank()) {
            Toast.makeText(context, "Invalid meeting link format: $rawMeetingLink", Toast.LENGTH_LONG).show()
            return
        }

        // Attendance telemetry: best-effort join recording (must NEVER block launching the meeting)
        try {
            if (manager.getUserRole().equals("STUDENT", true)) {
                val api = ApiClient.getService(manager)
                runCatching {
                    api.recordPortalJoin(latestSession.id)
                }
            }
        } catch (_: Exception) {
            // Non-blocking telemetry
        }

        // Launch meeting with priority 1: ACTION_VIEW with NEW_TASK
        val primaryIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val launched = runCatching {
            context.startActivity(primaryIntent)
            true
        }.getOrElse {
            // Priority 2: Fallback to browser intent
            runCatching {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                true
            }.getOrElse { false }
        }

        if (!launched) {
            // Priority 3: Copy to clipboard and inform user
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Google Meet Link", uri.toString()))
                Toast.makeText(context, "Link copied to clipboard! Open in browser: ${uri.toString().take(35)}...", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "Unable to launch Google Meet. Please install Chrome or Google Meet.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
