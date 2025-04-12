package com.tubes.purry.data.repository

import android.util.Log
import com.tubes.purry.data.model.ProfileResponse
import com.tubes.purry.data.remote.ApiService
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

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
}
