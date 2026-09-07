package com.example.suretouchapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.ui.screens.notifications.MobilePushRegistration
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MobilePushDeliveryInstrumentedTest {
    @Test fun registerDisposableVerificationAccount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.filesDir, "push-verification.json")
        assumeTrue("Requires an explicitly provisioned disposable verification account", fixture.exists())
        val json = JSONObject(fixture.readText())
        val manager = TokenManager(context)
        manager.saveToken(json.getString("access"), json.getString("refresh"))
        manager.saveUserRole("STUDENT")
        manager.saveUserInfo("Push verification", json.getString("email"))
        MobilePushRegistration.register(context, manager, manager.getSessionId())
        fixture.delete()
        Unit
    }
}
