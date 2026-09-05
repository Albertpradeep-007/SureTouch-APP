package com.example.suretouchapp.data.api

import com.example.suretouchapp.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

typealias ApiBody = Map<String, @JvmSuppressWildcards Any?>

/** Retrofit surface synchronized with the supplied SURE ProEd OpenAPI document. */
interface ApiService {
    // One backend-owned read powers the student dashboard and journey.
    @GET("students/statistics/") suspend fun getStudentStatistics(): Response<StudentStatisticsDto>

    @POST("auth/token/") suspend fun login(@Body request: LoginRequest): Response<TokenResponse>
    @POST("auth/token/refresh/") suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<TokenResponse>
    @POST("auth/send-verification-otp/")
    suspend fun sendEmailVerificationOtp(@Body request: SendEmailVerificationOtpRequest): Response<EmailVerificationOtpResponse>
    @POST("auth/verify-email-otp/")
    suspend fun verifyEmailOtp(@Body request: VerifyEmailOtpRequest): Response<EmailVerificationOtpResponse>
    @POST("users/forgot_password_request/")
    suspend fun requestPasswordReset(@Body request: ForgotPasswordRequest): Response<PasswordResetResponse>
    @POST("users/forgot_password_confirm/")
    suspend fun confirmPasswordReset(@Body request: ForgotPasswordConfirmRequest): Response<PasswordResetResponse>

    @GET("users/")
    suspend fun getUsers(
        @Query("role") role: String? = null,
        @Query("search") search: String? = null
    ): Response<PaginatedResponse<UserResponse>>
    @POST("users/") suspend fun register(@Body request: RegisterRequest): Response<UserResponse>
    @GET("users/{id}/") suspend fun getUserById(@Path("id") id: String): Response<UserResponse>
    @PUT("users/{id}/") suspend fun replaceUser(@Path("id") id: String, @Body body: ApiBody): Response<UserResponse>
    @PATCH("users/{id}/") suspend fun patchUser(@Path("id") id: String, @Body body: ApiBody): Response<UserResponse>
    @DELETE("users/{id}/") suspend fun deleteUser(@Path("id") id: String): Response<Unit>

    @GET("students/") suspend fun getStudents(): Response<PaginatedResponse<StudentProfileDto>>
    @POST("students/") suspend fun createStudentProfile(@Body body: ApiBody): Response<StudentProfileDto>
    @GET("students/{id}/") suspend fun getStudentProfileById(@Path("id") id: String): Response<StudentProfileDto>
    @PUT("students/{id}/") suspend fun replaceStudentProfile(@Path("id") id: String, @Body body: ApiBody): Response<StudentProfileDto>
    @PATCH("students/{id}/") suspend fun updateStudentProfile(@Path("id") id: String, @Body body: ApiBody): Response<StudentProfileDto>
    @Multipart
    @PATCH("students/{id}/")
    suspend fun uploadStudentProfilePhoto(
        @Path("id") id: String,
        @Part profilePhoto: MultipartBody.Part
    ): Response<StudentProfileDto>
    @Multipart
    @PATCH("students/{id}/")
    suspend fun uploadStudentResume(
        @Path("id") id: String,
        @Part resume: MultipartBody.Part
    ): Response<StudentProfileDto>
    @DELETE("students/{id}/") suspend fun deleteStudentProfile(@Path("id") id: String): Response<Unit>
    @GET("courses/") suspend fun getCourses(): Response<PaginatedResponse<CourseDto>>
    @POST("courses/") suspend fun createCourse(@Body body: ApiBody): Response<CourseDto>
    @GET("courses/{id}/") suspend fun getCourseById(@Path("id") id: String): Response<CourseDto>
    @PUT("courses/{id}/") suspend fun replaceCourse(@Path("id") id: String, @Body body: ApiBody): Response<CourseDto>
    @PATCH("courses/{id}/") suspend fun patchCourse(@Path("id") id: String, @Body body: ApiBody): Response<CourseDto>
    @DELETE("courses/{id}/") suspend fun deleteCourse(@Path("id") id: String): Response<Unit>

