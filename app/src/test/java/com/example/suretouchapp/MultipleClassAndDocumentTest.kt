package com.example.suretouchapp

import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.repository.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class MultipleClassAndDocumentTest {
    private val now = LocalDateTime.of(2026, 9, 11, 11, 0)
    private fun session(id: String, start: String, end: String) = AttendanceDto(
        id = id, date = "2026-09-11", startTime = start, endTime = end,
        classStatus = "SCHEDULED", meetingLink = "https://meet.google.com/shared"
    )
    @Test fun currentClassWinsOverPreviousClassGracePeriodInEitherApiOrder() {
        val old = session("old", "10:00", "11:00")
        val current = session("current", "11:00", "12:00")
        for (rows in listOf(listOf(old, current), listOf(current, old))) {
            assertEquals(current, (LiveClassSelector.resolveLiveClassState(rows, now = now) as LiveClassUiState.Ongoing).session)
            assertEquals(current, TimetableSessionPolicy.findNextActiveSession(rows, now).first)
        }
    }
    @Test fun sameDayAndSharedLinksKeepEveryDistinctClass() {
        val a = session("a", "10:30", "12:00")
        val b = session("b", "10:30", "12:00")
        assertEquals(listOf(a, b), LiveClassSelector.orderedSessions(listOf(b, a, b), now))
        assertEquals(b, (LiveClassSelector.resolveLiveClassState(listOf(b), now = now) as LiveClassUiState.Ongoing).session)
    }
    @Test fun cancelledMorningClassDoesNotHideAfternoonClass() {
        val cancelled = session("cancel", "10:00", "11:00").copy(classStatus = "CANCELLED")
        val later = session("later", "15:00", "16:00")
        assertEquals(later, (LiveClassSelector.resolveLiveClassState(listOf(cancelled, later), now = now) as LiveClassUiState.AwaitingUpcoming).nextSession)
    }
    @Test fun cancelledClassCanBeViewedWithoutJoinLink() {
        val cancelled = session("cancel", "10:00", "11:00").copy(classStatus = "CANCELLED")
        assertTrue(LiveClassSelector.resolveLiveClassState(listOf(cancelled), now = now) is LiveClassUiState.Cancelled)
    }
    @Test fun documentTokensOnlyGoToExactHttpsBackendOrigin() {
        assertTrue(DocumentPolicy.trustedUrl("https://sureproed.com/api/students/me/download-resume/?v=1"))
        for (url in listOf("https://sureproed.com.attacker.test/a", "https://attacker.test/sureproed.com", "http://sureproed.com/media/x", "https://sureproed.com:444/media/x", "file:///resume.pdf", "https://u@sureproed.com/media/x")) {
            assertFalse(url, DocumentPolicy.trustedUrl(url))
        }
    }
    @Test fun documentSizeValidationRejectsEmptyAndOversizedStreams() {
        assertArrayEquals(byteArrayOf(1, 2), DocumentPolicy.readBounded(byteArrayOf(1, 2).inputStream(), 2))
        assertTrue(runCatching { DocumentPolicy.readBounded(byteArrayOf(1, 2, 3).inputStream(), 2) }.isFailure)
        assertTrue(runCatching { DocumentPolicy.readBounded(byteArrayOf().inputStream()) }.isFailure)
    }
}
