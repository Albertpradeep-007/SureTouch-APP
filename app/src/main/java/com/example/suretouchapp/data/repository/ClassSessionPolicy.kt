package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.model.AttendanceDto
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class StudentSessionAttendance {
    PRESENT,
    BELOW_THRESHOLD,
    ABSENT,
    PENDING,
    CANCELLED
}

enum class TimetableClassStatus {
    NO_CLASS_SCHEDULED,
    AWAITING_UPCOMING,
    UPCOMING,
    ONGOING,
    ENDED,
    CANCELLED,
    RESCHEDULED
}

sealed interface LiveClassUiState {
    data class Ongoing(val session: AttendanceDto) : LiveClassUiState
    data class StartingSoon(val session: AttendanceDto, val minutesUntil: Long) : LiveClassUiState
    data class AwaitingUpcoming(val nextSession: AttendanceDto) : LiveClassUiState
    data class Cancelled(val session: AttendanceDto, val reason: String?) : LiveClassUiState
    data object NoClassScheduled : LiveClassUiState
}

private val SESSION_DATE_FORMATTERS = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE,
    DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US),
    DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US),
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US),
    DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)
)

fun parseSessionLocalDate(dateStr: String?): LocalDate? {
    if (dateStr.isNullOrBlank()) return null
    val clean = dateStr.trim().substringBefore("T").take(10).trim()
    for (formatter in SESSION_DATE_FORMATTERS) {
        try {
            return LocalDate.parse(clean, formatter)
        } catch (_: Exception) {}
    }
    val full = dateStr.trim()
    for (formatter in SESSION_DATE_FORMATTERS) {
        try {
            return LocalDate.parse(full, formatter)
        } catch (_: Exception) {}
    }
    return null
}

fun AttendanceDto.isCancelledSession(): Boolean =
    classStatus.equals("CANCELLED", ignoreCase = true) ||
        effectiveStatus.equals("CANCELLED", ignoreCase = true)

