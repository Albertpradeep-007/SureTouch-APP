package com.example.suretouchapp

import com.example.suretouchapp.data.model.ApplicationCreateRequest
import com.example.suretouchapp.data.model.ApplicationDto
import com.example.suretouchapp.data.model.ExamDto
import com.example.suretouchapp.data.model.ForgotPasswordConfirmRequest
import com.example.suretouchapp.data.model.VolunteerProfileDto
import com.example.suretouchapp.ui.screens.courses.allowsAnotherCourseApplication
import com.example.suretouchapp.ui.screens.courses.blocksCourseSelection
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApplicationApiContractTest {
    private val gson = Gson()

    @Test
    fun applicationCreatePayloadMatchesOpenApiContract() {
        val json = gson.toJsonTree(
            ApplicationCreateRequest(
                applicationNumber = "APP-20260807-001",
                course = "22222222-2222-2222-2222-222222222222"
            )
        ).asJsonObject

        assertEquals("APP-20260807-001", json["application_number"].asString)
        assertEquals(false, json.has("student"))
        assertEquals("22222222-2222-2222-2222-222222222222", json["course"].asString)
        assertEquals("APPLIED", json["status"].asString)
    }

    @Test
    fun applicationResponseReadsNestedPreScreening() {
        val application = gson.fromJson(
            """{
                "id":"33333333-3333-3333-3333-333333333333",
                "application_number":"APP-20260807-001",
                "student":"11111111-1111-1111-1111-111111111111",
                "course":"22222222-2222-2222-2222-222222222222",
                "status":"PRESCREENING_PENDING",
                "pre_screening":{
                    "id":"44444444-4444-4444-4444-444444444444",
                    "application":"33333333-3333-3333-3333-333333333333",
                    "status":"SCHEDULED"
                }
            }""".trimIndent(),
            ApplicationDto::class.java
        )

        assertNotNull(application.preScreening)
        assertEquals("SCHEDULED", application.preScreening?.status)
        assertEquals(application.id, application.preScreening?.application)
    }

    @Test
    fun activeApplicationKeepsEveryOtherCourseLocked() {
        val application = ApplicationDto(id = "application-1", status = "APPLIED")

        assertEquals(false, application.allowsAnotherCourseApplication(null))
    }

    @Test
    fun failedEvaluatedExamAllowsAnotherApplication() {
        val application = ApplicationDto(id = "application-1", status = "EXAM_COMPLETED", qualified = false)
        val exam = ExamDto(application = application.id, status = "EVALUATED", qualified = false)

        assertEquals(true, application.allowsAnotherCourseApplication(exam))
    }

    @Test
    fun successfulExamKeepsCourseSelectionLocked() {
        val application = ApplicationDto(id = "application-1", status = "QUALIFIED", qualified = true)
        val exam = ExamDto(application = application.id, status = "EVALUATED", qualified = true)

        assertEquals(false, application.allowsAnotherCourseApplication(exam))
    }

    @Test
    fun activeApplicationBlocksCourseSelection() {
        assertEquals(true, ApplicationDto(id = "active", status = "APPLIED").blocksCourseSelection())
    }

    @Test
    fun closedOrFailedApplicationReleasesCourseSelection() {
        listOf("REJECTED", "DROPPED", "CANCELLED", "COMPLETED").forEach { status ->
            assertEquals(false, ApplicationDto(id = status, status = status).blocksCourseSelection())
        }
        assertEquals(
            false,
            ApplicationDto(id = "failed", status = "EXAM_COMPLETED", qualified = false).blocksCourseSelection()
        )
    }

    @Test
    fun forgotPasswordConfirmationMatchesBackendContract() {
        val json = gson.toJsonTree(
            ForgotPasswordConfirmRequest(
                email = "student@example.com",
                otp = "123456",
                newPassword = "SecurePass123"
            )
        ).asJsonObject

        assertEquals("student@example.com", json["email"].asString)
        assertEquals("123456", json["otp"].asString)
        assertEquals("SecurePass123", json["new_password"].asString)
        assertEquals(false, json.has("newPassword"))
    }

    @Test
    fun volunteerProfileReadsProductionBackendShape() {
        val profile = gson.fromJson(
            """{
                "id":"profile-1",
                "user":"user-1",
                "first_name":"Asha",
                "last_name":"Rao",
                "email":"asha@example.com",
                "full_name":"Asha Rao",
                "organization_name":"SURE Trust",
                "occupation":"Community Volunteer",
                "profile_photo":"/media/volunteers/asha.jpg",
                "skills":"Mentoring, Digital Literacy",
                "availability_notes":"Weekends",
                "bio":"Volunteer mentor",
                "linkedin_url":"https://www.linkedin.com/in/asha",
                "assigned_cohorts":[{
                    "id":"cohort-1",
                    "code":"COHORT-01",
                    "name":"Digital Skills",
                    "course":"course-1",
                    "meeting_link":"https://meet.example.com/class"
                }],
                "upcoming_classes":[{
                    "id":"class-1",
                    "cohort":"cohort-1",
                    "title":"Orientation",
                    "class_date":"2026-09-01",
                    "start_time":"10:00:00"
                }]
            }""".trimIndent(),
            VolunteerProfileDto::class.java
        )

        assertEquals("Asha Rao", profile.fullName)
        assertEquals("SURE Trust", profile.organizationName)
        assertEquals("Mentoring, Digital Literacy", profile.skills)
        assertEquals("cohort-1", profile.assignedCohorts.single().id)
        assertEquals("Orientation", profile.upcomingClasses.single().sessionTitle)
        assertEquals(null, profile.hoursContributed)
    }
}
