package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.model.AssignmentDto
import com.example.suretouchapp.data.model.SubmissionDto
import java.net.URI
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

enum class AssignmentLifecycleState(val displayName: String) {
    UPCOMING("Upcoming"),
    OPEN("Open"),
    SUBMITTED("Submitted"),
    LATE_SUBMITTED("Late Submitted"),
    UNDER_REVIEW("Under Review"),
    RESUBMISSION_REQUIRED("Resubmission Required"),
    RESUBMITTED("Resubmitted"),
    EVALUATED("Evaluated"),
    CLOSED("Closed")
}

enum class SubmissionMethod(val id: String, val displayName: String, val defaultPlaceholder: String) {
    GITHUB("github", "GitHub Repository", "https://github.com/username/repository"),
    GOOGLE_DRIVE("google_drive", "Google Drive", "https://drive.google.com/file/d/..."),
    ONEDRIVE("onedrive", "OneDrive", "https://1drv.ms/..."),
    GOOGLE_DOCS("google_docs", "Google Docs / Sheets / Slides", "https://docs.google.com/document/d/..."),
    YOUTUBE("youtube", "YouTube", "https://youtube.com/watch?v=..."),
    DEPLOYMENT("deployment", "Deployment URL", "https://my-project.vercel.app"),
    OTHER("other", "Other External URL", "https://...");

    companion object {
        fun fromId(id: String?): SubmissionMethod =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OTHER
    }
}

data class ValidationResult(val isValid: Boolean, val errorMessage: String? = null)

object AssignmentValidator {
    private val GITHUB_COMMIT_REGEX = Regex("^[0-9a-fA-F]{7,40}$")

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains(".") -> "https://$trimmed"
            else -> trimmed
        }
    }

    fun validate(
        rawUrl: String,
        method: SubmissionMethod,
        commitHashRequired: Boolean = false,
        rawCommitHash: String? = null
    ): ValidationResult {
        val url = rawUrl.trim()
        if (url.isBlank()) {
            return ValidationResult(false, "Please enter your submission link.")
        }
        val cleanUrl = normalizeUrl(url)
        val uri = runCatching { URI(cleanUrl) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.US)
        if (host.isNullOrBlank() || (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true))) {
            return ValidationResult(false, "Please enter a valid link (e.g. https://github.com/... or Google Drive URL).")
        }

        when (method) {
            SubmissionMethod.GITHUB -> {
                if (!host.contains("github.com")) {
                    return ValidationResult(
                        false,
                        "Invalid Submission URL. This assignment accepts only GitHub Repository links."
                    )
                }
                val pathSegments = uri.path?.split("/")?.filter { it.isNotBlank() } ?: emptyList()
                if (pathSegments.size < 2) {
                    return ValidationResult(
                        false,
                        "Please provide the full repository path (https://github.com/username/repository)."
                    )
                }
            }
            SubmissionMethod.GOOGLE_DRIVE -> {
                if (!host.contains("drive.google.com") && !host.contains("docs.google.com")) {
                    return ValidationResult(
                        false,
                        "Invalid Submission URL. This assignment accepts only Google Drive links."
                    )
                }
            }
            SubmissionMethod.ONEDRIVE -> {
                if (!host.contains("1drv.ms") && !host.contains("onedrive.live.com") && !host.contains("sharepoint.com")) {
                    return ValidationResult(
                        false,
                        "Invalid Submission URL. This assignment accepts only OneDrive links."
                    )
                }
            }
            SubmissionMethod.GOOGLE_DOCS -> {
                if (!host.contains("docs.google.com") && !host.contains("drive.google.com")) {
                    return ValidationResult(
                        false,
                        "Invalid Submission URL. This assignment accepts only Google Docs, Sheets, or Slides links."
                    )
                }
            }
            SubmissionMethod.YOUTUBE -> {
                if (!host.contains("youtube.com") && !host.contains("youtu.be")) {
                    return ValidationResult(
                        false,
                        "Invalid Submission URL. This assignment accepts only YouTube links."
                    )
                }
            }
            SubmissionMethod.DEPLOYMENT, SubmissionMethod.OTHER -> {
                if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                    return ValidationResult(false, "Please enter a valid URL starting with https://")
                }
            }
        }

        if (commitHashRequired) {
            val hash = rawCommitHash?.trim().orEmpty()
            if (hash.isBlank()) {
                return ValidationResult(
                    false,
                    "GitHub Commit Hash is required for this assignment (e.g. a72f10c)."
                )
            }
            if (!GITHUB_COMMIT_REGEX.matches(hash)) {
                return ValidationResult(
                    false,
                    "Invalid commit hash. Please enter a valid 7 to 40 hexadecimal character SHA (e.g. a72f10c)."
                )
            }
        }

        return ValidationResult(true)
    }
}

