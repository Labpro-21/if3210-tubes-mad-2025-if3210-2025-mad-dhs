package com.tubes.purry.data.local

import androidx.room.*
import com.tubes.purry.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: Song)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<Song>)

    @Update
    suspend fun update(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Query("SELECT * FROM songs WHERE isLocal = 1 ORDER BY id DESC LIMIT 10")
    fun getNewSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 10")
    fun getRecentlyPlayed(): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getTotalSongCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE isLiked = 1")
    fun getLikedSongsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE lastPlayedAt > 0")
    fun getListenedSongsCount(): Flow<Int>

    // Get songs with similar artists to liked songs
    @Query("""
        SELECT * FROM songs 
        WHERE artist IN (
            SELECT DISTINCT s.artist 
            FROM songs s 
            INNER JOIN liked_songs ls ON s.id = ls.songId 
            WHERE ls.userId = :userId
        ) 
        AND id NOT IN (
            SELECT songId FROM liked_songs WHERE userId = :userId
        )
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    fun getSongsByLikedArtists(userId: Int, limit: Int): Flow<List<Song>>

    // Get most played songs by other users (popularity-based)
    @Query("""
        SELECT * FROM songs 
        WHERE lastPlayedAt > 0 
        AND id NOT IN (
            SELECT songId FROM liked_songs WHERE userId = :userId
        )
        ORDER BY lastPlayedAt DESC 
        LIMIT :limit
    """)
    fun getPopularSongs(userId: Int, limit: Int): Flow<List<Song>>

    // Get songs similar to recently played (based on listening patterns)
    @Query("""
        SELECT * FROM songs 
        WHERE id NOT IN (
            SELECT songId FROM liked_songs WHERE userId = :userId
        )
        AND id NOT IN (
            SELECT id FROM songs WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 5
        )
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    fun getDiscoverySongs(userId: Int, limit: Int): Flow<List<Song>>

    // Get user's listening history count
    @Query("SELECT COUNT(*) FROM songs WHERE lastPlayedAt > 0")
    suspend fun getUserListeningCount(): Int
}