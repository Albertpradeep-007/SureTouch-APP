package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.NotificationDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class NotificationRepository(private val manager: TokenManager) {
    companion object { private val mutex = Mutex() }
    suspend fun load(): List<NotificationDto> = mutex.withLock {
        val session = manager.getSessionId()
        val api = ApiClient.getService(manager)
        val items = mutableListOf<NotificationDto>()
        var page = 1
        do {
            val response = api.getNotifications(page = page++)
            if (!response.isSuccessful) throw IOException("Unable to load notifications (${response.code()})")
            val body = response.body() ?: throw IOException("Incomplete notification response")
            manager.requireCurrentSession(session)
            items.addAll(body.results)
            if (page > 1000) throw IOException("Notification pagination limit exceeded")
        } while (body.next != null)
        manager.withCurrentSession(session) { latestNotifications(items) }
    }
}
