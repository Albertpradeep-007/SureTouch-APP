package com.example.suretouchapp.data.repository

import com.example.suretouchapp.data.api.ApiBody
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.example.suretouchapp.data.model.UserResponse
import com.example.suretouchapp.data.model.VolunteerProfileDto
import java.io.IOException
import okhttp3.MultipartBody
import retrofit2.Response

/** Keeps volunteer profile reads and writes bound to the authenticated backend user. */
class VolunteerRepository(private val tokenManager: TokenManager) {
    private val service get() = ApiClient.getService(tokenManager)

    suspend fun loadProfile(): VolunteerProfileDto {
        val response = service.getVolunteerProfiles()
        if (!response.isSuccessful) {
            throw IOException("Volunteer profile request failed (${response.code()})")
        }

        val profiles = response.body()?.results.orEmpty()
        val email = tokenManager.getUserEmail().trim()
        val cachedName = tokenManager.getUserName().trim()
        val profile = profiles.firstOrNull { it.email.equals(email, ignoreCase = true) }
            ?: profiles.singleOrNull()
            ?: profiles.firstOrNull { it.fullName.equals(cachedName, ignoreCase = true) }
            ?: throw IOException("No volunteer profile is linked to this account")

        val user = loadUser(profile.user)
        val serverName = profile.fullName.ifBlank {
            listOf(profile.firstName, profile.lastName).filter(String::isNotBlank).joinToString(" ")
        }.ifBlank {
            listOfNotNull(user?.firstName, user?.lastName).filter(String::isNotBlank).joinToString(" ")
        }.ifBlank { cachedName }
        val serverEmail = profile.email.ifBlank { user?.email.orEmpty() }.ifBlank { email }
        val serverPhoto = profile.profilePhoto?.takeIf(String::isNotBlank)
            ?: user?.effectiveProfilePhoto
            ?: tokenManager.getProfilePhotoUrl()

        if (serverName.isNotBlank() || serverEmail.isNotBlank()) {
            tokenManager.saveUserInfo(serverName, serverEmail)
        }
        if (!serverPhoto.isNullOrBlank()) {
            tokenManager.saveProfilePhotoUrl(ApiClient.resolveServerUrl(serverPhoto))
        }
        tokenManager.saveStudentProfileDetails(
            phone = user?.phoneNumber ?: tokenManager.getPhone(),
            qualification = profile.skills ?: tokenManager.getQualification(),
            collegeName = profile.organizationName ?: tokenManager.getCollegeName(),
            bio = profile.bio ?: tokenManager.getBio(),
            linkedinUrl = profile.linkedinUrl ?: tokenManager.getLinkedinUrl(),
            tagline = profile.occupation ?: tokenManager.getTagline()
        )

        return profile.copy(
            fullName = serverName,
            email = serverEmail,
            profilePhoto = serverPhoto?.let(ApiClient::resolveServerUrl),
            phoneNumber = user?.phoneNumber ?: tokenManager.getPhone()
        )
    }

    suspend fun updateProfile(
        profile: VolunteerProfileDto,
        body: ApiBody,
        phoneNumber: String
    ): VolunteerProfileDto {
        if (profile.id.isBlank()) throw IOException("Volunteer profile ID is unavailable")

        val profileResponse = service.updateVolunteerProfile(profile.id, body)
        if (!profileResponse.isSuccessful || profileResponse.body() == null) {
            throw IOException("Volunteer profile update failed (${profileResponse.code()})")
        }

        if (profile.user.isNotBlank() && phoneNumber != profile.phoneNumber.orEmpty()) {
            val userResponse = service.patchUser(
                profile.user,
                mapOf("phone_number" to phoneNumber.trim())
            )
            if (!userResponse.isSuccessful) {
                throw IOException("Contact number update failed (${userResponse.code()})")
            }
        }

        return loadProfile()
    }

    suspend fun uploadProfilePhoto(profileId: String, photo: MultipartBody.Part): Response<VolunteerProfileDto> =
        service.uploadVolunteerProfilePhoto(profileId, photo)

    private suspend fun loadUser(userId: String): UserResponse? {
        if (userId.isBlank()) return null
        val response = service.getUserById(userId)
        return response.takeIf { it.isSuccessful }?.body()
    }
}