    @GET("applications/") suspend fun getMyApplications(): Response<PaginatedResponse<ApplicationDto>>
    @GET("applications/course-selection/") suspend fun getCourseSelection(): Response<CourseSelectionDto>
    @POST("applications/") suspend fun applyForCourse(@Body request: ApplicationCreateRequest): Response<ApplicationDto>
    @GET("applications/{id}/") suspend fun getApplication(@Path("id") id: String): Response<ApplicationDto>
    @PUT("applications/{id}/") suspend fun replaceApplication(@Path("id") id: String, @Body body: ApiBody): Response<ApplicationDto>
    @PATCH("applications/{id}/") suspend fun patchApplication(@Path("id") id: String, @Body body: ApiBody): Response<ApplicationDto>
    @DELETE("applications/{id}/") suspend fun deleteApplication(@Path("id") id: String): Response<Unit>
    @POST("applications/{id}/assign-cohort/") suspend fun assignApplicationCohort(@Path("id") id: String, @Body body: ApiBody): Response<ApplicationDto>
    @POST("applications/{id}/check-completion/") suspend fun checkApplicationCompletion(@Path("id") id: String): Response<ApplicationDto>
    @GET("applications/{id}/journey/") suspend fun getApplicationJourney(@Path("id") id: String): Response<StudentJourneyDto>
    @POST("applications/{id}/send-discontinue-otp/")
    suspend fun sendDiscontinueOtp(
        @Path("id") id: String
    ): Response<ApiBody>

    @POST("applications/{id}/discontinue/") suspend fun discontinueCourse(
        @Path("id") id: String,
        @Body body: ApiBody = emptyMap()
    ): Response<DiscontinueCourseResponseDto>

    @GET("cohorts/") suspend fun getCohorts(): Response<PaginatedResponse<CohortDto>>
    @POST("cohorts/") suspend fun createCohort(@Body body: ApiBody): Response<CohortDto>
    @GET("cohorts/{id}/") suspend fun getCohort(@Path("id") id: String): Response<CohortDto>
    @PUT("cohorts/{id}/") suspend fun replaceCohort(@Path("id") id: String, @Body body: ApiBody): Response<CohortDto>
    @PATCH("cohorts/{id}/") suspend fun patchCohort(@Path("id") id: String, @Body body: ApiBody): Response<CohortDto>
    @DELETE("cohorts/{id}/") suspend fun deleteCohort(@Path("id") id: String): Response<Unit>
    @POST("cohorts/{id}/create-github-repositories/") suspend fun createCohortGithubRepositories(@Path("id") id: String): Response<ApiBody>

    @GET("attendance/")
    suspend fun getAttendance(
        @Query("status") status: String? = null,
        @Query("class_date") classDate: String? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("page_size") pageSize: Int? = 500,
        @Query("page") page: Int? = null
    ): Response<PaginatedResponse<AttendanceDto>>

    @GET
    suspend fun getAttendanceByUrl(@Url url: String): Response<PaginatedResponse<AttendanceDto>>
    @POST("attendance/") suspend fun createAttendance(@Body body: ApiBody): Response<AttendanceDto>
    @GET("attendance/{id}/") suspend fun getAttendanceById(@Path("id") id: String): Response<AttendanceDto>
    @PUT("attendance/{id}/") suspend fun replaceAttendance(@Path("id") id: String, @Body body: ApiBody): Response<AttendanceDto>
    @PATCH("attendance/{id}/") suspend fun patchAttendance(@Path("id") id: String, @Body body: ApiBody): Response<AttendanceDto>
    @DELETE("attendance/{id}/") suspend fun deleteAttendance(@Path("id") id: String): Response<Unit>
    @POST("attendance/generate-lst/") suspend fun generateLstClass(@Body body: ApiBody): Response<ApiBody>
    @POST("attendance/setup-lst-automation/") suspend fun setupLstAutomation(@Body body: ApiBody): Response<ApiBody>
    @GET("attendance/get-lst-automation/") suspend fun getLstAutomation(): Response<ApiBody>
    @POST("attendance/toggle-lst-automation/") suspend fun toggleLstAutomation(@Body body: ApiBody): Response<ApiBody>
    @GET("attendance/warnings/") suspend fun getAbsenceWarnings(): Response<List<AbsenceWarningDto>>
    @POST("attendance/resolve_warning/") suspend fun resolveWarning(@Body body: ApiBody): Response<ApiBody>
    @POST("attendance/request-permission/") suspend fun requestLateJoinPermission(@Body body: ApiBody): Response<ApiBody>
    @GET("attendance/{id}/official-attendance/") suspend fun getOfficialAttendance(
        @Path("id") id: String,
        @Query("scope") scope: String? = null,
        @Query("scope_id") scopeId: String? = null
    ): Response<ApiBody>
    @POST("attendance/{id}/add-attendees/") suspend fun addAttendeesToSession(@Path("id") id: String, @Body body: ApiBody): Response<ApiBody>
    @GET("attendance/alerts/low/") suspend fun getLowAttendanceAlerts(): Response<List<ApiBody>>
    @GET("attendance/chat_history/") suspend fun getAttendanceChatHistory(@Query("warning_id") warningId: String): Response<List<PermissionRequestMessageDto>>

