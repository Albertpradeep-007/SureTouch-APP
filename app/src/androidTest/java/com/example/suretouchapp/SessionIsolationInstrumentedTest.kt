package com.example.suretouchapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.StudentStatisticsDto
import com.example.suretouchapp.data.repository.DashboardRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionIsolationInstrumentedTest {
    private lateinit var manager: TokenManager
    @Before fun setup() {
        // Instrumentation runs as the target UID; notification calls require its context.
        manager = TokenManager(InstrumentationRegistry.getInstrumentation().targetContext)
        manager.clearUserSessionAndProfile()
    }
    @After fun cleanup() { manager.clearUserSessionAndProfile() }
    private fun login(email: String) {
        manager.saveToken("access-$email", "refresh-$email")
        manager.saveUserInfo("Test student", email)
    }
    @Test fun oauthRoleSavedBeforeIdentitySurvivesFreshLogin() {
        manager.saveToken("new-access", "new-refresh")
        manager.saveUserRole("MENTOR")
        manager.saveUserInfo("Mentor", "mentor@example.com")
        assertEquals("MENTOR", manager.getUserRole())
    }
    @Test fun accountSwitchClearsEveryAcademicPreference() {
        login("first@example.com")
        manager.saveCohortCode("G2-26")
        manager.saveApplicationSnapshot("APP1", "TRAINING", "course", "VLSI", "cohort", true)
        manager.markNoticeRead("old-notice")
        login("second@example.com")
        assertEquals("", manager.getCohortCode())
        assertNull(manager.getApplicationSnapshot())
        assertFalse(manager.isNoticeRead("old-notice"))
        assertEquals("second@example.com", manager.getUserEmail())
    }
    @Test fun oldRefreshAndLogoutCannotReplaceNewAccount() {
        login("first@example.com")
        val old = manager.getSessionId()
        login("second@example.com")
        assertFalse(manager.saveRefreshedToken(old,"stale-access","stale-refresh"))
        manager.logoutIfCurrentSession(old)
        assertEquals("access-second@example.com", manager.getAccessToken())
        assertTrue(manager.isLoggedIn())
    }
    @Test fun sameSessionRefreshPreservesCohortAndIdentity() {
        login("first@example.com")
        manager.saveCohortCode("G2-26")
        val session = manager.getSessionId()
        assertTrue(manager.saveRefreshedToken(session,"new-access","new-refresh"))
        assertEquals(session,manager.getSessionId())
        assertEquals("G2-26",manager.getCohortCode())
        assertEquals("first@example.com",manager.getUserEmail())
    }
    @Test fun dashboardCacheCannotCrossLoginsWithinTtl() = runBlocking {
        login("first@example.com")
        var reads = 0
        val repository = DashboardRepository(manager, statisticsLoader = {
            reads++
            StudentStatisticsDto(attendancePercentage = if (manager.getUserEmail().startsWith("first")) 98.0 else 0.0)
        }, announcementsLoader = { emptyList() })
        assertEquals(98.0,repository.load().attendancePercentage,0.0)
        repository.load()
        assertEquals(1,reads)
        login("second@example.com")
        assertEquals(0.0,repository.load().attendancePercentage,0.0)
        assertEquals(2,reads)
    }
    @Test fun inflightDashboardCannotPublishAfterAccountSwitch() = runBlocking {
        login("first@example.com")
        val started=CompletableDeferred<Unit>()
        val released=CompletableDeferred<Unit>()
        val repository=DashboardRepository(manager,statisticsLoader={
            started.complete(Unit)
            released.await()
            StudentStatisticsDto(attendancePercentage=98.0)
        },announcementsLoader={emptyList()})
        val pending=async(start=CoroutineStart.UNDISPATCHED) { runCatching { repository.load() } }
        started.await()
        login("second@example.com")
        released.complete(Unit)
        assertTrue(pending.await().isFailure)
        assertEquals("",manager.getCohortCode())
    }
}
