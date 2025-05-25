package com.tubes.purry.data.local

import androidx.lifecycle.LiveData
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

    @Query("SELECT * FROM songs WHERE filePath IS NOT NULL ORDER BY title ASC")
    fun getNewSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 10")
    fun getRecentlyPlayed(): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getTotalSongCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE isLiked = 1")
    fun getLikedSongsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE lastPlayedAt > 0")
    fun getListenedSongsCount(): Flow<Int>

    @Query("SELECT * FROM songs WHERE filePath = :path LIMIT 1")
    suspend fun getSongByFilePath(path: String): Song?

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Song?

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: String)

    @Query("SELECT * FROM songs WHERE isLocal = 1 ORDER BY title ASC")
    fun getLibrarySongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :title || '%' AND artist LIKE '%' || :artist || '%' LIMIT 1")
    suspend fun getSongByTitleAndArtist(title: String, artist: String): Song?

    @Query("SELECT * FROM songs WHERE artist LIKE '%' || :artist || '%'")
    suspend fun getSongsByArtist(artist: String): List<Song>
}