    @GET("assignments/") suspend fun getAssignments(): Response<PaginatedResponse<AssignmentDto>>
    @POST("assignments/") suspend fun createAssignment(@Body body: ApiBody): Response<AssignmentDto>
    @GET("assignments/{id}/") suspend fun getAssignment(@Path("id") id: String): Response<AssignmentDto>
    @PUT("assignments/{id}/") suspend fun replaceAssignment(@Path("id") id: String, @Body body: ApiBody): Response<AssignmentDto>
    @PATCH("assignments/{id}/") suspend fun patchAssignment(@Path("id") id: String, @Body body: ApiBody): Response<AssignmentDto>
    @DELETE("assignments/{id}/") suspend fun deleteAssignment(@Path("id") id: String): Response<Unit>

    @GET("submissions/") suspend fun getSubmissions(): Response<PaginatedResponse<SubmissionDto>>
    @POST("submissions/") suspend fun submitAssignment(@Body request: AssignmentSubmissionRequest): Response<SubmissionDto>
    @GET("submissions/{id}/") suspend fun getSubmission(@Path("id") id: String): Response<SubmissionDto>
    @PUT("submissions/{id}/") suspend fun replaceSubmission(@Path("id") id: String, @Body body: ApiBody): Response<SubmissionDto>
    @PATCH("submissions/{id}/") suspend fun patchSubmission(@Path("id") id: String, @Body body: ApiBody): Response<SubmissionDto>
    @DELETE("submissions/{id}/") suspend fun deleteSubmission(@Path("id") id: String): Response<Unit>

    // Android is result-only. Assessments are taken on managed laptops.
    @GET("exams/") suspend fun getScreeningResults(): Response<PaginatedResponse<ExamDto>>

    @GET("pre-screenings/") suspend fun getPreScreenings(): Response<PaginatedResponse<PreScreeningDto>>
    @GET("pre-screenings/{id}/") suspend fun getPreScreening(@Path("id") id: String): Response<PreScreeningDto>

    @GET("pre-screening-interviews/")
    suspend fun getPreScreeningInterviews(): Response<PaginatedResponse<PreScreeningInterviewDto>>
    @POST("pre-screening-interviews/")
    suspend fun createPreScreeningInterview(@Body body: ApiBody): Response<PreScreeningInterviewDto>
    @GET("pre-screening-interviews/{id}/")
    suspend fun getPreScreeningInterview(@Path("id") id: String): Response<PreScreeningInterviewDto>
    @PUT("pre-screening-interviews/{id}/")
    suspend fun replacePreScreeningInterview(@Path("id") id: String, @Body body: ApiBody): Response<PreScreeningInterviewDto>
    @PATCH("pre-screening-interviews/{id}/")
    suspend fun patchPreScreeningInterview(@Path("id") id: String, @Body body: ApiBody): Response<PreScreeningInterviewDto>
    @DELETE("pre-screening-interviews/{id}/")
    suspend fun deletePreScreeningInterview(@Path("id") id: String): Response<Unit>

    @GET("trainings/") suspend fun getTrainings(): Response<PaginatedResponse<TrainingDto>>
    @GET("training-sessions/") suspend fun getTrainingSessions(): Response<PaginatedResponse<TrainingSessionDto>>
    @GET("training-attendances/") suspend fun getTrainingAttendances(): Response<PaginatedResponse<TrainingAttendanceDto>>

    // Published module-test marks only; no attempts or answer payloads exist in Android.
    @GET("module-test-submissions/") suspend fun getModuleTestResults(): Response<PaginatedResponse<ModuleTestResultDto>>
    @GET("certificates/") suspend fun getCertificates(): Response<PaginatedResponse<CertificateDto>>
    @POST("certificates/") suspend fun createCertificate(@Body body: ApiBody): Response<CertificateDto>
    @GET("certificates/{id}/") suspend fun getCertificate(@Path("id") id: String): Response<CertificateDto>
    @PUT("certificates/{id}/") suspend fun replaceCertificate(@Path("id") id: String, @Body body: ApiBody): Response<CertificateDto>
    @PATCH("certificates/{id}/") suspend fun patchCertificate(@Path("id") id: String, @Body body: ApiBody): Response<CertificateDto>
    @DELETE("certificates/{id}/") suspend fun deleteCertificate(@Path("id") id: String): Response<Unit>
    @GET("certificates/verify/") suspend fun verifyCertificate(@Query("verification_code") code: String): Response<CertificateDto>

