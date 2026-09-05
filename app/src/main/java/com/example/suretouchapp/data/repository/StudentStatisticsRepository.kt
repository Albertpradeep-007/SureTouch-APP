package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.api.fetchAllAttendancePages
import com.example.suretouchapp.data.model.ActiveCohortStatisticsDto
import com.example.suretouchapp.data.model.StudentStatisticsDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

class StudentStatisticsRepository(private val tokenManager: TokenManager) {
    suspend fun load(): StudentStatisticsDto? {
        val api = ApiClient.getService(tokenManager)
        val primaryResponse = runCatching { api.getStudentStatistics() }.getOrNull()
        if (primaryResponse != null && primaryResponse.code() in listOf(401, 403)) {
            tokenManager.logout()
            return null
        }
        val primary = primaryResponse?.takeIf { it.isSuccessful }?.body()
        if (primary != null) {
            val localLinkedin = tokenManager.getLinkedinUrl().isNotBlank()
            val isLinkedin = primary.isLinkedinConnected || localLinkedin
            val localGithub = tokenManager.getGithubUrl().isNotBlank()
            val githubUrl = primary.githubUrl ?: tokenManager.getGithubUrl().takeIf { it.isNotBlank() }
            val isGithub = primary.isGithubLinked || localGithub || !githubUrl.isNullOrBlank()

            // The backend aggregate currently counts every `conducted=true` row,
            // including future schedules. AttendanceSerializer already provides the
            // authoritative student-scoped READY/NOT_READY result for each session,
            // so use that source whenever the detailed request succeeds.
            val reconciledAttendance = loadDetailedAttendancePercentage()
                ?: primary.attendancePercentage
            val resolved = primary.copy(
                isLinkedinConnected = isLinkedin,
                githubUrl = githubUrl,
                isGithubLinked = isGithub,
                studentRoleVerified = primary.studentRoleVerified || (isLinkedin && isGithub),
                attendancePercentage = reconciledAttendance
            )
            val cached = tokenManager.getApplicationSnapshot()
            tokenManager.saveApplicationSnapshot(
                resolved.applicationNumber,
                resolved.applicationStatus,
                cached?.courseId,
                resolved.applicationCourseTitle,
                resolved.activeCohort?.id,
                resolved.screeningQualified
            )
            return resolved
        }

        return loadCompatibilityFallback()
    }

    private suspend fun loadDetailedAttendancePercentage(): Double? = coroutineScope {
        val api = ApiClient.getService(tokenManager)
        val attendanceRead = async {
            runCatching { api.fetchAllAttendancePages() }.getOrNull()
        }
        val profileRead = async {
            runCatching { api.getStudentProfileById("me") }.getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
        }
        val attendanceResponse = attendanceRead.await() ?: return@coroutineScope null
        val profile = profileRead.await()
        val identifiers = setOfNotNull(
            profile?.id?.takeIf(String::isNotBlank),
            profile?.userId?.takeIf(String::isNotBlank),
            profile?.studentCode?.takeIf(String::isNotBlank),
            tokenManager.getStudentCode().takeIf(String::isNotBlank)
        )
        calculateStudentAttendancePercentage(attendanceResponse, identifiers) ?: 0.0
    }