object AssignmentPolicy {
    fun resolveState(
        assignment: AssignmentDto,
        userSubmissions: List<SubmissionDto>,
        now: LocalDateTime = LocalDateTime.now()
    ): AssignmentLifecycleState {
        val latestSub = userSubmissions.maxByOrNull { it.attemptNumber }
            ?: userSubmissions.lastOrNull()

        val isEvaluated = latestSub?.evaluated == true ||
            latestSub?.marksObtained != null ||
            assignment.status?.uppercase(Locale.US) in setOf("GRADED", "EVALUATED")
        if (isEvaluated) return AssignmentLifecycleState.EVALUATED

        val subStatus = latestSub?.submissionStatus?.uppercase(Locale.US)
        if (subStatus == "RESUBMISSION_REQUIRED" ||
            (!latestSub?.resubmissionReason.isNullOrBlank()) ||
            (latestSub?.feedback?.contains("resubmit", ignoreCase = true) == true && !latestSub.evaluated)
        ) {
            return AssignmentLifecycleState.RESUBMISSION_REQUIRED
        }

        if (subStatus == "RESUBMITTED") {
            return AssignmentLifecycleState.RESUBMITTED
        }

        if (subStatus == "UNDER_REVIEW") {
            return AssignmentLifecycleState.UNDER_REVIEW
        }

        if (latestSub != null || !assignment.submittedLink.isNullOrBlank() || assignment.status?.uppercase(Locale.US) == "SUBMITTED") {
            val isLate = isSubmissionLate(latestSub, assignment, now)
            return if (isLate) AssignmentLifecycleState.LATE_SUBMITTED else AssignmentLifecycleState.SUBMITTED
        }

        val startDateTime = parseIsoDateTime(assignment.startAt ?: assignment.beginDate)
        if (startDateTime != null && now.isBefore(startDateTime)) {
            return AssignmentLifecycleState.UPCOMING
        }

        val dueDateTime = parseDeadlineDateTime(assignment.dueAt ?: assignment.dueDate)
        if (dueDateTime != null && now.isAfter(dueDateTime)) {
            return if (assignment.lateSubmissionAllowed) AssignmentLifecycleState.OPEN else AssignmentLifecycleState.CLOSED
        }

        return AssignmentLifecycleState.OPEN
    }

    fun isSubmissionLate(
        sub: SubmissionDto?,
        assignment: AssignmentDto,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        if (sub == null) return false

        // If explicitly flagged with an approved/active status other than LATE, trust it
        val explicitStatus = sub.submissionStatus?.uppercase(Locale.US)
        if (explicitStatus in setOf("SUBMITTED", "UNDER_REVIEW", "RESUBMISSION_REQUIRED", "RESUBMITTED", "EVALUATED")) {
            return false
        }

        val deadline = parseDeadlineDateTime(assignment.dueAt ?: assignment.dueDate)
        val submittedAt = parseIsoDateTime(sub.submittedAt)

        if (deadline != null && submittedAt != null) {
            // Student submitted on or before deadline (with 2 min clock-skew tolerance) -> definitely NOT late!
            return submittedAt.isAfter(deadline.plusMinutes(2))
        }

        // If the assignment deadline is still in the future or no deadline set, it cannot be late
        if (deadline != null && now.isBefore(deadline)) {
            return false
        }

        if (deadline == null) {
            return false
        }

        return sub.isLate
    }

