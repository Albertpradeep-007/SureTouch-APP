package com.example.suretouchapp

import com.example.suretouchapp.ui.screens.notifications.LiveClassPushPayload
import com.example.suretouchapp.ui.screens.notifications.LiveClassPushPolicy
import com.example.suretouchapp.ui.screens.notifications.LiveClassPushState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LiveClassPushPolicyTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun payload(
        type: String = "CLASS_STARTING_SOON",
        scheduledAt: String = "2026-09-12T22:20:00+05:30",
        sentAt: String = "2026-09-12T22:15:00+05:30"
    ): LiveClassPushPayload = LiveClassPushPayload.parse(
        mapOf(
            "type" to type,
            "notification_id" to "notification-125",
            "account_session" to "session-1",
            "class_id" to "125",
            "class_title" to "VLSI",
            "scheduled_at" to scheduledAt,
            "sent_at" to sentAt
        )
    )!!

    @Test fun fiveMinuteEarlyDeliveryIsShownAsUpcoming() {
        val result = LiveClassPushPolicy.present(
            payload(),
            Instant.parse("2026-09-12T16:45:04Z"),
            zone
        )
        assertEquals(LiveClassPushState.UPCOMING, result.state)
        assertEquals("VLSI Class Reminder", result.title)
        assertTrue(result.body.contains("Class starts at 10:20 PM"))
        assertTrue(result.body.contains("Received at 10:15 PM"))
        assertEquals(4, result.transportLatency.seconds)
    }

    @Test fun deliveryWithinTwoMinutesOfStartIsLive() {
        val result = LiveClassPushPolicy.present(
            payload(type = "CLASS_STARTED"),
            Instant.parse("2026-09-12T16:51:30Z"),
            zone
        )
        assertEquals(LiveClassPushState.LIVE, result.state)
        assertTrue(result.body.contains("Class is live now"))
        assertEquals(90, result.scheduleLateness.seconds)
    }

    @Test fun lateDeliveryRemainsVisibleAndStatesActualDelay() {
        val result = LiveClassPushPolicy.present(
            payload(type = "CLASS_STARTED"),
            Instant.parse("2026-09-12T16:54:00Z"),
            zone
        )
        assertEquals(LiveClassPushState.LATE, result.state)
        assertTrue(result.body.contains("Notification received at 10:24 PM"))
        assertTrue(result.body.contains("4 min late"))
        assertEquals(9 * 60, result.transportLatency.seconds)
    }

    @Test fun fcmTimestampCanSupplyMissingServerSentTime() {
        val data = mapOf(
            "type" to "CLASS_STARTED",
            "notification_id" to "notification-125",
            "account_session" to "session-1",
            "class_id" to "125",
            "scheduled_at" to "2026-09-12T22:20:00+05:30"
        )
        assertNotNull(LiveClassPushPayload.parse(data, Instant.parse("2026-09-12T16:50:00Z")))
    }

    @Test fun incompleteOrUnrelatedPayloadUsesAuthenticatedFallback() {
        val base = mapOf(
            "notification_id" to "notification-125",
            "account_session" to "session-1",
            "class_id" to "125",
            "scheduled_at" to "2026-09-12T22:20:00+05:30",
            "sent_at" to "2026-09-12T22:15:00+05:30"
        )
        assertNull(LiveClassPushPayload.parse(base + ("type" to "ASSIGNMENT")))
        assertNull(LiveClassPushPayload.parse(base + ("type" to "CLASS_STARTED") - "class_id"))
        assertNull(LiveClassPushPayload.parse(base + ("type" to "CLASS_STARTED") + ("scheduled_at" to "not-a-time")))
    }
}
