package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.PaginatedResponse
import java.io.IOException
import retrofit2.Response

/** Read complete lists without following arbitrary next URLs or crossing account sessions. */
class AccountPageLoader(private val manager: TokenManager) {
    private val session = manager.getSessionId()

    suspend fun <T> load(
        label: String,
        id: (T) -> String,
        request: suspend (Int) -> Response<PaginatedResponse<T>>
    ): List<T> {
        val rows = linkedMapOf<String, T>()
        for (page in 1..100) {
            manager.requireCurrentSession(session)
            val response = request(page)
            manager.requireCurrentSession(session)
            val body = response.takeIf { it.isSuccessful }?.body()
                ?: throw IOException("Unable to load $label (${response.code()}). Please retry.")
            body.results.forEach { row ->
                val key = id(row)
                if (key.isBlank()) throw IOException("Invalid $label response. Please retry.")
                rows[key] = row
            }
            if (body.next.isNullOrBlank()) return rows.values.toList()
            if (body.results.isEmpty()) break
        }
        throw IOException("Incomplete $label response. Please retry.")
    }
}
