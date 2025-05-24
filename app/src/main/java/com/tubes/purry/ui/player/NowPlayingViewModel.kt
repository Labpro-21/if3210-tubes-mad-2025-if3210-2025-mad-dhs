package com.tubes.purry.ui.player

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.tubes.purry.data.local.LikedSongDao
import com.tubes.purry.data.local.SongDao
import com.tubes.purry.data.model.LikedSong
import com.tubes.purry.data.model.ProfileData
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tubes.purry.data.model.Song
import com.tubes.purry.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.tubes.purry.data.model.SongInQueue

class NowPlayingViewModel(
    application: Application,
    private val likedSongDao: LikedSongDao,
    private val songDao: SongDao,
    private val profileViewModel: ProfileViewModel
) : AndroidViewModel(application) {

    private val _currSong = MutableLiveData<Song?>() // mutable untuk bisa diubah2
    val currSong: LiveData<Song?> = _currSong // untuk read

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _isLiked = MutableLiveData<Boolean>()
    val isLiked: LiveData<Boolean> = _isLiked

    private val _errorMessage = MutableLiveData<String>()
    private var originalAllSongs: List<Song> = emptyList()
    private var shuffledQueue: List<SongInQueue> = emptyList()

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
        _isPlaying.value = true
        Log.d("NowPlayingViewModel", "Calling PlayerController.play() with ${song.filePath}")

        viewModelScope.launch {
//            val userId = getUserIdBlocking() ?: return@launch
//            if (userId == null) {
//                Log.e("NowPlayingViewModel", "userId null! Tidak bisa lanjut play.")
//                return@launch
//            }
//            val isLiked = likedSongDao.isLiked(userId, song.id)
            val songWithLike = song.copy(isLiked = false)

//            if (_currSong.value?.id == song.id) {
//                _currSong.postValue(songWithLike)
//            }
            _currSong.postValue(songWithLike)

            Log.d(
                "NowPlayingViewModel",
                "Song data: filePath=${song.filePath}, resId=${song.resId}"
            )
            withContext(Dispatchers.Main) {
                val success = PlayerController.play(songWithLike, context)
                if (success) {
                    PlayerController.onPrepared = {
                        val duration = PlayerController.getDuration()
                        _currSong.postValue(_currSong.value?.copy(duration = duration))
                        _isPlaying.postValue(PlayerController.isPlaying())
                    }
//                    _isLiked.value = false

                    Handler(Looper.getMainLooper()).postDelayed({
                        _isPlaying.value = PlayerController.isPlaying()
                    }, 300)


                    val realDuration = PlayerController.getDuration()
                    _currSong.value = _currSong.value?.copy(duration = realDuration)

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
        }
//        val success = PlayerController.play(song, context)
//        if (success) {
//            _currSong.value = song
////            _isPlaying.value = true
//            Handler(Looper.getMainLooper()).postDelayed({
//                _isPlaying.value = PlayerController.isPlaying()
//            }, 300)
//
//
//
//            PlayerController.onCompletion = {
//                when (_repeatMode.value) {
//                    RepeatMode.ONE -> {
//                        _currSong.value?.let { playSong(it, context) }
//                    }
//                    else -> {
//                        removeCurrentFromQueue()
//                        playNextInQueue(context)
//                    }
//                }
//            }
//        } else {
//            _isPlaying.value = false
//            _errorMessage.value = "Gagal memutar lagu. Cek file atau perizinan."
//        }
    }

    private fun pauseSong() {
        PlayerController.pause()
        _isPlaying.value = false
    }

    private fun resumeSong() {
        if (!PlayerController.isPlaying()) {
            PlayerController.resume()
            _isPlaying.value = true
        } else {
            Log.d("NowPlayingViewModel", "resumeSong called but already playing.")
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value == true) {
            PlayerController.pause()
            _isPlaying.value = false
        } else {
            PlayerController.resume()
            _isPlaying.value = true
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            val userId = profileViewModel.profileData.value?.id
            userId?.let { id ->
                val isLiked = likedSongDao.isSongLiked(id, song.id)
                if (!isLiked) {
                    likedSongDao.likeSong(LikedSong(userId = id, songId = song.id))
                    _isLiked.postValue(true)
                } else {
                    likedSongDao.unlikeSong(id, song.id)
                    _isLiked.postValue(false)
                }
            }
        }
    }

    private suspend fun getUserIdBlocking(): Int? {
        return profileViewModel.profileData.value?.id ?: suspendCoroutine { cont ->
            val observer = object : Observer<ProfileData?> {
                override fun onChanged(value: ProfileData?) {
                    if (value != null) {
                        cont.resume(value.id)
                        profileViewModel.profileData.removeObserver(this)
                    }
                }
            }
            profileViewModel.profileData.observeForever(observer)
        }
    }

    fun addToQueue(song: Song, context: Context) {
        val updatedManualQueue = _manualQueue.value ?: mutableListOf()
        updatedManualQueue.add(SongInQueue(song, true))
        _manualQueue.value = updatedManualQueue

        if (_currSong.value == null) {
            playSong(song, context)
        }
    }

    fun setQueueFromClickedSong(clicked: Song, allSongs: List<Song>, context: Context) {
        originalAllSongs = allSongs

//        val newMainQueue = mutableListOf<SongInQueue>()
        val newMainQueue = allSongs.map { SongInQueue(it, fromManualQueue = false) }
//        newMainQueue.add(SongInQueue(clicked, fromManualQueue = false))
//        newMainQueue.addAll(allSongs.filter { it.id != clicked.id }.map { SongInQueue(it, false) })

        _manualQueue.value = mutableListOf()
        _mainQueue.value = newMainQueue
        currentQueueIndex = newMainQueue.indexOfFirst { it.song.id == clicked.id }
//        currentQueueIndex = 0
        playSong(clicked, context)
    }

    fun removeFromQueue(deletedSongId: String, context: Context) {
        val wasCurrent = _currSong.value?.id == deletedSongId

        val manualQueueList = _manualQueue.value ?: mutableListOf()
        manualQueueList.removeIf { it.song.id == deletedSongId }
        _manualQueue.value = manualQueueList

        val mainQueueList = _mainQueue.value?.toMutableList() ?: mutableListOf()
        val mainIndex = mainQueueList.indexOfFirst { it.song.id == deletedSongId }
        if (mainIndex != -1) {
            mainQueueList.removeAt(mainIndex)
            _mainQueue.value = mainQueueList
            if (mainIndex < currentQueueIndex) currentQueueIndex--
        }

        if (wasCurrent) {
            playNextInQueue(context)
        }
    }


    private fun removeCurrentFromQueue() {
        val currentInQueue = getCurrentSongInQueue() ?: return
        val updatedManual = _manualQueue.value?.toMutableList() ?: return
        updatedManual.removeIf { it.song.id == currentInQueue.song.id }
        _manualQueue.value = updatedManual
    }

    private fun playNextInQueue(context: Context) {
        val queue = if (_isShuffling.value == true) shuffledQueue else _mainQueue.value.orEmpty()
        if (queue.isEmpty()) return

        if (currentQueueIndex < queue.size - 1) {
            currentQueueIndex++
            playSong(queue[currentQueueIndex].song, context)
        } else if (_repeatMode.value == RepeatMode.ALL) {
            currentQueueIndex = 0
            playSong(queue[0].song, context)
        }
    }


    fun nextSong(context: Context, isManual: Boolean = true) {
        if (isManual && _repeatMode.value == RepeatMode.ONE) {
            _repeatMode.value = RepeatMode.ALL
        }
        playNextInQueue(context)
    }

    fun previousSong(context: Context) {
        val queue = if (_isShuffling.value == true) shuffledQueue else _mainQueue.value.orEmpty()
        if (queue.isEmpty()) return

        if (currentQueueIndex > 0) {
            currentQueueIndex--
            playSong(queue[currentQueueIndex].song, context)
        } else if (_repeatMode.value == RepeatMode.ALL && queue.isNotEmpty()) {
            currentQueueIndex = queue.size - 1
            playSong(queue[currentQueueIndex].song, context)
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

        if (isNowShuffling) {
            val shuffled = originalAllSongs.shuffled()
            shuffledQueue = shuffled.map { SongInQueue(it, fromManualQueue = false) }

            currentQueueIndex = shuffledQueue.indexOfFirst { it.song.id == currentSong.id }
            _mainQueue.value = shuffledQueue
        } else {
            val ordered = originalAllSongs.map { SongInQueue(it, fromManualQueue = false) }
            _mainQueue.value = ordered

            currentQueueIndex = ordered.indexOfFirst { it.song.id == currentSong.id }
            shuffledQueue = emptyList()
        }
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
//        PlayerController.release()
    }
}