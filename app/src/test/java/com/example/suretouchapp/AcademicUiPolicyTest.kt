package com.example.suretouchapp

import com.example.suretouchapp.data.repository.DashboardSnapshot
import com.example.suretouchapp.data.repository.ModuleGrade
import com.example.suretouchapp.ui.screens.dashboard.AttendanceOverviewState
import com.example.suretouchapp.ui.screens.dashboard.attendanceOverviewState
import com.example.suretouchapp.ui.screens.dashboard.hasPublishedScreeningResult
import com.example.suretouchapp.ui.screens.dashboard.publishedResultCount
import com.example.suretouchapp.ui.screens.dashboard.screeningEmptyMessage
import org.junit.Assert.*
import org.junit.Test

class AcademicUiPolicyTest {
    @Test fun newAccountHasNoResults() {
        val snapshot = DashboardSnapshot()
        assertFalse(snapshot.hasPublishedScreeningResult())
        assertEquals(0, snapshot.publishedResultCount())
        assertTrue(snapshot.screeningEmptyMessage().startsWith("Apply for a course"))
    }

    @Test fun administrativeEnrollmentDoesNotCreateAPassingExam() {
        val snapshot = DashboardSnapshot(cohortCode = "G2-26", screeningQualified = true, screeningGrade = "A")
        assertFalse(snapshot.hasPublishedScreeningResult())
        assertEquals(0, snapshot.publishedResultCount())
        assertTrue(snapshot.screeningEmptyMessage().startsWith("No screening result"))
    }

    @Test fun zeroMarksAreARealPublishedResult() {
        val snapshot = DashboardSnapshot(screeningMarksObtained = "0", screeningPercentage = "0.00")
        assertTrue(snapshot.hasPublishedScreeningResult())
        assertEquals(1, snapshot.publishedResultCount())
    }

    @Test fun placeholdersAndNonFiniteScoresAreNotResults() {
        for (value in listOf("", "--", "null", "NaN", "Infinity", "-1")) {
            assertFalse(DashboardSnapshot(screeningMarksObtained = value, screeningPercentage = value).hasPublishedScreeningResult())
        }
    }

    @Test fun countIncludesPublishedModulesAndActualScreeningOnly() {
        val grades = listOf(ModuleGrade(1, "Module 1", marks = 80), ModuleGrade(2, "Module 2"))
        assertEquals(1, DashboardSnapshot(grades = grades, screeningQualified = true).publishedResultCount())
        assertEquals(2, DashboardSnapshot(grades = grades, screeningPercentage = "80%").publishedResultCount())
    }

    @Test fun enrollmentAndClassLifecycleStayDistinct() {
        assertEquals(AttendanceOverviewState.ENROLLMENT_REQUIRED, attendanceOverviewState(false, 0, 0))
        assertEquals(AttendanceOverviewState.NO_CLASSES, attendanceOverviewState(true, 0, 0))
        assertEquals(AttendanceOverviewState.AWAITING_RESULTS, attendanceOverviewState(true, 3, 0))
        assertEquals(AttendanceOverviewState.RECORDED, attendanceOverviewState(true, 3, 1))
        // Explicit attendance remains valid for a student without a current cohort.
        assertEquals(AttendanceOverviewState.RECORDED, attendanceOverviewState(false, 1, 1))
    }
}
