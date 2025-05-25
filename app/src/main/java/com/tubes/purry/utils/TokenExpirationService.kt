package com.tubes.purry.utils

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.tubes.purry.data.model.RefreshTokenRequest
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.ui.auth.LoginActivity
import com.tubes.purry.ui.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class TokenExpirationService : Service() {

    private val timer = Timer()
    private val verificationInterval = 30 * 60 * 1000L // Check every 1 minute
    private lateinit var sessionManager: SessionManager

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, TokenExpirationService::class.java)
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (NetworkUtil.isNetworkAvailable(applicationContext)) {
                    checkTokenExpiration()
                }
            }
        }, 0, verificationInterval)

        return START_STICKY
    }

    private fun checkTokenExpiration() {
        Log.d("TokenCheck", "Verifying token...")
        val token = sessionManager.fetchAuthToken() ?: return
        if (token.isNullOrBlank()) {
            Log.d("TokenCheck", "Access token is null or blank → Logging out")
            logoutAndRedirect()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.apiService.verifyToken("Bearer $token")
                if (!response.isSuccessful && response.code() == 403) {
                    // Token expired, try to refresh
                    tryRefreshToken()
                }
            } catch (e: Exception) {
                // Network or other error, handle appropriately
            }
        }
    }

    private fun logoutAndRedirect() {
        sessionManager.clearTokens()
        PlayerController.release()

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_LOGOUT", true)
        }
        startActivity(intent)
    }

    private fun tryRefreshToken() {
        Log.d("TokenCheck", "Verifying token...")
        val refreshToken = sessionManager.fetchRefreshToken() ?: run {
            logoutUser()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = RefreshTokenRequest(refreshToken)
                val response = ApiClient.apiService.refreshToken(request)

                if (response.isSuccessful && response.body() != null) {
                    val newToken = response.body()?.token
                    val newRefreshToken = response.body()?.refreshToken

                    if (newToken != null) {
                        sessionManager.saveAuthToken(newToken)
                    }

                    if (newRefreshToken != null) {
                        sessionManager.saveRefreshToken(newRefreshToken)
                    }
                } else {
                    Log.e("TokenCheck", "Refresh gagal. response code: ${response.code()} body: ${response.errorBody()?.string()}")
                    logoutUser()
                }
            } catch (e: Exception) {
                logoutUser()
            }
        }
    }

    private fun logoutUser() {
        // Clear tokens
        sessionManager.clearTokens()
        PlayerController.release()

        // Redirect to login screen
        Handler(Looper.getMainLooper()).post {
            val intent = Intent(applicationContext, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            // Show a toast
            Toast.makeText(
                applicationContext,
                "Session expired. Please login again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
    }
}