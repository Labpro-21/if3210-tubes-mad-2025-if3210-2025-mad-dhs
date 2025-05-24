package com.tubes.purry.data.model

data class RawSong(
    val id: Int,
    val title: String,
    val artist: String,
    val artwork: String,
    val url: String,
    val duration: String, // ← tetap string dari API
    val country: String,
    val rank: Int,
    val createdAt: String,
    val updatedAt: String
)

