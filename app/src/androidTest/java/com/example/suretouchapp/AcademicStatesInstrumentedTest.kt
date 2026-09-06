package com.example.suretouchapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.suretouchapp.data.repository.DashboardSnapshot
import com.example.suretouchapp.ui.screens.dashboard.ProfessionalGradesScreen
import com.example.suretouchapp.ui.theme.SureTouchAPPTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AcademicStatesInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun show(snapshot: DashboardSnapshot) {
        compose.setContent { SureTouchAPPTheme { ProfessionalGradesScreen(snapshot, onBack={}) } }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val image=instrumentation.uiAutomation.takeScreenshot()
            ?: error("Unable to capture the emulator display")
        val dir=instrumentation.targetContext.getExternalFilesDir(null)
            ?: instrumentation.targetContext.cacheDir
        File(dir,name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun newAccountShowsZeroResultsWithoutFictitiousExam() {
        show(DashboardSnapshot())
        compose.onNodeWithText("0 RESULTS").assertIsDisplayed()
        compose.onNodeWithText("Pre-Screen Examination").assertDoesNotExist()
        compose.onNodeWithText("No screening result yet").assertIsDisplayed()
        screenshot("new-account-grades.png")
    }
    @Test fun administrativeQualificationAloneDoesNotShowExamPass() {
        show(DashboardSnapshot(cohortCode="G2-26",screeningQualified=true))
        compose.onNodeWithText("0 RESULTS").assertIsDisplayed()
        compose.onNodeWithText("PASS").assertDoesNotExist()
        screenshot("admin-enrolled-grades.png")
    }
    @Test fun zeroMarksAreDisplayedAsARealResult() {
        show(DashboardSnapshot(screeningMarksObtained="0",screeningTotalMarks="100",screeningPercentage="0",screeningQualified=false))
        compose.onNodeWithText("1 RESULT").assertIsDisplayed()
        compose.onNodeWithText("0 / 100").assertIsDisplayed()
        screenshot("zero-mark-grades.png")
    }
}
