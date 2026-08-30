package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.ApiBody
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.StudentProfileDto
import com.example.suretouchapp.data.model.UserResponse
import okhttp3.MultipartBody
import retrofit2.Response

/**
 * Handles fetching, updating, and synchronizing authenticated student profile data.
 * Adheres strictly to the user's authentic data and avoids cross-user data contamination.
 */
class StudentProfileRepository(private val tokenManager: TokenManager) {
    private val service get() = ApiClient.getService(tokenManager)

    suspend fun load(): StudentProfileDto? {
        val email = tokenManager.getUserEmail().trim().lowercase()

        // 1. Fetch student profile directly via "me" endpoint
        val response = runCatching { service.getStudentProfileById("me") }.getOrNull()
        val matchedProfile = if (response?.isSuccessful == true) response.body() else null
        val meUser = matchedProfile?.user

        if (meUser != null) {
            val fullName = listOfNotNull<String>(meUser.firstName, meUser.lastName).joinToString(" ").trim()
            if (fullName.isNotBlank()) {
                tokenManager.saveUserInfo(fullName, meUser.email.ifBlank { email })
            }
            val mePhone = meUser.phoneNumber
            if (!mePhone.isNullOrBlank()) tokenManager.savePhone(mePhone)
            val meGender = meUser.gender
            if (!meGender.isNullOrBlank()) tokenManager.saveGender(meGender)
            val meDob = meUser.dateOfBirth
            if (!meDob.isNullOrBlank()) tokenManager.saveDob(meDob)
        }

        if (matchedProfile != null) {
            matchedProfile.studentCode?.takeIf { it.isNotBlank() }?.let { tokenManager.saveStudentCode(it) }
            matchedProfile.cohortCode?.takeIf { it.isNotBlank() }?.let { tokenManager.saveCohortCode(it) }
            val photo = matchedProfile.effectiveProfilePhoto
                ?: meUser?.effectiveProfilePhoto
                ?: tokenManager.getProfilePhotoUrl()
            if (!photo.isNullOrBlank()) {
                tokenManager.saveProfilePhotoUrl(photo)
            }
            // Persist server resume URL if present, or clear stale previous account resume
            val serverResumeUrl = matchedProfile.resumeUrl?.takeIf(String::isNotBlank)
                ?: matchedProfile.resume?.takeIf(String::isNotBlank)
            if (serverResumeUrl != null) {
                val existingName = tokenManager.getResumeName().ifBlank { "Student_Resume_CV.pdf" }
                tokenManager.saveResumeDetails(serverResumeUrl, existingName)
            } else {
                tokenManager.clearResumeDetails()
            }

            tokenManager.saveStudentProfileDetails(
                phone = matchedProfile.phone ?: meUser?.phoneNumber ?: tokenManager.getPhone(),
                qualification = matchedProfile.degree ?: matchedProfile.educationLevel ?: tokenManager.getQualification(),
                collegeName = matchedProfile.college ?: tokenManager.getCollegeName(),
                bio = matchedProfile.bio ?: tokenManager.getBio(),
                githubUrl = matchedProfile.githubUrl ?: tokenManager.getGithubUrl(),
                linkedinUrl = matchedProfile.linkedinUrl ?: tokenManager.getLinkedinUrl(),
                tagline = matchedProfile.tagline ?: tokenManager.getTagline(),
                specialization = matchedProfile.specialization ?: tokenManager.getSpecialization(),
                graduationYear = matchedProfile.graduationYear ?: tokenManager.getGraduationYear(),
                city = matchedProfile.city ?: tokenManager.getCity(),
                state = matchedProfile.state ?: tokenManager.getState(),
                country = matchedProfile.country ?: tokenManager.getCountry(),
                skills = if (matchedProfile.skills.isNotEmpty()) matchedProfile.skills else tokenManager.getSkills(),
                hobbies = if (matchedProfile.hobbies.isNotEmpty()) matchedProfile.hobbies else tokenManager.getHobbies(),
                languages = if (matchedProfile.languages.isNotEmpty()) matchedProfile.languages else tokenManager.getLanguages(),
                portfolioUrl = matchedProfile.portfolioUrl ?: tokenManager.getPortfolioUrl(),
                gender = matchedProfile.gender ?: meUser?.gender ?: tokenManager.getGender(),
                dob = matchedProfile.dateOfBirth ?: meUser?.dateOfBirth ?: tokenManager.getDob(),
                permanentAddress = matchedProfile.permanentAddress ?: tokenManager.getPermanentAddress(),
                fatherName = matchedProfile.fatherName ?: tokenManager.getFatherName(),
                motherName = matchedProfile.motherName ?: tokenManager.getMotherName()
            )
            return matchedProfile
        }

        // 3. Fallback: Return DTO populated with authenticated student's local/cached data
        return StudentProfileDto(
            id = "",
            studentCode = tokenManager.getStudentCode().takeIf(String::isNotBlank),
            tagline = tokenManager.getTagline().takeIf(String::isNotBlank),
            degree = tokenManager.getQualification().takeIf(String::isNotBlank),
            college = tokenManager.getCollegeName().takeIf(String::isNotBlank),
            specialization = tokenManager.getSpecialization().takeIf(String::isNotBlank),
            graduationYear = tokenManager.getGraduationYear(),
            city = tokenManager.getCity().takeIf(String::isNotBlank),
            state = tokenManager.getState().takeIf(String::isNotBlank),
            country = tokenManager.getCountry().takeIf(String::isNotBlank),
            gender = tokenManager.getGender().takeIf(String::isNotBlank),
            dateOfBirth = tokenManager.getDob().takeIf(String::isNotBlank),
            permanentAddress = tokenManager.getPermanentAddress().takeIf(String::isNotBlank),
            fatherName = tokenManager.getFatherName().takeIf(String::isNotBlank),
            motherName = tokenManager.getMotherName().takeIf(String::isNotBlank),
            skills = tokenManager.getSkills(),
            hobbies = tokenManager.getHobbies(),
            languages = tokenManager.getLanguages(),
            bio = tokenManager.getBio().takeIf(String::isNotBlank),
            githubUrl = tokenManager.getGithubUrl().takeIf(String::isNotBlank),
            linkedinUrl = tokenManager.getLinkedinUrl().takeIf(String::isNotBlank),
            portfolioUrl = tokenManager.getPortfolioUrl().takeIf(String::isNotBlank),
            cohortCode = tokenManager.getCohortCode().takeIf(String::isNotBlank)
        )
    }

    suspend fun update(profileId: String, body: ApiBody): Response<StudentProfileDto> =
        service.updateStudentProfile(profileId, body)

    suspend fun patchUser(userId: String, body: ApiBody): Response<UserResponse> =
        service.patchUser(userId, body)

    suspend fun uploadPhoto(profileId: String, photo: MultipartBody.Part): Response<StudentProfileDto> =
        service.uploadStudentProfilePhoto(profileId, photo)

    suspend fun uploadResume(profileId: String, resume: MultipartBody.Part): Response<StudentProfileDto> =
        service.uploadStudentResume(profileId, resume)
}
