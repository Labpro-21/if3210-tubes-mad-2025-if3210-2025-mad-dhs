package com.tubes.purry.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
        prefs.edit().putInt(USER_ID, id).apply()
    }

    fun getUserId(): Int? {
        val id = prefs.getInt(USER_ID, -1)
        return if (id != -1) id else null
    }
}