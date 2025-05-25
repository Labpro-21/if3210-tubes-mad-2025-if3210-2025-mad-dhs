package com.tubes.purry.ui.player

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
import com.tubes.purry.utils.AudioOutputManager

class NowPlayingViewModel(
    private val likedSongDao: LikedSongDao,
    private val songDao: SongDao,
    private val profileViewModel: ProfileViewModel,
    private val applicationContext: Context // Added application context
) : ViewModel() {

    private val _currSong = MutableLiveData<Song?>() // mutable untuk bisa diubah2
    val currSong: LiveData<Song?> = _currSong // untuk read

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _isLiked = MutableLiveData<Boolean>()
    val isLiked: LiveData<Boolean> = _isLiked

    private val _errorMessage = MutableLiveData<String>()
    private var originalAllSongs: List<Song> = emptyList()
    private val _mainQueue = MutableLiveData<List<SongInQueue>>(emptyList())
    private val _manualQueue = MutableLiveData<MutableList<SongInQueue>?>(mutableListOf())

    private var currentQueueIndex = -1

    private val _isShuffling = MutableLiveData<Boolean>(false)
    val isShuffling: LiveData<Boolean> = _isShuffling

    private val _repeatMode = MutableLiveData<RepeatMode>(RepeatMode.NONE)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    // LiveData for active audio output
    private val _activeAudioOutputInfo = MutableLiveData<AudioOutputManager.ActiveOutputInfo>()
    val activeAudioOutputInfo: LiveData<AudioOutputManager.ActiveOutputInfo> = _activeAudioOutputInfo

    enum class RepeatMode { NONE, ONE, ALL }

    init {
        updateActiveAudioOutput() // Initial check
    }

    fun updateActiveAudioOutput() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = AudioOutputManager.getActiveAudioOutputInfo(applicationContext)
            _activeAudioOutputInfo.postValue(info)
        }
    }

    private fun getCurrentSongInQueue(): SongInQueue? {
        val currId = _currSong.value?.id ?: return null
        return _manualQueue.value?.find { it.song.id == currId }
    }

    fun playSong(song: Song, context: Context) {
        _isPlaying.value = true

        viewModelScope.launch {
            val userId = getUserIdBlocking() ?: return@launch
            val isLiked = likedSongDao.isLiked(userId, song.id)
            val songWithLike = song.copy(isLiked = isLiked)

            // This logic ensures that if the same song is "played" again (e.g., after queue manipulation
            // or repeat), its liked status is re-evaluated and the LiveData is updated.
            _currSong.postValue(songWithLike) // Post value before PlayerController.play
            _isLiked.postValue(isLiked)

            Log.d(
                "NowPlayingViewModel",
                "Song data: filePath=${song.filePath}, resId=${song.resId}"
            )

            // Ensure PlayerController.play runs on the main thread
            withContext(Dispatchers.Main) {
                val success = PlayerController.play(songWithLike, context)
                if (success) {
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

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            val userId = profileViewModel.profileData.value?.id
            userId?.let { id ->
                val isLiked = likedSongDao.isSongLiked(id, song.id)
                if (!isLiked) {
                    val likedSong = LikedSong(
                        userId = id,
                        songId = song.id
                    )
                    likedSongDao.likeSong(likedSong)
                    _isLiked.postValue(true) // Update the liked state
                } else {
                    likedSongDao.unlikeSong(id, song.id)
                    _isLiked.postValue(false) // Update the liked state
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