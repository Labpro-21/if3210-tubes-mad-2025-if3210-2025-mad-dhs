package com.tubes.purry.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song (
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val coveredUrl: String,
    val filePath: String?, // for user-uploaded songs
    val resId : Int?, // for raw resource (seeded)
    val duration: Int,
    val isLiked: Boolean = false,
    val isLocal: Boolean = false,
    val lastPlayedAt: Long = 0L
)