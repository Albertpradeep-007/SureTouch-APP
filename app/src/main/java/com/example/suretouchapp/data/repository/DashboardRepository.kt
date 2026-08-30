package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AttendanceDto
import com.example.suretouchapp.data.model.AnnouncementDto
import com.example.suretouchapp.data.model.ExamDto
import com.example.suretouchapp.data.model.ModuleTestResultDto
import com.example.suretouchapp.data.model.StudentStatisticsDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Locale

data class ModuleGrade(
    val moduleNumber: Int,
    val title: String,
    val marks: Int? = null,
    val maxMarks: Int = 100,
    val passed: Boolean = false,
    val unlocked: Boolean = false,
    val completedAt: String? = null
) {
    val percentage: Int?
        get() = marks?.let { if (maxMarks > 0) (it * 100) / maxMarks else 0 }
}

data class DashboardClassSession(
    val id: String,
    val title: String,
    val moduleTitle: String,
    val mentorName: String,
    val startTime: String,
    val endTime: String,
    val period: String,
    val date: String,
    val meetingLink: String? = null,
    val classStatus: String? = null,
    val rawDate: String? = null,
    val rawStartTime: String? = null,
    val rawEndTime: String? = null
)

data class DashboardAnnouncement(
    val id: String,
    val title: String,
    val message: String,
    val isPinned: Boolean,
    val createdAt: String? = null
)

data class DashboardSnapshot(
    val cohortCode: String? = null,
    val courseName: String? = null,
    val applicationStatus: String? = null,
    val screeningStatus: String? = null,
    val screeningMarksObtained: String? = null,
    val screeningTotalMarks: String? = null,
    val screeningPercentage: String? = null,
    val screeningGrade: String? = null,
    val screeningQualified: Boolean = false,
    val studentRoleVerified: Boolean = false,
    val certificateCount: Int = 0,
    val unreadNotificationCount: Int = 0,
    val totalApplications: Int = 0,
    val qualifiedApplications: Int = 0,
    val examsTaken: Int = 0,
    val moduleTestsPassed: Int = 0,
    val attendancePercentage: Double = 0.0,
    val isLinkedinConnected: Boolean = false,
    val githubUrl: String? = null,
    val isGithubLinked: Boolean = false,
    val portfolioUrl: String? = null,
    val isPortfolioLinked: Boolean = false,
    val announcementCount: Int = 0,
    val announcements: List<DashboardAnnouncement> = emptyList(),
    val openRequestCount: Int = 0,
    val mentorName: String? = null,
    val sessions: List<DashboardClassSession> = emptyList(),
    val grades: List<ModuleGrade> = emptyList(),
    val lastUpdatedAt: Long = 0L,
    val isRemote: Boolean = false
)

class DashboardRepository(private val tokenManager: TokenManager) {
    private val refreshMutex = Mutex()
    private var cachedSnapshot: DashboardSnapshot? = null
    private var cachedAt = 0L

    suspend fun load(force: Boolean = false): DashboardSnapshot = refreshMutex.withLock {
        val now = System.currentTimeMillis()
        cachedSnapshot?.takeIf { !force && now - cachedAt < CACHE_TTL_MS }?.let { return it }

        val (statistics, announcements) = coroutineScope {
            val statisticsRequest = async {
                runCatching { StudentStatisticsRepository(tokenManager).load() }.getOrNull()
            }
            val announcementsRequest = async {
                runCatching {
                    ApiClient.getService(tokenManager).getAnnouncements()
                        .takeIf { it.isSuccessful }
                        ?.body()
                        ?.results
                        .orEmpty()
                        .filter { it.isActive }
                        .sortedWith(
                            compareByDescending<AnnouncementDto> { it.isPinned }
                                .thenByDescending { it.createdAt }
                        )
                }.getOrDefault(emptyList())
            }
            Pair(statisticsRequest.await(), announcementsRequest.await())
        }
        val dashboardAnnouncements = announcements.map { item ->
            DashboardAnnouncement(
                id = item.id,
                title = item.title,
                message = item.message,
                isPinned = item.isPinned,
                createdAt = item.createdAt
            )
        }
        if (statistics == null) {
            throw java.io.IOException("Unable to retrieve verified student data from SURE Trust server.")
        }
        val baseSnapshot = statistics.toSnapshot(now)
        val loaded = baseSnapshot.copy(
            announcementCount = dashboardAnnouncements.size,
            announcements = dashboardAnnouncements.take(3)
        )

        loaded.cohortCode?.let(tokenManager::saveCohortCode)
        if (loaded.isRemote && loaded.cohortCode == null) {
            tokenManager.clearCohortCode()
        }
        val merged = if (!loaded.isRemote && loaded.cohortCode == null) {
            loaded.copy(cohortCode = tokenManager.getCohortCode().ifBlank { null })
        } else loaded
        cachedSnapshot = merged
        cachedAt = now
        merged
    }

