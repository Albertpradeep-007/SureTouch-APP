package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.model.NotificationDto
import java.time.Instant

fun notificationVersion(item: NotificationDto): String = item.updatedAt ?: item.createdAt
private fun notificationTime(item: NotificationDto): Instant =
    runCatching { Instant.parse(notificationVersion(item)) }.getOrDefault(Instant.EPOCH)

fun latestNotifications(items: List<NotificationDto>): List<NotificationDto> = items
    .filter { it.id.isNotBlank() }
    .groupBy { it.id }.values.map { versions -> versions.maxBy { notificationTime(it) } }
    .sortedWith(compareByDescending<NotificationDto> { notificationTime(it) }.thenByDescending { it.id })
