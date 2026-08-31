package com.example.suretouchapp.data.model

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement

data class PaginatedResponse<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList()
)

/** Models below mirror the supplied SURE ProEd OpenAPI schemas. */
data class StudentProfileDto(
    val id: String = "",
    // The final OpenAPI schema returns a UUID here. Some deployed student-summary
    // responses still expand the same field to a user object, so accept both shapes.
    @SerializedName("user") private val userValue: JsonElement? = null,
    @SerializedName("student_code") val studentCode: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("student_name") val studentName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("user_email") val userEmail: String? = null,
    @SerializedName("user_phone") val userPhone: String? = null,
    @SerializedName("user_first_name") val userFirstName: String? = null,
    @SerializedName("user_last_name") val userLastName: String? = null,
    @SerializedName("user_full_name") val userFullName: String? = null,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("is_public") val isPublic: Boolean = false,
    @SerializedName("profile_photo") val profilePhoto: String? = null,
    @SerializedName("profile_photo_url") val profilePhotoUrl: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("picture") val picture: String? = null,
    @SerializedName("profile_picture") val profilePicture: String? = null,
    val tagline: String? = null,
    val bio: String? = null,
    val status: String? = "AVAILABLE",
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val college: String? = null,
    val degree: String? = null,
    val specialization: String? = null,
    @SerializedName("education_level") val educationLevel: String? = null,
    @SerializedName("graduation_year") val graduationYear: Int? = null,
    @SerializedName("skills") val skills: List<String> = emptyList(),
    @SerializedName("hobbies") val hobbies: List<String> = emptyList(),
    @SerializedName("languages") val languages: List<String> = emptyList(),
    @SerializedName("linkedin_url") val linkedinUrl: String? = null,
    @SerializedName("linkedin_profile_photo_url") val linkedinProfilePhotoUrl: String? = null,
    @SerializedName("linkedin_photo") val linkedinPhoto: String? = null,
    @SerializedName("linkedin_avatar") val linkedinAvatar: String? = null,
    @SerializedName("linkedin_picture") val linkedinPicture: String? = null,
    @SerializedName("is_linkedin_connected") val isLinkedinConnected: Boolean = false,
    @SerializedName("github_url") val githubUrl: String? = null,
    @SerializedName("github_username") val githubUsername: String? = null,
    @SerializedName("portfolio_url") val portfolioUrl: String? = null,
    @SerializedName("resume") val resume: String? = null,
    @SerializedName("resume_url") val resumeUrl: String? = null,
    // Optional deployment extensions used by the student-facing profile UI.
    @SerializedName("father_name") val fatherName: String? = null,
    @SerializedName("mother_name") val motherName: String? = null,
    @SerializedName("permanent_address") val permanentAddress: String? = null,
    @SerializedName("correspondence_address") val correspondenceAddress: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    @SerializedName("cohort") val cohortId: String? = null,
    @SerializedName("cohort_code") val cohortCode: String? = null
) {
    val effectiveProfilePhoto: String?
        get() = profilePhoto?.takeIf(String::isNotBlank)
            ?: profilePhotoUrl?.takeIf(String::isNotBlank)
            ?: linkedinProfilePhotoUrl?.takeIf(String::isNotBlank)
            ?: linkedinPhoto?.takeIf(String::isNotBlank)
            ?: linkedinAvatar?.takeIf(String::isNotBlank)
            ?: linkedinPicture?.takeIf(String::isNotBlank)
            ?: photoUrl?.takeIf(String::isNotBlank)
            ?: avatar?.takeIf(String::isNotBlank)
            ?: picture?.takeIf(String::isNotBlank)
            ?: profilePicture?.takeIf(String::isNotBlank)
            ?: user?.effectiveProfilePhoto
    val user: UserDto?
        get() = userValue?.let { value ->
            when {
                value.isJsonPrimitive -> UserResponse(id = value.asString, email = "")
                value.isJsonObject -> value.asJsonObject.let { obj ->
                    UserResponse(
                        id = obj.get("id")?.takeUnless(JsonElement::isJsonNull)?.asString,
                        email = obj.get("email")?.takeUnless(JsonElement::isJsonNull)?.asString.orEmpty(),
                        firstName = obj.get("first_name")?.takeUnless(JsonElement::isJsonNull)?.asString,
                        lastName = obj.get("last_name")?.takeUnless(JsonElement::isJsonNull)?.asString,
                        phoneNumber = obj.get("phone_number")?.takeUnless(JsonElement::isJsonNull)?.asString,
                        role = obj.get("role")?.takeUnless(JsonElement::isJsonNull)?.asString
                    )
                }
                else -> null
            }
        }
    val userId: String? get() = user?.id
    val phone: String? get() = userPhone ?: user?.phoneNumber
    val qualification: String? get() = degree ?: educationLevel
    val collegeName: String? get() = college
    val formattedLocation: String
        get() = listOfNotNull(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
    val hasSkills: Boolean get() = skills.isNotEmpty()
    val hasLanguages: Boolean get() = languages.isNotEmpty()
    val hasHobbies: Boolean get() = hobbies.isNotEmpty()
}

data class CourseDto(
    val id: String = "",
    val code: String? = null,
    val name: String = "",
    val category: String? = null,
    val domain: String? = null,
    val subject: String? = null,
    val description: String = "",
    @SerializedName("curriculum_file") val curriculumFile: String? = null,
    val prerequisites: String? = null,
    @SerializedName("duration_weeks") val durationWeeks: Int = 12,
    val difficulty: String? = "INTERMEDIATE",
    @SerializedName("minimum_attendance_percentage") val minimumAttendancePercentage: String? = null,
    @SerializedName("minimum_assignment_percentage") val minimumAssignmentPercentage: String? = null,
    val status: String? = "PUBLISHED",
    @SerializedName("has_open_cohort") val hasOpenCohort: Boolean = false,
    @SerializedName("approved_by") val approvedBy: String? = null,
    @SerializedName("created_by") val createdBy: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) { val title: String get() = name }

data class ApplicationDto(
    val id: String = "",
    @SerializedName("application_number") val applicationNumber: String? = null,
    val student: String? = null,
    val course: String? = null,
    val status: String = "APPLIED",
    @SerializedName("applied_at") val appliedAt: String? = null,
    @SerializedName("assigned_cohort") val assignedCohort: String? = null,
    val qualified: Boolean? = null,
    @SerializedName("qualification_score") val qualificationScore: String? = null,
    @SerializedName("completed_course") val completedCourse: Boolean = false,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("final_score") val finalScore: String? = null,
    val remarks: String? = null,
    @SerializedName("pre_screening") val preScreening: PreScreeningDto? = null,
    // Optional deployment extension. When absent, the app derives verification from status.
    @SerializedName("student_role_verified") val studentRoleVerified: Boolean? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) { val courseId: String? get() = course }

data class ApplicationCreateRequest(
    @SerializedName("application_number") val applicationNumber: String,
    val course: String,
    val status: String = "APPLIED"
)

data class CourseSelectionDto(
    @SerializedName("can_apply") val canApply: Boolean = true,
    val reason: String = "AVAILABLE",
    val message: String = "You can select one published course.",
    @SerializedName("blocking_application") val blockingApplication: ApplicationDto? = null
)

data class DiscontinueCourseResponseDto(
    val message: String = "Course discontinued.",
    val application: ApplicationDto? = null
)

data class PreScreeningDto(
    val id: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val interviewer: String? = null,
    @SerializedName("scheduled_at") val scheduledAt: String? = null,
    @SerializedName("meeting_link") val meetingLink: String? = null,
    val status: String? = null,
    val remarks: String? = null,
    val application: String = ""
)

data class ExamDto(
    val id: String = "",
    val application: String? = null,
    val status: String? = null,
    @SerializedName("submitted_at") val submittedAt: String? = null,
    @SerializedName("marks_obtained") val marksObtained: String? = null,
    @SerializedName("total_marks") val totalMarks: String? = null,
    val percentage: String? = null,
    val qualified: Boolean? = null
)

data class PreScreeningInterviewDto(
    val id: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("scheduled_at") val scheduledAt: String? = null,
    @SerializedName("meeting_link") val meetingLink: String? = null,
    val status: String? = null,
    val feedback: String? = null,
    val score: String? = null,
    val application: String = "",
    val interviewer: String? = null
)

data class TrainingDto(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    @SerializedName("training_type") val trainingType: String? = null,
    @SerializedName("duration_hours") val durationHours: Int? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class TrainingSessionDto(
    val id: String = "",
    val title: String = "",
    @SerializedName("session_date") val sessionDate: String = "",
    @SerializedName("start_time") val startTime: String = "",
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("meeting_link") val meetingLink: String? = null,
    @SerializedName("recording_link") val recordingLink: String? = null,
    val notes: String? = null,
    val training: String = "",
    @SerializedName("conducted_by") val conductedBy: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class TrainingAttendanceDto(
    val id: String = "",
    val status: String? = null,
    val remarks: String? = null,
    val session: String = "",
    val student: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class ModuleTestResultDto(
    val id: String = "",
    val test: String = "",
    @SerializedName("module_id") val moduleId: String? = null,
    @SerializedName("module_number") val moduleNumber: Int = 1,
    val title: String = "Module Test",
    @SerializedName("marks_obtained") val marksObtained: String? = null,
    @SerializedName("total_marks") val maxMarks: String = "100",
    val percentage: String? = null,
    @SerializedName("qualified") val passed: Boolean = false,
    @SerializedName("submitted_at") val completedAt: String? = null
)

data class CohortDto(
    val id: String = "",
    val code: String? = null,
    val name: String = "",
    val course: String? = null,
    val module: String? = null,
    @SerializedName("module_name") val moduleName: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    val mentors: List<String> = emptyList(),
    val volunteers: List<String> = emptyList(),
    @SerializedName("max_students") val maxStudents: Int? = null,
    val status: String? = null,
    @SerializedName("training_started_at") val trainingStartedAt: String? = null,
    @SerializedName("github_repository_eligible_at") val githubRepositoryEligibleAt: String? = null,
    @SerializedName("can_provision_github_repositories") val canProvisionGithubRepositories: Boolean = false,
    @SerializedName("github_repositories_last_provisioned_at") val githubRepositoriesLastProvisionedAt: String? = null,
    @SerializedName("lst_batch") val lstBatch: String? = null,
    @SerializedName("meeting_link") val meetingLink: String? = null,
    @SerializedName("created_by") val createdBy: String? = null,
    @SerializedName("course_name") val courseName: String? = "Sure ProEd Specialization",
    @SerializedName("mentor_name") val mentorName: String? = "Assigned Trust Mentor"
)

data class AttendanceDto(
    val id: String = "",
    val cohort: String? = null,
    @SerializedName("course_name") val courseName: String? = null,
    @SerializedName("cohort_code") val cohortCode: String? = null,
    @SerializedName("title") val sessionTitle: String? = null,
    @SerializedName("class_date") val date: String = "",
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("class_status") val classStatus: String? = null,
    @SerializedName("effective_status") val effectiveStatus: String? = null,
    val conducted: Boolean = false,
    @SerializedName("conducted_by") val conductedBy: String? = null,
    @SerializedName("conducted_by_name") val conductedByName: String? = null,
    val attendees: List<String> = emptyList(),
    @SerializedName("meeting_link") val meetingLink: String? = null,
    @SerializedName("recording_link") val recordingLink: String? = null,
    val notes: String? = null,
    @SerializedName("student_dashboard_data") val studentDashboardData: StudentAttendanceDataDto? = null,
    // Session payloads do not always include a student-specific attendance flag.
    // Defaulting to true made every scheduled class look attended (100%).
    val present: Boolean = false
)

/** Student-scoped attendance result emitted by the backend AttendanceSerializer. */
data class StudentAttendanceDataDto(
    val status: String? = null,
    val course: String? = null,
    val cohort: String? = null,
    @SerializedName("class_date") val classDate: String? = null,
    @SerializedName("active_duration_seconds") val activeDurationSeconds: Long = 0,
    @SerializedName("attendance_percentage") val attendancePercentage: Double? = null,
    @SerializedName("attendance_status") val attendanceStatus: String? = null,
    @SerializedName("warning_state") val warningState: String? = null,
    @SerializedName("meeting_link") val meetingLink: String? = null
)

typealias TimetableSessionDto = AttendanceDto

data class AbsenceWarningDto(
    val id: String = "",
    @SerializedName("session_title") val sessionTitle: String? = null,
    @SerializedName("class_date") val classDate: String? = null,
    val status: String = "PENDING",
    @SerializedName("apology_text") val apologyText: String? = null,
    val resolved: Boolean = false
)

data class PermissionRequestMessageDto(
    @SerializedName("message_id") val messageId: String = "",
    val message: String = "",
    @SerializedName("sender_id") val senderId: String = "",
    @SerializedName("sender_name") val senderName: String = "",
    val timestamp: String = ""
)

data class AssignmentDto(
    val id: String = "",
    val cohort: String? = null,
    val module: String? = null,
    @SerializedName("module_name") val moduleName: String? = null,
    val title: String = "",
    val description: String = "",
    @SerializedName("assignment_type") val assignmentType: String? = null,
    @SerializedName("created_by") val createdBy: String? = null,
    @SerializedName("begin_date") val beginDate: String? = null,
    @SerializedName("deadline") val dueDate: String = "",
    @SerializedName("max_marks") val maxMarks: String = "100.00",
    @SerializedName("pass_percentage") val passPercentage: String? = null,
    val status: String? = null,
    @SerializedName("submission_url") val submittedLink: String? = null,
    val grade: String? = null,
    val score: Int? = null
)

data class AssignmentSubmissionRequest(
    @SerializedName("submission_url") val submissionLink: String,
    val assignment: String? = null,
    @SerializedName("submission_text") val submissionText: String? = null
)

data class SubmissionDto(
    val id: String = "",
    val assignment: String? = null,
    val student: String? = null,
    @SerializedName("student_code") val studentCode: String? = null,
    @SerializedName("student_name") val studentName: String? = null,
    @SerializedName("submission_text") val submissionText: String? = null,
    @SerializedName("submission_url") val submissionUrl: String? = null,
    @SerializedName("commit_sha") val commitSha: String? = null,
    @SerializedName("github_repo_url") val githubRepoUrl: String? = null,
    @SerializedName("github_commit_url") val githubCommitUrl: String? = null,
    @SerializedName("github_tree_url") val githubTreeUrl: String? = null,
    @SerializedName("submitted_at") val submittedAt: String? = null,
    @SerializedName("is_late") val isLate: Boolean = false,
    val evaluated: Boolean = false,
    @SerializedName("evaluated_by") val evaluatedBy: String? = null,
    @SerializedName("evaluated_at") val evaluatedAt: String? = null,
    @SerializedName("marks_obtained") val marksObtained: String? = null,
    val passed: Boolean? = null,
    val feedback: String? = null
)

data class CertificateDto(
    val id: String = "",
    @SerializedName("certificate_number") val certificateNumber: String? = null,
    @SerializedName("verification_code") val verificationCode: String? = null,
    val student: String? = null,
    val application: String? = null,
    @SerializedName("certificate_type") val certificateType: String? = null,
    @SerializedName("issued_at") val issuedAt: String? = null,
    @SerializedName("issued_by") val issuedBy: String? = null,
    @SerializedName("certificate_file") val certificateFile: String? = null,
    val status: String? = null,
    @SerializedName("revoked_at") val revokedAt: String? = null,
    @SerializedName("revocation_reason") val revocationReason: String? = null
)

data class CompanyDto(
    val id: String = "",
    val user: String? = null,
    val name: String = "",
    val description: String? = null,
    val website: String? = null,
    val logo: String? = null,
    val industry: String? = null,
    val location: String? = null,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("shortlisted_students") val shortlistedStudents: List<String> = emptyList()
)

/** Mentor-posted opening distributed only to students in an assigned cohort. */
data class JobReferenceDto(
    val id: String = "",
    val title: String = "",
    val cohort: String? = null,
    val company: String? = null,
    @SerializedName("company_name") val companyName: String? = null,
    val location: String? = null,
    @SerializedName("employment_type") val employmentType: String? = null,
    val description: String? = null,
    @SerializedName("apply_url") val applyUrl: String? = null,
    val deadline: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("created_by") val createdBy: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class NotificationDto(
    val id: String,
    val title: String,
    val message: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("notification_type") val notificationType: String = "INFO",
    @SerializedName("action_url") val actionUrl: String? = null
)

data class StudentStatisticsDto(
    @SerializedName("student_code") val studentCode: String? = null,
    @SerializedName("total_applications") val totalApplications: Int = 0,
    @SerializedName("qualified_applications") val qualifiedApplications: Int = 0,
    @SerializedName("active_cohort") val activeCohort: ActiveCohortStatisticsDto? = null,
    @SerializedName("exams_taken") val examsTaken: Int = 0,
    @SerializedName("module_tests_passed") val moduleTestsPassed: Int = 0,
    @SerializedName("attendance_percentage") val attendancePercentage: Double = 0.0,
    @SerializedName("application_status") val applicationStatus: String? = null,
    @SerializedName("application_number") val applicationNumber: String? = null,
    @SerializedName("application_course_title") val applicationCourseTitle: String? = null,
    @SerializedName("applied_at") val appliedAt: String? = null,
    @SerializedName("screening_status") val screeningStatus: String? = null,
    @SerializedName("screening_scheduled_at") val screeningScheduledAt: String? = null,
    @SerializedName("screening_meeting_link") val screeningMeetingLink: String? = null,
    @SerializedName("screening_marks_obtained") val screeningMarksObtained: String? = null,
    @SerializedName("screening_total_marks") val screeningTotalMarks: String? = null,
    @SerializedName("screening_percentage") val screeningPercentage: String? = null,
    @SerializedName("screening_grade") val screeningGrade: String? = null,
    @SerializedName("screening_qualified") val screeningQualified: Boolean = false,
    @SerializedName("interview_status") val interviewStatus: String? = null,
    @SerializedName("interview_scheduled_at") val interviewScheduledAt: String? = null,
    @SerializedName("interview_meeting_link") val interviewMeetingLink: String? = null,
    @SerializedName("interview_score") val interviewScore: String? = null,
    @SerializedName("student_role_verified") val studentRoleVerified: Boolean = false,
    @SerializedName("certificate_count") val certificateCount: Int = 0,
    @SerializedName("unread_notification_count") val unreadNotificationCount: Int = 0,
    @SerializedName("is_linkedin_connected") val isLinkedinConnected: Boolean = false,
    @SerializedName("github_url") val githubUrl: String? = null,
    @SerializedName("is_github_linked") val isGithubLinked: Boolean = false,
    @SerializedName("portfolio_url") val portfolioUrl: String? = null,
    @SerializedName("is_portfolio_linked") val isPortfolioLinked: Boolean = false,
    @SerializedName("open_request_count") val openRequestCount: Int = 0,
    @SerializedName("upcoming_sessions") val upcomingSessions: List<AttendanceDto> = emptyList(),
    @SerializedName("module_grades") val moduleGrades: List<ModuleTestResultDto> = emptyList(),
    val journey: StudentJourneyDto? = null
)

data class StudentJourneyDto(
    val status: String = "SIGNUP",
    @SerializedName("current_step") val currentStep: Int = 1,
    @SerializedName("completed_steps") val completedSteps: Int = 0,
    @SerializedName("total_steps") val totalSteps: Int = 18,
    @SerializedName("completion_percentage") val completionPercentage: Double = 0.0,
    @SerializedName("linkedin_required") val linkedinRequired: Boolean = false,
    @SerializedName("github_required") val githubRequired: Boolean = false,
    @SerializedName("can_assign_cohort") val canAssignCohort: Boolean = false,
    @SerializedName("requirements_verified") val requirementsVerified: Boolean = false,
    val blockers: List<String> = emptyList(),
    val steps: List<StudentJourneyStepDto> = emptyList()
)

data class StudentJourneyStepDto(
    @SerializedName("step_number") val stepNumber: Int = 0,
    val code: String = "",
    val title: String = "",
    val state: String = "UPCOMING",
    val completed: Boolean = false,
    val subtitle: String = "",
    val date: String? = null,
    val details: String? = null,
    @SerializedName("action_url") val actionUrl: String? = null
)

data class CommunityActivityDto(
    val id: String = "",
    val application: String = "",
    val student: String? = null,
    val cohort: String? = null,
    @SerializedName("activity_type") val activityType: String = "TREE_PLANTATION",
    val title: String = "",
    @SerializedName("activity_date") val activityDate: String = "",
    val description: String? = null,
    @SerializedName("evidence_url") val evidenceUrl: String? = null,
    val status: String = "PENDING",
    @SerializedName("verification_remarks") val verificationRemarks: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class ActiveCohortStatisticsDto(
    val id: String = "",
    val code: String? = null,
    val name: String? = null,
    @SerializedName("course_id") val courseId: String? = null,
    @SerializedName("course_title") val courseTitle: String? = null,
    val status: String? = null,
    @SerializedName("mentor_name") val mentorName: String? = null,
    val mentors: List<String> = emptyList(),
    val modules: List<String> = emptyList()
)

data class AnnouncementDto(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    @SerializedName("target_audience") val targetAudience: String = "ALL",
    val cohort: String? = null,
    val attachment: String? = null,
    @SerializedName("link_url") val linkUrl: String? = null,
    @SerializedName("is_pinned") val isPinned: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("created_at") val createdAt: String? = null
)

data class UserRequestDto(
    val id: String = "",
    @SerializedName("request_number") val requestNumber: String = "",
    @SerializedName("sender_role") val senderRole: String? = null,
    @SerializedName("sender_email") val senderEmail: String? = null,
    val category: String = "OTHER",
    val subject: String = "",
    val description: String = "",
    val attachment: String? = null,
    val status: String = "PENDING",
    @SerializedName("admin_remarks") val adminRemarks: String? = null,
    @SerializedName("resolved_at") val resolvedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class LinkedInAuthUrlResponse(
    @SerializedName("authorization_url") val authorizationUrl: String? = null,
    @SerializedName("auth_url") val authUrl: String? = null,
    val url: String? = null
)
data class LinkedInCallbackRequest(val code: String)
data class LinkedInConnectResponse(
    val detail: String? = null,
    val error: String? = null,
    @SerializedName("is_linkedin_connected") val isLinkedinConnected: Boolean = false,
    @SerializedName("linkedin_url") val linkedinUrl: String? = null,
    @SerializedName("profile_photo") val profilePhoto: String? = null,
    @SerializedName("profile_photo_url") val profilePhotoUrl: String? = null,
    @SerializedName("linkedin_profile_photo_url") val linkedinProfilePhotoUrl: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("picture") val picture: String? = null,
    @SerializedName("profile_picture") val profilePicture: String? = null,
    val access: String? = null,
    val refresh: String? = null,
    val user: UserResponse? = null
)

data class GitHubAuthUrlResponse(
    @SerializedName("authorization_url") val authorizationUrl: String? = null,
    @SerializedName("auth_url") val authUrl: String? = null,
    val url: String? = null
)

data class GitHubCallbackRequest(val code: String)

data class GitHubConnectResponse(
    val detail: String? = null,
    val error: String? = null,
    @SerializedName("is_github_connected") val isGitHubConnected: Boolean = false,
    @SerializedName("github_username") val githubUsername: String? = null,
    @SerializedName("github_url") val githubUrl: String? = null,
    @SerializedName("github_org_invite_status") val githubOrgInviteStatus: String? = null,
    @SerializedName("github_repo_url") val githubRepoUrl: String? = null,
    @SerializedName("initialized_folders") val initializedFolders: List<String> = emptyList(),
    val access: String? = null,
    val refresh: String? = null,
    val user: UserResponse? = null
)

data class MentorAssignedCohortDto(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val course: String = "",
    @SerializedName("meeting_link") val meetingLink: String? = null
)

data class MentorProfileDto(
    val id: String = "",
    val user: String = "",
    @SerializedName("first_name") val firstName: String = "",
    @SerializedName("last_name") val lastName: String = "",
    val email: String = "",
    @SerializedName("full_name") val fullName: String = "",
    val gender: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("company_name") val companyName: String? = null,
    val designation: String? = null,
    val expertise: String? = null,
    @SerializedName("years_of_experience") val yearsOfExperience: Int? = null,
    @SerializedName("profile_photo") val profilePhoto: String? = null,
    val bio: String? = null,
    @SerializedName("linkedin_url") val linkedinUrl: String? = null,
    @SerializedName("is_linkedin_connected") val isLinkedinConnected: Boolean = false,
    @SerializedName("github_username") val githubUsername: String? = null,
    @SerializedName("github_url") val githubUrl: String? = null,
    @SerializedName("is_github_connected") val isGithubConnected: Boolean = false,
    @SerializedName("assigned_cohorts") val assignedCohorts: List<MentorAssignedCohortDto> = emptyList()
)

data class FeedbackDto(
    val id: String = "",
    val user: String = "",
    @SerializedName("feedback_type") val feedbackType: String = "SYSTEM",
    @SerializedName("related_id") val relatedId: String? = null,
    val rating: Int = 5,
    val comments: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class FAQDto(
    val id: String = "",
    val question: String = "",
    val answer: String = "",
    val category: String = "GENERAL",
    val order: Int = 0
)

data class JobPostingDto(
    val id: String = "",
    val title: String = "",
    val company: String = "",
    val location: String? = null,
    @SerializedName("job_type") val jobType: String = "FULL_TIME",
    val description: String? = null,
    val requirements: String? = null,
    @SerializedName("salary_range") val salaryRange: String? = null,
    @SerializedName("apply_url") val applyUrl: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("created_at") val createdAt: String? = null
)

data class VolunteerTaskDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val cohort: String? = null,
    @SerializedName("cohort_name") val cohortName: String? = null,
    val status: String = "PENDING",
    val priority: String = "MEDIUM",
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("assigned_to") val assignedTo: String? = null,
    @SerializedName("assigned_to_email") val assignedToEmail: String? = null,
    @SerializedName("assigned_by") val assignedBy: String? = null,
    @SerializedName("assigned_by_email") val assignedByEmail: String? = null,
    @SerializedName("completion_notes") val completionNotes: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class VolunteerAssignedCohortDto(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val course: String = "",
    @SerializedName("meeting_link") val meetingLink: String? = null
)

data class VolunteerProfileDto(
    val id: String = "",
    val user: String = "",
    @SerializedName("first_name") val firstName: String = "",
    @SerializedName("last_name") val lastName: String = "",
    val email: String = "",
    @SerializedName("full_name") val fullName: String = "",
    @SerializedName("organization_name") val organizationName: String? = null,
    val occupation: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("profile_photo") val profilePhoto: String? = null,
    val skills: String? = null,
    @SerializedName("availability_notes") val availabilityNotes: String? = null,
    val bio: String? = null,
    @SerializedName("linkedin_id") val linkedinId: String? = null,
    @SerializedName("linkedin_url") val linkedinUrl: String? = null,
    @SerializedName("assigned_cohorts") val assignedCohorts: List<VolunteerAssignedCohortDto> = emptyList(),
    @SerializedName("upcoming_classes") val upcomingClasses: List<AttendanceDto> = emptyList(),
    // Optional deployment extension; the production serializer may omit this value.
    @SerializedName("hours_contributed") val hoursContributed: Int? = null,
    // Hydrated from the profile's authenticated user record when it is not embedded.
    @Transient val phoneNumber: String? = null
)

data class CohortChatMessageDto(
    val id: String = "",
    @SerializedName("sender_id") val senderId: String? = null,
    @SerializedName("sender_name") val senderName: String = "",
    @SerializedName("sender_role") val senderRole: String? = null,
    val body: String = "",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("is_deleted") val isDeleted: Boolean = false
)

data class CohortChatMessagesResponse(
    @SerializedName("cohort_id") val cohortId: String = "",
    @SerializedName("conversation_id") val conversationId: String = "",
    val count: Int = 0,
    @SerializedName("has_more") val hasMore: Boolean = false,
    val results: List<CohortChatMessageDto> = emptyList()
)

data class CohortChatUnreadCountResponse(
    @SerializedName("cohort_id") val cohortId: String = "",
    @SerializedName("unread_count") val unreadCount: Int = 0
)

data class AppVersionInfoDto(
    @SerializedName("version_code") val versionCode: Int = 1,
    @SerializedName("version_name") val versionName: String = "1.0.0",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("release_notes") val releaseNotes: String = "",
    @SerializedName("is_mandatory") val isMandatory: Boolean = false,
    @SerializedName("min_supported_version_code") val minSupportedVersionCode: Int = 1,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long = 0L,
    @SerializedName("published_at") val publishedAt: String = ""
)