    fun formatMentorName(mentorName: String?, createdBy: String?): String {
        val isUuid = { s: String? -> s != null && s.trim().matches(Regex("^[0-9a-fA-F-]{32,36}$")) }
        val mentor = mentorName?.trim()
        if (!mentor.isNullOrBlank() && !isUuid(mentor)) {
            return mentor
        }
        val creator = createdBy?.trim()
        if (!creator.isNullOrBlank() && !isUuid(creator)) {
            return creator
        }
        return "Faculty Lead & Mentors"
    }

    fun formatSubmissionDateTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "Just now"
        val dt = parseIsoDateTime(raw) ?: return raw.trim()
        return try {
            val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.US)
            dt.format(formatter)
        } catch (_: Exception) {
            raw.trim()
        }
    }

    fun canSubmit(
        state: AssignmentLifecycleState,
        assignment: AssignmentDto,
        attemptsMade: Int,
        maxAttempts: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        if (state == AssignmentLifecycleState.UPCOMING || state == AssignmentLifecycleState.CLOSED) {
            return false
        }
        if (state == AssignmentLifecycleState.EVALUATED) {
            return false
        }
        if (attemptsMade >= maxAttempts) {
            return false
        }
        // If mentor explicitly requested revision, student is permitted to resubmit
        if (state == AssignmentLifecycleState.RESUBMISSION_REQUIRED) {
            return true
        }

        val deadline = parseDeadlineDateTime(assignment.dueAt ?: assignment.dueDate)
        val isDeadlinePassed = deadline != null && now.isAfter(deadline)

        // Once the deadline has passed, submissions and resubmissions are strictly closed
        if (isDeadlinePassed) {
            // If late submissions are enabled and user hasn't submitted yet
            if (assignment.lateSubmissionAllowed && attemptsMade == 0) {
                return true
            }
            return false
        }

        return state == AssignmentLifecycleState.OPEN ||
            state == AssignmentLifecycleState.SUBMITTED ||
            state == AssignmentLifecycleState.LATE_SUBMITTED
    }

    fun canSubmit(state: AssignmentLifecycleState, attemptsMade: Int, maxAttempts: Int): Boolean {
        return canSubmit(state, AssignmentDto(), attemptsMade, maxAttempts)
    }

    fun parseDeadlineDateTime(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim()
        // If date-only (yyyy-MM-dd), deadline is the end of that day (23:59:59)
        if (clean.length == 10) {
            try {
                return LocalDate.parse(clean).atTime(23, 59, 59)
            } catch (_: Exception) {}
        }
        val parsed = parseIsoDateTime(clean) ?: return null
        // If deadline was entered without explicit time (midnight 00:00:00), treat as end of day
        return if (parsed.toLocalTime() == LocalTime.MIDNIGHT) {
            parsed.toLocalDate().atTime(23, 59, 59)
        } else {
            parsed
        }
    }

    fun parseIsoDateTime(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim()

        // 1. Try parsing ISO with offset (e.g. 2026-09-11T11:27:20.320416+05:30 or Z)
        try {
            return java.time.OffsetDateTime.parse(clean).toLocalDateTime()
        } catch (_: Exception) {}

        // 2. Try ZonedDateTime
        try {
            return java.time.ZonedDateTime.parse(clean).toLocalDateTime()
        } catch (_: Exception) {}

        // 3. Try Instant
        try {
            return java.time.Instant.parse(clean).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        } catch (_: Exception) {}

        // 4. Try standard local patterns without offset
        val formats = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        )
        for (formatter in formats) {
            try {
                return if (clean.length == 10) {
                    LocalDate.parse(clean, formatter).atStartOfDay()
                } else {
                    LocalDateTime.parse(clean, formatter)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun formatRemainingTime(dueRaw: String?, now: LocalDateTime = LocalDateTime.now()): String {
        val dueDate = parseDeadlineDateTime(dueRaw) ?: return "No deadline set"
        val duration = Duration.between(now, dueDate)
        if (duration.isNegative || duration.isZero) {
            return "Deadline passed"
        }
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        return when {
            days > 1 -> "$days days remaining"
            days == 1L -> "1 day, $hours hrs remaining"
            hours > 0 -> "$hours hrs, $minutes mins remaining"
            else -> "$minutes minutes remaining"
        }
    }
}
