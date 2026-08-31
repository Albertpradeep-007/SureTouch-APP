package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.model.AttendanceDto
import java.time.LocalDate
import java.time.LocalTime

enum class StudentSessionAttendance {
    PRESENT,
    BELOW_THRESHOLD,
    ABSENT,
    PENDING,
    CANCELLED
}

private val SESSION_DATE_FORMATTERS = listOf(
    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
    java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.US),
    java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy", java.util.Locale.US),
    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", java.util.Locale.US),
    java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd", java.util.Locale.US)
)

private fun parseSessionLocalDate(dateStr: String?): LocalDate? {
    if (dateStr.isNullOrBlank()) return null
    val clean = dateStr.trim().take(11).trim()
    for (formatter in SESSION_DATE_FORMATTERS) {
        try {
            return LocalDate.parse(clean, formatter)
        } catch (_: Exception) {}
    }
    return null
}

fun AttendanceDto.isCancelledSession(): Boolean =
    classStatus.equals("CANCELLED", ignoreCase = true) ||
        effectiveStatus.equals("CANCELLED", ignoreCase = true)

fun AttendanceDto.isCompletedSession(): Boolean {
    if (isCancelledSession()) return false
    if (classStatus.equals("COMPLETED", ignoreCase = true) ||
        effectiveStatus.equals("COMPLETED", ignoreCase = true) ||
        conducted
    ) {
        return true
    }
    val parsedDate = parseSessionLocalDate(date)
    if (parsedDate != null) {
        val today = LocalDate.now()
        if (parsedDate.isBefore(today)) {
            return true
        } else if (parsedDate.isEqual(today)) {
            val end = endTime?.trim()?.take(5)
            if (!end.isNullOrBlank()) {
                try {
                    val parsedEnd = LocalTime.parse(end)
                    if (LocalTime.now().isAfter(parsedEnd)) {
                        return true
                    }
                } catch (_: Exception) {}
            }
        }
    }
    return false
}

fun AttendanceDto.studentAttendance(
    studentIdentifiers: Set<String> = emptySet()
): StudentSessionAttendance {
    if (isCancelledSession()) return StudentSessionAttendance.CANCELLED

    val normalizedIds = studentIdentifiers.filter(String::isNotBlank).map(String::lowercase).toSet()
    val isListedAsPresent = normalizedIds.isNotEmpty() && attendees.any { it.trim().lowercase() in normalizedIds }

    studentDashboardData?.let { dashboardData ->
        val percentage = dashboardData.attendancePercentage ?: 0.0
        return when {
            dashboardData.attendanceStatus.equals("PRESENT", ignoreCase = true) || percentage >= 40.0 || isListedAsPresent || present ->
                StudentSessionAttendance.PRESENT
            dashboardData.attendanceStatus.equals("BELOW THRESHOLD", ignoreCase = true) || percentage > 0.0 ->
                StudentSessionAttendance.BELOW_THRESHOLD
            dashboardData.attendanceStatus.equals("ABSENT", ignoreCase = true) ->
                StudentSessionAttendance.ABSENT
            isCompletedSession() ->
                StudentSessionAttendance.ABSENT
            else -> StudentSessionAttendance.PENDING
        }
    }

    if (present || isListedAsPresent) {
        return StudentSessionAttendance.PRESENT
    }

    if (isCompletedSession()) {
        return StudentSessionAttendance.ABSENT
    }

    return StudentSessionAttendance.PENDING
}

/** Returns null until at least one completed, non-cancelled session exists. */
fun calculateStudentAttendancePercentage(
    sessions: List<AttendanceDto>,
    studentIdentifiers: Set<String> = emptySet()
): Double? {
    val recorded = sessions.map { it.studentAttendance(studentIdentifiers) }
        .filter {
            it == StudentSessionAttendance.PRESENT ||
                it == StudentSessionAttendance.BELOW_THRESHOLD ||
                it == StudentSessionAttendance.ABSENT
        }
    if (recorded.isEmpty()) return null
    return recorded.count { it == StudentSessionAttendance.PRESENT } * 100.0 / recorded.size
}

object ClassSchedulePolicy {
    fun parseMinutes(value: String): Int? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null

