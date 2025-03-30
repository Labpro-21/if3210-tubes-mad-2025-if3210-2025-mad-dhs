package com.tubes.purry.ui.library

import androidx.lifecycle.*
import com.tubes.purry.data.model.Song
import com.tubes.purry.data.repository.SongRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SongViewModel(private val repository: SongRepository) : ViewModel() {

    fun insertSong(song: Song) = viewModelScope.launch {
        repository.insertSong(song)
    }

    fun updateSong(song: Song) = viewModelScope.launch {
        repository.updateSong(song)
    }

    fun deleteSong(song: Song) = viewModelScope.launch {
        repository.deleteSong(song)
    }

    val newSongs: LiveData<List<Song>> = repository.getNewSongs().asLiveData()

    val recentlyPlayed: LiveData<List<Song>> = repository.getRecentlyPlayed().asLiveData()

    fun markAsPlayed(song: Song) = viewModelScope.launch {
        val updatedSong = song.copy(lastPlayedAt = System.currentTimeMillis())
        repository.updateSong(updatedSong)
    }
}
