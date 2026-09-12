package com.example.suretouchapp.ui.screens.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.suretouchapp.MainActivity
import com.example.suretouchapp.R
import com.example.suretouchapp.data.model.AnnouncementDto
import com.example.suretouchapp.data.model.AssignmentDto
import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.model.NotificationDto
import com.example.suretouchapp.data.model.SubmissionDto
import com.example.suretouchapp.data.repository.ClassSchedulePolicy
import com.example.suretouchapp.data.repository.isCancelledSession
import com.example.suretouchapp.data.repository.latestNotifications
import com.example.suretouchapp.data.repository.notificationVersion
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

object SureProEdNotificationManager {
    const val CHANNEL_ANNOUNCEMENTS = "sure_proed_announcements_v2"
    const val CHANNEL_CLASS_REMINDERS = "sure_proed_class_reminders_v2"
    const val CHANNEL_ACADEMIC = "sure_proed_academic_v2"
    const val CHANNEL_LEARNING = "sure_proed_learning_v2"
    const val CHANNEL_COMMUNITY = "sure_proed_community_v2"
    const val CHANNEL_ACHIEVEMENTS = "sure_proed_achievements_v2"
    
    private const val GROUP_STUDENT_UPDATES = "sure_proed_student_updates"
    private const val PREFS_NAME = "sure_proed_notification_delivery"
    private const val KEY_DELIVERED_IDS = "delivered_ids"
    private const val KEY_DELIVERED_ANNOUNCEMENT_IDS = "delivered_announcement_ids"
    private const val KEY_DELIVERED_ASSIGNMENT_IDS = "delivered_assignment_ids"
    private const val KEY_DELIVERED_GRADE_IDS = "delivered_grade_ids"
    private const val KEY_SCHEDULED_CLASS_IDS = "scheduled_class_ids"
    private const val KEY_15M_REMINDER_IDS = "reminder_15m_class_ids"
    private const val KEY_DELIVERED_CANCELLED_IDS = "delivered_cancelled_ids"
    private const val KEY_DELIVERED_RESCHEDULED_IDS = "delivered_rescheduled_ids"
    private const val KEY_DIRECT_CLASS_PUSH_IDS = "direct_class_push_ids"
    private const val KEY_DIRECT_CLASS_EVENT_KEYS = "direct_class_event_keys"
    private const val KEY_TRAY_ID_PREFIX = "tray_id_"
    private const val PUSH_METRICS_PREFS_NAME = "sure_proed_push_delivery_metrics"
    private const val KEY_PUSH_METRICS = "records"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val vibrationPattern = longArrayOf(0, 300, 200, 300)

