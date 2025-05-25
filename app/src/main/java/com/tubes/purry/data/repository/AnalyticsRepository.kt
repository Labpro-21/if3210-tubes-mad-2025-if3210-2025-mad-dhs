package com.tubes.purry.data.repository

import android.util.Log
import com.tubes.purry.data.local.AnalyticsDao
import com.tubes.purry.data.local.SongDao
import com.tubes.purry.data.model.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsRepository(
    private val analyticsDao: AnalyticsDao,
    private val songDao: SongDao
) {

    suspend fun startListeningSession(userId: Int, song: Song): Long {
        val session = ListeningSession(
            userId = userId,
            songId = song.id,
            songTitle = song.title,
            artist = song.artist,
            startTime = System.currentTimeMillis()
        )

        // INSERT and return the generated ID
        val sessionId = analyticsDao.insertListeningSession(session)
        Log.d("AnalyticsRepository", "✅ Created session with ID: $sessionId")
        return sessionId
    }

    suspend fun endListeningSession(sessionId: Long, duration: Long) {
        val session = analyticsDao.getSessionById(sessionId)
        session?.let {
            val updatedSession = it.copy(
                endTime = System.currentTimeMillis(),
                duration = duration / 1000 // Convert to seconds
            )
            analyticsDao.updateListeningSession(updatedSession)
        }
    }

    suspend fun getMonthlyAnalytics(userId: Int, month: String): MonthlyAnalytics {
        val totalSeconds = analyticsDao.getTotalListeningTimeByMonth(userId, month) ?: 0L
        val totalMinutes = totalSeconds / 60

        val dailyAvg = analyticsDao.getDailyAverageByMonth(userId, month)?.let {
            (it / 60).toInt()
        } ?: 0

        val topArtistResult = analyticsDao.getTopArtistByMonth(userId, month)
        val topSongResult = analyticsDao.getTopSongByMonth(userId, month)

        val totalSongs = analyticsDao.getTotalSongsPlayedByMonth(userId, month)
        val totalArtists = analyticsDao.getTotalArtistsListenedByMonth(userId, month)

        // Get day streaks
        val dayStreaks = calculateDayStreaks(userId, month)

        // Get cover art for top song and artist
        val topSongCover = topSongResult?.let { result ->
            songDao.getSongByTitleAndArtist(result.songTitle, result.artist)?.coverPath
        }

        val topArtistCover = topArtistResult?.let { result ->
            songDao.getSongsByArtist(result.artist).firstOrNull()?.coverPath
        }

        return MonthlyAnalytics(
            month = month,
            totalMinutesListened = totalMinutes,
            dailyAverage = dailyAvg,
            topArtist = topArtistResult?.artist,
            topArtistPlayCount = topArtistResult?.play_count ?: 0,
            topArtistCover = topArtistCover,
            topSong = topSongResult?.songTitle,
            topSongArtist = topSongResult?.artist,
            topSongPlayCount = topSongResult?.play_count ?: 0,
            topSongCover = topSongCover,
            totalSongsPlayed = totalSongs,
            totalArtistsListened = totalArtists,
            dayStreaks = dayStreaks
        )
    }

    suspend fun getTopSongs(userId: Int, month: String, limit: Int = 10): List<TopSong> {
        val results = analyticsDao.getTopSongsByMonth(userId, month, limit)
        return results.mapIndexed { index, result ->
            val cover = songDao.getSongByTitleAndArtist(result.songTitle, result.artist)?.coverPath
            TopSong(
                rank = index + 1,
                title = result.songTitle,
                artist = result.artist,
                cover = cover,
                playCount = result.play_count
            )
        }
    }

    suspend fun getTopArtists(userId: Int, month: String, limit: Int = 10): List<TopArtist> {
        val results = analyticsDao.getTopArtistsByMonth(userId, month, limit)
        return results.mapIndexed { index, result ->
            val cover = songDao.getSongsByArtist(result.artist).firstOrNull()?.coverPath
            TopArtist(
                rank = index + 1,
                name = result.artist,
                cover = cover,
                playCount = result.play_count
            )
        }
    }

    suspend fun getDailyChart(userId: Int, month: String): List<DailyChartData> {
        val results = analyticsDao.getDailyListeningByMonth(userId, month)
        return results.map { result ->
            val day = result.date.split("-").last().toInt()
            val minutes = (result.total_duration / 60).toInt()
            DailyChartData(day, minutes)
        }
    }

    private suspend fun calculateDayStreaks(userId: Int, month: String): List<DayStreak> {
        val songsByDate = analyticsDao.getSongsByDateForStreaks(userId, month)
        val streaks = mutableListOf<DayStreak>()

        // Group by song
        val songGroups = songsByDate.groupBy { "${it.songTitle}-${it.artist}" }

        for ((songKey, dates) in songGroups) {
            val sortedDates = dates.sortedBy { it.date }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            var streakStart = 0
            var currentStreak = 1

            for (i in 1 until sortedDates.size) {
                val prevDate = dateFormat.parse(sortedDates[i-1].date)
                val currDate = dateFormat.parse(sortedDates[i].date)

                if (prevDate != null && currDate != null) {
                    val daysDiff = ((currDate.time - prevDate.time) / (1000 * 60 * 60 * 24)).toInt()

                    if (daysDiff == 1) {
                        currentStreak++
                    } else {
                        // End of streak, record if >= 2 days
                        if (currentStreak >= 2) {
                            val song = sortedDates[streakStart]
                            val cover = songDao.getSongByTitleAndArtist(song.songTitle, song.artist)?.coverPath

                            streaks.add(DayStreak(
                                songTitle = song.songTitle,
                                artist = song.artist,
                                cover = cover,
                                startDate = sortedDates[streakStart].date,
                                endDate = sortedDates[i-1].date,
                                streakDays = currentStreak
                            ))
                        }
                        streakStart = i
                        currentStreak = 1
                    }
                }
            }

            // Check final streak
            if (currentStreak >= 2) {
                val song = sortedDates[streakStart]
                val cover = songDao.getSongByTitleAndArtist(song.songTitle, song.artist)?.coverPath

                streaks.add(DayStreak(
                    songTitle = song.songTitle,
                    artist = song.artist,
                    cover = cover,
                    startDate = sortedDates[streakStart].date,
                    endDate = sortedDates.last().date,
                    streakDays = currentStreak
                ))
            }
        }

        return streaks.sortedByDescending { it.streakDays }
    }

    fun getSessionsByMonth(userId: Int, month: String): Flow<List<ListeningSession>> {
        return analyticsDao.getSessionsByMonth(userId, month)
    }
}