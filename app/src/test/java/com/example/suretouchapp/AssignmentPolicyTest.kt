package com.example.suretouchapp

import com.example.suretouchapp.data.model.AssignmentDto
import com.example.suretouchapp.data.model.SubmissionDto
import com.example.suretouchapp.data.repository.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class AssignmentPolicyTest {

    private val baseTime = LocalDateTime.of(2026, 9, 15, 12, 0)

    @Test
    fun urlValidatorAcceptsValidGitHubRepo() {
        val valid = AssignmentValidator.validate(
            rawUrl = "https://github.com/student/uart-project",
            method = SubmissionMethod.GITHUB,
            commitHashRequired = true,
            rawCommitHash = "a72f10c"
        )
        assertTrue(valid.isValid)
        assertNull(valid.errorMessage)
    }

    @Test
    fun urlValidatorRejectsNonGithubUrlWhenGithubSelected() {
        val invalid = AssignmentValidator.validate(
            rawUrl = "https://drive.google.com/file/d/12345",
            method = SubmissionMethod.GITHUB
        )
        assertFalse(invalid.isValid)
        assertTrue(invalid.errorMessage!!.contains("only GitHub Repository"))
    }

    @Test
    fun urlValidatorRequiresCommitHashWhenEnabled() {
        val missingHash = AssignmentValidator.validate(
            rawUrl = "https://github.com/student/uart-project",
            method = SubmissionMethod.GITHUB,
            commitHashRequired = true,
            rawCommitHash = ""
        )
        assertFalse(missingHash.isValid)
        assertTrue(missingHash.errorMessage!!.contains("Commit Hash is required"))

        val invalidHex = AssignmentValidator.validate(
            rawUrl = "https://github.com/student/uart-project",
            method = SubmissionMethod.GITHUB,
            commitHashRequired = true,
            rawCommitHash = "not-a-valid-hex!"
        )
        assertFalse(invalidHex.isValid)
        assertTrue(invalidHex.errorMessage!!.contains("Invalid commit hash"))
    }

    @Test
    fun urlValidatorAcceptsGoogleDriveLink() {
        val validDrive = AssignmentValidator.validate(
            rawUrl = "https://drive.google.com/file/d/1B2C3D4E5F/view?usp=sharing",
            method = SubmissionMethod.GOOGLE_DRIVE
        )
        assertTrue(validDrive.isValid)
    }

    @Test
    fun assignmentIsUpcomingBeforeStartTime() {
        val assignment = AssignmentDto(
            id = "assign-1",
            title = "VLSI Design",
            beginDate = "2026-09-16T10:00:00",
            dueDate = "2026-09-20T23:59:59"
        )
        val state = AssignmentPolicy.resolveState(assignment, emptyList(), now = baseTime)
        assertEquals(AssignmentLifecycleState.UPCOMING, state)
        assertFalse(AssignmentPolicy.canSubmit(state, 0, 3))
    }

    @Test
    fun assignmentIsOpenDuringWindow() {
        val assignment = AssignmentDto(
            id = "assign-2",
            title = "DFT Assignment",
            beginDate = "2026-09-14T00:00:00",
            dueDate = "2026-09-16T23:59:59"
        )
        val state = AssignmentPolicy.resolveState(assignment, emptyList(), now = baseTime)
        assertEquals(AssignmentLifecycleState.OPEN, state)
        assertTrue(AssignmentPolicy.canSubmit(state, 0, 3))
    }

    @Test
    fun assignmentIsClosedAfterDeadlineWithoutLateSubmission() {
        val assignment = AssignmentDto(
            id = "assign-3",
            title = "Closed Assignment",
            beginDate = "2026-09-10T00:00:00",
            dueDate = "2026-09-14T23:59:59",
            lateSubmissionAllowed = false
        )
        val state = AssignmentPolicy.resolveState(assignment, emptyList(), now = baseTime)
        assertEquals(AssignmentLifecycleState.CLOSED, state)
        assertFalse(AssignmentPolicy.canSubmit(state, 0, 3))
    }

    @Test
    fun assignmentShowsResubmissionRequiredWithAttemptsRemaining() {
        val assignment = AssignmentDto(
            id = "assign-4",
            title = "Resubmission Test",
            beginDate = "2026-09-10T00:00:00",
            dueDate = "2026-09-20T23:59:59"
        )
        val sub = SubmissionDto(
            id = "sub-1",
            assignment = "assign-4",
            attemptNumber = 1,
            submissionStatus = "RESUBMISSION_REQUIRED",
            resubmissionReason = "Testbench does not test overflow conditions."
        )
        val state = AssignmentPolicy.resolveState(assignment, listOf(sub), now = baseTime)
        assertEquals(AssignmentLifecycleState.RESUBMISSION_REQUIRED, state)
        assertTrue(AssignmentPolicy.canSubmit(state, 1, 3))
    }

    @Test
    fun assignmentMaxAttemptsReachedBlocksSubmission() {
        assertFalse(AssignmentPolicy.canSubmit(AssignmentLifecycleState.OPEN, attemptsMade = 3, maxAttempts = 3))
    }

    @Test
    fun assignmentIsEvaluatedWhenMarksPresent() {
        val assignment = AssignmentDto(
            id = "assign-5",
            title = "Graded Task"
        )
        val sub = SubmissionDto(
            id = "sub-graded",
            assignment = "assign-5",
            evaluated = true,
            marksObtained = "18.00",
            feedback = "Good implementation."
        )
        val state = AssignmentPolicy.resolveState(assignment, listOf(sub), now = baseTime)
        assertEquals(AssignmentLifecycleState.EVALUATED, state)
    }

    @Test
    fun submissionOnDeadlineDateIsNotLateEvenIfBackendFlaggedLate() {
        val assignment = AssignmentDto(
            id = "assign-6",
            title = "Test Assignment",
            dueDate = "2026-09-11"
        )
        // Student submitted at 11:27 AM on Sep 11, 2026.
        val sub = SubmissionDto(
            id = "sub-on-time",
            assignment = "assign-6",
            submittedAt = "2026-09-11T11:27:20.320416+05:30",
            isLate = true // Backend erroneously flagged is_late because deadline defaulted to midnight 00:00:00
        )
        val isLate = AssignmentPolicy.isSubmissionLate(sub, assignment, now = baseTime)
        assertFalse("Submission on due date before end of day must NOT be late", isLate)

        val state = AssignmentPolicy.resolveState(assignment, listOf(sub), now = baseTime)
        assertEquals(AssignmentLifecycleState.SUBMITTED, state)
    }

    @Test
    fun submissionPastDeadlineIsCorrectlyFlaggedLate() {
        val assignment = AssignmentDto(
            id = "assign-7",
            title = "Strict Deadline",
            dueDate = "2026-09-10T18:00:00"
        )
        val sub = SubmissionDto(
            id = "sub-late",
            assignment = "assign-7",
            submittedAt = "2026-09-11T11:27:20.320416+05:30",
            isLate = true
        )
        val isLate = AssignmentPolicy.isSubmissionLate(sub, assignment, now = baseTime)
        assertTrue("Submission after deadline should be late", isLate)

        val state = AssignmentPolicy.resolveState(assignment, listOf(sub), now = baseTime)
        assertEquals(AssignmentLifecycleState.LATE_SUBMITTED, state)
    }

    @Test
    fun formatMentorNameFiltersOutRawUuids() {
        assertEquals("Faculty Lead & Mentors", AssignmentPolicy.formatMentorName(null, null))
        assertEquals("Faculty Lead & Mentors", AssignmentPolicy.formatMentorName("237917b3-ccf6-4295-9655-b1d4db5126c8", "237917b3-ccf6-4295-9655-b1d4db5126c8"))
        assertEquals("Dr. Ramesh Kumar", AssignmentPolicy.formatMentorName("Dr. Ramesh Kumar", "237917b3-ccf6-4295-9655-b1d4db5126c8"))
    }

    @Test
    fun formatSubmissionDateTimeFormatsIsoWithOffsetCorrectly() {
        val formatted = AssignmentPolicy.formatSubmissionDateTime("2026-09-11T11:27:20.320416+05:30")
        assertTrue(formatted.contains("Sep 11, 2026"))
        assertTrue(formatted.contains("11:27"))
    }

    @Test
    fun canSubmitBlocksResubmissionAfterDeadlineHasPassed() {
        val assignment = AssignmentDto(
            id = "assign-past-deadline",
            title = "LFSR Verilog",
            dueDate = "2026-09-11", // Deadline was Sep 11
            lateSubmissionAllowed = false
        )
        val testNow = java.time.LocalDateTime.of(2026, 9, 12, 14, 0) // Current date: Sep 12

        // Student has already submitted Attempt 1 of 3
        val canResubmit = AssignmentPolicy.canSubmit(
            state = AssignmentLifecycleState.SUBMITTED,
            assignment = assignment,
            attemptsMade = 1,
            maxAttempts = 3,
            now = testNow
        )
        assertFalse("Resubmission must be blocked once deadline has passed", canResubmit)

        // But if mentor requested a revision, student can resubmit
        val canResubmitWhenRequested = AssignmentPolicy.canSubmit(
            state = AssignmentLifecycleState.RESUBMISSION_REQUIRED,
            assignment = assignment,
            attemptsMade = 1,
            maxAttempts = 3,
            now = testNow
        )
        assertTrue("Student must be able to submit when revision requested by mentor", canResubmitWhenRequested)
    }
}


