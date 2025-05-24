package com.tubes.purry.data.remote

import com.tubes.purry.data.model.LoginRequest
import com.tubes.purry.data.model.LoginResponse
import com.tubes.purry.data.model.OnlineSong
import com.tubes.purry.data.model.ProfileData
import com.tubes.purry.data.model.ProfileResponse
import com.tubes.purry.data.model.RefreshTokenRequest
import com.tubes.purry.data.model.RefreshTokenResponse
import com.tubes.purry.data.model.Song
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

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
    @PUT("api/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Part("location") location: RequestBody,
        @Part profilePhoto: MultipartBody.Part?
    ): Response<ProfileData>

    @GET("api/top-songs/global")
    suspend fun getTopSongsGlobal(): List<OnlineSong>

    @GET("api/top-songs/{country}")
    suspend fun getTopSongsByCountry(@Path("country") code: String): List<OnlineSong>

    @GET("api/songs/{id}")
    suspend fun getSongById(@Path("id") id: Int): Response<Song>
}