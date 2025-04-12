package com.tubes.purry.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tubes.purry.MainActivity
import com.tubes.purry.data.local.AppDatabase
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
        authRepository = AuthRepository(ApiClient.apiService, sessionManager)

        // Check if user is already logged in
        if (sessionManager.fetchAuthToken() != null) {
            navigateToMainActivity()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 8) {
                Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

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
                        val accessToken = response.body()?.accessToken
                        val refreshToken = response.body()?.refreshToken

                        accessToken?.let {
                            sessionManager.saveAuthToken(it)
                            Log.d("LoginActivity", "AccessToken saved: ${it.take(10)}...")
                        }

                        refreshToken?.let {
                            sessionManager.saveRefreshToken(it)
                            Log.d("LoginActivity", "Refresh token saved")
                        }

                        // Verify token was saved correctly
                        val savedToken = sessionManager.fetchAuthToken()
                        Log.d("LoginActivity", "Token verification after save: ${savedToken != null}")

                        // Fetch profile using saved token
                        accessToken?.let { token ->
                            Log.d("LoginActivity", "Attempting to fetch profile using token: Bearer $token")
                            val profileResponse = ApiClient.apiService.getProfile("Bearer $token")
                            if (profileResponse.isSuccessful && profileResponse.body() != null) {
                                val profileData = profileResponse.body()!!
                                Log.d("LoginActivity", "$profileData")

                                // Insert into Room DB
                                val db = AppDatabase.getDatabase(applicationContext)
                                db.userProfileDao().insertOrUpdate(profileData)
                                Log.d("LoginActivity", "User profile inserted into database")
                            } else {
                                Log.e("LoginActivity", "Failed to fetch profile: ${profileResponse.message()}")
                            }
                        }

                        withContext(Dispatchers.Main) {
                            navigateToMainActivity()
                        }
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