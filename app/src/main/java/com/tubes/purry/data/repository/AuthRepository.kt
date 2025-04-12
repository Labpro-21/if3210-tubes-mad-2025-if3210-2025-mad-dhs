package com.tubes.purry.data.repository

import android.util.Log
import com.tubes.purry.data.model.LoginRequest
import com.tubes.purry.data.model.LoginResponse
import com.tubes.purry.data.model.RefreshTokenRequest
import com.tubes.purry.data.remote.ApiService
import com.tubes.purry.utils.SessionManager
import com.tubes.purry.utils.TokenUtils
import retrofit2.Response

class AuthRepository(
    private val apiService: ApiService,
    private val sessionManager: SessionManager
){
    suspend fun getValidAuthToken(): String? {
        var token = sessionManager.fetchAuthToken()
        if (token == null || TokenUtils.isTokenExpiringSoon(token)) {
            // Token is either missing or expiring soon, so refresh it
            val refreshToken = sessionManager.fetchRefreshToken() ?: return null

            val refreshResponse = apiService.refreshToken(RefreshTokenRequest(refreshToken))

            if (refreshResponse.isSuccessful && refreshResponse.body() != null) {
                // Save the new tokens
                sessionManager.saveAuthToken(refreshResponse.body()!!.token)
                sessionManager.saveRefreshToken(refreshResponse.body()!!.refreshToken)
                token = refreshResponse.body()!!.token // Return the new token
            } else {
                Log.e("AuthRepository", "Token refresh failed.")
                return null
            }
        }
        return token
    }

    suspend fun login(loginRequest: LoginRequest): Response<LoginResponse> {
        return apiService.login(loginRequest)
    }
}
