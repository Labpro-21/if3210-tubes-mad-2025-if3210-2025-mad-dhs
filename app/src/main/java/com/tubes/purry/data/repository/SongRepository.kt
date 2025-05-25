package com.tubes.purry.data.repository

import androidx.lifecycle.LiveData
import com.tubes.purry.data.local.LikedSongDao
import com.tubes.purry.data.local.SongDao
import com.tubes.purry.data.model.Song
import kotlinx.coroutines.flow.Flow //untuk stream data async

class SongRepository(private val songDao: SongDao, private val likedSongDao: LikedSongDao) {
    suspend fun insertSong(song: Song) = songDao.insert(song)
    suspend fun deleteSong(song: Song) = songDao.delete(song)
    suspend fun updateSong(song: Song) = songDao.update(song)
    fun getNewSongs(): Flow<List<Song>> = songDao.getNewSongs()
    fun getRecentlyPlayed(): Flow<List<Song>> = songDao.getRecentlyPlayed()
    fun getTotalSongCount(): Flow<Int> = songDao.getTotalSongCount()
    fun getListenedSongsCount(): Flow<Int> = songDao.getListenedSongsCount()
    fun getLikedSongsByUser(userId: Int): LiveData<List<Song>> {
        return likedSongDao.getLikedSongsByUser(userId)
    }
    fun getLikedCountByUser(userId: Int): Flow<Int> {
        return likedSongDao.getLikedCountByUser(userId)
    }
    fun getAllSongs(): Flow<List<Song>> {
        return songDao.getAllSongs()
    }
    fun getLibrarySongs(): Flow<List<Song>> {
        return songDao.getLibrarySongs()
    }
    suspend fun saveDownloadedSong(song: Song, filePath: String) {
        val localVersion = song.copy(
            isLocal = true,
            filePath = filePath
        )
        songDao.insert(localVersion)
    }

}