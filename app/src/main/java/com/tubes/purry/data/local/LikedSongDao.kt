package com.tubes.purry.data.local

import androidx.lifecycle.LiveData
import com.tubes.purry.data.model.LikedSong
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tubes.purry.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun likeSong(likedSong: LikedSong)

    @Query("DELETE FROM liked_songs WHERE userId = :userId AND songId = :songId")
    suspend fun unlikeSong(userId: Int, songId: String)

    @Query("""SELECT * FROM songs WHERE id IN (SELECT songId FROM liked_songs WHERE userId = :userId)""")
    fun getLikedSongsByUser(userId: Int): LiveData<List<Song>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE userId = :userId AND songId = :songId)")
    suspend fun isSongLiked(userId: Int, songId: String): Boolean

    @Query("SELECT COUNT(*) FROM liked_songs WHERE userId = :userId")
    fun getLikedCountByUser(userId: Int): Flow<Int>
}