    @GET("companies/") suspend fun getCompanies(): Response<PaginatedResponse<CompanyDto>>
    @POST("companies/") suspend fun createCompany(@Body body: ApiBody): Response<CompanyDto>
    @GET("companies/{id}/") suspend fun getCompany(@Path("id") id: String): Response<CompanyDto>
    @PUT("companies/{id}/") suspend fun replaceCompany(@Path("id") id: String, @Body body: ApiBody): Response<CompanyDto>
    @PATCH("companies/{id}/") suspend fun patchCompany(@Path("id") id: String, @Body body: ApiBody): Response<CompanyDto>
    @DELETE("companies/{id}/") suspend fun deleteCompany(@Path("id") id: String): Response<Unit>

    // Mentor job-reference deployment extension. The backend distributes published
    // openings only to students in the supplied cohort after authorization checks.
    @GET("job-references/") suspend fun getJobReferences(): Response<PaginatedResponse<JobReferenceDto>>
    @POST("job-references/") suspend fun createJobReference(@Body body: ApiBody): Response<JobReferenceDto>
    @PATCH("job-references/{id}/") suspend fun patchJobReference(@Path("id") id: String, @Body body: ApiBody): Response<JobReferenceDto>

    @GET("notifications/") suspend fun getNotifications(): Response<PaginatedResponse<NotificationDto>>
    @POST("notifications/") suspend fun sendNotification(@Body body: ApiBody): Response<NotificationDto>
    @POST("notifications/{id}/mark-read/") suspend fun markNotificationRead(@Path("id") id: String): Response<NotificationDto>
    @POST("notifications/mark-all-read/") suspend fun markAllNotificationsRead(): Response<ApiBody>
    @GET("community-activities/") suspend fun getCommunityActivities(): Response<PaginatedResponse<CommunityActivityDto>>
    @POST("community-activities/") suspend fun createCommunityActivity(@Body body: ApiBody): Response<CommunityActivityDto>
    @POST("community-activities/{id}/verify/") suspend fun verifyCommunityActivity(@Path("id") id: String, @Body body: ApiBody): Response<CommunityActivityDto>
    @GET("announcements/") suspend fun getAnnouncements(): Response<PaginatedResponse<AnnouncementDto>>
    @POST("announcements/") suspend fun createAnnouncement(@Body body: ApiBody): Response<AnnouncementDto>
    @Multipart
    @POST("announcements/")
    suspend fun createAnnouncementMultipart(
        @Part("title") title: RequestBody,
        @Part("message") message: RequestBody,
        @Part("target_audience") targetAudience: RequestBody,
        @Part("cohort") cohort: RequestBody? = null,
        @Part("link_url") linkUrl: RequestBody? = null,
        @Part("is_pinned") isPinned: RequestBody,
        @Part("is_active") isActive: RequestBody,
        @Part attachment: MultipartBody.Part? = null
    ): Response<AnnouncementDto>
    @DELETE("announcements/{id}/") suspend fun deleteAnnouncement(@Path("id") id: String): Response<Unit>
    @GET("requests/") suspend fun getUserRequests(): Response<PaginatedResponse<UserRequestDto>>
    @Multipart
    @POST("requests/")
    suspend fun createUserRequest(
        @Part("category") category: RequestBody,
        @Part("subject") subject: RequestBody,
        @Part("description") description: RequestBody,
        @Part attachment: MultipartBody.Part? = null
    ): Response<UserRequestDto>
    @GET("auth/linkedin/connect/")
    suspend fun getLinkedInAuthUrl(@Query("client") client: String = "mobile"): Response<LinkedInAuthUrlResponse>
    @POST("auth/linkedin/callback/") suspend fun connectLinkedInCallback(@Body request: LinkedInCallbackRequest): Response<LinkedInConnectResponse>
    @POST("auth/linkedin/disconnect/") suspend fun disconnectLinkedIn(): Response<LinkedInConnectResponse>
    @GET("auth/github/connect/")
    suspend fun getGitHubAuthUrl(@Query("client") client: String = "mobile"): Response<GitHubAuthUrlResponse>
    @POST("auth/github/callback/") suspend fun connectGitHubCallback(@Body request: GitHubCallbackRequest): Response<GitHubConnectResponse>
    @POST("auth/github/disconnect/") suspend fun disconnectGitHub(): Response<GitHubConnectResponse>

