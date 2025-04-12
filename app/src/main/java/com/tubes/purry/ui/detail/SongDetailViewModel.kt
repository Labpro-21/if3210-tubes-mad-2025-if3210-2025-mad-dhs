package com.tubes.purry.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tubes.purry.data.model.Song

class SongDetailViewModel : ViewModel() {
    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    fun setSong(song: Song) {
        _currentSong.value = song
    }
}