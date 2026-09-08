package com.example.suretouchapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.TokenObtainRequest
import com.example.suretouchapp.data.repository.AccountSessionRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignInStabilityInstrumentedTest {
    private val tokens = TokenManager(InstrumentationRegistry.getInstrumentation().targetContext)
    private val server = MockWebServer()
    @Before fun setup() { server.start(); tokens.saveToken("expired-access", "valid-refresh") }
    @After fun cleanup() { server.shutdown(); tokens.clearUserSessionAndProfile() }
    private fun api() = ApiClient.createService(tokens, tokens.getSessionId(), server.url("/api/").toString())
    private fun reply(code: Int, body: String = "{}") = server.enqueue(MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json"))
    private val identity = """{"id":"account-a","email":"account-a@example.test","role":"STUDENT"}"""

    @Test fun signInOmitsStaleAuthorizationAndVerifiesNewIdentity() = runBlocking {
        reply(200, """{"access":"new-access","refresh":"new-refresh"}""")
        val response = api().login(TokenObtainRequest(email="account-a@example.test", password="local-only"))
        assertNull(server.takeRequest().getHeader("Authorization"))
        tokens.saveToken(response.body()!!.access, response.body()!!.refresh!!)
        reply(200, identity)
        val service = api()
        AccountSessionRepository(tokens) { service.getCurrentUser() }.verifyCurrentAccount()
        assertTrue(tokens.isLoggedIn())
        assertEquals("account-a@example.test", tokens.getUserEmail())
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
    }
    @Test fun temporaryRefreshFailureKeepsSessionAndNextRetrySucceeds() = runBlocking {
        reply(401); reply(503)
        val service = api()
        assertTrue(runCatching { service.getCurrentUser() }.isFailure)
        assertTrue(tokens.isLoggedIn())
        reply(401); reply(200, """{"access":"renewed","refresh":"rotated"}"""); reply(200, identity)
        assertTrue(service.getCurrentUser().isSuccessful)
        assertEquals("renewed", tokens.getAccessToken())
        assertEquals("rotated", tokens.getRefreshToken())
    }
    @Test fun confirmedInvalidRefreshEndsSession() = runBlocking {
        reply(401); reply(401, """{"code":"token_not_valid"}""")
        runCatching { api().getCurrentUser() }
        assertFalse(tokens.isLoggedIn())
    }
    @Test fun cancelledIdentityVerificationDoesNotLogOut() = runBlocking {
        assertTrue(runCatching { AccountSessionRepository(tokens) { throw kotlinx.coroutines.CancellationException("screen stopped") }.verifyCurrentAccount() }.isFailure)
        assertTrue(tokens.isLoggedIn())
    }
    @Test fun temporaryIdentityFailureDoesNotLogOut() = runBlocking {
        reply(503)
        val service = api()
        assertTrue(runCatching { AccountSessionRepository(tokens) { service.getCurrentUser() }.verifyCurrentAccount() }.isFailure)
        assertTrue(tokens.isLoggedIn())
        reply(200, identity)
        AccountSessionRepository(tokens) { service.getCurrentUser() }.verifyCurrentAccount()
        assertEquals("account-a@example.test", tokens.getUserEmail())
    }
}
