package com.example.suretouchapp

import com.example.suretouchapp.data.api.ApiClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiClientUrlTest {
    @Test
    fun relativeMediaUsesApiOrigin() {
        assertEquals(
            "https://api.sureproed.com/media/students/photo.jpg?v=7",
            ApiClient.resolveServerUrl("/media/students/photo.jpg?v=7")
        )
    }

    @Test
    fun legacyFrontendBackendPathsMigrateToApiOrigin() {
        assertEquals(
            "https://api.sureproed.com/media/students/photo.jpg?v=7",
            ApiClient.resolveServerUrl("https://sureproed.com/media/students/photo.jpg?v=7")
        )
        assertEquals(
            "https://api.sureproed.com/api/students/me/download-resume/?v=2",
            ApiClient.resolveServerUrl("https://sureproed.com/api/students/me/download-resume/?v=2")
        )
    }

    @Test
    fun retiredDirectAddressesMigrateToTlsApiOrigin() {
        listOf(
            "http://106.51.129.34:8000/media/students/photo.jpg",
            "https://106.51.129.34:8000/media/students/photo.jpg",
            "http://10.0.2.2:8000/media/students/photo.jpg",
            "http://127.0.0.1:8000/media/students/photo.jpg"
        ).forEach { oldUrl ->
            assertEquals(
                "https://api.sureproed.com/media/students/photo.jpg",
                ApiClient.resolveServerUrl(oldUrl)
            )
        }
    }

    @Test
    fun apiOriginIsAlwaysHttpsWithoutDevelopmentPort() {
        assertEquals(
            "https://api.sureproed.com/api/health/",
            ApiClient.resolveServerUrl("http://api.sureproed.com:8000/api/health/")
        )
    }

    @Test
    fun unrelatedAndLookalikeHostsAreNotRewritten() {
        listOf(
            "https://sureproed.com/",
            "https://media.licdn.com/photo.jpg",
            "https://sureproed.com.attacker.test/media/photo.jpg",
            "content://media/external/images/1"
        ).forEach { url -> assertEquals(url, ApiClient.resolveServerUrl(url)) }
    }

    @Test
    fun publicPathsAreCorrectlyIdentified() {
        listOf(
            "/api/auth/token/",
            "/api/auth/token/refresh/",
            "/api/users/forgot_password_request/",
            "/api/users/forgot_password_confirm/",
            "/api/users/setup_password/",
            "/api/users/setup-password/",
            "/api/auth/send-verification-otp/",
            "/api/auth/verify-email-otp/",
            "/api/app/version-check/",
            "/api/app-releases/"
        ).forEach { path ->
            org.junit.Assert.assertTrue("Expected public path for $path", ApiClient.isPublicPath(path))
        }

        listOf(
            "/api/students/me/",
            "/api/users/me/",
            "/api/applications/",
            "/api/courses/",
            "/api/attendance/"
        ).forEach { path ->
            org.junit.Assert.assertFalse("Expected non-public path for $path", ApiClient.isPublicPath(path))
        }
    }
}
