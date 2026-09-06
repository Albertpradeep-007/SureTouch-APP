package com.example.suretouchapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.*
import com.example.suretouchapp.ui.screens.auth.ForgotPasswordSheet
import com.example.suretouchapp.ui.theme.SureTouchAPPTheme
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.io.File

@RunWith(AndroidJUnit4::class)
class ForgotPasswordFlowInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val requests = mutableListOf<ForgotPasswordRequest>()
    private var completedRole: String? = null
    private var confirmed: ForgotPasswordConfirmRequest? = null

    private fun show(shared: Boolean) {
        val manager = TokenManager(InstrumentationRegistry.getInstrumentation().targetContext)
        manager.clearUserSessionAndProfile()
        compose.setContent { SureTouchAPPTheme {
            ForgotPasswordSheet(
                manager, initialEmail="learner@example.com", onDismiss={},
                onSuccess={ _, role -> completedRole=role },
                requestReset={ request ->
                    requests.add(request)
                    if (shared && request.role == null) Response.error(409,
                        """{"code":"ACCOUNT_ROLE_REQUIRED","roles":["STUDENT","VOLUNTEER"]}""".toResponseBody())
                    else Response.success(PasswordResetResponse(detail="Code sent"))
                },
                confirmReset={ request -> confirmed=request; Response.success(PasswordResetResponse(detail="Updated")) }
            )
        } }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation()
        val target=File(i.targetContext.getExternalFilesDir(null),name)
        target.outputStream().use { i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            i.uiAutomation.executeShellCommand("cp ${target.absolutePath} /sdcard/Download/$name")
        ).use { it.readBytes() }
    }

    @Test fun sharedEmailUsesInlineChoiceAndImmediatelySendsSelectedCode() {
        show(true)
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Choose your account").assertIsDisplayed()
        compose.onNodeWithText("Mentor").assertDoesNotExist()
        screenshot("reset-account-choice.png")
        compose.onNodeWithText("Student",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Set a new password").assertIsDisplayed()
        compose.onNodeWithText("Student account").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2,requests.size)
            assertEquals("STUDENT",requests.last().role)
        }
        screenshot("reset-code-step.png")
        compose.onNodeWithText("6-digit code").performTextInput("123456")
        compose.onNodeWithText("New password").performTextInput("Personal@Pass123!")
        compose.onNodeWithText("Confirm new password").performTextInput("Personal@Pass123!")
        compose.onNodeWithText("Reset password",substring=false).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("STUDENT",confirmed?.role)
            assertEquals("learner@example.com",confirmed?.email)
            assertEquals("STUDENT",completedRole)
        }
    }

    @Test fun singleAccountSkipsRoleChoice() {
        show(false)
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Set a new password").assertIsDisplayed()
        compose.onNodeWithText("Choose your account").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1,requests.size); assertNull(requests.single().role) }
    }
}

