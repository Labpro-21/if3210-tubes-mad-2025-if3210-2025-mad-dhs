package com.tubes.purry.data.repository

import com.tubes.purry.data.model.LoginRequest
import com.tubes.purry.data.model.LoginResponse
import com.tubes.purry.data.model.RefreshTokenRequest
import com.tubes.purry.data.model.RefreshTokenResponse
import com.tubes.purry.data.remote.ApiService
import retrofit2.Response

class AuthRepository(private val apiService: ApiService) {

    suspend fun login(loginRequest: LoginRequest): Response<LoginResponse> {
        return apiService.login(loginRequest)
    }

//    suspend fun refreshToken(refreshTokenRequest: RefreshTokenRequest): Response<RefreshTokenResponse> {
//        return apiService.refreshToken(refreshTokenRequest)
//    }

    suspend fun verifyToken(token: String): Response<Void> {
        return apiService.verifyToken(token)
    }
}