fun AttendanceDto.isCompletedSession(): Boolean {
    if (isCancelledSession()) return false
    val status = (effectiveStatus ?: classStatus)?.trim()?.uppercase(Locale.US)
    if (status == "COMPLETED") {
        return true
    }
    if (status == "SCHEDULED" || status == "UPCOMING" || status == "LIVE" || status == "ONGOING") {
        return false
    }
    if (conducted && status == null) {
        return true
    }
    val parsedDate = parseSessionLocalDate(date)
    if (parsedDate != null) {
        val today = LocalDate.now()
        if (parsedDate.isBefore(today)) {
            return true
        } else if (parsedDate.isEqual(today)) {
            val parsedEnd = ClassSchedulePolicy.parseLocalTime(endTime)
            if (parsedEnd != null && LocalTime.now().isAfter(parsedEnd.plusMinutes(15))) {
                return true
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
        if (!dashboardData.status.equals("READY", ignoreCase = true)) {
            return StudentSessionAttendance.PENDING
        }
        val percentage = dashboardData.attendancePercentage ?: 0.0
        val finalizedStatus = dashboardData.attendanceStatus?.trim()?.uppercase()
        return when {
            finalizedStatus == "PRESENT" -> StudentSessionAttendance.PRESENT
            finalizedStatus == "BELOW THRESHOLD" -> StudentSessionAttendance.BELOW_THRESHOLD
            finalizedStatus == "ABSENT" -> StudentSessionAttendance.ABSENT
            percentage >= 40.0 || isListedAsPresent || present -> StudentSessionAttendance.PRESENT
            percentage > 0.0 -> StudentSessionAttendance.BELOW_THRESHOLD
            isCompletedSession() -> StudentSessionAttendance.ABSENT
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
    val nonCancelled = sessions.filterNot { it.isCancelledSession() }
    val recorded = nonCancelled.map { it.studentAttendance(studentIdentifiers) }
        .filter {
            it == StudentSessionAttendance.PRESENT ||
                it == StudentSessionAttendance.BELOW_THRESHOLD ||
                it == StudentSessionAttendance.ABSENT
        }
    if (recorded.isEmpty()) return null
    return recorded.count { it == StudentSessionAttendance.PRESENT } * 100.0 / recorded.size
}

object ClassSchedulePolicy {
    fun parseLocalTime(value: String?): LocalTime? {
        val trimmed = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        val timeFormatters = listOf(
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("HH:mm", Locale.US),
            DateTimeFormatter.ofPattern("H:mm", Locale.US),
            DateTimeFormatter.ofPattern("h:mm a", Locale.US),
            DateTimeFormatter.ofPattern("hh:mm a", Locale.US),
            DateTimeFormatter.ofPattern("h:mma", Locale.US),
            DateTimeFormatter.ofPattern("hh:mma", Locale.US)
        )
        for (formatter in timeFormatters) {
            try {
                return LocalTime.parse(trimmed.uppercase(Locale.US), formatter)
            } catch (_: Exception) {}
        }
        val regex = Regex("(?i)^(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*(AM|PM)?$")
        val match = regex.find(trimmed)
        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            val ampm = match.groupValues[3].uppercase(Locale.US)
            if (ampm == "PM" && hour < 12) hour += 12
            if (ampm == "AM" && hour == 12) hour = 0
            if (hour in 0..23 && minute in 0..59) {
                return LocalTime.of(hour, minute)
            }
        }
        return null
    }

    fun parseMinutes(value: String): Int? {
        val lt = parseLocalTime(value) ?: return null
        return lt.hour * 60 + lt.minute
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

object TimetableSessionPolicy {
    fun resolveStatus(session: AttendanceDto, now: LocalDateTime = LocalDateTime.now()): TimetableClassStatus {
        if (session.isCancelledSession()) return TimetableClassStatus.CANCELLED
        val backendStatus = session.effectiveStatus?.trim()?.uppercase(Locale.US)
            ?: session.classStatus?.trim()?.uppercase(Locale.US)

        if (backendStatus == "CANCELLED") return TimetableClassStatus.CANCELLED
        if (backendStatus == "RESCHEDULED") return TimetableClassStatus.RESCHEDULED

        val date = parseSessionLocalDate(session.date)
        val startTime = ClassSchedulePolicy.parseLocalTime(session.startTime)
        val endTime = ClassSchedulePolicy.parseLocalTime(session.endTime) ?: startTime?.plusHours(1)

        if (date == null || startTime == null || endTime == null) {
            return if (session.isCompletedSession()) TimetableClassStatus.ENDED else TimetableClassStatus.UPCOMING
        }

        val startAt = LocalDateTime.of(date, startTime)
        val endAt = LocalDateTime.of(date, endTime)

        if (backendStatus == "COMPLETED" || (session.conducted && backendStatus != "SCHEDULED" && backendStatus != "UPCOMING" && !session.isCancelledSession())) {
            return TimetableClassStatus.ENDED
        }

        return when {
            now.isBefore(startAt) -> {
                val isToday = now.toLocalDate().isEqual(date)
                val minutesUntil = Duration.between(now, startAt).toMinutes()
                if (isToday && minutesUntil in 0..60) {
                    TimetableClassStatus.UPCOMING
                } else {
                    TimetableClassStatus.AWAITING_UPCOMING
                }
            }
            !now.isAfter(endAt.plusMinutes(15)) -> TimetableClassStatus.ONGOING
            backendStatus == "SCHEDULED" || backendStatus == "UPCOMING" -> TimetableClassStatus.UPCOMING
            else -> TimetableClassStatus.ENDED
        }
    }

    fun findNextActiveSession(
        sessions: List<AttendanceDto>,
        now: LocalDateTime = LocalDateTime.now(),
        allowedCohorts: Set<String> = emptySet()
    ): Pair<AttendanceDto?, TimetableClassStatus> {
        val cohortFiltered = sessions.filter { session ->
            !session.isCancelledSession() &&
                (allowedCohorts.isEmpty() || session.cohort in allowedCohorts || session.cohortCode in allowedCohorts)
        }

        // 1. Is there an active ONGOING session right now?
        val ongoing = cohortFiltered.firstOrNull { resolveStatus(it, now) == TimetableClassStatus.ONGOING }
        if (ongoing != null) {
            return Pair(ongoing, TimetableClassStatus.ONGOING)
        }

        // 2. Search for earliest future session whose endAt is after now
        val upcomingCandidates = cohortFiltered.mapNotNull { session ->
            val date = parseSessionLocalDate(session.date) ?: return@mapNotNull null
            val startTime = ClassSchedulePolicy.parseLocalTime(session.startTime) ?: return@mapNotNull null
            val endTime = ClassSchedulePolicy.parseLocalTime(session.endTime) ?: startTime.plusHours(1)
            val startAt = LocalDateTime.of(date, startTime)
            val endAt = LocalDateTime.of(date, endTime)
            if (endAt.isAfter(now)) {
                Triple(session, startAt, endAt)
            } else null
        }.sortedBy { it.second }

        val next = upcomingCandidates.firstOrNull() ?: return Pair(null, TimetableClassStatus.NO_CLASS_SCHEDULED)
        val nextSession = next.first
        val status = resolveStatus(nextSession, now)
        return Pair(nextSession, status)
    }

    fun getWeekDateRange(now: LocalDate = LocalDate.now()): ClosedRange<LocalDate> {
        val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return monday..sunday
    }

    fun filterCurrentWeekSessions(sessions: List<AttendanceDto>, now: LocalDate = LocalDate.now()): List<AttendanceDto> {
        val range = getWeekDateRange(now)
        return sessions.filter { session ->
            val date = parseSessionLocalDate(session.date) ?: return@filter false
            date in range
        }.sortedWith(compareBy<AttendanceDto> { parseSessionLocalDate(it.date) }.thenBy { it.startTime })
    }

    fun filterHistorySessions(sessions: List<AttendanceDto>, now: LocalDate = LocalDate.now()): List<AttendanceDto> {
        val range = getWeekDateRange(now)
        return sessions.filter { session ->
            val date = parseSessionLocalDate(session.date) ?: return@filter false
            val isPriorWeek = date.isBefore(range.start)
            val isCurrentWeekPastOrCancelledOrRescheduled = date in range && (
                session.isCancelledSession() ||
                    session.classStatus.equals("RESCHEDULED", ignoreCase = true) ||
                    session.isCompletedSession()
            )
            isPriorWeek || isCurrentWeekPastOrCancelledOrRescheduled
        }.sortedWith(compareByDescending<AttendanceDto> { parseSessionLocalDate(it.date) }.thenByDescending { it.startTime })
    }
}

object LiveClassSelector {
    fun resolveLiveClassState(
        sessions: List<AttendanceDto>,
        allowedCohorts: Set<String> = emptySet(),
        now: LocalDateTime = LocalDateTime.now()
    ): LiveClassUiState {
        val cohortFiltered = sessions.filter { session ->
            allowedCohorts.isEmpty() || session.cohort in allowedCohorts || session.cohortCode in allowedCohorts
        }

        val today = now.toLocalDate()
        val todayCancelled = cohortFiltered.firstOrNull {
            it.isCancelledSession() && parseSessionLocalDate(it.date)?.isEqual(today) == true
        }

        // 1. Check for ONGOING session
        for (session in cohortFiltered) {
            if (session.meetingLink.isNullOrBlank()) continue
            if (session.isCancelledSession()) continue
            if (session.classStatus.equals("COMPLETED", ignoreCase = true) ||
                session.effectiveStatus.equals("COMPLETED", ignoreCase = true)
            ) continue
            val date = parseSessionLocalDate(session.date) ?: continue
            if (!date.isEqual(today)) continue
            val startTime = ClassSchedulePolicy.parseLocalTime(session.startTime) ?: continue
            val endTime = ClassSchedulePolicy.parseLocalTime(session.endTime) ?: startTime.plusHours(1)
            val startAt = LocalDateTime.of(date, startTime)
            val endAt = LocalDateTime.of(date, endTime)

            if (!now.isBefore(startAt) && now.isBefore(endAt)) {
                return LiveClassUiState.Ongoing(session)
            }
        }

        // 2. Check for STARTING SOON session (within 15 minutes before start time)
        for (session in cohortFiltered) {
            if (session.meetingLink.isNullOrBlank()) continue
            if (session.isCancelledSession()) continue
            if (session.classStatus.equals("COMPLETED", ignoreCase = true) ||
                session.effectiveStatus.equals("COMPLETED", ignoreCase = true)
            ) continue
            val date = parseSessionLocalDate(session.date) ?: continue
            if (!date.isEqual(today)) continue
            val startTime = ClassSchedulePolicy.parseLocalTime(session.startTime) ?: continue
            val startAt = LocalDateTime.of(date, startTime)

            if (now.isBefore(startAt)) {
                val diff = Duration.between(now, startAt).toMinutes()
                if (diff in 0..15) {
                    return LiveClassUiState.StartingSoon(session, diff)
                }
            }
        }

        // 3. Find next future scheduled session
        val (nextSession, _) = TimetableSessionPolicy.findNextActiveSession(cohortFiltered, now, allowedCohorts)
        if (nextSession != null) {
            if (todayCancelled != null) {
                return LiveClassUiState.Cancelled(todayCancelled, todayCancelled.notes)
            }
            return LiveClassUiState.AwaitingUpcoming(nextSession)
        }

        if (todayCancelled != null) {
            return LiveClassUiState.Cancelled(todayCancelled, todayCancelled.notes)
        }

        return LiveClassUiState.NoClassScheduled
    }

    fun activeSession(
        sessions: List<AttendanceDto>,
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now(),
        allowedCohorts: Set<String> = emptySet()
    ): AttendanceDto? {
        val state = resolveLiveClassState(sessions, allowedCohorts, LocalDateTime.of(date, time))
        return when (state) {
            is LiveClassUiState.Ongoing -> state.session
            is LiveClassUiState.StartingSoon -> state.session
            else -> null
        }
    }
}
