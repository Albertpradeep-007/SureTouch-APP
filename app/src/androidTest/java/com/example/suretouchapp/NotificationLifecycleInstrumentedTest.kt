package com.example.suretouchapp

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.NotificationDto
import com.example.suretouchapp.ui.screens.notifications.SureProEdNotificationManager as Alerts
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationLifecycleInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val system = context.getSystemService(NotificationManager::class.java)
    private val tokens = TokenManager(context)
    @Before fun setup() {
        tokens.saveToken("test-a", "refresh-a")
        val command = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
        )
        android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
        Alerts.createChannels(context)
    }
    @After fun cleanup() { tokens.clearUserSessionAndProfile() }
    private fun row() = NotificationDto("notification-1", "Old title", "Original", "2026-09-01T10:00:00Z")
    private fun assertCount(expected: Int) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 3000
        while (system.activeNotifications.size != expected && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(20)
        }
        assertEquals(expected, system.activeNotifications.size)
    }
    private fun awaitTitle(expected: String) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 3000
        while (system.activeNotifications.singleOrNull()?.notification?.extras?.getString("android.title")?.contains(expected) != true && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(20)
        }
        assertTrue(system.activeNotifications.single().notification.extras.getString("android.title")!!.contains(expected))
    }
    @Test fun updateReplacesPanelEntryAndOlderDeliveryCannotRestoreIt() {
        val original = row()
        val updated = original.copy(title = "Updated title", message = "Updated body", updatedAt = "2026-09-02T10:00:00Z")
        Alerts.syncUnread(context, listOf(original))
        Alerts.syncUnread(context, listOf(updated))
        Alerts.syncUnread(context, listOf(original))
        assertCount(1)
        awaitTitle("Updated title")
        val active = system.activeNotifications
        assertEquals(1, active.size)
        assertEquals("Updated title", active.single().notification.extras.getString("android.title"))
    }
    @Test fun readAndDeletionRemovePanelEntriesAndPartialPushDoesNotDeleteOthers() {
        val first = row()
        val second = first.copy(id = "second")
        Alerts.syncUnread(context, listOf(first, second), completeSnapshot = true)
        Alerts.syncUnread(context, listOf(first))
        assertCount(2)
        Alerts.syncUnread(context, listOf(first.copy(isRead = true, updatedAt = "2026-09-03T00:00:00Z")))
        assertCount(1)
        Alerts.syncUnread(context, emptyList(), completeSnapshot = true)
        assertCount(0)
    }
    @Test fun switchingTwoAccountsClearsNotificationsAndDeliveryCache() {
        Alerts.syncUnread(context, listOf(row()))
        assertCount(1)
        tokens.saveToken("test-b", "refresh-b")
        assertCount(0)
        assertTrue(context.getSharedPreferences("sure_proed_notification_delivery", 0).all.isEmpty())
    }
    @Test fun classUpdatesReplaceScheduledAndReminderAlerts() {
        val session = com.example.suretouchapp.data.model.AttendanceDto(id = "class-1", sessionTitle = "Example class", date = "2026-09-09", startTime = "10:00")
        Alerts.showClassScheduledNotification(context, session)
        Alerts.showUpcomingClassReminder(context, session.id, "Example class", "10:00", null)
        Alerts.showClassRescheduledNotification(context, session.copy(startTime = "11:00"))
        assertCount(1)
        Alerts.showClassCancelledNotification(context, session)
        assertCount(1)
        awaitTitle("Cancelled")
    }
}
