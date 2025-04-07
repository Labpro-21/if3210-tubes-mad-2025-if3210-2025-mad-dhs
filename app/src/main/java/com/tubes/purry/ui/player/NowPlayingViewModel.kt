package com.tubes.purry.ui.player

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tubes.purry.data.model.Song

class NowPlayingViewModel : ViewModel() {
    private val _currSong = MutableLiveData<Song>() // mutable untuk bisa diubah2
    val currSong: LiveData<Song> = _currSong // untuk read

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    fun playSong(song: Song, context: Context) {
        _currSong.value = song
        _isPlaying.value = true
        PlayerController.play(song, context)
    }

    fun pauseSong() {
        _isPlaying.value = false
        PlayerController.pause()
    }

    fun resumeSong() {
        _isPlaying.value = true
        PlayerController.resume()
    }

    fun togglePlayPause() {
        if(_isPlaying.value == true) pauseSong() else resumeSong()
    }

    override fun onCleared() {
        super.onCleared()
        PlayerController.release()
    }
}