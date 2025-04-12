package com.tubes.purry.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "liked_songs",
    primaryKeys = ["userId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProfileData::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songId"]), Index(value = ["userId"])]
)
data class LikedSong(
    val userId: Int,
    val songId: String,
    val likedAt: Long = System.currentTimeMillis()
)
