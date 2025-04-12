package com.tubes.purry.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "user_profile")
data class ProfileData(
    @PrimaryKey @SerializedName("id") val id: Int,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("profilePhoto") val profilePhoto: String,
    @SerializedName("location") val location: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("songsAdded") val songsAdded: Int = 0,
    @SerializedName("likedSongs") val likedSongs: Int = 0,
    @SerializedName("listenedSongs") val listenedSongs: Int = 0
)

typealias ProfileResponse = ProfileData