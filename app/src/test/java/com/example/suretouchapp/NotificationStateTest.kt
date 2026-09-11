package com.example.suretouchapp

import com.example.suretouchapp.data.model.NotificationDto
import com.example.suretouchapp.data.repository.latestNotifications
import org.junit.Assert.*
import org.junit.Test

class NotificationStateTest {
    @Test fun updatedVersionReplacesOriginalEvenWhenResponsesArriveOutOfOrder() {
        val old = NotificationDto("id", "Old", "Old message", "2026-09-01T10:00:00Z")
        val updated = old.copy(title = "New", updatedAt = "2026-09-02T10:00:00Z", isRead = true)
        assertEquals(listOf(updated), latestNotifications(listOf(updated, old, old)))
    }
    @Test fun differentIdentitiesAreNotMergedAndUpdateTimeDeterminesOrder() {
        val old = NotificationDto("a", "Same", "Same", "2026-09-01T10:00:00Z", updatedAt = "2026-09-03T10:00:00Z")
        val newer = old.copy(id = "b", updatedAt = "2026-09-02T10:00:00Z")
        assertEquals(listOf(old, newer), latestNotifications(listOf(newer, old)))
    }
    @Test fun nonZAndSqlTimestampsSortNewestToOldest() {
        val oldest = NotificationDto("1", "Oldest", "Msg", "2026-01-01 09:00:00")
        val middle = NotificationDto("2", "Middle", "Msg", "2026-05-15T14:30:00")
        val newest = NotificationDto("3", "Newest", "Msg", "2026-09-10T12:00:00+05:30")
        // Pass in oldest-first order; must be sorted newest-first
        assertEquals(listOf(newest, middle, oldest), latestNotifications(listOf(oldest, middle, newest)))
    }
    @Test fun blankUpdatedAtFallsBackToCreatedAt() {
        val old = NotificationDto("1", "Old", "Msg", "2026-01-01T10:00:00Z", updatedAt = "")
        val newer = NotificationDto("2", "New", "Msg", "2026-02-01T10:00:00Z", updatedAt = "   ")
        assertEquals(listOf(newer, old), latestNotifications(listOf(old, newer)))
    }
}
