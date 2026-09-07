package com.zenlock.focusguard.data.network

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    val user: UserDto,
    val token: String,
    val isNewUser: Boolean
)

data class UserDto(
    @SerializedName("_id") val id: String,
    val email: String,
    val username: String?,
    val displayName: String?,
    val dob: String?,
    val gender: String?,
    val country: String?,
    val state: String?,
    val city: String?,
    val isProfileComplete: Boolean
)

data class GoogleAuthRequest(
    val idToken: String
)

data class SignupRequest(
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class ProfileSetupRequest(
    val username: String,
    val dob: String,
    val gender: String,
    val country: String,
    val state: String,
    val city: String
)

data class ProfileResponse(
    val user: UserDto
)
