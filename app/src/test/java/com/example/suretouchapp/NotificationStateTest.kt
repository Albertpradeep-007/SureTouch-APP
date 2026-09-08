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
}
