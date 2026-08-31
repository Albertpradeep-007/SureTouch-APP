package com.example.suretouchapp

import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.model.StudentAttendanceDataDto
import com.example.suretouchapp.data.repository.ClassSchedulePolicy
import com.example.suretouchapp.data.repository.LiveClassSelector
import com.example.suretouchapp.data.repository.StudentSessionAttendance
import com.example.suretouchapp.data.repository.calculateStudentAttendancePercentage
import com.example.suretouchapp.data.repository.studentAttendance
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ClassSessionPolicyTest {
    @Test
    fun scheduledSessionIsPendingNotPresentOrAbsent() {
        val session = AttendanceDto(classStatus = "SCHEDULED", conducted = true, present = false)
        assertEquals(StudentSessionAttendance.PENDING, session.studentAttendance())
        assertNull(calculateStudentAttendancePercentage(listOf(session)))
    }

    @Test
    fun attendancePercentageUsesOnlyCompletedSessions() {
        val sessions = listOf(
            AttendanceDto(id = "1", classStatus = "COMPLETED", present = true),
            AttendanceDto(id = "2", classStatus = "COMPLETED", present = false),
            AttendanceDto(id = "3", classStatus = "SCHEDULED", present = false)
        )
        assertEquals(50.0, calculateStudentAttendancePercentage(sessions)!!, 0.001)
    }

    @Test
    fun attendeeIdentifiersCanConfirmPresence() {
        val session = AttendanceDto(classStatus = "COMPLETED", attendees = listOf("student-42"))
        assertEquals(StudentSessionAttendance.PRESENT, session.studentAttendance(setOf("student-42")))
    }

    @Test
    fun backendStudentDashboardDataIsAuthoritative() {
        val readyPresent = AttendanceDto(
            classStatus = "COMPLETED",
            studentDashboardData = StudentAttendanceDataDto(
                status = "READY",
                attendancePercentage = 82.0,
                attendanceStatus = "PRESENT"
            )
        )
        val readyBelowThreshold = AttendanceDto(
            classStatus = "COMPLETED",
            attendees = listOf("student-42"),
            studentDashboardData = StudentAttendanceDataDto(
                status = "READY",
                attendancePercentage = 25.0,
                attendanceStatus = "BELOW THRESHOLD"
            )
        )
        val notReady = AttendanceDto(
            classStatus = "COMPLETED",
            studentDashboardData = StudentAttendanceDataDto(status = "NOT_READY")
        )

        assertEquals(StudentSessionAttendance.PRESENT, readyPresent.studentAttendance())
        assertEquals(StudentSessionAttendance.BELOW_THRESHOLD, readyBelowThreshold.studentAttendance(setOf("student-42")))
        assertEquals(StudentSessionAttendance.PENDING, notReady.studentAttendance())
        assertEquals(50.0, calculateStudentAttendancePercentage(listOf(readyPresent, readyBelowThreshold, notReady))!!, 0.001)
    }

    @Test
    fun overlappingClassInSameCohortConflicts() {
        val existing = AttendanceDto(
            id = "existing",
            cohort = "cohort-a",
            date = "2026-08-31",
            startTime = "10:00:00",
            endTime = "11:00:00",
            classStatus = "SCHEDULED"
        )
        assertSame(
            existing,
            ClassSchedulePolicy.findConflict(
                listOf(existing), "cohort-a", "2026-08-31", "10:30", "11:30"
            )
        )
        assertNull(
            ClassSchedulePolicy.findConflict(
                listOf(existing), "cohort-b", "2026-08-31", "10:30", "11:30"
            )
        )
        assertSame(
            existing,
            ClassSchedulePolicy.findConflict(
                listOf(existing), "cohort-a", "2026-08-31", "10:00", "11:00"
            )
        )
        assertNull(
            ClassSchedulePolicy.findConflict(
                listOf(existing), "cohort-a", "2026-08-31", "11:00", "12:00"
            )
        )
    }

    @Test
    fun liveClassSelectorRejectsStoredPastAndCompletedClasses() {
        val storedPast = AttendanceDto(
            id = "past", date = "2026-08-30", startTime = "10:00", endTime = "11:00",
            classStatus = "SCHEDULED", meetingLink = "https://meet.google.com/past"
        )
        val completed = AttendanceDto(
            id = "done", date = "2026-08-31", startTime = "10:00", endTime = "11:00",
            classStatus = "COMPLETED", meetingLink = "https://meet.google.com/done"
        )
        val active = AttendanceDto(
            id = "active", date = "2026-08-31", startTime = "10:00", endTime = "11:00",
            classStatus = "SCHEDULED", meetingLink = "https://meet.google.com/live"
        )
        assertSame(
            active,
            LiveClassSelector.activeSession(
                listOf(storedPast, completed, active),
                LocalDate.of(2026, 8, 31),
                LocalTime.of(10, 30)
            )
        )
    }

    @Test
    fun staleLiveStatusOutsideItsTimeWindowIsRejected() {
        val staleLive = AttendanceDto(
            id = "stale", date = "2026-08-31", startTime = "08:00", endTime = "09:00",
            classStatus = "LIVE", meetingLink = "https://meet.google.com/stale"
        )
        assertNull(
            LiveClassSelector.activeSession(
                listOf(staleLive),
                LocalDate.of(2026, 8, 31),
                LocalTime.of(10, 30)
            )
        )
    }
}
