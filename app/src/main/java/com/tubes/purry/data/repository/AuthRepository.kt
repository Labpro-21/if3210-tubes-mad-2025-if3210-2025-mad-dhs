package com.tubes.purry.data.repository

import android.util.Log
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import com.tubes.purry.data.model.LoginRequest
import com.tubes.purry.data.model.LoginResponse
import com.tubes.purry.data.model.RefreshTokenRequest
import com.tubes.purry.data.model.RefreshTokenResponse
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
                val newAccessToken = refreshResponse.body()!!.token
                val newRefreshToken = refreshResponse.body()!!.refreshToken

                sessionManager.saveAuthToken(newAccessToken)
                sessionManager.saveRefreshToken(newRefreshToken)

                val userIdFromToken = sessionManager.getUserIdFromToken()
                if (userIdFromToken != null) {
                    sessionManager.saveUserId(userIdFromToken)
                    Log.d("AuthRepository", "User ID from token saved: $userIdFromToken")
                } else {
                    Log.e("AuthRepository", "Failed to extract user ID from new token")
                    return null
                }

                token = newAccessToken
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

    suspend fun refreshToken(refreshTokenRequest: RefreshTokenRequest): Response<RefreshTokenResponse> {
        return apiService.refreshToken(refreshTokenRequest)
    }

    suspend fun verifyToken(token: String): Response<Void> {
        return apiService.verifyToken(token)
    }
}