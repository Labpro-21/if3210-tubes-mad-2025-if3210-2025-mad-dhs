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
}