        val channels = listOf(
            NotificationChannel(CHANNEL_ANNOUNCEMENTS, "Announcements & Broadcasts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Important organisation announcements, broadcasts, and system circulars"
                setSound(defaultSoundUri, audioAttributes)
                enableVibration(true)
                this.vibrationPattern = vibrationPattern
                enableLights(true)
                lightColor = 0xFF7C3AED.toInt()
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(CHANNEL_CLASS_REMINDERS, "Class Schedule & Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "15-minute upcoming class reminders and newly scheduled live classes with Google Meet links"
                setSound(defaultSoundUri, audioAttributes)
                enableVibration(true)
                this.vibrationPattern = vibrationPattern
                enableLights(true)
                lightColor = 0xFFDC2626.toInt()
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(CHANNEL_ACADEMIC, "Academic and Cohort Updates", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Screening, student verification, cohort assignment, and timetable changes"
                setSound(defaultSoundUri, audioAttributes)
                enableVibration(true)
                this.vibrationPattern = vibrationPattern
                enableLights(true)
                lightColor = 0xFF2563EB.toInt()
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(CHANNEL_LEARNING, "Classes and Assignments", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Live classes, attendance, assignments, module tests, and grades"
                setSound(defaultSoundUri, audioAttributes)
                enableVibration(true)
                this.vibrationPattern = vibrationPattern
                enableLights(true)
                lightColor = 0xFF059669.toInt()
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(CHANNEL_COMMUNITY, "SURE TRUST Activities", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tree plantation, blood donation, life skills, and helping-society activities"
                setSound(defaultSoundUri, audioAttributes)
                enableVibration(true)
            },
            NotificationChannel(CHANNEL_ACHIEVEMENTS, "Certificates and Achievements", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Programme completion, certificates, grades, and student achievements"
                setSound(defaultSoundUri, audioAttributes)
                enableVibration(true)
                this.vibrationPattern = vibrationPattern
                enableLights(true)
                lightColor = 0xFFF59E0B.toInt()
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
        manager.createNotificationChannels(channels)
    }

    fun canPost(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun notifyIfAllowed(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notification delivery.
        }
    }

    fun playNotificationSound(context: Context) {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, soundUri)
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                }
                ringtone.play()
            }
        } catch (_: Exception) {}
    }

    fun dismissNotification(context: Context, notificationId: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(notificationId.hashCode())
            if (prefs.contains(KEY_TRAY_ID_PREFIX + notificationId)) {
                manager.cancel(prefs.getInt(KEY_TRAY_ID_PREFIX + notificationId, notificationId.hashCode()))
                prefs.edit().remove(KEY_TRAY_ID_PREFIX + notificationId).commit()
            }
        } catch (_: Exception) {}
    }

