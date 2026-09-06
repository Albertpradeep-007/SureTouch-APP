package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.StudentStatisticsDto
import java.io.IOException

class StudentStatisticsRepository(private val tokenManager: TokenManager) {
    suspend fun load(): StudentStatisticsDto? {
        val sessionId = tokenManager.getSessionId()
        val response = ApiClient.getService(tokenManager).getStudentStatistics()
        return tokenManager.withCurrentSession(sessionId) {
            if (!response.isSuccessful) {
                if (response.code() == 401) tokenManager.logoutIfCurrentSession(sessionId)
                throw IOException("Unable to load student statistics (HTTP ${response.code()}). Please retry.")
            }
            val statistics = response.body()
                ?: throw IOException("The server returned no student statistics. Please retry.")
            // This endpoint is deployed in production. An error must not rebuild academic
            // records from cached applications, unrelated examinations, or guessed defaults.
            tokenManager.saveApplicationSnapshot(
                statistics.applicationNumber,
                statistics.applicationStatus,
                statistics.activeCohort?.courseId,
                statistics.applicationCourseTitle,
                statistics.activeCohort?.id,
                statistics.screeningQualified
            )
            statistics
        }
    }
}
