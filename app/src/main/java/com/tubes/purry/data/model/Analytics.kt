package com.tubes.purry.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

// Entity untuk tracking listening session
@Entity(tableName = "listening_sessions")
data class ListeningSession(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,
    val songId: String,
    val songTitle: String,
    val artist: String,
    val startTime: Long,
    val endTime: Long? = null,
    val duration: Long = 0, // dalam detik
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    val month: String = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
)

// Data class untuk monthly analytics
data class MonthlyAnalytics(
    val month: String, // Format: "yyyy-MM"
    val totalMinutesListened: Long,
    val dailyAverage: Int,
    val topArtist: String?,
    val topArtistPlayCount: Int,
    val topArtistCover: String?,
    val topSong: String?,
    val topSongArtist: String?,
    val topSongPlayCount: Int,
    val topSongCover: String?,
    val totalSongsPlayed: Int,
    val totalArtistsListened: Int,
    val dayStreaks: List<DayStreak> = emptyList()
)

// Data class untuk day streak
data class DayStreak(
    val songTitle: String,
    val artist: String,
    val cover: String?,
    val startDate: String,
    val endDate: String,
    val streakDays: Int
)

// Data class untuk top songs list
data class TopSong(
    val rank: Int,
    val title: String,
    val artist: String,
    val cover: String?,
    val playCount: Int
)

// Data class untuk top artists list
data class TopArtist(
    val rank: Int,
    val name: String,
    val cover: String?,
    val playCount: Int
)

// Data class untuk daily chart data
data class DailyChartData(
    val day: Int,
    val minutes: Int
)

// Data class untuk export
data class AnalyticsExportData(
    val month: String,
    val userInfo: ProfileData,
    val analytics: MonthlyAnalytics,
    val topSongs: List<TopSong>,
    val topArtists: List<TopArtist>,
    val dailyChart: List<DailyChartData>,
    val exportDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)