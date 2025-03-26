package com.tubes.purry.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class SessionManager(context: Context) {
    private var prefs: SharedPreferences

    companion object {
        const val USER_TOKEN = "user_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val PREF_NAME = "purrytify_prefs"
    }

    init {
        // Use standard SharedPreferences instead of EncryptedSharedPreferences for now
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveAuthToken(token: String) {
        val editor = prefs.edit()
        editor.putString(USER_TOKEN, token)
        val result = editor.commit()
        Log.d("SessionManager", "Token saved result: $result")
    }

    fun fetchAuthToken(): String? {
        val token = prefs.getString(USER_TOKEN, null)
        Log.d("SessionManager", "Token fetch result: ${token != null}")
        return token
    }

    fun saveRefreshToken(token: String) {
        val editor = prefs.edit()
        editor.putString(REFRESH_TOKEN, token)
        val result = editor.commit()
        Log.d("SessionManager", "Refresh token saved result: $result")
    }

    fun fetchRefreshToken(): String? {
        return prefs.getString(REFRESH_TOKEN, null)
    }

    fun clearTokens() {
        val editor = prefs.edit()
        editor.clear()
        editor.commit()
        Log.d("SessionManager", "Tokens cleared")
    }
}