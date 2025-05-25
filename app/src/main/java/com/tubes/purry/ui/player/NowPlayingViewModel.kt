package com.tubes.purry.ui.player

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.tubes.purry.data.local.AppDatabase
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
import com.tubes.purry.data.model.toTemporarySong
import com.tubes.purry.data.remote.ApiClient.apiService
import com.tubes.purry.utils.formatDuration
import com.tubes.purry.utils.parseDuration

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
        viewModelScope.launch {
            val songWithLike = song.copy(isLiked = false)
            _currSong.postValue(songWithLike)
            withContext(Dispatchers.Main) {
                val success = PlayerController.play(songWithLike, context)
                if (success) {
                    PlayerController.onPrepared = {
                        val realDuration = PlayerController.getDuration()
                        _currSong.postValue(_currSong.value?.copy(duration = realDuration))
                        _isPlaying.postValue(PlayerController.isPlaying())
                    }
                    PlayerController.onCompletion = {
                        when (_repeatMode.value) {
                            RepeatMode.ONE -> playSong(_currSong.value!!, context)
                            else -> {
                                removeCurrentFromQueue()
                                playNextInQueue(context)
                            }
                        }
                    }
                    markSongAsRecentlyPlayed(songWithLike)
                    val userId = getUserIdBlocking()
                    if (userId != null) {
                        val isLiked = likedSongDao.isSongLiked(userId, songWithLike.id)
                        _isLiked.postValue(isLiked)
                    }
                } else {
                    _isPlaying.value = false
                    _errorMessage.value = "Gagal memutar lagu. Cek file atau perizinan."
                }
            }
        }
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
            pauseSong()
        } else {
            resumeSong()
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            val userId = profileViewModel.profileData.value?.id
            if (userId == null) {
                Log.e("NowPlayingViewModel", "User not logged in.")
                return@launch
            }

            val db = AppDatabase.getDatabase(getApplication())
            val songExists = db.songDao().getById(song.id) != null

            if (!songExists) {
                db.songDao().insert(song)
            //    Log.e("NowPlayingViewModel", "Cannot like: user or song not found in DB.")
            //    return@launch
            }

            val isLiked = db.LikedSongDao().isSongLiked(userId, song.id)
            if (!isLiked) {
                db.LikedSongDao().likeSong(LikedSong(userId = userId, songId = song.id))
                _isLiked.postValue(true)
            } else {
                db.LikedSongDao().unlikeSong(userId, song.id)
                _isLiked.postValue(false)
            }

//            userId?.let { id ->
//                val isLiked = likedSongDao.isSongLiked(id, song.id)
//                if (!isLiked) {
//                    likedSongDao.likeSong(LikedSong(userId = id, songId = song.id))
//                    _isLiked.postValue(true)
//                } else {
//                    likedSongDao.unlikeSong(id, song.id)
//                    _isLiked.postValue(false)
//                }
//            }
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
            _mainQueue.value = listOf(SongInQueue(song, true))
            currentQueueIndex = 0
            playSong(song, context)
        }
    }

    fun setQueueFromClickedSong(clicked: Song, allSongs: List<Song>, context: Context) {
        originalAllSongs = allSongs
        val newMainQueue = allSongs.map { SongInQueue(it, fromManualQueue = false) }
        _manualQueue.value = mutableListOf()
        _mainQueue.value = newMainQueue
        currentQueueIndex = newMainQueue.indexOfFirst { it.song.id == clicked.id }
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
        val currentInQueue = _currSong.value ?: return
        val updatedManual = _manualQueue.value?.toMutableList() ?: return
        updatedManual.removeIf { it.song.id == currentInQueue.id }
        _manualQueue.value = updatedManual
    }

    private fun playNextInQueue(context: Context) {
        val manualQueue = _manualQueue.value.orEmpty()
        val mainQueue = _mainQueue.value.orEmpty()
        val queue = if (_isShuffling.value == true) shuffledQueue else manualQueue + mainQueue
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
        val manualQueue = _manualQueue.value.orEmpty()
        val mainQueue = _mainQueue.value.orEmpty()
        val queue = if (_isShuffling.value == true) shuffledQueue else manualQueue + mainQueue
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

    fun fetchSongById(id: Int, context: Context) {
        viewModelScope.launch {
            try {
                val response = apiService.getSongById(id)
                if (response.isSuccessful) {
                    response.body()?.let { raw ->
                        val converted = Song(
                            id = "srv-${raw.id}",
                            title = raw.title,
                            artist = raw.artist,
                            filePath = raw.url,
                            coverPath = raw.artwork,
                            duration = parseDuration(raw.duration),
                            isLocal = false,
                            isLiked = false
                        )
                        _currSong.postValue(converted)
                        playSong(converted, context)
                    }
                } else {
                    Log.e("NowPlayingVM", "Gagal load lagu: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("NowPlayingVM", "Error saat fetch lagu: ${e.message}")
            }
        }
    }

    private suspend fun markSongAsRecentlyPlayed(song: Song) {
        val updatedSong = song.copy(lastPlayedAt = System.currentTimeMillis())
        if (songDao.getById(song.id) != null) {
            songDao.update(updatedSong)
        } else {
            songDao.insert(updatedSong)
        }
    }

    fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(minutes, secs)
    }

    override fun onCleared() {
        super.onCleared()
//        PlayerController.release()
    }
}