package com.example.suretouchapp.ui.screens.notifications

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class LiveClassPushType {
    CLASS_STARTING_SOON,
    CLASS_STARTED
}

enum class LiveClassPushState {
    UPCOMING,
    LIVE,
    LATE
}

data class LiveClassPushPayload(
    val type: LiveClassPushType,
    val notificationId: String,
    val accountSession: String,
    val classId: String,
    val classTitle: String,
    val scheduledAt: Instant,
    val sentAt: Instant
) {
    companion object {
        fun parse(data: Map<String, String>, fcmSentAt: Instant? = null): LiveClassPushPayload? {
            val type = runCatching {
                LiveClassPushType.valueOf(data["type"]?.trim()?.uppercase(Locale.US).orEmpty())
            }.getOrNull() ?: return null
            val notificationId = data["notification_id"]?.trim()?.takeIf(String::isNotBlank) ?: return null
            val accountSession = data["account_session"]?.trim()?.takeIf(String::isNotBlank) ?: return null
            val classId = data["class_id"]?.trim()?.takeIf(String::isNotBlank) ?: return null
            val scheduledAt = parseInstant(data["scheduled_at"] ?: data["event_time"]) ?: return null
            val sentAt = parseInstant(data["sent_at"]) ?: fcmSentAt ?: return null
            val classTitle = sequenceOf(data["class_title"], data["course_name"])
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .firstOrNull()
                ?.take(120)
                ?: "Your class"
            return LiveClassPushPayload(
                type = type,
                notificationId = notificationId,
                accountSession = accountSession,
                classId = classId,
                classTitle = classTitle,
                scheduledAt = scheduledAt,
                sentAt = sentAt
            )
        }

        private fun parseInstant(raw: String?): Instant? {
            val value = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
            return runCatching { Instant.parse(value) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        }
    }
}

data class LiveClassPushPresentation(
    val state: LiveClassPushState,
    val title: String,
    val body: String,
    val scheduleLateness: Duration,
    val transportLatency: Duration
)

data class LiveClassPushDisplayResult(
    val presentation: LiveClassPushPresentation,
    val displayedAt: Instant?,
    val duplicate: Boolean
)

object LiveClassPushPolicy {
    private val liveWindow: Duration = Duration.ofMinutes(2)
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun present(
        payload: LiveClassPushPayload,
        receivedAt: Instant,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): LiveClassPushPresentation {
        val scheduleLateness = nonNegative(Duration.between(payload.scheduledAt, receivedAt))
        val transportLatency = nonNegative(Duration.between(payload.sentAt, receivedAt))
        val state = when {
            receivedAt.isBefore(payload.scheduledAt) -> LiveClassPushState.UPCOMING
            !receivedAt.isAfter(payload.scheduledAt.plus(liveWindow)) -> LiveClassPushState.LIVE
            else -> LiveClassPushState.LATE
        }
        val scheduled = timeFormatter.format(payload.scheduledAt.atZone(zoneId))
        val received = timeFormatter.format(receivedAt.atZone(zoneId))
        val title = when (state) {
            LiveClassPushState.UPCOMING -> "${payload.classTitle} Class Reminder"
            LiveClassPushState.LIVE,
            LiveClassPushState.LATE -> "${payload.classTitle} Class Started"
        }
        val body = when (state) {
            LiveClassPushState.UPCOMING ->
                "Class starts at $scheduled\nReceived at $received"
            LiveClassPushState.LIVE ->
                "Class is live now\nScheduled: $scheduled\nReceived: $received"
            LiveClassPushState.LATE ->
                "Class started at $scheduled\nNotification received at $received • ${formatDuration(scheduleLateness)} late"
        }
        return LiveClassPushPresentation(
            state = state,
            title = title,
            body = body,
            scheduleLateness = scheduleLateness,
            transportLatency = transportLatency
        )
    }

    internal fun formatDuration(duration: Duration): String {
        val seconds = duration.seconds.coerceAtLeast(0)
        return when {
            seconds < 60 -> "$seconds sec"
            seconds < 3600 -> {
                val minutes = seconds / 60
                val remainder = seconds % 60
                if (remainder == 0L) "$minutes min" else "${minutes}m ${remainder}s"
            }
            else -> {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                if (minutes == 0L) "$hours hr" else "${hours}h ${minutes}m"
            }
        }
    }

    private fun nonNegative(duration: Duration): Duration =
        if (duration.isNegative) Duration.ZERO else duration
}
