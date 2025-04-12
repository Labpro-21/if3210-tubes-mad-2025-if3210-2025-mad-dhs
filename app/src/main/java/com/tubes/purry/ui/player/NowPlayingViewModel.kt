package com.tubes.purry.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tubes.purry.data.model.Song
import com.tubes.purry.data.model.SongInQueue

class NowPlayingViewModel : ViewModel() {
    private val _currSong = MutableLiveData<Song?>() // mutable untuk bisa diubah2
    val currSong: LiveData<Song?> = _currSong // untuk read

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _errorMessage = MutableLiveData<String>()
    private var originalAllSongs: List<Song> = emptyList()
    private val _mainQueue = MutableLiveData<List<SongInQueue>>(emptyList())
    private val _manualQueue = MutableLiveData<MutableList<SongInQueue>?>(mutableListOf())

    private var currentQueueIndex = -1

    private val _isShuffling = MutableLiveData<Boolean>(false)
    val isShuffling: LiveData<Boolean> = _isShuffling

    private val _repeatMode = MutableLiveData<RepeatMode>(RepeatMode.NONE)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    enum class RepeatMode { NONE, ONE, ALL }

    private fun getCurrentSongInQueue(): SongInQueue? {
        val currId = _currSong.value?.id ?: return null
        return _manualQueue.value?.find { it.song.id == currId }
    }

    fun playSong(song: Song, context: Context) {
        val success = PlayerController.play(song, context)
        if (success) {
            _currSong.value = song
//            _isPlaying.value = true
            Handler(Looper.getMainLooper()).postDelayed({
                _isPlaying.value = PlayerController.isPlaying()
            }, 300)

            PlayerController.onCompletion = {
                when (_repeatMode.value) {
                    RepeatMode.ONE -> {
                        _currSong.value?.let { playSong(it, context) }
                    }
                    else -> {
                        removeCurrentFromQueue()
                        playNextInQueue(context)
                    }
                }
            }
        } else {
            _isPlaying.value = false
            _errorMessage.value = "Gagal memutar lagu. Cek file atau perizinan."
        }
    }

    private fun pauseSong() {
        _isPlaying.value = false
        PlayerController.pause()
    }

    private fun resumeSong() {
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
        val updatedManualQueue = _manualQueue.value ?: mutableListOf()
        updatedManualQueue.add(SongInQueue(song, fromManualQueue = true))
        _manualQueue.value = updatedManualQueue

        if (_currSong.value == null) {
            playSong(song, context)
        }
    }

    fun setQueueFromClickedSong(clicked: Song, allSongs: List<Song>, context: Context) {
        originalAllSongs = allSongs

        val newMainQueue = mutableListOf<SongInQueue>()
        newMainQueue.add(SongInQueue(clicked, fromManualQueue = false))
        newMainQueue.addAll(allSongs.filter { it.id != clicked.id }.map { SongInQueue(it, false) })

        _manualQueue.value = mutableListOf()
        _mainQueue.value = newMainQueue
        currentQueueIndex = 0
        playSong(clicked, context)
    }

    fun removeFromQueue(deletedSongId: String, context: Context) {
        val wasCurrent = _currSong.value?.id == deletedSongId

        val manualQueueList = _manualQueue.value ?: mutableListOf()
        val manualIndex = manualQueueList.indexOfFirst { it.song.id == deletedSongId }

        if (manualIndex != -1) {
            manualQueueList.removeAt(manualIndex)
            _manualQueue.value = manualQueueList
        } else {
            val mainQueueList = _mainQueue.value?.toMutableList() ?: mutableListOf()
            val mainIndex = mainQueueList.indexOfFirst { it.song.id == deletedSongId }

            if (mainIndex != -1) {
                mainQueueList.removeAt(mainIndex)
                _mainQueue.value = mainQueueList
                if (mainIndex < currentQueueIndex) currentQueueIndex--
            }
        }

        if (wasCurrent) {
            playNextInQueue(context)
        }
    }


    private fun removeCurrentFromQueue() {
        val currentInQueue = getCurrentSongInQueue()
        if (currentInQueue?.fromManualQueue == true) {
            val updatedManual = _manualQueue.value?.toMutableList()
            updatedManual?.removeIf { it.song.id == currentInQueue.song.id }
            _manualQueue.value = updatedManual
        }
    }

    private fun playNextInQueue(context: Context) {
        val manual = _manualQueue.value ?: mutableListOf()
        if (manual.isNotEmpty()) {
            val nextManual = manual.removeAt(0)
            _manualQueue.value = manual
            playSong(nextManual.song, context)
            return
        }

        val main = _mainQueue.value.orEmpty()
        if (main.isEmpty()) return

        if (currentQueueIndex < main.size - 1) {
            currentQueueIndex++
            playSong(main[currentQueueIndex].song, context)
        } else if (_repeatMode.value == RepeatMode.ALL) {
            currentQueueIndex = 0
            playSong(main[0].song, context)
        }
    }


    fun nextSong(context: Context, isManual: Boolean = true) {
        if (isManual && _repeatMode.value == RepeatMode.ONE) {
            _repeatMode.value = RepeatMode.ALL
        }
        playNextInQueue(context)
    }

    fun previousSong(context: Context) {
        val main = _mainQueue.value.orEmpty()
        if (main.isEmpty()) return

        if (_isShuffling.value == true) {
            val randomSong = main.random()
            currentQueueIndex = main.indexOf(randomSong)
            playSong(randomSong.song, context)
        } else {
            if (currentQueueIndex > 0) {
                currentQueueIndex--
                playSong(main[currentQueueIndex].song, context)
            } else if (_repeatMode.value == RepeatMode.ALL && main.isNotEmpty()) {
                currentQueueIndex = main.size - 1
                playSong(main[currentQueueIndex].song, context)
            }
        }
    }


    fun clearQueue() {
        _mainQueue.value = emptyList()
        _manualQueue.value = mutableListOf()
        _currSong.value = null
        _isPlaying.value = false
        currentQueueIndex = -1
    }

    fun toggleShuffle() {
        val isNowShuffling = !(_isShuffling.value ?: false)
        _isShuffling.value = isNowShuffling

        val currentSong = _currSong.value ?: return
        val newMainQueue = mutableListOf<SongInQueue>()

        if (isNowShuffling) {
            val shuffled = originalAllSongs.shuffled().filter { it.id != currentSong.id }
            newMainQueue.add(SongInQueue(currentSong, fromManualQueue = false))
            newMainQueue.addAll(shuffled.map { SongInQueue(it, false) })
        } else {
            val ordered = originalAllSongs.filter { it.id != currentSong.id }
            newMainQueue.add(SongInQueue(currentSong, fromManualQueue = false))
            newMainQueue.addAll(ordered.map { SongInQueue(it, false) })
        }

        _mainQueue.value = newMainQueue
        currentQueueIndex = 0
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