        val timeFormatters = listOf(
            java.time.format.DateTimeFormatter.ISO_LOCAL_TIME,
            java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.US),
            java.time.format.DateTimeFormatter.ofPattern("H:mm", java.util.Locale.US),
            java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US),
            java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.US),
            java.time.format.DateTimeFormatter.ofPattern("h:mma", java.util.Locale.US),
            java.time.format.DateTimeFormatter.ofPattern("hh:mma", java.util.Locale.US)
        )

        for (formatter in timeFormatters) {
            try {
                val lt = LocalTime.parse(trimmed.uppercase(java.util.Locale.US), formatter)
                return lt.hour * 60 + lt.minute
            } catch (_: Exception) {}
        }

        val regex = Regex("(?i)^(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*(AM|PM)?$")
        val match = regex.find(trimmed)
        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            val ampm = match.groupValues[3].uppercase(java.util.Locale.US)
            if (ampm == "PM" && hour < 12) hour += 12
            if (ampm == "AM" && hour == 12) hour = 0
            if (hour in 0..23 && minute in 0..59) {
                return hour * 60 + minute
            }
        }

        return null
    }

    fun isValidTimeRange(start: String, end: String): Boolean {
        val startMinutes = parseMinutes(start) ?: return false
        val endMinutes = parseMinutes(end) ?: return false
        return startMinutes < endMinutes
    }

    fun getTimeRangeError(start: String, end: String): String? {
        if (start.isBlank() || end.isBlank()) return null
        val startMinutes = parseMinutes(start)
        val endMinutes = parseMinutes(end)
        if (startMinutes == null) return "Invalid start time format."
        if (endMinutes == null) return "Invalid end time format."
        if (startMinutes >= endMinutes) {
            return "End time must be after start time on the same day."
        }
        if (endMinutes - startMinutes < 5) {
            return "Class duration must be at least 5 minutes."
        }
        return null
    }

    fun overlaps(start1: String, end1: String, start2: String, end2: String): Boolean {
        val s1 = parseMinutes(start1) ?: return false
        val e1 = parseMinutes(end1) ?: return false
        val s2 = parseMinutes(start2) ?: return false
        val e2 = parseMinutes(end2) ?: return false
        return s1 < e2 && e1 > s2
    }

    fun findConflict(
        sessions: List<AttendanceDto>,
        cohortId: String,
        date: String,
        start: String,
        end: String,
        excludedSessionId: String? = null
    ): AttendanceDto? {
        if (cohortId.isBlank() || date.isBlank() || !isValidTimeRange(start, end)) return null
        return sessions.firstOrNull { session ->
            session.id != excludedSessionId &&
                (session.cohort == cohortId || session.cohortCode == cohortId) &&
                session.date.take(10) == date.take(10) &&
                !session.isCancelledSession() &&
                overlaps(
                    start,
                    end,
                    session.startTime?.take(8).orEmpty(),
                    session.endTime?.take(8).orEmpty()
                )
        }
    }
}

object LiveClassSelector {
    fun activeSession(
        sessions: List<AttendanceDto>,
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now(),
        allowedCohorts: Set<String> = emptySet()
    ): AttendanceDto? {
        return sessions.asSequence()
            .filter { !it.meetingLink.isNullOrBlank() }
            .filter { !it.isCancelledSession() && !it.isCompletedSession() }
            .filter { session ->
                allowedCohorts.isEmpty() || session.cohort in allowedCohorts || session.cohortCode in allowedCohorts
            }
            .filter { it.date.take(10) == date.toString() }
            .filter { session ->
                val explicitlyLive = session.classStatus.isLiveStatus() || session.effectiveStatus.isLiveStatus()
                // Prefer the clock window when it is available so a stale LIVE
                // backend status cannot keep an old class visible indefinitely.
                isWithinWindow(time, session.startTime, session.endTime) ?: explicitlyLive
            }
            .sortedBy { it.startTime }
            .firstOrNull()
    }

    private fun String?.isLiveStatus(): Boolean =
        this?.uppercase() in setOf("LIVE", "ONGOING", "IN_PROGRESS")

    private fun isWithinWindow(now: LocalTime, start: String?, end: String?): Boolean? {
        val startTime = start?.take(5)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
        val endTime = end?.take(5)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
        return !now.isBefore(startTime) && now.isBefore(endTime)
    }
}
