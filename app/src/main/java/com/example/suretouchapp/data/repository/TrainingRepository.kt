package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AttendanceDto
import java.time.LocalDate
import java.util.Locale

enum class TrainingKind { LIFE_SKILLS, SOFT_SKILLS }

data class TrainingSnapshot(
    val cohortCode: String? = null,
    val sessions: List<AttendanceDto> = emptyList(),
    val completed: Boolean = false
)

/**
 * Reads the final OpenAPI training resources. Access remains gated by the
 * authenticated student's backend cohort assignment.
 */
class TrainingRepository(private val tokenManager: TokenManager) {
    suspend fun load(kind: TrainingKind): TrainingSnapshot {
        val dashboard = DashboardRepository(tokenManager).load(force = true)
        val service = ApiClient.getService(tokenManager)
        if (dashboard.cohortCode == null) return TrainingSnapshot()

        val expectedType = when (kind) {
            TrainingKind.LIFE_SKILLS -> "LST"
            TrainingKind.SOFT_SKILLS -> "SOFT_SKILLS"
        }
        val trainings = runCatching { service.getTrainings() }
            .getOrNull()?.takeIf { it.isSuccessful }?.body()?.results.orEmpty()
            .filter { it.isActive && it.trainingType?.uppercase(Locale.US) == expectedType }
        val trainingIds = trainings.mapTo(mutableSetOf()) { it.id }
        val sessions = runCatching { service.getTrainingSessions() }
            .getOrNull()?.takeIf { it.isSuccessful }?.body()?.results.orEmpty()
            .filter { it.training in trainingIds }
        val attendance = runCatching { service.getTrainingAttendances() }
            .getOrNull()?.takeIf { it.isSuccessful }?.body()?.results.orEmpty()
        val attendanceBySession = attendance.associateBy { it.session }
        val today = LocalDate.now()
        val matchingSessions = sessions.map { session ->
            val attendanceRecord = attendanceBySession[session.id]
            val conducted = runCatching { LocalDate.parse(session.sessionDate) <= today }.getOrDefault(false)
            AttendanceDto(
                id = session.id,
                sessionTitle = session.title,
                date = session.sessionDate,
                startTime = session.startTime,
                endTime = session.endTime,
                conducted = conducted,
                conductedBy = session.conductedBy,
                meetingLink = session.meetingLink,
                recordingLink = session.recordingLink,
                notes = attendanceRecord?.remarks ?: session.notes,
                present = attendanceRecord?.status?.uppercase(Locale.US) == "PRESENT"
            )
        }.sortedWith(compareBy(AttendanceDto::date, AttendanceDto::startTime))

        val conductedSessions = matchingSessions.filter { it.conducted }
        return TrainingSnapshot(
            cohortCode = dashboard.cohortCode,
            sessions = matchingSessions,
            completed = conductedSessions.isNotEmpty() && conductedSessions.all { it.present }
        )
    }
}
