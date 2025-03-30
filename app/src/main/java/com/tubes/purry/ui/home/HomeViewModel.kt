package com.tubes.purry.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.tubes.purry.data.repository.SongRepository

class HomeViewModel (
    private val songRepository: SongRepository
) : ViewModel() {
    val recentlyPlayed = songRepository.getRecentlyPlayed().asLiveData()
    val newSong = songRepository.getNewSongs().asLiveData()
}