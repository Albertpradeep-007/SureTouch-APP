package com.example.suretouchapp.data.api

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.util.Locale
import java.util.UUID

class TokenManager internal constructor(
    private val prefs: SharedPreferences,
    private val clearNotifications: () -> Unit = {}
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("sure_proed_prefs", Context.MODE_PRIVATE),
        {
            // These directories contain only private, reproducible document copies.
            java.io.File(context.filesDir, "resumes").deleteRecursively()
            java.io.File(context.cacheDir, "private_documents").deleteRecursively()
            context.cacheDir.listFiles()?.filter { it.name.startsWith("view_resume_") || it.name.startsWith("temp_resume_") }?.forEach { it.delete() }
            androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
            context.getSharedPreferences("sure_proed_notification_delivery", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("sure_proed_push_delivery_metrics", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("sure_proed_push_registration", Context.MODE_PRIVATE).edit().clear().apply()
        }
    )

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_IDENTITY_CONTRACT_VERSION = "identity_contract_version"
        private const val IDENTITY_CONTRACT_VERSION = 1
        private val sessionLock = Any()
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_APPLICATION_NOTICE_COURSE = "application_notice_course"
        private const val KEY_APPLICATION_NOTICE_TIME = "application_notice_time"
        private const val KEY_APPLICATION_NOTICE_UNREAD = "application_notice_unread"

        val sessionExpiredFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
    }

    init {
        synchronized(sessionLock) {
            // Earlier APKs could retain a token for a mapped-email account while
            // displaying another identity. Require a fresh, canonical sign-in once.
            if (prefs.contains(KEY_ACCESS_TOKEN) &&
                prefs.getInt(KEY_IDENTITY_CONTRACT_VERSION, 0) != IDENTITY_CONTRACT_VERSION
            ) {
                clearUserSessionAndProfile()
            }
        }
    }

    fun logout() = synchronized(sessionLock) {
        clearUserSessionAndProfile()
        sessionExpiredFlow.tryEmit(Unit)
        Unit
    }

    fun logoutIfCurrentSession(sessionId: String) = synchronized(sessionLock) {
        if (getSessionId() == sessionId) logout()
    }

    fun clearUserSessionAndProfile() = synchronized(sessionLock) {
        // This preference file contains account data only. Clearing it also removes
        // legacy fields and notice state that a maintained removal list can miss.
        prefs.edit().clear().putString(KEY_SESSION_ID, UUID.randomUUID().toString()).apply()
        clearNotifications()
    }

    fun clear() = logout()

    /** A login begins a fresh session; token refresh must use saveRefreshedToken. */
    fun saveToken(access: String, refresh: String) = synchronized(sessionLock) {
        clearNotifications()
        prefs.edit().clear()
            .putString(KEY_SESSION_ID, UUID.randomUUID().toString())
            .putInt(KEY_IDENTITY_CONTRACT_VERSION, IDENTITY_CONTRACT_VERSION)
            .putString(KEY_ACCESS_TOKEN, access)
            .putString(KEY_REFRESH_TOKEN, refresh)
            .putBoolean("session_identity_pending", true)
            .putBoolean("offline_session", false)
            .apply()
    }

    fun saveRefreshedToken(sessionId: String, access: String, refresh: String): Boolean =
        synchronized(sessionLock) {
            if (getSessionId() != sessionId) return@synchronized false
            prefs.edit().putString(KEY_ACCESS_TOKEN, access)
                .putString(KEY_REFRESH_TOKEN, refresh).apply()
            true
        }

    fun getSessionId(): String = synchronized(sessionLock) {
        prefs.getString(KEY_SESSION_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_SESSION_ID, it).apply()
        }
    }

    fun isCurrentSession(sessionId: String): Boolean = getSessionId() == sessionId

    fun requireCurrentSession(sessionId: String) {
        if (!isCurrentSession(sessionId)) throw IOException("Account session changed. Please reload.")
    }

    /** Keep response validation and all related preference/cache writes atomic with logout. */
    fun <T> withCurrentSession(sessionId: String, action: () -> T): T = synchronized(sessionLock) {
        requireCurrentSession(sessionId)
        action()
    }

    fun startOfflineSession() = synchronized(sessionLock) {
        clearNotifications()
        prefs.edit().clear()
            .putString(KEY_SESSION_ID, UUID.randomUUID().toString())
            .putBoolean("offline_session", true)
            .apply()
    }

    fun saveUserInfo(name: String, email: String) = synchronized(sessionLock) {
        val normalizedEmail = email.trim().lowercase(Locale.US)
        val previousEmail = getUserEmail().trim().lowercase(Locale.US)
        val editor = prefs.edit()
        if (previousEmail != normalizedEmail && !prefs.getBoolean("session_identity_pending", false)) {
            // Also clean legacy cached data when the previous email is blank.
            val retainedKeys = setOf(KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN, KEY_SESSION_ID, KEY_IDENTITY_CONTRACT_VERSION, "offline_session")
            prefs.all.keys.filterNot { it in retainedKeys }.forEach(editor::remove)
            if (previousEmail.isNotBlank()) {
                editor.putString(KEY_SESSION_ID, UUID.randomUUID().toString())
            }
        }
        editor.remove("session_identity_pending")
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, normalizedEmail)
            .apply()
    }

    fun registerUserAccount(email: String, pass: String, name: String) {
        val cleanEmail = email.trim().lowercase()
        prefs.edit()
            .putString("pass_$cleanEmail", pass.trim())
            .putString("name_$cleanEmail", name.trim())
            .apply()
    }

    fun isValidAccount(email: String, pass: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val storedPass = prefs.getString("pass_$cleanEmail", null)
        return storedPass != null && storedPass == pass.trim()
    }

    fun getRegisteredName(email: String): String {
        val cleanEmail = email.trim().lowercase()
        return prefs.getString("name_$cleanEmail", null) ?: cleanEmail.substringBefore("@")
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "Student") ?: "Student"

    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""

    fun markNewAccountOnboarding() {
        prefs.edit()
            .putBoolean("new_account_welcome", true)
            .putBoolean("needs_course_selection", true)
            .apply()
    }

    fun shouldShowWelcome(): Boolean = prefs.getBoolean("new_account_welcome", false)

    fun needsCourseSelection(): Boolean = prefs.getBoolean("needs_course_selection", false)

    fun isLiveClassGuidelinesAgreed(): Boolean = prefs.getBoolean("live_class_guidelines_agreed", false)

    fun saveLiveClassGuidelinesAgreed(agreed: Boolean) {
        prefs.edit().putBoolean("live_class_guidelines_agreed", agreed).apply()
    }

    fun getReadNoticeIds(): Set<String> = prefs.getStringSet("read_notice_ids", emptySet()) ?: emptySet()

    fun markNoticeRead(id: String) {
        if (id.isBlank()) return
        val current = getReadNoticeIds().toMutableSet()
        current.add(id)
        prefs.edit().putStringSet("read_notice_ids", current).apply()
    }

    fun markAllNoticesRead(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val current = getReadNoticeIds().toMutableSet()
        current.addAll(ids.filter { it.isNotBlank() })
        prefs.edit().putStringSet("read_notice_ids", current).apply()
    }

    fun isNoticeRead(id: String): Boolean = getReadNoticeIds().contains(id)

    fun getUnreadNoticeCount(noticeIds: Collection<String>): Int {
        val read = getReadNoticeIds()
        return noticeIds.count { it.isNotBlank() && !read.contains(it) }
    }

    fun markCourseApplied() {
        prefs.edit().putBoolean("needs_course_selection", false).apply()
    }

    fun saveApplicationSnapshot(
        applicationNumber: String?,
        status: String?,
        courseId: String?,
        courseTitle: String?,
        assignedCohort: String? = null,
        qualified: Boolean? = null
    ) {
        prefs.edit()
            .putString("last_application_number", applicationNumber)
            .putString("last_application_status", status)
            .putString("last_application_course_id", courseId)
            .putString("last_application_course_title", courseTitle)
            .putString("last_application_cohort_id", assignedCohort)
            .apply {
                if (qualified == null) remove("last_application_qualified")
                else putBoolean("last_application_qualified", qualified)
            }
            .apply()
    }

    fun getApplicationSnapshot(): CachedApplicationSnapshot? {
        val number = prefs.getString("last_application_number", null)
        val status = prefs.getString("last_application_status", null)
        val courseId = prefs.getString("last_application_course_id", null)
        val title = prefs.getString("last_application_course_title", null)
        if (number.isNullOrBlank() && status.isNullOrBlank() && courseId.isNullOrBlank() && title.isNullOrBlank()) return null
        return CachedApplicationSnapshot(
            applicationNumber = number,
            status = status,
            courseId = courseId,
            courseTitle = title,
            assignedCohort = prefs.getString("last_application_cohort_id", null),
            qualified = if (prefs.contains("last_application_qualified")) prefs.getBoolean("last_application_qualified", false) else null
        )
    }

    fun saveCourseApplicationNotice(courseName: String) {
        prefs.edit()
            .putString(KEY_APPLICATION_NOTICE_COURSE, courseName.trim())
            .putLong(KEY_APPLICATION_NOTICE_TIME, System.currentTimeMillis())
            .putBoolean(KEY_APPLICATION_NOTICE_UNREAD, true)
            .apply()
    }

    fun getCourseApplicationNoticeCourse(): String? =
        prefs.getString(KEY_APPLICATION_NOTICE_COURSE, null)?.takeIf(String::isNotBlank)

    fun getCourseApplicationNoticeTime(): Long = prefs.getLong(KEY_APPLICATION_NOTICE_TIME, 0L)

    fun hasUnreadCourseApplicationNotice(): Boolean =
        prefs.getBoolean(KEY_APPLICATION_NOTICE_UNREAD, false)

    fun markCourseApplicationNoticeRead() {
        prefs.edit().putBoolean(KEY_APPLICATION_NOTICE_UNREAD, false).apply()
    }

    fun saveUserRole(role: String) {
        prefs.edit().putString(KEY_USER_ROLE, role.uppercase()).apply()
    }

    fun getUserRole(): String = prefs.getString(KEY_USER_ROLE, "STUDENT") ?: "STUDENT"

    fun isMentor(): Boolean = getUserRole() == "MENTOR"

    fun isVolunteerTrustee(): Boolean = getUserRole() in setOf(
        "VOLUNTEER", "VOLUNTEER_TRUSTEE", "VOLUNTEER TRUSTEE", "TRUSTEE"
    )

    fun isCompany(): Boolean = getUserRole() in setOf(
        "COMPANY", "RECRUITER", "EMPLOYER", "HR"
    )

    fun clearAll() = logout()

    fun saveStudentProfileDetails(
        phone: String = getPhone(),
        qualification: String = getQualification(),
        collegeName: String = getCollegeName(),
        bio: String = getBio(),
        githubUrl: String = getGithubUrl(),
        linkedinUrl: String = getLinkedinUrl(),
        tagline: String = getTagline(),
        specialization: String = getSpecialization(),
        graduationYear: Int? = getGraduationYear(),
        city: String = getCity(),
        state: String = getState(),
        country: String = getCountry(),
        skills: List<String> = getSkills(),
        hobbies: List<String> = getHobbies(),
        languages: List<String> = getLanguages(),
        portfolioUrl: String = getPortfolioUrl(),
        gender: String = getGender(),
        dob: String = getDob(),
        permanentAddress: String = getPermanentAddress(),
        fatherName: String = getFatherName(),
        motherName: String = getMotherName()
    ) {
        val editor = prefs.edit()
            .putString("profile_phone", phone.trim())
            .putString("profile_qualification", qualification.trim())
            .putString("profile_college", collegeName.trim())
            .putString("profile_bio", bio.trim())
            .putString("profile_github", githubUrl.trim())
            .putString("profile_linkedin", linkedinUrl.trim())
            .putString("profile_tagline", tagline.trim())
            .putString("profile_specialization", specialization.trim())
            .putString("profile_city", city.trim())
            .putString("profile_state", state.trim())
            .putString("profile_country", country.trim())
            .putString("profile_gender", gender.trim())
            .putString("profile_dob", dob.trim())
            .putString("profile_permanent_address", permanentAddress.trim())
            .putString("profile_father_name", fatherName.trim())
            .putString("profile_mother_name", motherName.trim())
            .putString("profile_skills", skills.joinToString("\n"))
            .putString("profile_hobbies", hobbies.joinToString("\n"))
            .putString("profile_languages", languages.joinToString("\n"))
            .putString("profile_portfolio", portfolioUrl.trim())
        if (graduationYear != null) {
            editor.putInt("profile_graduation_year", graduationYear)
        } else {
            editor.remove("profile_graduation_year")
        }
        editor.apply()
    }

    fun savePhone(phone: String) {
        prefs.edit().putString("profile_phone", phone.trim()).apply()
    }

    fun saveGender(gender: String) {
        prefs.edit().putString("profile_gender", gender.trim()).apply()
    }

    fun saveDob(dob: String) {
        prefs.edit().putString("profile_dob", dob.trim()).apply()
    }

    fun savePermanentAddress(address: String) {
        prefs.edit().putString("profile_permanent_address", address.trim()).apply()
    }

    fun saveFatherName(name: String) {
        prefs.edit().putString("profile_father_name", name.trim()).apply()
    }

    fun saveMotherName(name: String) {
        prefs.edit().putString("profile_mother_name", name.trim()).apply()
    }

    fun getPhone(): String = prefs.getString("profile_phone", "") ?: ""
    fun getQualification(): String = prefs.getString("profile_qualification", "") ?: ""
    fun getCollegeName(): String = prefs.getString("profile_college", "") ?: ""
    fun getBio(): String = prefs.getString("profile_bio", "") ?: ""
    fun getGithubUrl(): String = prefs.getString("profile_github", "") ?: ""
    fun getLinkedinUrl(): String = prefs.getString("profile_linkedin", "") ?: ""
    fun getTagline(): String = prefs.getString("profile_tagline", "") ?: ""
    fun getSpecialization(): String = prefs.getString("profile_specialization", "") ?: ""
    fun getGraduationYear(): Int? = if (prefs.contains("profile_graduation_year")) prefs.getInt("profile_graduation_year", 0).takeIf { it > 0 } else null
    fun getCity(): String = prefs.getString("profile_city", "") ?: ""
    fun getState(): String = prefs.getString("profile_state", "") ?: ""
    fun getCountry(): String = prefs.getString("profile_country", "") ?: ""
    fun getGender(): String = prefs.getString("profile_gender", "") ?: ""
    fun getDob(): String = prefs.getString("profile_dob", "") ?: ""
    fun getPermanentAddress(): String = prefs.getString("profile_permanent_address", "") ?: ""
    fun getFatherName(): String = prefs.getString("profile_father_name", "") ?: ""
    fun getMotherName(): String = prefs.getString("profile_mother_name", "") ?: ""
    fun getSkills(): List<String> = prefs.getString("profile_skills", null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    fun getHobbies(): List<String> = prefs.getString("profile_hobbies", null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    fun getLanguages(): List<String> = prefs.getString("profile_languages", null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    fun getPortfolioUrl(): String = prefs.getString("profile_portfolio", "") ?: ""

    fun saveResumeDetails(resumeUrl: String, fileName: String = "", localPath: String = "") {
        val editor = prefs.edit()
            .putString("profile_resume_url", resumeUrl.trim())
            .putString("profile_resume_name", fileName.trim())
        if (localPath.isNotBlank()) {
            editor.putString("profile_local_resume_path", localPath.trim())
        } else {
            // A server refresh or replacement must never retain a file from an
            // older resume (or another login session) as a local fallback.
            editor.remove("profile_local_resume_path")
        }
        editor.apply()
    }

    fun saveLocalResumePath(localPath: String) {
        prefs.edit().putString("profile_local_resume_path", localPath.trim()).apply()
    }

    fun getLocalResumePath(): String = prefs.getString("profile_local_resume_path", "") ?: ""

    fun clearResumeDetails() {
        prefs.edit()
            .remove("profile_resume_url")
            .remove("profile_resume_name")
            .remove("profile_local_resume_path")
            .apply()
    }

    fun getResumeUrl(): String = prefs.getString("profile_resume_url", "") ?: ""
    fun getResumeName(): String = prefs.getString("profile_resume_name", "") ?: ""

    fun saveStudentCode(value: String) {
        prefs.edit().putString("student_code", value.trim()).apply()
    }

    fun getStudentCode(): String = prefs.getString("student_code", "") ?: ""

    fun saveCohortCode(value: String) {
        prefs.edit().putString("cohort_code", value.trim()).apply()
    }

    fun getCohortCode(): String = prefs.getString("cohort_code", "") ?: ""
    fun getCourseTitle(): String = prefs.getString("last_application_course_title", "") ?: ""

    fun getCompany(): String = prefs.getString("profile_college", "") ?: ""

    fun clearCohortCode() {
        prefs.edit().remove("cohort_code").apply()
    }

    fun saveProfilePhotoUrl(url: String) {
        prefs.edit().putString("profile_photo_url", url.trim()).apply()
    }

    fun getProfilePhotoUrl(): String? = prefs.getString("profile_photo_url", null)?.takeIf(String::isNotBlank)

    fun saveCoverPhotoUrl(url: String?) {
        if (url.isNullOrBlank()) {
            prefs.edit().remove("mentor_cover_photo_url").apply()
        } else {
            prefs.edit().putString("mentor_cover_photo_url", url.trim()).apply()
        }
    }

    fun getCoverPhotoUrl(): String? = prefs.getString("mentor_cover_photo_url", null)?.takeIf(String::isNotBlank)

    fun saveMentorProfileDetails(
        name: String,
        designation: String,
        company: String,
        headline: String,
        qualification: String,
        location: String,
        phone: String,
        dob: String,
        gender: String,
        linkedinUrl: String,
        experienceYears: Int
    ) {
        prefs.edit()
            .putString(KEY_USER_NAME, name.trim())
            .putString("profile_designation", designation.trim())
            .putString("profile_company", company.trim())
            .putString("profile_tagline", headline.trim())
            .putString("profile_qualification", qualification.trim())
            .putString("profile_location", location.trim())
            .putString("profile_phone", phone.trim())
            .putString("profile_dob", dob.trim())
            .putString("profile_gender", gender.trim())
            .putString("profile_linkedin", linkedinUrl.trim())
            .putInt("profile_experience", experienceYears)
            .apply()
    }

    fun saveMentorId(id: String) {
        prefs.edit().putString("mentor_id", id.trim()).apply()
    }
    fun getMentorId(): String = prefs.getString("mentor_id", "") ?: ""
    fun getMentorDesignation(): String = prefs.getString("profile_designation", "") ?: ""
    fun getMentorCompany(): String = prefs.getString("profile_company", "") ?: ""
    fun getMentorLocation(): String = prefs.getString("profile_location", "") ?: ""
    fun getMentorDob(): String = prefs.getString("profile_dob", "") ?: ""
    fun getMentorGender(): String = prefs.getString("profile_gender", "") ?: ""
    fun getMentorExperience(): Int = prefs.getInt("profile_experience", 0)

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrEmpty() || prefs.getBoolean("offline_session", false)

    fun hasVerifiedIdentity(): Boolean = isLoggedIn() && !prefs.getBoolean("session_identity_pending", false)

    fun getQuickAccessTitles(role: String = "STUDENT"): Set<String>? = synchronized(sessionLock) {
        val raw = prefs.getStringSet("quick_access_titles_${role.uppercase()}", null)
        return raw?.toSet()
    }

    fun saveQuickAccessTitles(role: String = "STUDENT", titles: Set<String>) = synchronized(sessionLock) {
        prefs.edit().putStringSet("quick_access_titles_${role.uppercase()}", titles.toSet()).apply()
    }

    fun resetQuickAccessTitles(role: String = "STUDENT") = synchronized(sessionLock) {
        prefs.edit().remove("quick_access_titles_${role.uppercase()}").apply()
    }
}

data class CachedApplicationSnapshot(
    val applicationNumber: String?,
    val status: String?,
    val courseId: String?,
    val courseTitle: String?,
    val assignedCohort: String?,
    val qualified: Boolean?
)
