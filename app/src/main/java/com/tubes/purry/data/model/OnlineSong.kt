package com.tubes.purry.data.model

import com.tubes.purry.utils.parseDuration
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class OnlineSong(
    val id: Int,
    val title: String,
    val artist: String,
    val artwork: String,
    val url: String,
    val duration: String,
    val country: String,
    val rank: Int,
)

fun OnlineSong.toLocalSong(filePath: String): Song {
    return Song(
        id = UUID.randomUUID().toString(),
        serverId = this.id,
        title = this.title,
        artist = this.artist,
        filePath = filePath,
        coverPath = this.artwork,
        duration = 0,
        isLiked = false,
        isLocal = true,
        lastPlayedAt = 0L
    )
}


fun OnlineSong.toTemporarySong(): Song {
    return Song(
        id = "srv-${this.id}",
        serverId = this.id,
        title = this.title,
        artist = this.artist,
        filePath = this.url,
        coverPath = this.artwork,
        duration = parseDuration(this.duration),
        isLocal = false
    )
}
