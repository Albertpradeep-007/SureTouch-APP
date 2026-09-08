package com.example.suretouchapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.test.platform.app.InstrumentationRegistry
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SignInNavigationInstrumentedTest {
    // Queue coroutine resumptions on the UI test clock instead of resuming the
    // navigation callback on MockWebServer's network thread.
    @get:Rule val compose = createComposeRule(effectContext = kotlinx.coroutines.test.StandardTestDispatcher())
    @Test fun realSignInScreenSurvivesTokenSaveAndDelayedIdentityVerification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokens = TokenManager(context)
        tokens.clearUserSessionAndProfile()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val response = MockResponse().setHeader("Content-Type", "application/json")
                return when (request.path?.substringBefore('?')) {
                    "/api/auth/token/" -> response.setBody("""{"access":"local-access","refresh":"local-refresh"}""")
                    "/api/users/me/" -> response.setBody("""{"id":"local-mentor","email":"mentor@example.test","role":"MENTOR","first_name":"Local"}""")
                        .setBodyDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    else -> response.setBody("""{"results":[],"count":0,"next":null}""")
                }
            }
        }
        server.start()
        val baseUrl = server.url("/api/").toString()
        ApiClient.testServiceFactory = { manager, session -> ApiClient.createService(manager, session, baseUrl) }
        try {
            compose.setContent { AppNavigation() }
            compose.onNodeWithText("Email Address").performTextInput("mentor@example.test")
            compose.onNodeWithText("Password").performTextInput("local-only")
            compose.onNode(hasText("Log In") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            try {
                compose.waitUntil(10_000) {
                    tokens.getUserEmail() == "mentor@example.test" &&
                        compose.onAllNodesWithText("Email Address").fetchSemanticsNodes().isEmpty()
                }
            } catch (failure: Throwable) {
                throw AssertionError("requests=${server.requestCount}; loggedIn=${tokens.isLoggedIn()}; UI=${compose.onRoot().printToString()}", failure)
            }
            compose.waitForIdle()
            assertTrue(tokens.isLoggedIn())
            assertEquals("MENTOR", tokens.getUserRole())
            compose.onNodeWithText("Email Address").assertDoesNotExist()
        } finally {
            androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
            tokens.clearUserSessionAndProfile()
            ApiClient.testServiceFactory = null
            server.shutdown()
        }
    }
}
