package com.example.suretouchapp.ui.screens.dashboard

import com.example.suretouchapp.data.repository.DashboardSnapshot

/** Admission eligibility alone is not evidence of an evaluated examination. */
internal fun DashboardSnapshot.hasPublishedScreeningResult(): Boolean =
    screeningMarksObtained.asScore() != null || screeningPercentage.asScore() != null

private fun String?.asScore(): Double? = this?.trim()?.removeSuffix("%")
    ?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

internal fun DashboardSnapshot.publishedResultCount(): Int =
    grades.count { it.marks != null } + if (hasPublishedScreeningResult()) 1 else 0

internal fun DashboardSnapshot.screeningEmptyMessage(): String = when {
    cohortCode != null -> "No screening result is recorded for this enrollment. Module results will appear after evaluation."
    screeningStatus?.uppercase() in setOf("SUBMITTED", "EVALUATED") ->
        "Your screening assessment is awaiting published marks."
    applicationStatus != null -> "Your screening result will appear after you complete the assessment and it is evaluated."
    else -> "Apply for a course to begin. Your grades will appear after an assessment is completed and evaluated."
}

internal enum class AttendanceOverviewState { ENROLLMENT_REQUIRED, NO_CLASSES, AWAITING_RESULTS, RECORDED }

internal fun attendanceOverviewState(
    hasCohort: Boolean,
    sessionCount: Int,
    recordedSessionCount: Int
): AttendanceOverviewState = when {
    recordedSessionCount > 0 -> AttendanceOverviewState.RECORDED
    sessionCount > 0 -> AttendanceOverviewState.AWAITING_RESULTS
    !hasCohort -> AttendanceOverviewState.ENROLLMENT_REQUIRED
    else -> AttendanceOverviewState.NO_CLASSES
}
