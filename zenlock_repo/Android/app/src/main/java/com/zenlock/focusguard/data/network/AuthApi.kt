package com.zenlock.focusguard.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("auth/google")
    suspend fun googleAuth(@Body request: GoogleAuthRequest): Response<AuthResponse>

    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/profile-setup")
    suspend fun setupProfile(
        @Header("Authorization") token: String,
        @Body request: ProfileSetupRequest
    ): Response<ProfileResponse>

    @GET("auth/profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<ProfileResponse>

    @PUT("auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: ProfileSetupRequest
    ): Response<ProfileResponse>

    @DELETE("auth/account")
    suspend fun deleteAccount(
        @Header("Authorization") token: String
    ): Response<Unit>
}
