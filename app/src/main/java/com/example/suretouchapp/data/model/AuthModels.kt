package com.example.suretouchapp.data.model

import com.google.gson.annotations.SerializedName

// Registration Request (POST /api/users/)
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    val gender: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    val role: String = "STUDENT"
)

// User Response (POST /api/users/ success response)
data class UserResponse(
    val id: String? = null,
    val email: String,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    val gender: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("profile_photo") val profilePhoto: String? = null,
    @SerializedName("profile_photo_url") val profilePhotoUrl: String? = null,
    @SerializedName("linkedin_profile_photo_url") val linkedinProfilePhotoUrl: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("picture") val picture: String? = null,
    @SerializedName("profile_picture") val profilePicture: String? = null,
    val role: String? = "STUDENT",
    @SerializedName("is_active") val isActive: Boolean? = true,
    @SerializedName("is_staff") val isStaff: Boolean? = false,
    @SerializedName("is_email_verified") val isEmailVerified: Boolean? = false,
    @SerializedName("date_joined") val dateJoined: String? = null,
    @SerializedName("last_login") val lastLogin: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    val effectiveProfilePhoto: String?
        get() = profilePhoto?.takeIf(String::isNotBlank)
            ?: profilePhotoUrl?.takeIf(String::isNotBlank)
            ?: linkedinProfilePhotoUrl?.takeIf(String::isNotBlank)
            ?: photoUrl?.takeIf(String::isNotBlank)
            ?: avatar?.takeIf(String::isNotBlank)
            ?: picture?.takeIf(String::isNotBlank)
            ?: profilePicture?.takeIf(String::isNotBlank)
}

// Legacy alias for UserResponse
typealias UserDto = UserResponse

// Login Request (POST /api/auth/token/)
data class LoginRequest(
    val email: String,
    val password: String
)

// Legacy alias for LoginRequest
typealias TokenObtainRequest = LoginRequest

// Token Response (POST /api/auth/token/ and /api/auth/token/refresh/)
data class TokenResponse(
    val access: String,
    val refresh: String? = null
)

// Refresh Token Request (POST /api/auth/token/refresh/)
data class RefreshTokenRequest(
    val refresh: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ForgotPasswordConfirmRequest(
    val email: String,
    val otp: String,
    @SerializedName("new_password") val newPassword: String
)

data class PasswordResetResponse(
    val detail: String? = null,
    val error: String? = null
)

data class SendEmailVerificationOtpRequest(
    val email: String,
    val password: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    val gender: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    val role: String = "STUDENT"
)

data class VerifyEmailOtpRequest(
    val email: String,
    val otp: String
)

data class EmailVerificationOtpResponse(
    val detail: String? = null,
    @SerializedName("is_email_verified") val isEmailVerified: Boolean = false,
    val access: String? = null,
    val refresh: String? = null,
    val user: UserResponse? = null,
    val error: String? = null
)


