package com.example.suretouchapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.repository.StudentProfileRepository
import com.example.suretouchapp.ui.screens.liveclass.LiveClassScreen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.mockwebserver.*
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDateTime

class ClassAndResumeInstrumentedTest {
    @get:Rule val compose = createComposeRule(effectContext = StandardTestDispatcher())
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bothSameDayClassesAppearAcrossPagesAndSelectedClassOwnsDetails() {
        val tokens = TokenManager(context)
        tokens.saveToken("local-a", "refresh")
        tokens.saveUserInfo("A", "a@example.test")
        tokens.saveUserRole("STUDENT")
        val now = LocalDateTime.now()
        fun row(id: String, name: String) = """{"id":"$id","title":"$name","class_date":"${now.toLocalDate()}","start_time":"${now.minusMinutes(15).toLocalTime()}","end_time":"${now.plusMinutes(40).toLocalTime()}","class_status":"SCHEDULED","meeting_link":"https://meet.google.com/shared-link"}"""
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(
                if (request.requestUrl?.queryParameter("page") == "2") """{"results":[${row("b", "Second class")}],"next":null,"count":2}"""
                else """{"results":[${row("a", "First class")}],"next":"?page=2","count":2}"""
            )
        }
        server.start()
        val base = server.url("/api/").toString()
        ApiClient.testServiceFactory = { manager, session -> ApiClient.createService(manager, session, base) }
        try {
            compose.setContent { LiveClassScreen(tokens, {}) }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Second class", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Second class", substring = true).performClick()
            compose.onNodeWithText("Second class").assertExists()
            compose.onNodeWithText("Choose a class (2)").assertExists()
        } finally {
            tokens.clearUserSessionAndProfile()
            ApiClient.testServiceFactory = null
            server.shutdown()
        }
    }

    @Test fun resumeRefreshReplacesMetadataAndClearsRemovedResume() = runBlocking {
        val tokens = TokenManager(context)
        tokens.saveToken("local-a", "refresh")
        val server = MockWebServer()
        server.start()
        val base = server.url("/api/").toString()
        ApiClient.testServiceFactory = { manager, session -> ApiClient.createService(manager, session, base) }
        try {
            val repo = StudentProfileRepository(tokens)
            for (name in listOf("first.pdf", "replacement.docx")) {
                server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"id":"a","resume":"/media/students/resumes/$name","resume_url":"/api/students/a/download-resume/?v=$name","resume_name":"$name"}"""))
                repo.load()
                assertEquals(name, tokens.getResumeName())
                assertTrue(tokens.getResumeUrl().endsWith("v=$name"))
                assertEquals("Bearer local-a", server.takeRequest().getHeader("Authorization"))
            }
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"id":"a","resume":null,"resume_url":null}"""))
            repo.load()
            assertEquals("", tokens.getResumeUrl())
            assertEquals("", tokens.getLocalResumePath())
        } finally {
            tokens.clearUserSessionAndProfile()
            ApiClient.testServiceFactory = null
            server.shutdown()
        }
    }

    @Test fun oldResumeUploadCannotTargetNewLoginAndPrivateFilesAreCleared() = runBlocking {
        val tokens = TokenManager(context)
        tokens.saveToken("local-a", "refresh")
        val repo = StudentProfileRepository(tokens)
        val dir = java.io.File(context.cacheDir, "private_documents").apply { mkdirs() }
        val file = java.io.File(dir, "account-a.pdf").apply { writeText("private-a") }
        tokens.saveToken("local-b", "refresh")
        assertFalse(file.exists())
        val result = runCatching { repo.uploadResume("a", MultipartBody.Part.createFormData("resume", "resume.pdf", "local-only".toRequestBody())) }
        assertTrue(result.isFailure)
        assertEquals("local-b", tokens.getAccessToken())
        tokens.clearUserSessionAndProfile()
    }
}
