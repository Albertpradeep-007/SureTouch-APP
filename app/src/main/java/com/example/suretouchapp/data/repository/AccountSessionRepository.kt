package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.UserResponse
import retrofit2.Response
import java.io.IOException
import java.util.Locale

/** Resolve identity from the authenticated caller, never from a login-email search. */
class AccountSessionRepository(
    private val tokenManager: TokenManager,
    private val identityLoader: suspend () -> Response<UserResponse> = {
        ApiClient.getService(tokenManager).getCurrentUser()
    }
) {
    suspend fun verifyCurrentAccount(): UserResponse {
        val sessionId = tokenManager.getSessionId()
        try {
            val response = identityLoader()
            val user = response.takeIf { it.isSuccessful }?.body()
                ?: throw IOException("Unable to verify your account. Please sign in again.")
            if (user.id.isNullOrBlank() || user.email.isBlank() || user.role.isNullOrBlank()) {
                throw IOException("The server returned incomplete account details. Please sign in again.")
            }
            return tokenManager.withCurrentSession(sessionId) {
                val name = listOfNotNull(user.firstName, user.lastName).joinToString(" ").trim()
                    .ifBlank { user.email.substringBefore("@") }
                tokenManager.saveUserInfo(name, user.email)
                tokenManager.saveUserRole(user.role)
                user.phoneNumber?.let(tokenManager::savePhone)
                user.gender?.let(tokenManager::saveGender)
                user.dateOfBirth?.let(tokenManager::saveDob)
                user
            }
        } catch (error: Exception) {
            tokenManager.logoutIfCurrentSession(sessionId)
            throw error
        }
    }
}

enum class AccountWorkspace { STUDENT, MENTOR, VOLUNTEER, UNSUPPORTED }

fun accountWorkspace(role: String?): AccountWorkspace = when (role?.trim()?.uppercase(Locale.US)) {
    "STUDENT" -> AccountWorkspace.STUDENT
    "MENTOR" -> AccountWorkspace.MENTOR
    "VOLUNTEER", "VOLUNTEER_TRUSTEE", "VOLUNTEER TRUSTEE", "TRUSTEE" -> AccountWorkspace.VOLUNTEER
    else -> AccountWorkspace.UNSUPPORTED
}
