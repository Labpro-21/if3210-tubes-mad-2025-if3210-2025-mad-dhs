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
import com.tubes.purry.data.model.Song
import com.tubes.purry.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.tubes.purry.data.model.SongInQueue

class NowPlayingViewModel(
    private val likedSongDao: LikedSongDao,
    private val songDao: SongDao,
    private val profileViewModel: ProfileViewModel
) : ViewModel() {

    private val _currSong = MutableLiveData<Song?>()
    val currSong: LiveData<Song?> = _currSong

    private val _isPlaying = MutableLiveData<Boolean>(false) // Default to false
    val isPlaying: LiveData<Boolean> = _isPlaying

    // ... (other LiveData and properties) ...
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
    enum class RepeatMode { NONE, ONE, ALL }


    init {
        // Set up PlayerController callbacks
        PlayerController.onCompletion = {
            Log.d("NowPlayingViewModel", "PlayerController.onCompletion triggered. RepeatMode: ${_repeatMode.value}")
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
        PlayerController.pause()
        _isPlaying.value = false
    }

    private fun resumeSong() {
        if (PlayerController.currentlyPlaying != null) {
            PlayerController.resume()
            _isPlaying.value = true
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


    // ... (getUserIdBlocking, addToQueue, setQueueFromClickedSong, removeFromQueue as before)
    // Make sure they use context.applicationContext when calling PlayerController methods

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
                    // Optional: Add a timeout or handle case where it never becomes non-null
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

    fun setQueueFromClickedSong(clickedSong: Song, allSongsFromSource: List<Song>, context: Context) {
        Log.d("NowPlayingViewModel", "Setting queue from clicked song: ${clickedSong.title}")
        originalAllSongs = allSongsFromSource.distinctBy { it.id } // Ensure unique songs

        val newMainQueue = mutableListOf<SongInQueue>()
        newMainQueue.add(SongInQueue(clickedSong, fromManualQueue = false)) // Clicked song first

        // Add rest of the songs, ensuring clicked song isn't duplicated if it was part of originalAllSongs
        newMainQueue.addAll(
            originalAllSongs
                .filter { it.id != clickedSong.id }
                .map { SongInQueue(it, fromManualQueue = false) }
        )

        _mainQueue.postValue(newMainQueue)
        _manualQueue.postValue(mutableListOf()) // Clear manual queue

        currentQueueIndex = 0 // Clicked song is at index 0
        playSong(clickedSong, context.applicationContext)
    }


    fun removeFromQueue(deletedSongId: String, context: Context) {
        val wasCurrentPlaying = PlayerController.currentlyPlaying?.id == deletedSongId

        var foundAndRemoved = false

        // Try removing from manual queue first
        _manualQueue.value?.let { currentManualQueue ->
            val newManualQueue = currentManualQueue.filterNot { it.song.id == deletedSongId }.toMutableList()
            if (newManualQueue.size < currentManualQueue.size) {
                _manualQueue.postValue(newManualQueue)
                foundAndRemoved = true
            }
        }

        // If not in manual or still need to check main
        if (!foundAndRemoved) {
            _mainQueue.value?.let { currentMainQueue ->
                val itemIndexInMain = currentMainQueue.indexOfFirst { it.song.id == deletedSongId }
                if (itemIndexInMain != -1) {
                    val newMainQueue = currentMainQueue.filterNot { it.song.id == deletedSongId }
                    _mainQueue.postValue(newMainQueue)

                    if (itemIndexInMain < currentQueueIndex) {
                        currentQueueIndex--
                    } else if (itemIndexInMain == currentQueueIndex && !wasCurrentPlaying) {
                        // If it was the current index but NOT the currently playing song
                        // (e.g. queue was modified externally), adjust index.
                        // This case is less likely if PlayerController.currentlyPlaying is source of truth
                        currentQueueIndex = if (newMainQueue.isEmpty()) -1 else currentQueueIndex.coerceAtMost(newMainQueue.size -1)

                    }
                    foundAndRemoved = true
                }
            }
        }

        originalAllSongs = originalAllSongs.filterNot { it.id == deletedSongId }


        if (wasCurrentPlaying) {
            Log.d("NowPlayingViewModel", "Removed currently playing song: $deletedSongId. Playing next.")
            PlayerController.releaseMediaPlayer() // Stop current playback
            playNextInQueue(context.applicationContext, playEvenIfEmpty = false) // playEvenIfEmpty might need adjustment
        } else if (foundAndRemoved) {
            Log.d("NowPlayingViewModel", "Removed song $deletedSongId from queue.")
        }
    }


    private fun removeCurrentFromQueue() {
        val currentSongObject = PlayerController.currentlyPlaying ?: return
        // This function is mainly for when a manually queued song finishes and shouldn't repeat with the main queue.
        _manualQueue.value?.let { manual ->
            val isManual = manual.any {it.song.id == currentSongObject.id && it.fromManualQueue }
            if(isManual) {
                val updatedManualQueue = manual.filterNot { it.song.id == currentSongObject.id }.toMutableList()
                _manualQueue.postValue(updatedManualQueue)
                Log.d("NowPlayingViewModel", "Removed ${currentSongObject.title} from manual queue after completion.")
            }
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



    fun previousSong(context: Context) {
        val main = _mainQueue.value.orEmpty()
        if (main.isEmpty()) {
            Log.d("NowPlayingViewModel", "PreviousSong: Main queue empty.")
            return
        }

        // If shuffling, "previous" might mean a new random song or actual previous,
        // For simplicity, let's make it play a new random if shuffling.
        if (_isShuffling.value == true) {
            var prevIndex = (0 until main.size).random()
            if (main.size > 1 && main[prevIndex].song.id == PlayerController.currentlyPlaying?.id) {
                prevIndex = (prevIndex - 1 + main.size) % main.size // try one before
            }
            currentQueueIndex = prevIndex
            Log.d("NowPlayingViewModel", "Shuffling: Playing previous (random) at index $currentQueueIndex: ${main[currentQueueIndex].song.title}")
        } else {
            currentQueueIndex--
        }

        if (currentQueueIndex < 0) {
            if (_repeatMode.value == RepeatMode.ALL && main.isNotEmpty()) {
                currentQueueIndex = main.size - 1 // Loop to end
                Log.d("NowPlayingViewModel", "Start of queue, Repeat.ALL: Looping to end. Index: $currentQueueIndex")
            } else {
                Log.d("NowPlayingViewModel", "Start of queue, No Repeat: Resetting to first song or stopping if already there.")
                // Behavior: if at start and press prev, either restart current or do nothing.
                // For now, let's restart current song if possible, or just stay.
                // Or, set index to 0 and let playSong handle it (might restart or continue if already playing)
                currentQueueIndex = 0
                if (PlayerController.currentlyPlaying != null && PlayerController.currentlyPlaying!!.id == main[currentQueueIndex].song.id) {
                    PlayerController.seekTo(0) // Restart current song
                    _isPlaying.value = true // Assuming seekTo doesn't change this, but play might
                    return
                }
                // If not the same song, or nothing was playing, proceed to play main[0]
            }
        }

        if (currentQueueIndex in main.indices) {
            val prevSongToPlay = main[currentQueueIndex].song
            Log.d("NowPlayingViewModel", "Playing previous from main queue (Index $currentQueueIndex): ${prevSongToPlay.title}")
            playSong(prevSongToPlay, context)
        } else {
            Log.w("NowPlayingViewModel", "previousSong: currentQueueIndex $currentQueueIndex is out of bounds. current song: ${PlayerController.currentlyPlaying?.title}")
            // If it became -1 and no repeat, effectively means "do nothing" or stop.
            // Let's try to play the first song if queue is not empty
            if(main.isNotEmpty()){
                currentQueueIndex = 0
                playSong(main[currentQueueIndex].song, context)
            } else {
                PlayerController.release()
                _currSong.postValue(null)
                _isPlaying.postValue(false)
            }
        }
    }

    fun clearQueue() {
        _mainQueue.postValue(emptyList())
        _manualQueue.postValue(mutableListOf())
        // _currSong.postValue(null) // Don't nullify current song if player is still active with it
        // _isPlaying.postValue(false)
        currentQueueIndex = -1
        originalAllSongs = emptyList()
        Log.d("NowPlayingViewModel", "Queues cleared.")
        // PlayerController.release() // This will stop music and notification
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
        _isShuffling.postValue(isNowShuffling)
        Log.d("NowPlayingViewModel", "Shuffle toggled. IsShuffling: $isNowShuffling")

        PlayerController.currentlyPlaying?.let { currentActualSong ->
            val currentSongInQueue = SongInQueue(currentActualSong, fromManualQueue = false) // Assume it's from main for reordering
            val newMainQueue = mutableListOf<SongInQueue>()

            if (isNowShuffling) {
                // Keep current song, shuffle the rest of originalAllSongs
                newMainQueue.add(currentSongInQueue)
                val shuffledRest = originalAllSongs.filter { it.id != currentActualSong.id }.shuffled()
                newMainQueue.addAll(shuffledRest.map { SongInQueue(it, fromManualQueue = false) })
            } else {
                // Revert to original order, current song first
                newMainQueue.add(currentSongInQueue)
                val orderedRest = originalAllSongs.filter { it.id != currentActualSong.id }
                newMainQueue.addAll(orderedRest.map { SongInQueue(it, fromManualQueue = false) })
            }
            _mainQueue.postValue(newMainQueue)
            currentQueueIndex = 0 // Current song is now at the start of the reordered main queue
            Log.d("NowPlayingViewModel", "Queue reordered. New main queue size: ${newMainQueue.size}. Current index: $currentQueueIndex")
        } ?: Log.d("NowPlayingViewModel", "ToggleShuffle: No current song playing, queue not reordered.")
    }


    fun toggleRepeat() {
        val newMode = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE, null -> RepeatMode.NONE
        }
        _repeatMode.postValue(newMode)
        PlayerController.PlayerSessionCallback.currentRepeatMode = newMode // Update static ref
        Log.d("NowPlayingViewModel", "Repeat mode toggled to: $newMode")
    }


    override fun onCleared() {
        super.onCleared()
        // PlayerController.fullyReleaseSession() // Release session when ViewModel is cleared
        // Or, if playback should continue beyond ViewModel scope (e.g. via a Service),
        // then this release should happen elsewhere (e.g. when Service is destroyed).
        // For now, if your app structure relies on this ViewModel for playback, releasing here is okay.
        // However, for true background play independent of UI lifecycle, a Service is better.
        Log.d("NowPlayingViewModel", "onCleared called.")
    }
}