    fun dismissAnnouncement(context: Context, announcementId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(("announcement_" + announcementId).hashCode())
        } catch (_: Exception) {}
    }

    fun dismissAll(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancelAll()
        } catch (_: Exception) {}
    }

    fun syncAnnouncements(context: Context, announcements: List<AnnouncementDto>) {
        // Personal notifications are synchronized from the authoritative API feed.
    }

    fun showAnnouncementNotification(context: Context, announcement: AnnouncementDto) {
        createChannels(context)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val titleText = if (announcement.isPinned) "📌 [PINNED] ${announcement.title}" else "📢 Announcement: ${announcement.title}"
        val messageText = announcement.message.ifBlank { "New official announcement published on SURE Trust." }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_notices", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("announcement_" + announcement.id).hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sure_trust_official_logo)
        val builder = NotificationCompat.Builder(context, CHANNEL_ANNOUNCEMENTS)
            .setSmallIcon(R.drawable.ic_sureproed_notification)
            .setLargeIcon(largeLogo)
            .setColor(0xFF7C3AED.toInt())
            .setContentTitle(titleText)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setSubText("SURE Trust • Announcement")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        notifyIfAllowed(context, ("announcement_" + announcement.id).hashCode(), builder.build())
    }

    fun syncAssignments(context: Context, assignments: List<AssignmentDto>) {
        // Personal notifications are synchronized from the authoritative API feed.
    }

    fun showAssignmentNotification(context: Context, assignment: AssignmentDto) {
        createChannels(context)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val titleText = "📝 New Assignment: ${assignment.title.ifBlank { "Cohort Assignment" }}"
        val dueInfo = if (assignment.dueDate.isNotBlank()) "Due Date: ${assignment.dueDate}" else "Review instructions in portal."
        val messageText = "${assignment.description.ifBlank { "A new assignment has been uploaded to your cohort." }}\n$dueInfo"

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_assignments", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("assignment_" + assignment.id).hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sure_trust_official_logo)
        val builder = NotificationCompat.Builder(context, CHANNEL_LEARNING)
            .setSmallIcon(R.drawable.ic_sureproed_notification)
            .setLargeIcon(largeLogo)
            .setColor(0xFF059669.toInt())
            .setContentTitle(titleText)
            .setContentText(if (assignment.dueDate.isNotBlank()) "Due: ${assignment.dueDate} • Max Marks: ${assignment.maxMarks}" else assignment.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setSubText("SURE ProEd • Assignment")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        notifyIfAllowed(context, ("assignment_" + assignment.id).hashCode(), builder.build())
    }

    fun syncSubmissionsAndGrades(
        context: Context,
        submissions: List<SubmissionDto>,
        assignments: List<AssignmentDto> = emptyList()
    ) {
        // Personal notifications are synchronized from the authoritative API feed.
    }

    fun showGradeNotification(
        context: Context,
        id: String,
        title: String,
        gradeOrScore: String,
        feedback: String
    ) {
        createChannels(context)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val titleText = "🎯 Grade Released: $title"
        val messageText = "Score / Grade: $gradeOrScore\n$feedback"

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_assignments", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("grade_" + id).hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sure_trust_official_logo)
        val builder = NotificationCompat.Builder(context, CHANNEL_ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_sureproed_notification)
            .setLargeIcon(largeLogo)
            .setColor(0xFFF59E0B.toInt())
            .setContentTitle(titleText)
            .setContentText("Score: $gradeOrScore • $feedback")
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setSubText("SURE ProEd • Grades & Performance")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        notifyIfAllowed(context, ("grade_" + id).hashCode(), builder.build())
    }

    fun syncUnread(context: Context, notifications: List<NotificationDto>, completeSnapshot: Boolean = false) {
        val manager = com.example.suretouchapp.data.api.TokenManager(context)
        val session = manager.getSessionId()
        manager.withCurrentSession(session) {
            if (!manager.isLoggedIn()) return@withCurrentSession
            val owner = runCatching {
                val payload = manager.getAccessToken()!!.split('.')[1]
                val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
                org.json.JSONObject(json).getString("user_id")
            }.getOrNull()
            if (owner != null && notifications.any { it.user != owner }) return@withCurrentSession
            syncCurrentUnread(context, notifications, completeSnapshot)
        }
    }

    @Synchronized
    private fun syncCurrentUnread(context: Context, notifications: List<NotificationDto>, completeSnapshot: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val delivered = prefs.getStringSet(KEY_DELIVERED_IDS, emptySet()).orEmpty().toMutableSet()
        val directClassPushes = prefs.getStringSet(KEY_DIRECT_CLASS_PUSH_IDS, emptySet()).orEmpty().toMutableSet()
        val latest = latestNotifications(notifications)
        val editor = prefs.edit()
        val toDismiss = mutableSetOf<String>()
        val toShow = mutableListOf<NotificationDto>()
        if (completeSnapshot) {
            val current = latest.map { it.id }.toSet()
            (delivered - current).forEach { id ->
                toDismiss += id
                editor.remove("version_$id")
                editor.putBoolean("deleted_$id", true)
                delivered.remove(id)
                directClassPushes.remove(id)
            }
        }
        latest.forEach { item ->
            if (prefs.getBoolean("deleted_${item.id}", false) && !completeSnapshot) return@forEach
            val version = notificationVersion(item)
            val previous = prefs.getString("version_${item.id}", null)
            val older = previous != null && runCatching {
                java.time.Instant.parse(version) < java.time.Instant.parse(previous)
            }.getOrDefault(false)
            if (!older) {
                val wasShownDirectly = directClassPushes.remove(item.id)
                if (completeSnapshot) editor.remove("deleted_${item.id}")
                if (item.isRead) toDismiss += item.id
                else if (!wasShownDirectly && canPost(context) && (item.id !in delivered || previous != version)) {
                    toShow += item
                    editor.putInt(KEY_TRAY_ID_PREFIX + item.id, trayNotificationId(item))
                }
                if (item.isRead || canPost(context) || wasShownDirectly) {
                    delivered += item.id
                    editor.putString("version_${item.id}", version)
                }
            }
        }
        editor.putStringSet(KEY_DELIVERED_IDS, delivered)
        editor.putStringSet(KEY_DIRECT_CLASS_PUSH_IDS, directClassPushes)
        // Persist the delivery version before releasing the process-wide lock.
        // This prevents the dashboard, WorkManager and FCM from replaying the
        // same unchanged notification when their refreshes overlap.
        if (!editor.commit()) return
        toDismiss.forEach { dismissNotification(context, it) }
        toShow.forEach { show(context, it) }
    }

    @Synchronized
    fun syncTimetableAndClasses(context: Context, sessions: List<AttendanceDto>) {
        if (!canPost(context) || sessions.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deliveredSchedules = prefs.getStringSet(KEY_SCHEDULED_CLASS_IDS, emptySet()).orEmpty().toMutableSet()
        val delivered15mReminders = prefs.getStringSet(KEY_15M_REMINDER_IDS, emptySet()).orEmpty().toMutableSet()
        val deliveredCancelled = prefs.getStringSet(KEY_DELIVERED_CANCELLED_IDS, emptySet()).orEmpty().toMutableSet()
        val deliveredRescheduled = prefs.getStringSet(KEY_DELIVERED_RESCHEDULED_IDS, emptySet()).orEmpty().toMutableSet()
        val now = System.currentTimeMillis()
        for (session in sessions) {
            val status = (session.effectiveStatus ?: session.classStatus)?.trim()?.uppercase(Locale.US)
            val sessionVersion = sessionNotificationVersion(session)
            val isCancelled = session.isCancelledSession() || status == "CANCELLED"
            val isRescheduled = status == "RESCHEDULED"

            if (isCancelled) {
                cancelClassAlarm(context, session.id)
                val cancelKey = "${session.id}_cancelled_$sessionVersion"
                if (cancelKey !in deliveredCancelled) {
                    showClassCancelledNotification(context, session)
                    deliveredCancelled += cancelKey
                }
                continue
            }

            if (isRescheduled) {
                cancelClassAlarm(context, session.id)
                val reschedKey = "${session.id}_rescheduled_$sessionVersion"
                if (reschedKey !in deliveredRescheduled) {
                    showClassRescheduledNotification(context, session)
                    deliveredRescheduled += reschedKey
                }
            }

            // Keep the device notification in lockstep with every server-side
            // change to this class, instead of treating only time changes as new.
            val sessionKey = sessionVersion
            val startMillis = parseClassStartTimeMillis(session.date, session.startTime)

            if (sessionKey !in deliveredSchedules && !isRescheduled) {
                if (startMillis == null || startMillis >= now - (2 * 3600 * 1000L)) {
                    showClassScheduledNotification(context, session)
                    deliveredSchedules += sessionKey
                }
            }

            if (startMillis != null) {
                val diffMillis = startMillis - now
                val reminderThresholdMillis = ClassSchedulePolicy.EARLY_JOIN_MINUTES * 60 * 1000L

                if (diffMillis in 0..reminderThresholdMillis) {
                    if (sessionKey !in delivered15mReminders) {
                        showUpcomingClassReminder(
                            context = context,
                            sessionId = session.id,
                            title = session.sessionTitle?.ifBlank { "Live Class" } ?: "Live Class",
                            startTime = session.startTime ?: "Soon",
                            meetingLink = session.meetingLink
                        )
                        delivered15mReminders += sessionKey
                    }
                } else if (diffMillis > reminderThresholdMillis) {
                    schedule15MinAlarm(context, session, startMillis - reminderThresholdMillis)
                }
            }
        }

        prefs.edit()
            .putStringSet(KEY_SCHEDULED_CLASS_IDS, deliveredSchedules.toList().takeLast(200).toSet())
            .putStringSet(KEY_15M_REMINDER_IDS, delivered15mReminders.toList().takeLast(200).toSet())
            .putStringSet(KEY_DELIVERED_CANCELLED_IDS, deliveredCancelled.toList().takeLast(200).toSet())
            .putStringSet(KEY_DELIVERED_RESCHEDULED_IDS, deliveredRescheduled.toList().takeLast(200).toSet())
            .commit()
    }

    private fun schedule15MinAlarm(context: Context, session: AttendanceDto, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ClassScheduleAlarmReceiver::class.java).apply {
                putExtra(ClassScheduleAlarmReceiver.EXTRA_ACCOUNT_SESSION, com.example.suretouchapp.data.api.TokenManager(context).getSessionId())
                putExtra(ClassScheduleAlarmReceiver.EXTRA_SESSION_ID, session.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                session.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            // Exact-alarm access can be revoked after the capability check.
        }
    }

    private fun sessionNotificationVersion(session: AttendanceDto): String = listOf(
        session.id,
        session.updatedAt.orEmpty(),
        session.date,
        session.startTime.orEmpty(),
        session.endTime.orEmpty(),
        session.effectiveStatus ?: session.classStatus.orEmpty(),
        session.sessionTitle.orEmpty(),
        session.notes.orEmpty(),
    ).joinToString("|")

    fun showClassScheduledNotification(context: Context, session: AttendanceDto) {
        createChannels(context)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val titleText = "Class Scheduled: ${session.sessionTitle?.ifBlank { "Live Session" } ?: "Live Session"}"
        val meetInfo = when {
            session.meetingLink.isNullOrBlank() -> "Class schedule updated."
            ClassSchedulePolicy.canJoin(session) -> "Open the app to join."
            else -> "Join opens 15 minutes before class."
        }
        val messageText = "Scheduled on ${session.date} at ${session.startTime ?: "scheduled time"}. $meetInfo"

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("scheduled_" + session.id).hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sure_trust_official_logo)
        val builder = NotificationCompat.Builder(context, CHANNEL_CLASS_REMINDERS)
            .setSmallIcon(R.drawable.ic_sureproed_notification)
            .setLargeIcon(largeLogo)
            .setColor(0xFF6C2BD9.toInt())
            .setContentTitle(titleText)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setSubText("SURE ProEd • Timetable")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        showClassState(context, session.id, builder.build())
    }

    fun showClassCancelledNotification(context: Context, session: AttendanceDto) {
        createChannels(context)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val title = session.sessionTitle?.ifBlank { session.courseName } ?: session.courseName ?: "Live Class"
        val titleText = "⚠️ Class Cancelled: $title"
        val reasonText = if (!session.notes.isNullOrBlank()) "\nReason: ${session.notes}" else ""
        val messageText = "The class scheduled on ${session.date} at ${session.startTime ?: "scheduled time"} has been cancelled.$reasonText"

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_timetable", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("cancelled_" + session.id).hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sure_trust_official_logo)
        val builder = NotificationCompat.Builder(context, CHANNEL_CLASS_REMINDERS)
            .setSmallIcon(R.drawable.ic_sureproed_notification)
            .setLargeIcon(largeLogo)
            .setColor(0xFFDC2626.toInt())
            .setContentTitle(titleText)
            .setContentText("Class on ${session.date} at ${session.startTime ?: "scheduled time"} cancelled.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setSubText("SURE ProEd • Class Cancelled")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        showClassState(context, session.id, builder.build())
    }

    fun showClassRescheduledNotification(context: Context, session: AttendanceDto) {
        createChannels(context)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val title = session.sessionTitle?.ifBlank { session.courseName } ?: session.courseName ?: "Live Class"
        val titleText = "🗓️ Class Rescheduled: $title"
        val meetInfo = when {
            session.meetingLink.isNullOrBlank() -> ""
            ClassSchedulePolicy.canJoin(session) -> " Open the app to join."
            else -> " Join opens 15 minutes before class."
        }
        val messageText = "This class has been rescheduled to ${session.date} at ${session.startTime ?: "scheduled time"}.$meetInfo"

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_timetable", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("rescheduled_" + session.id).hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sure_trust_official_logo)
        val builder = NotificationCompat.Builder(context, CHANNEL_CLASS_REMINDERS)
            .setSmallIcon(R.drawable.ic_sureproed_notification)
            .setLargeIcon(largeLogo)
            .setColor(0xFFD97706.toInt())
            .setContentTitle(titleText)
            .setContentText("Rescheduled to ${session.date} at ${session.startTime ?: "scheduled time"}.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setSubText("SURE ProEd • Timetable Update")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        showClassState(context, session.id, builder.build())
    }

    fun showUpcomingClassReminder(
        context: Context,
        sessionId: String,
        title: String,
        startTime: String,
        meetingLink: String?
    ) {
        createChannels(context)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val titleText = "Class Starting in 15 Minutes!"
        val messageText = "$title starts at $startTime. Open the app to view the latest class details."
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_live_class", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            ("reminder_15m_" + sessionId).hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sure_trust_official_logo)
        val builder = NotificationCompat.Builder(context, CHANNEL_CLASS_REMINDERS)
            .setSmallIcon(R.drawable.ic_sureproed_notification)
            .setLargeIcon(largeLogo)
            .setColor(0xFFDC2626.toInt())
            .setContentTitle(titleText)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setSubText("SURE ProEd • Live Class Alert")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        builder.addAction(R.drawable.ic_sureproed_notification, "Open class", pendingIntent)

        showClassState(context, sessionId, builder.build())
    }

    /**
     * Renders a complete live-class FCM data payload synchronously. This method
     * deliberately performs no network request so onMessageReceived() can post
     * the time-sensitive alert inside Firebase's short execution window.
     */
    @Synchronized
    fun showLiveClassPush(
        context: Context,
        payload: LiveClassPushPayload,
        receivedAt: Instant
    ): LiveClassPushDisplayResult {
        val presentation = LiveClassPushPolicy.present(payload, receivedAt, ClassSchedulePolicy.timeZone)
        if (!canPost(context)) return LiveClassPushDisplayResult(presentation, displayedAt = null, duplicate = false)
        createChannels(context)
        val eventKey = "${payload.notificationId}:${payload.type.name}"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (eventKey in prefs.getStringSet(KEY_DIRECT_CLASS_EVENT_KEYS, emptySet()).orEmpty()) {
            return LiveClassPushDisplayResult(presentation, displayedAt = null, duplicate = true)
        }
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_live_class", true)
            putExtra("class_id", payload.classId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("live_push_" + payload.classId).hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sure_trust_official_logo)
        val builder = NotificationCompat.Builder(context, CHANNEL_CLASS_REMINDERS)
            .setSmallIcon(R.drawable.ic_sureproed_notification)
            .setLargeIcon(largeLogo)
            .setColor(0xFFDC2626.toInt())
            .setContentTitle(presentation.title)
            .setContentText(presentation.body.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(presentation.body))
            .setSubText("SURE ProEd • Live Class Alert")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // The optional T event should alert after the T-5 reminder, while a
            // duplicate delivery of either event is suppressed by eventKey.
            .setOnlyAlertOnce(false)
            .setWhen(receivedAt.toEpochMilli())
            .setShowWhen(true)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        val action = if (presentation.state == LiveClassPushState.UPCOMING) "Open class" else "Join now"
        builder.addAction(R.drawable.ic_sureproed_notification, action, pendingIntent)

        val trayId = ("class_state_" + payload.classId).hashCode()
        notifyIfAllowed(context, trayId, builder.build())
        val displayedAt = Instant.now()
        rememberDirectClassPush(context, payload.notificationId, trayId, eventKey)
        recordPushMetric(context, payload, presentation, receivedAt, displayedAt)
        return LiveClassPushDisplayResult(presentation, displayedAt, duplicate = false)
    }

    private fun rememberDirectClassPush(context: Context, notificationId: String, trayId: Int, eventKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val delivered = prefs.getStringSet(KEY_DELIVERED_IDS, emptySet()).orEmpty().toMutableSet()
        val direct = prefs.getStringSet(KEY_DIRECT_CLASS_PUSH_IDS, emptySet()).orEmpty().toMutableSet()
        val events = prefs.getStringSet(KEY_DIRECT_CLASS_EVENT_KEYS, emptySet()).orEmpty().toMutableSet()
        delivered += notificationId
        direct += notificationId
        events += eventKey
        prefs.edit()
            .putStringSet(KEY_DELIVERED_IDS, delivered.toList().takeLast(500).toSet())
            .putStringSet(KEY_DIRECT_CLASS_PUSH_IDS, direct.toList().takeLast(200).toSet())
            .putStringSet(KEY_DIRECT_CLASS_EVENT_KEYS, events.toList().takeLast(200).toSet())
            .putInt(KEY_TRAY_ID_PREFIX + notificationId, trayId)
            .commit()
    }

    private fun recordPushMetric(
        context: Context,
        payload: LiveClassPushPayload,
        presentation: LiveClassPushPresentation,
        receivedAt: Instant,
        displayedAt: Instant
    ) {
        val prefs = context.getSharedPreferences(PUSH_METRICS_PREFS_NAME, Context.MODE_PRIVATE)
        val existing = runCatching {
            org.json.JSONArray(prefs.getString(KEY_PUSH_METRICS, "[]"))
        }.getOrElse { org.json.JSONArray() }
        val retained = org.json.JSONArray()
        val first = (existing.length() - 199).coerceAtLeast(0)
        for (index in first until existing.length()) retained.put(existing.opt(index))
        retained.put(
            org.json.JSONObject()
                .put("notification_id", payload.notificationId)
                .put("class_id", payload.classId)
                .put("event_type", payload.type.name)
                .put("scheduled_at", payload.scheduledAt.toString())
                .put("sent_at", payload.sentAt.toString())
                .put("device_received_at", receivedAt.toString())
                .put("notification_displayed_at", displayedAt.toString())
                .put("transport_latency_ms", presentation.transportLatency.toMillis())
                .put("schedule_lateness_ms", presentation.scheduleLateness.toMillis())
        )
        prefs.edit().putString(KEY_PUSH_METRICS, retained.toString()).apply()
    }

    private fun showClassState(context: Context, id: String, notification: android.app.Notification) {
        val system = NotificationManagerCompat.from(context)
        listOf("scheduled_", "cancelled_", "rescheduled_", "reminder_15m_").forEach {
            system.cancel((it + id).hashCode())
        }
        notifyIfAllowed(context, ("class_state_" + id).hashCode(), notification)
    }

    private fun cancelClassAlarm(context: Context, id: String) {
        val pending = PendingIntent.getBroadcast(context, id.hashCode(),
            Intent(context, ClassScheduleAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(pending)
        pending.cancel()
    }

    fun parseClassStartTimeMillis(dateStr: String?, timeStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val trimmedDate = dateStr.trim()
        
        // Handle full ISO date-time strings if present in dateStr
        if (trimmedDate.contains("T")) {
            try {
                return java.time.Instant.parse(trimmedDate).toEpochMilli()
            } catch (_: Exception) {}
            try {
                return java.time.OffsetDateTime.parse(trimmedDate).toInstant().toEpochMilli()
            } catch (_: Exception) {}
            try {
                return LocalDateTime.parse(trimmedDate.substringBefore("Z")).atZone(ClassSchedulePolicy.timeZone).toInstant().toEpochMilli()
            } catch (_: Exception) {}
        }

        val cleanDate = trimmedDate.take(10)
        val cleanTime = timeStr?.trim()?.ifBlank { "09:00:00" } ?: "09:00:00"

        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd hh:mm:ss a",
            "yyyy-MM-dd h:mm:ss a",
            "yyyy-MM-dd hh:mma",
            "yyyy-MM-dd h:mma",
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd h:mm a",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
        )
        for (pattern in patterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
                val dt = LocalDateTime.parse("$cleanDate $cleanTime", formatter)
                return dt.atZone(ClassSchedulePolicy.timeZone).toInstant().toEpochMilli()
            } catch (_: Exception) {}
        }
        try {
            val date = LocalDate.parse(cleanDate)
            return date.atStartOfDay(ClassSchedulePolicy.timeZone).toInstant().toEpochMilli()
        } catch (_: Exception) {}
        return null
    }

    fun showPreview(context: Context) {
        if (!canPost(context)) return
        show(
            context,
            NotificationDto(
                id = "sure-proed-preview-${System.currentTimeMillis()}",
                title = "SURE ProEd Announcement Update",
                message = "Official announcements, batch updates, classes, assignments, and grades will notify you here with ringtone.",
                createdAt = "",
                isRead = false
            )
        )
        playNotificationSound(context)
    }

    fun showCourseApplicationSuccess(context: Context, courseName: String) {
        if (!canPost(context)) return
        show(
            context,
            NotificationDto(
                id = "course-application-${System.currentTimeMillis()}",
                title = "Course Applied Successfully",
                message = "Your application for $courseName was submitted. Prepare for the pre-screen exam. All the best!",
                createdAt = "",
                isRead = false
            )
        )
        playNotificationSound(context)
    }

    private fun show(context: Context, item: NotificationDto) {
        createChannels(context)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val category = classify(item)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_notifications", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            trayNotificationId(item),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val largeLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sure_trust_official_logo)
        val builder = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_sureproed_notification)
            .setLargeIcon(largeLogo)
            .setColor(0xFF6C2BD9.toInt())
            .setContentTitle(item.title)
            .setContentText(item.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.message))
            .setSubText("SURE ProEd • ${category.label}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(category.priority)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            
        notifyIfAllowed(context, trayNotificationId(item), builder.build())
    }

    private fun trayNotificationId(item: NotificationDto): Int {
        val path = item.actionUrl?.substringBefore('?')?.trim().orEmpty()
        val attendanceId = Regex("^/?attendance/([^/]+)/?$").matchEntire(path)?.groupValues?.getOrNull(1)
        return attendanceId?.let { ("class_state_" + it).hashCode() } ?: item.id.hashCode()
    }

    private fun classify(item: NotificationDto): NotificationCategory {
        val text = "${item.title} ${item.message}".uppercase(Locale.US)
        return when {
            listOf("ANNOUNCEMENT", "NOTICE", "BROADCAST", "CIRCULAR", "ALERT", "UPDATE").any(text::contains) ->
                NotificationCategory(CHANNEL_ANNOUNCEMENTS, "Announcement", NotificationCompat.PRIORITY_MAX)
            listOf("GRADE", "SCORE", "MARKS", "EVALUATION", "EVALUATED", "CERTIFICATE", "COMPLETED", "ACHIEVEMENT").any(text::contains) ->
                NotificationCategory(CHANNEL_ACHIEVEMENTS, "Grade & Achievement", NotificationCompat.PRIORITY_MAX)
            listOf("ASSIGNMENT", "CLASS", "ATTENDANCE", "MODULE", "TEST").any(text::contains) ->
                NotificationCategory(CHANNEL_LEARNING, "Assignment & Class Update", NotificationCompat.PRIORITY_HIGH)
            listOf("TREE", "PLANTATION", "BLOOD", "DONATION", "LIFE SKILL", "SOCIETY", "SOCIAL").any(text::contains) ->
                NotificationCategory(CHANNEL_COMMUNITY, "Community Activity", NotificationCompat.PRIORITY_DEFAULT)
            else -> NotificationCategory(CHANNEL_ACADEMIC, "Academic Update", NotificationCompat.PRIORITY_HIGH)
        }
    }

    private data class NotificationCategory(val channelId: String, val label: String, val priority: Int)
}
