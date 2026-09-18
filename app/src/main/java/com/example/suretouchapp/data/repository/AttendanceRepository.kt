package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.AttendanceDto
import java.io.IOException

class AttendanceRepository(private val manager: TokenManager) {
    suspend fun load(): List<AttendanceDto> {
        val session = manager.getSessionId()
        val api = ApiClient.getService(manager)
        val rows = linkedMapOf<String, AttendanceDto>()
        for (page in 1..100) {
            val response = api.getAttendance(page = page, pageSize = 500)
            manager.requireCurrentSession(session)
            val body = response.takeIf { it.isSuccessful }?.body()
                ?: throw IOException("Unable to refresh classes. Please retry.")
            body.results.filter { it.id.isNotBlank() }.forEach { rows[it.id] = it }
            if (body.next.isNullOrBlank()) return rows.values.toList()
            if (body.results.isEmpty()) break
        }
        throw IOException("Incomplete class schedule. Please retry.")
    }

    suspend fun endClass(sessionId: String): AttendanceDto {
        val session = manager.getSessionId()
        val api = ApiClient.getService(manager)
        val response = api.patchAttendance(sessionId, mapOf("conducted" to false))
        manager.requireCurrentSession(session)
        if (!response.isSuccessful) {
            val errorMsg = response.errorBody()?.string() ?: "Failed to end class session."
            throw IOException(errorMsg)
        }
        return response.body() ?: throw IOException("Empty response when ending class session.")
    }

    suspend fun deleteClass(sessionId: String) {
        val session = manager.getSessionId()
        val api = ApiClient.getService(manager)
        val response = api.deleteAttendance(sessionId)
        manager.requireCurrentSession(session)
        if (!response.isSuccessful) {
            val errorMsg = response.errorBody()?.string() ?: "Failed to delete class session."
            throw IOException(errorMsg)
        }
    }
}
