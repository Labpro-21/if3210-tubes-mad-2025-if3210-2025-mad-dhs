package com.tubes.purry.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val filePath: String? = null,
    val resId: Int? = null,
    val coverResId: Int? = null,
    val coverPath: String? = null,
    val duration: Int = 0,
    val isLiked: Boolean = false,
    val isLocal: Boolean = false,
    val lastPlayedAt: Long = 0L
)
