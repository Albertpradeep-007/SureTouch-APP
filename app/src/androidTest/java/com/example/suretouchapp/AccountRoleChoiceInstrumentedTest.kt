package com.example.suretouchapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.suretouchapp.ui.screens.auth.AccountRoleChoiceDialog
import com.example.suretouchapp.ui.theme.SureTouchAPPTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountRoleChoiceInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun matchedRolesOnlyAndExplicitSelection() {
        var selected: String? = null
        compose.setContent { SureTouchAPPTheme {
            AccountRoleChoiceDialog(listOf("STUDENT", "VOLUNTEER"), onSelect={ selected=it }, onDismiss={})
        } }
        compose.onNodeWithText("Student").assertIsDisplayed()
        compose.onNodeWithText("Mentor").assertDoesNotExist()
        compose.onNodeWithText("Volunteer").performClick()
        compose.runOnIdle { assertEquals("VOLUNTEER", selected) }
    }

    @Test fun singleRoleDoesNotShowAnExtraPrompt() {
        compose.setContent { SureTouchAPPTheme {
            AccountRoleChoiceDialog(listOf("STUDENT"), onSelect={}, onDismiss={})
        } }
        compose.onNodeWithText("Which account do you want to use?").assertDoesNotExist()
    }

    @Test fun resetClearlyNamesTheAccountChoice() {
        compose.setContent { SureTouchAPPTheme {
            AccountRoleChoiceDialog(listOf("STUDENT", "MENTOR"), forPasswordReset=true, onSelect={}, onDismiss={})
        } }
        compose.onNodeWithText("Reset password for which account?").assertIsDisplayed()
        compose.onNodeWithText("Volunteer").assertDoesNotExist()
    }
}