    private fun StudentStatisticsDto.toSnapshot(now: Long) = DashboardSnapshot(
        cohortCode = activeCohort?.code?.takeIf(String::isNotBlank),
        courseName = activeCohort?.courseTitle?.takeIf(String::isNotBlank),
        applicationStatus = applicationStatus,
        screeningStatus = screeningStatus,
        screeningMarksObtained = screeningMarksObtained,
        screeningTotalMarks = screeningTotalMarks,
        screeningPercentage = screeningPercentage,
        screeningGrade = screeningGrade,
        screeningQualified = screeningQualified,
        studentRoleVerified = studentRoleVerified && isLinkedinConnected && isGithubLinked,
        certificateCount = certificateCount,
        unreadNotificationCount = unreadNotificationCount,
        totalApplications = totalApplications,
        qualifiedApplications = qualifiedApplications,
        examsTaken = examsTaken,
        moduleTestsPassed = moduleTestsPassed,
        attendancePercentage = attendancePercentage,
        isLinkedinConnected = isLinkedinConnected,
        githubUrl = githubUrl,
        isGithubLinked = isGithubLinked,
        portfolioUrl = portfolioUrl,
        isPortfolioLinked = isPortfolioLinked,
        openRequestCount = openRequestCount,
        mentorName = activeCohort?.mentorName?.takeIf(String::isNotBlank),
        sessions = upcomingSessions.map(::toDashboardSession),
        grades = buildProgression(moduleGrades),
        lastUpdatedAt = now,
        isRemote = true
    )

    private fun buildProgression(results: List<ModuleTestResultDto>): List<ModuleGrade> =
        results.sortedBy { it.moduleNumber }.map { result ->
            result.toGrade(unlocked = true)
        }

    private fun ModuleTestResultDto.toGrade(unlocked: Boolean) = ModuleGrade(
        moduleNumber = moduleNumber,
        title = title.ifBlank { "Module $moduleNumber Test" },
        marks = marksObtained?.toDoubleOrNull()?.toInt(),
        maxMarks = maxMarks.toDoubleOrNull()?.toInt() ?: 100,
        passed = passed,
        unlocked = unlocked,
        completedAt = completedAt
    )

    private fun gradeFromPercentage(exam: ExamDto?): String? {
        val percentage = exam?.percentage?.toDoubleOrNull() ?: return null
        return when {
            percentage >= 90 -> "A+"
            percentage >= 80 -> "A"
            percentage >= 70 -> "B"
            percentage >= 60 -> "C"
            percentage >= 50 -> "D"
            else -> "F"
        }
    }

    private fun toDashboardSession(item: AttendanceDto): DashboardClassSession {
        val startHour = item.startTime?.take(2)?.toIntOrNull() ?: 9
        val sessionTitle = item.sessionTitle?.trim().orEmpty()
        val genericTitle = sessionTitle.uppercase(Locale.US).let {
            it.startsWith("DOMAIN SESSION") || it.startsWith("CLASS SESSION")
        }
        val courseName = item.courseName?.trim()?.takeIf(String::isNotBlank)
        return DashboardClassSession(
            id = item.id,
            title = courseName ?: sessionTitle.ifBlank { "Scheduled class" },
            moduleTitle = when {
                !genericTitle && sessionTitle.isNotBlank() && !sessionTitle.equals(courseName, ignoreCase = true) -> sessionTitle
                !item.notes.isNullOrBlank() -> item.notes
                else -> "Course session"
            },
            mentorName = item.conductedByName?.trim()?.takeIf(String::isNotBlank) ?: "Assigned trainer",
            startTime = formatClockTime(item.startTime),
            endTime = formatClockTime(item.endTime),
            period = if (startHour >= 12) "PM IST" else "AM IST",
            date = formatDate(item.date),
            meetingLink = item.meetingLink,
            classStatus = item.effectiveStatus ?: item.classStatus,
            rawDate = item.date,
            rawStartTime = item.startTime,
            rawEndTime = item.endTime
        )
    }

    private fun formatClockTime(value: String?): String {
        val raw = value?.take(5) ?: return "--:--"
        val source = SimpleDateFormat("HH:mm", Locale.US)
        val target = SimpleDateFormat("hh:mm", Locale.US)
        return runCatching { target.format(requireNotNull(source.parse(raw))) }.getOrDefault(raw)
    }

    private fun formatDate(value: String): String = runCatching {
        val source = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val target = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
        target.format(requireNotNull(source.parse(value))).uppercase(Locale.US)
    }.getOrDefault(value.uppercase(Locale.US))

    private companion object {
        const val CACHE_TTL_MS = 15_000L
    }
}