    @GET("volunteers/mentor-profiles/")
    suspend fun getMentorProfiles(): Response<PaginatedResponse<MentorProfileDto>>
    @PATCH("volunteers/mentor-profiles/{id}/")
    suspend fun updateMentorProfile(@Path("id") id: String, @Body body: ApiBody): Response<MentorProfileDto>

    @GET("feedback/")
    suspend fun getFeedbacks(): Response<PaginatedResponse<FeedbackDto>>
    @POST("feedback/")
    suspend fun submitFeedback(@Body body: ApiBody): Response<FeedbackDto>

    @GET("faqs/")
    suspend fun getFaqs(): Response<PaginatedResponse<FAQDto>>

    @GET("job-postings/")
    suspend fun getJobPostings(): Response<PaginatedResponse<JobPostingDto>>
    @POST("job-postings/")
    suspend fun createJobPosting(@Body body: ApiBody): Response<JobPostingDto>

    @GET("volunteers/tasks/")
    suspend fun getVolunteerTasks(): Response<PaginatedResponse<VolunteerTaskDto>>
    @POST("volunteers/tasks/")
    suspend fun createVolunteerTask(@Body body: ApiBody): Response<VolunteerTaskDto>
    @PATCH("volunteers/tasks/{id}/")
    suspend fun patchVolunteerTask(@Path("id") id: String, @Body body: ApiBody): Response<VolunteerTaskDto>

    @GET("volunteers/profiles/")
    suspend fun getVolunteerProfiles(): Response<PaginatedResponse<VolunteerProfileDto>>
    @GET("volunteers/profiles/{id}/")
    suspend fun getVolunteerProfile(@Path("id") id: String): Response<VolunteerProfileDto>
    @PATCH("volunteers/profiles/{id}/")
    suspend fun updateVolunteerProfile(@Path("id") id: String, @Body body: ApiBody): Response<VolunteerProfileDto>
    @Multipart
    @PATCH("volunteers/profiles/{id}/")
    suspend fun uploadVolunteerProfilePhoto(
        @Path("id") id: String,
        @Part profilePhoto: MultipartBody.Part
    ): Response<VolunteerProfileDto>

    @GET("cohorts/{cohort_id}/chat/messages/")
    suspend fun getCohortChatMessages(
        @Path("cohort_id") cohortId: String,
        @Query("before") before: String? = null
    ): Response<CohortChatMessagesResponse>

    @POST("cohorts/{cohort_id}/chat/messages/")
    suspend fun sendCohortChatMessage(
        @Path("cohort_id") cohortId: String,
        @Body body: ApiBody
    ): Response<CohortChatMessageDto>

    @GET("cohorts/{cohort_id}/chat/unread-count/")
    suspend fun getCohortChatUnreadCount(@Path("cohort_id") cohortId: String): Response<CohortChatUnreadCountResponse>

    @POST("cohorts/{cohort_id}/chat/read/")
    suspend fun markCohortChatRead(@Path("cohort_id") cohortId: String): Response<ApiBody>

    @DELETE("cohorts/{cohort_id}/chat/messages/{message_id}/")
    suspend fun deleteCohortChatMessage(
        @Path("cohort_id") cohortId: String,
        @Path("message_id") messageId: String
    ): Response<ApiBody>

    @GET("app/version-check/")
    suspend fun checkAppVersion(): Response<AppVersionInfoDto>
}

/**
 * Helper to fetch all attendance records across all paginated pages from Django.
 */
suspend fun ApiService.fetchAllAttendancePages(
    status: String? = null,
    classDate: String? = null,
    dateFrom: String? = null,
    dateTo: String? = null,
    pageSize: Int = 500
): List<AttendanceDto> {
    val allItems = mutableListOf<AttendanceDto>()
    var nextUrl: String? = null
    var page = 1

    val firstResponse = runCatching {
        getAttendance(
            status = status,
            classDate = classDate,
            dateFrom = dateFrom,
            dateTo = dateTo,
            pageSize = pageSize,
            page = 1
        )
    }.getOrNull()

    if (firstResponse != null && firstResponse.isSuccessful) {
        val body = firstResponse.body()
        if (body != null) {
            allItems.addAll(body.results)
            nextUrl = body.next
        }
    }

    while (!nextUrl.isNullOrBlank() && page < 20) {
        page++
        val pageResponse = runCatching {
            getAttendanceByUrl(nextUrl)
        }.getOrNull()

        if (pageResponse != null && pageResponse.isSuccessful) {
            val body = pageResponse.body()
            if (body != null) {
                allItems.addAll(body.results)
                nextUrl = body.next
            } else {
                break
            }
        } else {
            break
        }
    }

    return allItems
}