    private suspend fun loadCompatibilityFallback(): StudentStatisticsDto? = coroutineScope {
        val api = ApiClient.getService(tokenManager)
        val applicationsRaw = async { runCatching { api.getMyApplications() }.getOrNull() }
        val coursesRead = async { runCatching { api.getCourses() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.results }
        val profileRead = async { runCatching { api.getStudentProfileById("me") }.getOrNull()?.takeIf { it.isSuccessful }?.body() }
        val examsRead = async { runCatching { api.getScreeningResults() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.results }
        val interviewsRead = async { runCatching { api.getPreScreeningInterviews() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.results }
        val cohortsRead = async { runCatching { api.getCohorts() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.results }
        val attendanceRead = async { runCatching { api.fetchAllAttendancePages() }.getOrNull() }

        val applicationsResponse = applicationsRaw.await()
        if (applicationsResponse != null && applicationsResponse.code() in listOf(401, 403)) {
            tokenManager.logout()
            return@coroutineScope null
        }

        val applications = applicationsResponse?.takeIf { it.isSuccessful }?.body()?.results
        val courses = coursesRead.await()
        val profile = profileRead.await()
        val exams = examsRead.await()
        val interviews = interviewsRead.await()
        val cohorts = cohortsRead.await()
        val attendance = attendanceRead.await().orEmpty()
        val cached = tokenManager.getApplicationSnapshot()

        val application = applications?.firstOrNull()
        val applicationNumber = application?.applicationNumber ?: cached?.applicationNumber
        val applicationStatus = application?.status ?: cached?.status
        val applicationCourseId = application?.course ?: cached?.courseId
        val courseTitle = courses?.firstOrNull { it.id == applicationCourseId }?.name ?: cached?.courseTitle
        val assignedCohortId = application?.assignedCohort ?: cached?.assignedCohort
        val cohort = cohorts?.firstOrNull { it.id == assignedCohortId }
        val exam = exams?.firstOrNull { it.application == application?.id } ?: exams?.firstOrNull()
        val interview = interviews?.firstOrNull { it.application == application?.id } ?: interviews?.firstOrNull()
        val qualified = application?.qualified == true || exam?.qualified == true ||
            applicationStatus?.uppercase(Locale.US) in setOf("QUALIFIED", "WAITLISTED", "COHORT_ASSIGNED", "IN_PROGRESS", "COMPLETED") ||
            cached?.qualified == true
        val linkedinConnected = profile?.isLinkedinConnected == true ||
            !profile?.linkedinUrl.isNullOrBlank() ||
            tokenManager.getLinkedinUrl().isNotBlank()
        val githubUrl = profile?.githubUrl ?: tokenManager.getGithubUrl().takeIf { it.isNotBlank() }
        val isGithubLinked = !githubUrl.isNullOrBlank()
        val interviewPassed = interview?.status?.uppercase(Locale.US) == "PASSED"
        val workflowRoleVerified = application?.studentRoleVerified == true ||
            applicationStatus?.uppercase(Locale.US) in setOf("COHORT_ASSIGNED", "IN_PROGRESS", "COMPLETED") ||
            (
                interviewPassed &&
                    applicationStatus?.uppercase(Locale.US) in setOf("QUALIFIED", "WAITLISTED")
                )

        val attendanceIdentifiers = setOfNotNull(
            profile?.id?.takeIf(String::isNotBlank),
            profile?.userId?.takeIf(String::isNotBlank),
            profile?.studentCode?.takeIf(String::isNotBlank),
            tokenManager.getStudentCode().takeIf(String::isNotBlank)
        )
        val attendancePercentage = calculateStudentAttendancePercentage(attendance, attendanceIdentifiers) ?: 0.0

        if (application == null && profile == null && cached == null) return@coroutineScope null

        tokenManager.saveApplicationSnapshot(
            applicationNumber,
            applicationStatus,
            applicationCourseId,
            courseTitle,
            assignedCohortId,
            qualified
        )

        StudentStatisticsDto(
            studentCode = profile?.studentCode,
            totalApplications = applications?.size ?: if (applicationNumber == null) 0 else 1,
            qualifiedApplications = applications?.count { it.qualified == true } ?: if (qualified) 1 else 0,
            activeCohort = assignedCohortId?.let {
                ActiveCohortStatisticsDto(
                    id = it,
                    code = cohort?.code ?: tokenManager.getCohortCode().ifBlank { null },
                    name = cohort?.name,
                    courseTitle = courseTitle ?: cohort?.courseName,
                    status = cohort?.status,
                    mentorName = cohort?.mentorName
                )
            },
            examsTaken = if (exam == null) 0 else 1,
            attendancePercentage = attendancePercentage,
            applicationStatus = applicationStatus,
            applicationNumber = applicationNumber,
            applicationCourseTitle = courseTitle,
            appliedAt = application?.appliedAt,
            screeningStatus = exam?.status ?: application?.preScreening?.status,
            screeningScheduledAt = application?.preScreening?.scheduledAt,
            screeningMeetingLink = application?.preScreening?.meetingLink,
            screeningMarksObtained = exam?.marksObtained,
            screeningTotalMarks = exam?.totalMarks,
            screeningPercentage = exam?.percentage,
            screeningGrade = gradeFrom(exam?.percentage),
            screeningQualified = qualified,
            interviewStatus = interview?.status,
            interviewScheduledAt = interview?.scheduledAt,
            interviewMeetingLink = interview?.meetingLink,
            interviewScore = interview?.score,
            studentRoleVerified = linkedinConnected &&
                isGithubLinked &&
                workflowRoleVerified,
            isLinkedinConnected = linkedinConnected,
            githubUrl = githubUrl,
            isGithubLinked = isGithubLinked,
            portfolioUrl = profile?.portfolioUrl ?: tokenManager.getPortfolioUrl().takeIf { it.isNotBlank() },
            isPortfolioLinked = !profile?.portfolioUrl.isNullOrBlank() || tokenManager.getPortfolioUrl().isNotBlank()
        )
    }

    private fun gradeFrom(value: String?): String? {
        val percentage = value?.toDoubleOrNull() ?: return null
        return when {
            percentage >= 90 -> "A+"
            percentage >= 80 -> "A"
            percentage >= 70 -> "B"
            percentage >= 60 -> "C"
            percentage >= 50 -> "D"
            else -> "F"
        }
    }
}
