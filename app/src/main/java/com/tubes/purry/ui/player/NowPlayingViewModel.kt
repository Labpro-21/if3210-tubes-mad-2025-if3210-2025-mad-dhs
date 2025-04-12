package com.tubes.purry.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tubes.purry.data.model.Song

class NowPlayingViewModel : ViewModel() {
    private val _currSong = MutableLiveData<Song>() // mutable untuk bisa diubah2
    val currSong: LiveData<Song> = _currSong // untuk read

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    private val _queue = MutableLiveData<List<Song>>(emptyList())
    val queue: LiveData<List<Song>> = _queue

    private var currentQueueIndex = -1

    private val _isShuffling = MutableLiveData<Boolean>(false)
    val isShuffling: LiveData<Boolean> = _isShuffling

    private val _repeatMode = MutableLiveData<RepeatMode>(RepeatMode.NONE)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    enum class RepeatMode { NONE, ONE, ALL }
    private var allSongs: List<Song> = emptyList()

    fun setAllSongs(songs: List<Song>) {
        allSongs = songs
        if (_queue.value.isNullOrEmpty()) {
            _queue.value = songs
        }
    }

    fun playSong(song: Song, context: Context) {
        val success = PlayerController.play(song, context)
        if (success) {
            _currSong.value = song
            _isPlaying.value = true

            PlayerController.onCompletion = {
                nextSong(context)
            }
        } else {
            _isPlaying.value = false
            _errorMessage.value = "Gagal memutar lagu. Cek file atau perizinan."
        }
    }

    fun pauseSong() {
        _isPlaying.value = false
        PlayerController.pause()
    }

    fun resumeSong() {
        if (!PlayerController.isPlaying()) {
            _isPlaying.value = true
            PlayerController.resume()
        } else {
            Log.d("NowPlayingViewModel", "resumeSong called but already playing.")
        }
    }

    fun togglePlayPause() {
        val currentlyPlaying = _isPlaying.value ?: false
        if (currentlyPlaying) {
            pauseSong()
        } else {
            resumeSong()
        }
    }

    fun addToQueue(song: Song, context: Context) {
        val updatedQueue = _queue.value?.toMutableList() ?: mutableListOf()
        updatedQueue.add(song)
        _queue.value = updatedQueue

        if (currentQueueIndex == -1) {
            currentQueueIndex = 0
            playSong(updatedQueue[0], context)
        }
    }

    fun nextSong(context: Context) {
        val queueList = _queue.value.orEmpty()
        if (queueList.isEmpty()) return

        val isManualSkip = true

        if (isManualSkip && _repeatMode.value == RepeatMode.ONE) {
            _repeatMode.value = RepeatMode.ALL
        }

        if (_isShuffling.value == true) {
            val randomSong = queueList.random()
            playSong(randomSong, context)
        } else {
            if (currentQueueIndex < queueList.size - 1) {
                currentQueueIndex++
                playSong(queueList[currentQueueIndex], context)
            } else if (_repeatMode.value == RepeatMode.ALL) {
                currentQueueIndex = 0
                playSong(queueList[0], context)
            }
        }
    }

    fun previousSong(context: Context) {
        val queueList = _queue.value.orEmpty()
        if (queueList.isEmpty()) return

        if (_isShuffling.value == true) {
            val randomSong = queueList.random()
            playSong(randomSong, context)
        } else {
            if (currentQueueIndex > 0) {
                currentQueueIndex--
                playSong(queueList[currentQueueIndex], context)
            }
        }
    }


    fun toggleShuffle() {
        _isShuffling.value = !(_isShuffling.value ?: false)
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE, null -> RepeatMode.NONE
        }
    }


    override fun onCleared() {
        super.onCleared()
        PlayerController.release()
    }
}