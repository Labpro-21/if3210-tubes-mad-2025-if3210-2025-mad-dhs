package com.tubes.purry.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(primaryKeys = ["userId", "position"])
data class SongQueue(
    val userId: Int,
    val songId: String,
    val position: Int
)
