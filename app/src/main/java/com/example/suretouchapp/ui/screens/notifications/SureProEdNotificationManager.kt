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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CHANNEL_CLASS_REMINDERS, "Class Schedule & Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "15-minute upcoming class reminders and newly scheduled live classes with Google Meet links"
                setSound(defaultSoundUri, audioAttributes)
                enableVibration(true)
                this.vibrationPattern = vibrationPattern
                enableLights(true)
                lightColor = 0xFFDC2626.toInt()
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CHANNEL_ACADEMIC, "Academic and Cohort Updates", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Screening, student verification, cohort assignment, and timetable changes"
                setSound(defaultSoundUri, audioAttributes)
                enableVibration(true)
                this.vibrationPattern = vibrationPattern
                enableLights(true)
                lightColor = 0xFF2563EB.toInt()
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CHANNEL_LEARNING, "Classes and Assignments", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Live classes, attendance, assignments, module tests, and grades"
                setSound(defaultSoundUri, audioAttributes)
                enableVibration(true)
                this.vibrationPattern = vibrationPattern
                enableLights(true)
                lightColor = 0xFF059669.toInt()
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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

    fun syncAnnouncements(context: Context, announcements: List<AnnouncementDto>) {
        if (!canPost(context) || announcements.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val delivered = prefs.getStringSet(KEY_DELIVERED_ANNOUNCEMENT_IDS, emptySet()).orEmpty().toMutableSet()
        
        val activeList = announcements.filter { it.isActive }
        var hasDeliveredNew = false

        for (item in activeList) {
            if (item.id !in delivered) {
                showAnnouncementNotification(context, item)
                delivered += item.id
                hasDeliveredNew = true
            }
        }

        if (hasDeliveredNew) {
            playNotificationSound(context)
        }

        prefs.edit()
            .putStringSet(KEY_DELIVERED_ANNOUNCEMENT_IDS, delivered.toList().takeLast(200).toSet())
            .apply()
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notifyIfAllowed(context, ("announcement_" + announcement.id).hashCode(), builder.build())
    }

    fun syncAssignments(context: Context, assignments: List<AssignmentDto>) {
        if (!canPost(context) || assignments.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val delivered = prefs.getStringSet(KEY_DELIVERED_ASSIGNMENT_IDS, emptySet()).orEmpty().toMutableSet()
        var hasNew = false

        for (assignment in assignments) {
            if (assignment.id.isNotBlank() && assignment.id !in delivered) {
                showAssignmentNotification(context, assignment)
                delivered += assignment.id
                hasNew = true
            }
        }

        if (hasNew) {
            playNotificationSound(context)
        }

        prefs.edit()
            .putStringSet(KEY_DELIVERED_ASSIGNMENT_IDS, delivered.toList().takeLast(200).toSet())
            .apply()
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notifyIfAllowed(context, ("assignment_" + assignment.id).hashCode(), builder.build())
    }

    fun syncSubmissionsAndGrades(
        context: Context,
        submissions: List<SubmissionDto>,
        assignments: List<AssignmentDto> = emptyList()
    ) {
        if (!canPost(context)) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deliveredGrades = prefs.getStringSet(KEY_DELIVERED_GRADE_IDS, emptySet()).orEmpty().toMutableSet()
        val assignmentMap = assignments.associateBy { it.id }
        var hasNewGrade = false

        for (submission in submissions) {
            val isEvaluated = submission.evaluated || !submission.marksObtained.isNullOrBlank() || submission.passed != null
            if (isEvaluated) {
                val scoreKey = "${submission.id}_${submission.marksObtained}_${submission.evaluated}"
                if (scoreKey !in deliveredGrades) {
                    val assignmentTitle = assignmentMap[submission.assignment]?.title ?: "Assignment"
                    val score = submission.marksObtained ?: "Evaluated"
                    val feedback = submission.feedback ?: if (submission.passed == true) "Passed! Well done." else "Evaluation completed."
                    showGradeNotification(
                        context = context,
                        id = submission.id,
                        title = assignmentTitle,
                        gradeOrScore = score,
                        feedback = feedback
                    )
                    deliveredGrades += scoreKey
                    hasNewGrade = true
                }
            }
        }

        for (assignment in assignments) {
            val hasGrade = !assignment.grade.isNullOrBlank() || assignment.score != null || assignment.status?.uppercase() in setOf("GRADED", "EVALUATED")
            if (hasGrade) {
                val gradeKey = "${assignment.id}_grade_${assignment.grade}_${assignment.score}"
                if (gradeKey !in deliveredGrades) {
                    val score = assignment.grade ?: assignment.score?.let { "$it Marks" } ?: "Graded"
                    showGradeNotification(
                        context = context,
                        id = assignment.id,
                        title = assignment.title.ifBlank { "Cohort Assignment" },
                        gradeOrScore = score,
                        feedback = "Your score/grade has been published."
                    )
                    deliveredGrades += gradeKey
                    hasNewGrade = true
                }
            }
        }

        if (hasNewGrade) {
            playNotificationSound(context)
        }

        prefs.edit()
            .putStringSet(KEY_DELIVERED_GRADE_IDS, deliveredGrades.toList().takeLast(200).toSet())
            .apply()
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notifyIfAllowed(context, ("grade_" + id).hashCode(), builder.build())
    }

    fun syncUnread(context: Context, notifications: List<NotificationDto>) {
        if (!canPost(context)) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val delivered = prefs.getStringSet(KEY_DELIVERED_IDS, emptySet()).orEmpty().toMutableSet()
        var hasNew = false

        notifications.asSequence()
            .filter { !it.isRead && it.id !in delivered }
            .take(5)
            .forEach { notification ->
                show(context, notification)
                delivered += notification.id
                hasNew = true
            }

        if (hasNew) {
            playNotificationSound(context)
        }

        prefs.edit().putStringSet(KEY_DELIVERED_IDS, delivered.toList().takeLast(200).toSet()).apply()
    }

    fun syncTimetableAndClasses(context: Context, sessions: List<AttendanceDto>) {
        if (!canPost(context) || sessions.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deliveredSchedules = prefs.getStringSet(KEY_SCHEDULED_CLASS_IDS, emptySet()).orEmpty().toMutableSet()
        val delivered15mReminders = prefs.getStringSet(KEY_15M_REMINDER_IDS, emptySet()).orEmpty().toMutableSet()
        val now = System.currentTimeMillis()
        var hasNewAlert = false

        for (session in sessions) {
            val sessionKey = "${session.id}_${session.date}_${session.startTime.orEmpty()}"
            val startMillis = parseClassStartTimeMillis(session.date, session.startTime)

            if (sessionKey !in deliveredSchedules) {
                if (startMillis == null || startMillis >= now - (2 * 3600 * 1000L)) {
                    showClassScheduledNotification(context, session)
                    deliveredSchedules += sessionKey
                    hasNewAlert = true
                }
            }

            if (startMillis != null) {
                val diffMillis = startMillis - now
                val reminderThresholdMillis = 15 * 60 * 1000L

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
                        hasNewAlert = true
                    }
                } else if (diffMillis > reminderThresholdMillis) {
                    schedule15MinAlarm(context, session, startMillis - reminderThresholdMillis)
                }
            }
        }

        if (hasNewAlert) {
            playNotificationSound(context)
        }

        prefs.edit()
            .putStringSet(KEY_SCHEDULED_CLASS_IDS, deliveredSchedules.toList().takeLast(200).toSet())
            .putStringSet(KEY_15M_REMINDER_IDS, delivered15mReminders.toList().takeLast(200).toSet())
            .apply()
    }

    private fun schedule15MinAlarm(context: Context, session: AttendanceDto, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ClassScheduleAlarmReceiver::class.java).apply {
                putExtra(ClassScheduleAlarmReceiver.EXTRA_SESSION_ID, session.id)
                putExtra(ClassScheduleAlarmReceiver.EXTRA_SESSION_TITLE, session.sessionTitle ?: "Live Class")
                putExtra(ClassScheduleAlarmReceiver.EXTRA_START_TIME, session.startTime ?: "Soon")
                putExtra(ClassScheduleAlarmReceiver.EXTRA_MEETING_LINK, session.meetingLink)
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

    fun showClassScheduledNotification(context: Context, session: AttendanceDto) {
        createChannels(context)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val titleText = "Class Scheduled: ${session.sessionTitle?.ifBlank { "Live Session" } ?: "Live Session"}"
        val meetInfo = if (!session.meetingLink.isNullOrBlank()) "Google Meet link attached." else "Class schedule updated."
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
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notifyIfAllowed(context, ("scheduled_" + session.id).hashCode(), builder.build())
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
        val meetText = if (!meetingLink.isNullOrBlank()) " Tap to join Google Meet." else " Tap to open class dashboard."
        val messageText = "$title starts at $startTime.$meetText"

        val launchIntent = if (!meetingLink.isNullOrBlank()) {
            try {
                Intent(Intent.ACTION_VIEW, Uri.parse(meetingLink)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } catch (_: Exception) {
                Intent(context, MainActivity::class.java)
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (!meetingLink.isNullOrBlank()) {
            val joinIntent = Intent(Intent.ACTION_VIEW, Uri.parse(meetingLink)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val joinPendingIntent = PendingIntent.getActivity(
                context,
                ("join_meet_" + sessionId).hashCode(),
                joinIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_sureproed_notification,
                "Join Google Meet",
                joinPendingIntent
            )
        }

        notifyIfAllowed(context, ("reminder_15m_" + sessionId).hashCode(), builder.build())
    }

    fun parseClassStartTimeMillis(dateStr: String?, timeStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val cleanDate = dateStr.trim().take(10)
        val cleanTime = timeStr?.trim()?.ifBlank { "09:00:00" } ?: "09:00:00"

        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd h:mm a",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
        )
        for (pattern in patterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
                val dt = LocalDateTime.parse("$cleanDate $cleanTime", formatter)
                return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {}
        }
        try {
            val date = LocalDate.parse(cleanDate)
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
            item.id.hashCode(),
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
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setGroup(GROUP_STUDENT_UPDATES)
            .setPriority(category.priority)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            
        notifyIfAllowed(context, item.id.hashCode(), builder.build())
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
