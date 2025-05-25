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

    private val _currSong = MutableLiveData<Song?>()
    val currSong: LiveData<Song?> = _currSong

    private val _isPlaying = MutableLiveData<Boolean>(false) // Default to false
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
        // Set up PlayerController callbacks
        PlayerController.onCompletion = {
            Log.d("NowPlayingViewModel", "PlayerController.onCompletion triggered. RepeatMode: ${_repeatMode.value}")
            updateActiveAudioOutput()
            // Ensure this runs on the main thread as it might update UI indirectly
            viewModelScope.launch(Dispatchers.Main) {
                val currentContext = PlayerController.appContext // Get context from PlayerController
                if (currentContext == null) {
                    Log.e("NowPlayingViewModel", "Context is null in onCompletion, cannot proceed.")
                    return@launch
                }
                when (_repeatMode.value) {
                    RepeatMode.ONE -> {
                        _currSong.value?.let { currentSong ->
                            Log.d("NowPlayingViewModel", "Repeat.ONE: Replaying ${currentSong.title}")
                            // We need context here. Consider how to get it reliably.
                            // For now, assuming PlayerController holds an application context.
                            playSong(currentSong, currentContext, isReplay = true)
                        }
                    }
                    else -> {
                        Log.d("NowPlayingViewModel", "Repeat.NONE or Repeat.ALL: Playing next.")
                        removeCurrentFromQueue() // Only if it was manually added and should be one-shot
                        playNextInQueue(currentContext)
                    }
                }
            }
        }

        PlayerController.onNextSong = {
            viewModelScope.launch(Dispatchers.Main) {
                val currentContext = PlayerController.appContext
                if (currentContext != null) {
                    Log.d("NowPlayingViewModel", "PlayerController.onNextSong triggered by notification.")
                    nextSong(currentContext, fromUserAction = false) // fromUserAction = false if by notification
                } else {
                    Log.e("NowPlayingViewModel", "Context null for onNextSong")
                }
            }
        }

        PlayerController.onPreviousSong = {
            viewModelScope.launch(Dispatchers.Main) {
                val currentContext = PlayerController.appContext
                if (currentContext != null) {
                    Log.d("NowPlayingViewModel", "PlayerController.onPreviousSong triggered by notification.")
                    previousSong(currentContext)
                } else {
                    Log.e("NowPlayingViewModel", "Context null for onPreviousSong")
                }
            }
        }
        // Update PlayerController's static repeat mode reference
        _repeatMode.observeForever { mode ->
            PlayerController.PlayerSessionCallback.currentRepeatMode = mode ?: RepeatMode.NONE
        }
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

    fun playSong(song: Song, context: Context, isReplay: Boolean = false) {
        _isPlaying.value = true // Optimistically set to true

        viewModelScope.launch {
            val userId = getUserIdBlocking() // Ensure we have a user ID
            if (userId == null) {
                _errorMessage.postValue("User not identified. Cannot play song.")
                _isPlaying.postValue(false)
                return@launch
            }
            val isSongLiked = likedSongDao.isLiked(userId, song.id)
            // Update internal _isLiked LiveData if the song changes
            if (_currSong.value?.id != song.id || isReplay) { // only update if song changes or it's a replay
                _isLiked.postValue(isSongLiked)
            }

            val songWithLikeStatus = song.copy(isLiked = isSongLiked)
            _currSong.postValue(songWithLikeStatus) // Update current song immediately for UI


            Log.d("NowPlayingViewModel", "Requesting PlayerController to play: ${song.title}")
            val success = PlayerController.play(songWithLikeStatus, context.applicationContext)

            // Post the actual state from PlayerController
            // Use postValue if still in background thread, or setValue if ensured on Main
            // Since PlayerController.play might be async internally for preparation,
            // it's safer to rely on its state or have it callback.
            // For now, we assume PlayerController.play will set its internal state
            // and the notification will reflect that.
            // The isPlaying LiveData could be updated via a callback from PlayerController
            // or by observing PlayerController's state if it exposed LiveData.

            // A slight delay to ensure PlayerController has time to update its internal state
            withContext(Dispatchers.Main) { // Ensure this check is on main thread
                Handler(Looper.getMainLooper()).postDelayed({
                    _isPlaying.value = PlayerController.isPlaying() // More reliable
                    if (!PlayerController.isPlaying() && !isReplay) { // if it didn't start and not a replay
                        _errorMessage.value = "Failed to play ${song.title}."
                    }
                }, 100) // 100ms delay, adjust as needed
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
            Log.w("NowPlayingViewModel", "Resume called but no current song in PlayerController.")
            // Optionally, try to play the first song in the queue if available
            val context = PlayerController.appContext
            if (context != null) {
                playNextInQueue(context, playEvenIfEmpty = true) // Attempt to play if queue has items
            }
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
            val userId = profileViewModel.profileData.value?.id ?: getUserIdBlocking()
            userId?.let { id ->
                val currentLikedState = likedSongDao.isSongLiked(id, song.id)
                if (!currentLikedState) {
                    likedSongDao.likeSong(LikedSong(userId = id, songId = song.id))
                    _isLiked.postValue(true)
                    updateSongInDbAndPlayer(song.id, true)
                } else {
                    likedSongDao.unlikeSong(id, song.id)
                    _isLiked.postValue(false)
                    updateSongInDbAndPlayer(song.id, false)
                }
            }
        }
    }

    private suspend fun updateSongInDbAndPlayer(songId: String, isLiked: Boolean) {
        val songToUpdate = _currSong.value?.takeIf { it.id == songId }
            ?: originalAllSongs.find { it.id == songId }
            ?: _mainQueue.value?.find { it.song.id == songId }?.song
            ?: _manualQueue.value?.find { it.song.id == songId }?.song

        songToUpdate?.let {
            val updatedSong = it.copy(isLiked = isLiked)
            songDao.update(updatedSong) // Update in DB
            if (_currSong.value?.id == songId) {
                _currSong.postValue(updatedSong) // Update current song in ViewModel
                // If PlayerController's current song needs update (e.g., for notification re-render with like status)
                if (PlayerController.currentlyPlaying?.id == songId) {
                    PlayerController.currentlyPlaying = updatedSong
                    // Force update notification if visual changes due to like (not typical for media notifications)
                    // PlayerController.showOrUpdateNotification(updatedSong, PlayerController.isPlaying())
                }
            }
        }
    }


    private suspend fun getUserIdBlocking(): Int? {
        // Simplified: assumes profileViewModel.profileData is populated or will be soon.
        // In a real app, you might need more robust handling if it can be null for long.
        if (profileViewModel.profileData.value != null) {
            return profileViewModel.profileData.value!!.id
        }
        // Fallback if the above is null, try to wait for it.
        return suspendCoroutine { continuation ->
            val observer = object : Observer<ProfileData?> {
                override fun onChanged(value: ProfileData?) {
                    if (value != null) {
                        profileViewModel.profileData.removeObserver(this) // Clean up observer
                        continuation.resume(value.id)
                    }
                }
            }
            // Observe on the main thread
            Handler(Looper.getMainLooper()).post {
                profileViewModel.profileData.observeForever(observer)
            }
            // If it's already available (e.g. race condition)
            if (profileViewModel.profileData.value != null) {
                profileViewModel.profileData.removeObserver(observer)
                continuation.resume(profileViewModel.profileData.value!!.id)
            }
        }
    }

    fun addToQueue(song: Song, context: Context) {
        val songInQueue = SongInQueue(song, fromManualQueue = true)
        val updatedManualQueue = _manualQueue.value ?: mutableListOf()
        updatedManualQueue.add(songInQueue)
        _manualQueue.postValue(updatedManualQueue) // Use postValue for thread safety

        if (PlayerController.currentlyPlaying == null) {
            Log.d("NowPlayingViewModel", "Queue was empty, playing added song: ${song.title}")
            playSong(song, context.applicationContext)
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

    private fun playNextInQueue(context: Context, forceAdvance: Boolean = false, playEvenIfEmpty: Boolean = false) {
        Log.d("NowPlayingViewModel", "playNextInQueue called. forceAdvance: $forceAdvance. Current song: ${PlayerController.currentlyPlaying?.title}")
        val manual = _manualQueue.value ?: mutableListOf()
        if (manual.isNotEmpty()) {
            val nextManualSongInQueue = manual.removeAt(0)
            _manualQueue.postValue(manual) // Update LiveData
            Log.d("NowPlayingViewModel", "Playing next from manual queue: ${nextManualSongInQueue.song.title}")
            playSong(nextManualSongInQueue.song, context)
            return
        }

        val main = _mainQueue.value.orEmpty()
        if (main.isEmpty()) {
            Log.d("NowPlayingViewModel", "Main queue is empty.")
            if (playEvenIfEmpty) { // e.g. after resume, if queue was populated but nothing was 'current'
                //This case might be redundant if PlayerController.currentlyPlaying implies a song is in queue
            } else {
                PlayerController.release() // Stop player, dismiss notification
                _currSong.postValue(null)
                _isPlaying.postValue(false)
            }
            return
        }

        if (_isShuffling.value == true && !forceAdvance) { // Shuffle usually means pick any random, not strictly "next" in original order
            var nextIndex = (0 until main.size).random()
            // Avoid playing the same song immediately unless it's the only one
            if (main.size > 1 && main[nextIndex].song.id == PlayerController.currentlyPlaying?.id) {
                nextIndex = (nextIndex + 1) % main.size
            }
            currentQueueIndex = nextIndex
            Log.d("NowPlayingViewModel", "Shuffling: Playing song at new index $currentQueueIndex: ${main[currentQueueIndex].song.title}")
        } else { // Not shuffling or shuffle is overridden (e.g. user pressed next)
            currentQueueIndex++
            Log.d("NowPlayingViewModel", "Advancing queue. New currentQueueIndex before check: $currentQueueIndex. Main queue size: ${main.size}")
        }

        if (currentQueueIndex >= main.size) { // Reached end of queue
            if (_repeatMode.value == RepeatMode.ALL) {
                currentQueueIndex = 0
                Log.d("NowPlayingViewModel", "End of queue, Repeat.ALL: Looping to start. Index: $currentQueueIndex")
            } else {
                Log.d("NowPlayingViewModel", "End of queue, No Repeat or Repeat.ONE (handled by onCompletion): Stopping.")
                PlayerController.release() // Stop player, dismiss notification
                _currSong.postValue(null)
                _isPlaying.postValue(false)
                currentQueueIndex = -1 // Reset index
                return
            }
        }

        if (currentQueueIndex in main.indices) {
            val nextSongToPlay = main[currentQueueIndex].song
            Log.d("NowPlayingViewModel", "Playing next from main queue (Index $currentQueueIndex): ${nextSongToPlay.title}")
            playSong(nextSongToPlay, context)
        } else {
            Log.w("NowPlayingViewModel", "playNextInQueue: currentQueueIndex $currentQueueIndex is out of bounds for main queue size ${main.size}. Stopping.")
            PlayerController.release()
            _currSong.postValue(null)
            _isPlaying.postValue(false)
            currentQueueIndex = -1
        }
    }

    fun nextSong(context: Context, fromUserAction: Boolean = true) {
        Log.d("NowPlayingViewModel", "nextSong called. fromUserAction: $fromUserAction, RepeatMode: ${_repeatMode.value}")
        if (fromUserAction && _repeatMode.value == RepeatMode.ONE) {
            // If user explicitly hits next, disable repeat ONE for this action
            // and behave as if repeat ALL or NONE.
            playNextInQueue(context, forceAdvance = true)
        } else {
            playNextInQueue(context)
        }
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

    fun stopPlaybackAndClearNotification() {
        PlayerController.release() // Stops player and dismisses notification
        _currSong.postValue(null)
        _isPlaying.postValue(false)
        clearQueue()
        Log.d("NowPlayingViewModel", "Playback stopped and notification cleared explicitly.")
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