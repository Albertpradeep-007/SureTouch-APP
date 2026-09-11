package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.model.NotificationDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun notificationVersion(item: NotificationDto): String =
    item.updatedAt?.trim()?.takeIf { it.isNotBlank() } ?: item.createdAt.trim()

fun parseNotificationInstant(raw: String?): Instant {
    if (raw.isNullOrBlank()) return Instant.EPOCH
    val s = raw.trim()

    runCatching { return Instant.parse(s) }
    runCatching { return OffsetDateTime.parse(s).toInstant() }

    if (s.contains('T')) {
        runCatching {
            val clean = s.substringBefore('Z').substringBefore('+')
            return LocalDateTime.parse(clean).atZone(ZoneId.systemDefault()).toInstant()
        }
    }

    val patterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd"
    )
    for (pattern in patterns) {
        runCatching {
            val dt = if (pattern == "yyyy-MM-dd") {
                LocalDate.parse(s.take(10)).atStartOfDay()
            } else {
                LocalDateTime.parse(s, DateTimeFormatter.ofPattern(pattern, Locale.US))
            }
            return dt.atZone(ZoneId.systemDefault()).toInstant()
        }
    }

    return Instant.EPOCH
}

fun notificationTime(item: NotificationDto): Instant =
    parseNotificationInstant(notificationVersion(item))

fun latestNotifications(items: List<NotificationDto>): List<NotificationDto> = items
    .filter { it.id.isNotBlank() }
    .groupBy { it.id }.values.map { versions -> versions.maxBy { notificationTime(it) } }
    .sortedWith(
        compareByDescending<NotificationDto> { notificationTime(it) }
            .thenByDescending { it.id.toLongOrNull() ?: 0L }
            .thenByDescending { it.id }
    )
