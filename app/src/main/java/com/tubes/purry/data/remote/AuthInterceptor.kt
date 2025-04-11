package com.tubes.purry.data.remote

import android.content.Context
import android.content.Intent
import android.util.Log
import com.tubes.purry.ui.auth.LoginActivity
import com.tubes.purry.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val context: Context
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 403) {
            Log.e("AuthInterceptor", "Token expired - forcing logout")
            val sessionManager = SessionManager(context)
            sessionManager.clearTokens()

            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }

        return response
    }
}
