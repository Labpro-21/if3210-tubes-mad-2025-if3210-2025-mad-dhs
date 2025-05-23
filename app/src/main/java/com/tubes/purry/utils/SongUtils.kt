package com.tubes.purry.utils

fun parseDuration(duration: String): Int {
    val parts = duration.split(":")
    return if (parts.size == 2) {
        val minutes = parts[0].toIntOrNull() ?: 0
        val seconds = parts[1].toIntOrNull() ?: 0
        minutes * 60 + seconds
    } else 0
}