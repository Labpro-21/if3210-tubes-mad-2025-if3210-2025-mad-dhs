package com.tubes.purry.data.local

import androidx.room.*
import com.tubes.purry.data.model.ListeningSession
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {

    @Insert
    suspend fun insertListeningSession(session: ListeningSession): Long

    @Update
    suspend fun updateListeningSession(session: ListeningSession)

    @Query("SELECT * FROM listening_sessions WHERE userId = :userId AND month = :month ORDER BY startTime DESC")
    fun getSessionsByMonth(userId: Int, month: String): Flow<List<ListeningSession>>

    @Query("SELECT SUM(duration) FROM listening_sessions WHERE userId = :userId AND month = :month")
    suspend fun getTotalListeningTimeByMonth(userId: Int, month: String): Long?

    @Query("SELECT AVG(daily_total) FROM (SELECT SUM(duration) as daily_total FROM listening_sessions WHERE userId = :userId AND month = :month GROUP BY date)")
    suspend fun getDailyAverageByMonth(userId: Int, month: String): Double?

    @Query("""
        SELECT artist, COUNT(*) as play_count, MAX(startTime) as recent_time
        FROM listening_sessions 
        WHERE userId = :userId AND month = :month 
        GROUP BY artist 
        ORDER BY play_count DESC, recent_time DESC 
        LIMIT 1
    """)
    suspend fun getTopArtistByMonth(userId: Int, month: String): TopArtistResult?

    @Query("""
        SELECT songTitle, artist, COUNT(*) as play_count, MAX(startTime) as recent_time
        FROM listening_sessions 
        WHERE userId = :userId AND month = :month 
        GROUP BY songId 
        ORDER BY play_count DESC, recent_time DESC 
        LIMIT 1
    """)
    suspend fun getTopSongByMonth(userId: Int, month: String): TopSongResult?

    @Query("""
        SELECT songTitle, artist, COUNT(*) as play_count
        FROM listening_sessions 
        WHERE userId = :userId AND month = :month 
        GROUP BY songId 
        ORDER BY play_count DESC 
        LIMIT :limit
    """)
    suspend fun getTopSongsByMonth(userId: Int, month: String, limit: Int): List<TopSongResult>

    @Query("""
        SELECT artist, COUNT(*) as play_count
        FROM listening_sessions 
        WHERE userId = :userId AND month = :month 
        GROUP BY artist 
        ORDER BY play_count DESC 
        LIMIT :limit
    """)
    suspend fun getTopArtistsByMonth(userId: Int, month: String, limit: Int): List<TopArtistResult>

    @Query("SELECT COUNT(DISTINCT songId) FROM listening_sessions WHERE userId = :userId AND month = :month")
    suspend fun getTotalSongsPlayedByMonth(userId: Int, month: String): Int

    @Query("SELECT COUNT(DISTINCT artist) FROM listening_sessions WHERE userId = :userId AND month = :month")
    suspend fun getTotalArtistsListenedByMonth(userId: Int, month: String): Int

    @Query("""
        SELECT date, SUM(duration) as total_duration
        FROM listening_sessions 
        WHERE userId = :userId AND month = :month 
        GROUP BY date 
        ORDER BY date
    """)
    suspend fun getDailyListeningByMonth(userId: Int, month: String): List<DailyListeningResult>

    // Query untuk day streaks - lagu yang didengar berturut-turut
    @Query("""
        SELECT songTitle, artist, date, COUNT(*) as play_count
        FROM listening_sessions 
        WHERE userId = :userId AND month = :month 
        GROUP BY songId, date 
        HAVING play_count > 0
        ORDER BY songTitle, date
    """)
    suspend fun getSongsByDateForStreaks(userId: Int, month: String): List<SongDateResult>

    @Query("SELECT * FROM listening_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): ListeningSession?
}

// Data classes untuk query results
data class TopArtistResult(
    val artist: String,
    val play_count: Int,
    val recent_time: Long? = null
)

data class TopSongResult(
    val songTitle: String,
    val artist: String,
    val play_count: Int,
    val recent_time: Long? = null
)

data class DailyListeningResult(
    val date: String,
    val total_duration: Long
)

data class SongDateResult(
    val songTitle: String,
    val artist: String,
    val date: String,
    val play_count: Int
)