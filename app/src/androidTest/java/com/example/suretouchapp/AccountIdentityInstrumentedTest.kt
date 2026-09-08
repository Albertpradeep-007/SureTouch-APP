package com.example.suretouchapp

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.UserResponse
import com.example.suretouchapp.data.repository.AccountSessionRepository
import com.example.suretouchapp.data.repository.AccountWorkspace
import com.example.suretouchapp.data.repository.accountWorkspace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class AccountIdentityInstrumentedTest {
    private fun manager(): TokenManager = TokenManager(
        InstrumentationRegistry.getInstrumentation().targetContext
    ).also { it.clearUserSessionAndProfile(); it.saveToken("access", "refresh") }

    @Test fun mappedEmailLoginUsesAuthenticatedPrimaryIdentityAndRole() = runBlocking {
        val manager = manager()
        val repository = AccountSessionRepository(manager) {
            Response.success(UserResponse(id="staff-id", email="staff@suretrust.local", role="VOLUNTEER"))
        }
        repository.verifyCurrentAccount()
        assertEquals("staff@suretrust.local", manager.getUserEmail())
        assertEquals(AccountWorkspace.VOLUNTEER, accountWorkspace(manager.getUserRole()))
        assertNotEquals(AccountWorkspace.STUDENT, accountWorkspace(manager.getUserRole()))
    }

    @Test fun temporaryIdentityFailureRetainsCredentialsWithoutOpeningAnUnverifiedWorkspace() = runBlocking {
        val manager = manager()
        val repository = AccountSessionRepository(manager) {
            Response.error<UserResponse>(500, "server error".toResponseBody())
        }
        assertTrue(runCatching { repository.verifyCurrentAccount() }.isFailure)
        assertTrue(manager.isLoggedIn())
        assertFalse(manager.hasVerifiedIdentity())
    }

    @Test fun oldIdentityResponseCannotChangeNewLogin() = runBlocking {
        val manager = manager()
        val gate = CompletableDeferred<Unit>()
        val repository = AccountSessionRepository(manager) {
            gate.await()
            Response.success(UserResponse(id="old-id", email="old@example.com", role="VOLUNTEER"))
        }
        val pending = async(start=CoroutineStart.UNDISPATCHED) { runCatching { repository.verifyCurrentAccount() } }
        manager.saveToken("new-access", "new-refresh")
        manager.saveUserInfo("New", "new@example.com")
        manager.saveUserRole("STUDENT")
        gate.complete(Unit)
        assertTrue(pending.await().isFailure)
        assertEquals("new@example.com", manager.getUserEmail())
        assertEquals("STUDENT", manager.getUserRole())
        assertTrue(manager.isLoggedIn())
    }

    @Test fun legacyWrongIdentitySessionRequiresFreshSignInOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("sure_proed_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("access_token", "legacy").putString("user_role", "STUDENT").commit()
        val manager = TokenManager(context)
        assertFalse(manager.isLoggedIn())
        manager.saveToken("canonical", "refresh")
        manager.saveUserInfo("Student", "seeded@example.com")
        manager.saveUserRole("STUDENT")
        assertTrue(TokenManager(context).isLoggedIn())
    }
}
