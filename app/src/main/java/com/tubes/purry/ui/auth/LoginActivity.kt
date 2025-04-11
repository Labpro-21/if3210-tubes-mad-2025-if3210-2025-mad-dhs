package com.tubes.purry.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tubes.purry.MainActivity
import com.tubes.purry.data.model.LoginRequest
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.data.repository.AuthRepository
import com.tubes.purry.databinding.ActivityLoginBinding
import com.tubes.purry.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        authRepository = AuthRepository(ApiClient.apiService)

        val token = sessionManager.fetchAuthToken()
        if (!token.isNullOrEmpty()) {
            // cek validitas token
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = authRepository.verifyToken("Bearer $token")
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) { // valid
                            navigateToMainActivity()
                        } else {
                            sessionManager.clearTokens()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        // Jaga-jaga kalau ada error jaringan
                        Toast.makeText(this@LoginActivity, "Error verifying token", Toast.LENGTH_SHORT).show()
                        sessionManager.clearTokens()
                    }
                }
            }
        }

//        if (intent.getBooleanExtra("EXTRA_LOGOUT", false)) {
//            sessionManager.clearTokens()
//            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
//        }
//
//        // Check if user is already logged in
//        if (sessionManager.fetchAuthToken() != null) {
//            navigateToMainActivity()
//        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                login(email, password)
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun login(email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = authRepository.login(LoginRequest(email, password))
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val loginResponse = response.body()!!
                        response.body()?.accessToken?.let {
                            sessionManager.saveAuthToken(it)
                            Log.d("LoginActivity", "AccessToken saved: ${it.take(10)}...")
                        }

                        response.body()?.refreshToken?.let {
                            sessionManager.saveRefreshToken(it)
                            Log.d("LoginActivity", "Refresh token saved")
                        }

                        // Verify token was saved correctly
                        val savedToken = sessionManager.fetchAuthToken()
                        Log.d("LoginActivity", "Token verification after save: ${savedToken != null}")

                        sessionManager.saveUserId(loginResponse.id)
                        Log.d("LoginActivity", "Access token saved. User ID: ${loginResponse.id}")

                        navigateToMainActivity()
                    } else {
                        Toast.makeText(this@LoginActivity, "Login failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}