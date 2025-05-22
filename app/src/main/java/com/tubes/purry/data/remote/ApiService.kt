package com.tubes.purry.data.remote

import com.tubes.purry.data.model.LoginRequest
import com.tubes.purry.data.model.LoginResponse
import com.tubes.purry.data.model.ProfileData
import com.tubes.purry.data.model.ProfileResponse
import com.tubes.purry.data.model.RefreshTokenRequest
import com.tubes.purry.data.model.RefreshTokenResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Part

interface ApiService {
    @POST("api/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>

    @GET("api/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ProfileResponse>

    @POST("api/refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @GET("api/verify-token")
    suspend fun verifyToken(@Header("Authorization") token: String): Response<Void>

    @Multipart
    @PATCH("api/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Part("location") location: RequestBody?,
        @Part profilePhoto: MultipartBody.Part?
    ): Response<ProfileData>
}