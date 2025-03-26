package com.tubes.purry.data.repository

import com.tubes.purry.data.model.ProfileResponse
import com.tubes.purry.data.remote.ApiService
import retrofit2.Response

class ProfileRepository(private val apiService: ApiService) {

    suspend fun getProfile(token: String): Response<ProfileResponse> {
        return apiService.getProfile(token)
    }
}