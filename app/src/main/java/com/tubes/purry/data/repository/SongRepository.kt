package com.tubes.purry.data.repository

import com.tubes.purry.data.local.SongDao
import com.tubes.purry.data.model.Song
import kotlinx.coroutines.flow.Flow //untuk stream data async

class SongRepository(private val songDao: SongDao) {
    suspend fun insertSong(song: Song) = songDao.insert(song)
    suspend fun deleteSong(song: Song) = songDao.delete(song)
    suspend fun updateSong(song: Song) = songDao.update(song)
    fun getNewSongs(): Flow<List<Song>> = songDao.getNewSongs()
    fun getRecentlyPlayed(): Flow<List<Song>> = songDao.getRecentlyPlayed()
}