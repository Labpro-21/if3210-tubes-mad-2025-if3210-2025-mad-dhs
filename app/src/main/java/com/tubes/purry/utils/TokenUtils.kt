package com.tubes.purry.utils

import android.util.Base64
import org.json.JSONObject

object TokenUtils {
    fun isTokenExpiringSoon(token: String, thresholdInSeconds: Long = 60): Boolean {
        return try {
            val parts = token.split(".")
            val payload = String(Base64.decode(parts[1], Base64.DEFAULT))
            val json = JSONObject(payload)
            val exp = json.getLong("exp")
            val currentTime = System.currentTimeMillis() / 1000
            currentTime > (exp - thresholdInSeconds)
        } catch (e: Exception) {
            true // Assume it's expiring if something fails
        }
    }
}
