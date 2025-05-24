package com.tubes.purry.utils

fun parseDuration(durationStr: String): Int {
    return try {
        val parts = durationStr.split(":")
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
        minutes * 60 + seconds
    } catch (e: Exception) {
        0
    }
}

