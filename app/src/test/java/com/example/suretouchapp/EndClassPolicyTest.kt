package com.example.suretouchapp

import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.repository.StudentSessionAttendance
import com.example.suretouchapp.data.repository.calculateStudentAttendancePercentage
import com.example.suretouchapp.data.repository.isCancelledSession
import com.example.suretouchapp.data.repository.isCompletedSession
import com.example.suretouchapp.data.repository.studentAttendance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndClassPolicyTest {

    @Test
    fun endingClassMarksSessionAsCompleted() {
        val scheduledSession = AttendanceDto(
            id = "sess-101",
            sessionTitle = "Kotlin Coroutines Masterclass",
            date = "2026-09-17",
            startTime = "10:00:00",
            endTime = "11:00:00",
            classStatus = "SCHEDULED",
            conducted = true
        )

        assertFalse(scheduledSession.isCompletedSession())

        // Simulating the payload and optimistic update produced by handleEndSession
        val endClassPatchBody = mapOf(
            "conducted" to false,
            "end_time" to "11:05:00",
            "class_status" to "COMPLETED"
        )
        val endedSession = scheduledSession.copy(
            classStatus = endClassPatchBody["class_status"] as String,
            effectiveStatus = endClassPatchBody["class_status"] as String,
            conducted = endClassPatchBody["conducted"] as Boolean,
            endTime = endClassPatchBody["end_time"] as String
        )

        assertTrue(endedSession.isCompletedSession())
        assertFalse(endedSession.isCancelledSession())
        assertEquals("COMPLETED", endedSession.classStatus)
        assertEquals("11:05:00", endedSession.endTime)
    }

    @Test
    fun endedClassParticipatesInAttendancePercentageCalculation() {
        val session1 = AttendanceDto(
            id = "sess-1",
            classStatus = "COMPLETED",
            attendees = listOf("stu-1"),
            conducted = false
        )
        val session2 = AttendanceDto(
            id = "sess-2",
            classStatus = "SCHEDULED",
            attendees = emptyList(),
            conducted = true
        )

        // Only session 1 is completed
        val initialPercentage = calculateStudentAttendancePercentage(listOf(session1, session2), setOf("stu-1"))
        assertNotNull(initialPercentage)
        assertEquals(100.0, initialPercentage!!, 0.001)

        // End session 2 without student attending
        val endedSession2 = session2.copy(
            classStatus = "COMPLETED",
            effectiveStatus = "COMPLETED",
            conducted = false
        )
        val updatedPercentage = calculateStudentAttendancePercentage(listOf(session1, endedSession2), setOf("stu-1"))
        assertNotNull(updatedPercentage)
        assertEquals(50.0, updatedPercentage!!, 0.001)
    }

    @Test
    fun endClassActionAvailabilityPolicy() {
        fun canEndClass(session: AttendanceDto, isReadOnly: Boolean): Boolean {
            val completed = session.isCompletedSession()
            val isCancelled = session.isCancelledSession()
            return !completed && !isCancelled && !isReadOnly
        }

        val activeScheduled = AttendanceDto(classStatus = "SCHEDULED")
        assertTrue(canEndClass(activeScheduled, isReadOnly = false))

        val readOnlyCohort = AttendanceDto(classStatus = "SCHEDULED")
        assertFalse(canEndClass(readOnlyCohort, isReadOnly = true))

        val completedClass = AttendanceDto(classStatus = "COMPLETED")
        assertFalse(canEndClass(completedClass, isReadOnly = false))

        val cancelledClass = AttendanceDto(classStatus = "CANCELLED")
        assertFalse(canEndClass(cancelledClass, isReadOnly = false))
    }
}
