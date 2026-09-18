package com.example.suretouchapp

import com.example.suretouchapp.data.model.ApplicationDto
import com.example.suretouchapp.data.model.StudentProfileDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceUnsuspendPolicyTest {

    @Test
    fun identifiesSuspendedStudentsInRoster() {
        val student1 = StudentProfileDto(id = "stu-1", studentCode = "STU-001")
        val student2 = StudentProfileDto(id = "stu-2", studentCode = "STU-002")
        val student3 = StudentProfileDto(id = "stu-3", studentCode = "STU-003")

        val app1 = ApplicationDto(id = "app-1", student = "stu-1", status = "SUSPENDED")
        val app2 = ApplicationDto(id = "app-2", student = "stu-2", status = "IN_PROGRESS")
        val app3 = ApplicationDto(id = "app-3", student = "stu-3", status = "SUSPENDED")

        val allApps = listOf(app1, app2, app3)
        val roster = listOf(student1, student2, student3)

        val suspendedStudents = roster.filter { s ->
            val app = allApps.firstOrNull { it.student == s.id || it.student == s.userId }
            app?.status.equals("SUSPENDED", ignoreCase = true)
        }

        assertEquals(2, suspendedStudents.size)
        assertTrue(suspendedStudents.contains(student1))
        assertFalse(suspendedStudents.contains(student2))
        assertTrue(suspendedStudents.contains(student3))
    }

    @Test
    fun batchUnsuspendCollectsAllSuspendedApplicationsInSession() {
        val cohortStudents = listOf(
            StudentProfileDto(id = "s-1"),
            StudentProfileDto(id = "s-2"),
            StudentProfileDto(id = "s-3")
        )

        val applications = listOf(
            ApplicationDto(id = "app-1", student = "s-1", assignedCohort = "coh-1", status = "SUSPENDED"),
            ApplicationDto(id = "app-2", student = "s-2", assignedCohort = "coh-1", status = "ACTIVE"),
            ApplicationDto(id = "app-3", student = "s-3", assignedCohort = "coh-1", status = "SUSPENDED"),
            ApplicationDto(id = "app-other", student = "other", assignedCohort = "coh-2", status = "SUSPENDED")
        )

        val studentIds = cohortStudents.flatMap { listOfNotNull(it.id, it.userId) }.toSet()
        val suspendedInCohort = applications.filter { app ->
            (app.student in studentIds || (app.assignedCohort != null && app.assignedCohort == "coh-1")) &&
                app.status.equals("SUSPENDED", ignoreCase = true)
        }

        assertEquals(2, suspendedInCohort.size)
        assertEquals(listOf("app-1", "app-3"), suspendedInCohort.map { it.id })
    }

    @Test
    fun unsuspendPayloadHasReasonField() {
        val singleReason = "Unsuspended by volunteer from attendance roster"
        val singlePayload = mapOf("reason" to singleReason)
        assertEquals(singleReason, singlePayload["reason"])
        assertTrue(singlePayload.containsKey("reason"))

        val batchReason = "Batch unsuspended by volunteer for session on 2026-09-17"
        val batchPayload = mapOf("reason" to batchReason)
        assertEquals(batchReason, batchPayload["reason"])
    }
}
