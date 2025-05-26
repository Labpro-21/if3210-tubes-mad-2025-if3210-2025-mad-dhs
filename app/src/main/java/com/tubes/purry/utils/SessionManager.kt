package com.tubes.purry.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import org.json.JSONObject
import android.util.Base64


class SessionManager(context: Context) {
    private var prefs: SharedPreferences

    companion object {
        const val USER_TOKEN = "user_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val USER_ID = "user_id"
        const val PREF_NAME = "purrytify_encrypted_prefs"
    }

    init {
        try {
            // Create or get the master key for encryption
            val keySpec = KeyGenParameterSpec.Builder(
                MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
                .build()

            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyGenParameterSpec(keySpec)
                .build()

            // Create the EncryptedSharedPreferences
            prefs = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            Log.d("SessionManager", "Successfully initialized EncryptedSharedPreferences")
        } catch (e: Exception) {
            Log.e("SessionManager", "Error creating EncryptedSharedPreferences: ${e.message}")
            // Fallback to regular SharedPreferences if encryption fails
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            Log.d("SessionManager", "Falling back to regular SharedPreferences")
        }
    }

    fun saveAuthToken(token: String) {
        try {
            val editor = prefs.edit()
            editor.putString(USER_TOKEN, token)
            val result = editor.commit() // Using commit() for immediate write
            Log.d("SessionManager", "Auth token saved result: $result")
        } catch (e: Exception) {
            Log.e("SessionManager", "Error saving auth token: ${e.message}")
        }
    }

    fun fetchAuthToken(): String? {
        try {
            val token = prefs.getString(USER_TOKEN, null)
            Log.d("SessionManager", "Auth token fetch result: ${token != null}")
            return token
        } catch (e: Exception) {
            Log.e("SessionManager", "Error fetching auth token: ${e.message}")
            return null
        }
    }

    fun saveRefreshToken(token: String) {
        try {
            val editor = prefs.edit()
            editor.putString(REFRESH_TOKEN, token)
            val result = editor.commit() // Using commit() for immediate write
            Log.d("SessionManager", "Refresh token saved result: $result")
        } catch (e: Exception) {
            Log.e("SessionManager", "Error saving refresh token: ${e.message}")
        }
    }

    fun fetchRefreshToken(): String? {
        try {
            val token = prefs.getString(REFRESH_TOKEN, null)
            Log.d("SessionManager", "Refresh token fetch result: ${token != null}")
            return token
        } catch (e: Exception) {
            Log.e("SessionManager", "Error fetching refresh token: ${e.message}")
            return null
        }
    }

    fun clearTokens() {
        try {
            val editor = prefs.edit()
            editor.clear()
            val result = editor.commit()
            Log.d("SessionManager", "Tokens cleared result: $result")
        } catch (e: Exception) {
            Log.e("SessionManager", "Error clearing tokens: ${e.message}")
        }
    }

    fun saveUserId(id: Int) {
        try {
            val editor = prefs.edit()
            editor.putInt(USER_ID, id)
            val result = editor.commit()
            Log.d("SessionManager", "Saving userId: $id")
        } catch (e: Exception) {
            Log.e("SessionManager", "Error saving refresh token: ${e.message}")
        }
    }

    fun getUserIdFromToken(): Int? {
        val token = fetchAuthToken() ?: return null
        return try {
            val payload = token.split(".")[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
            val json = JSONObject(String(decodedBytes))
            val id = json.optInt("id", -1)
            Log.d("SessionManager", "Decoded userId from token: $id")
            if (id != -1) id else null
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to decode user ID: ${e.message}")
            null
        }
    }

    fun getUserId(): Int? {
        val id = prefs.getInt(USER_ID, -1)
        Log.d("SessionManager", "Fetched userId: $id")
        return if (id != -1) id else null
    }

    fun clearAuthData() {
        prefs.edit {
            remove(USER_TOKEN)
            remove(REFRESH_TOKEN)
        }
        Log.d("SessionManager", "Auth data cleared")
    }

    // Add these methods to your SessionManager class

    fun isLoggedIn(): Boolean {
        try {
            val token = fetchAuthToken()
            val userId = getUserId()
            val result = !token.isNullOrEmpty() && userId != null && userId != -1
            Log.d("SessionManager", "isLoggedIn check - token exists: ${!token.isNullOrEmpty()}, userId: $userId, result: $result")
            return result
        } catch (e: Exception) {
            Log.e("SessionManager", "Error checking login status: ${e.message}")
            return false
        }
    }

    fun isTokenValid(): Boolean {
        try {
            val token = fetchAuthToken() ?: return false

            val parts = token.split(".")
            if (parts.size != 3) {
                Log.d("SessionManager", "Invalid token format")
                return false
            }

            val payload = parts[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
            val json = JSONObject(String(decodedBytes))


            val exp = json.optLong("exp", 0)
            if (exp > 0) {
                val currentTime = System.currentTimeMillis() / 1000
                val isExpired = currentTime > exp
                Log.d("SessionManager", "Token expiration check - current: $currentTime, exp: $exp, expired: $isExpired")
                return !isExpired
            }

            return true
        } catch (e: Exception) {
            Log.e("SessionManager", "Error validating token: ${e.message}")
            return false
        }
    }

    fun clearAllData() {
        try {
            val editor = prefs.edit()
            editor.clear()
            val result = editor.commit()
            Log.d("SessionManager", "All data cleared result: $result")
        } catch (e: Exception) {
            Log.e("SessionManager", "Error clearing all data: ${e.message}")
        }
    }

}