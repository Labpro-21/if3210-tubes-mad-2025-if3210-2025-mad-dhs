package com.tubes.purry.utils

import java.util.Locale

fun formatDuration(durationInMs: Int): String {
    val totalSeconds = durationInMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}