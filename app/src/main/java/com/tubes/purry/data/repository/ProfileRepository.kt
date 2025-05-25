package com.tubes.purry.data.repository

import android.net.Uri
import android.util.Log
import com.tubes.purry.data.model.ProfileData
import com.tubes.purry.data.model.ProfileResponse
import com.tubes.purry.data.remote.ApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.File

class ProfileRepository(
    private val apiService: ApiService,
    private val authRepository: AuthRepository // For refreshing token
) {

    suspend fun getProfile(): Response<ProfileResponse> {
        // Get a valid auth token before making the API call
        val token = authRepository.getValidAuthToken() ?: return Response.error(401,
            "Token expired".toResponseBody(null)
        )

        Log.d("ProfileRepository", "Making profile API request")
        Log.d("ProfileRepository", "Using token: $token")

        // Make the request using the valid token
        return apiService.getProfile("Bearer $token")
    }

    suspend fun updateProfile(token: String, location: String, profilePhotoUri: Uri?): Response<ProfileData> {
        val locationPart = location.toRequestBody("text/plain".toMediaTypeOrNull())

        var profilePhotoPart: MultipartBody.Part? = null
        if (profilePhotoUri != null) {
            val file = File(profilePhotoUri.path)
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            profilePhotoPart = MultipartBody.Part.createFormData("profilePhoto", file.name, requestFile)
        }

        return apiService.updateProfile(token, locationPart, profilePhotoPart)
    }
}