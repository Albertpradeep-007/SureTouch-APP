package com.example.suretouchapp

import com.example.suretouchapp.data.model.accountRoleChoices
import org.junit.Assert.*
import org.junit.Test

class AccountRoleChoiceTest {
    @Test fun onlyServerMatchedRolesAreOffered() {
        assertEquals(listOf("STUDENT", "VOLUNTEER"), accountRoleChoices("""{"code":"ACCOUNT_ROLE_REQUIRED","roles":["STUDENT","VOLUNTEER"]}"""))
    }
    @Test fun singleAccountNeverOpensChooser() {
        assertTrue(accountRoleChoices("""{"code":"ACCOUNT_ROLE_REQUIRED","roles":["STUDENT"]}""").isEmpty())
    }
    @Test fun ordinaryErrorsAndSuccessfulLoginNeverOpenChooser() {
        assertTrue(accountRoleChoices("""{"detail":"Invalid credentials"}""").isEmpty())
        assertTrue(accountRoleChoices("""{"access":"token","user":{"role":"STUDENT"}}""").isEmpty())
    }
    @Test fun malformedAndDuplicateRolesNeverCreateChoices() {
        assertTrue(accountRoleChoices("unavailable").isEmpty())
        assertTrue(accountRoleChoices("""{"code":"ACCOUNT_ROLE_REQUIRED","roles":["STUDENT","STUDENT","UNKNOWN"]}""").isEmpty())
    }
}
