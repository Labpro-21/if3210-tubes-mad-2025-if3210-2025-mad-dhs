package com.tubes.purry.ui.player

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.local.LikedSongDao
import com.tubes.purry.data.local.SongDao
import com.tubes.purry.data.model.LikedSong
import com.tubes.purry.data.model.ProfileData
import com.tubes.purry.data.repository.AnalyticsRepository
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tubes.purry.data.model.Song
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.tubes.purry.data.model.SongInQueue
import com.tubes.purry.data.remote.ApiClient.apiService
import com.tubes.purry.utils.parseDuration

class NowPlayingViewModel(
    application: Application,
    private val likedSongDao: LikedSongDao,
    private val songDao: SongDao,
    private val profileViewModel: ProfileViewModel
) : AndroidViewModel(application) {

    val profileData: LiveData<ProfileData> get() = profileViewModel.profileData
    private val _currSong = MutableLiveData<Song?>()
    val currSong: LiveData<Song?> = _currSong

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _isLiked = MutableLiveData<Boolean>()
    val isLiked: LiveData<Boolean> = _isLiked

    private val _isShuffling = MutableLiveData<Boolean>(false)
    val isShuffling: LiveData<Boolean> = _isShuffling

    private val _repeatMode = MutableLiveData<RepeatMode>(RepeatMode.NONE)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    // ===== PERBAIKAN DURASI - TAMBAHAN LIVE DATA =====
    private val _currentPosition = MutableLiveData<Int>(0)
    val currentPosition: LiveData<Int> = _currentPosition

    private val _songDuration = MutableLiveData<Int>(0)
    val songDuration: LiveData<Int> = _songDuration

    enum class RepeatMode { NONE, ONE, ALL }

    private val _errorMessage = MutableLiveData<String>()

    private var originalAllSongs: List<Song> = emptyList()
    private var shuffledQueue: List<SongInQueue> = emptyList()
    private var currentQueueIndex = -1
    private val _mainQueue = MutableLiveData<List<SongInQueue>>(emptyList())
    private val _manualQueue = MutableLiveData<MutableList<SongInQueue>?>(mutableListOf())

    // ===== PERBAIKAN DURASI - HANDLER UNTUK UPDATE POSISI =====
    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionRunnable = object : Runnable {
        override fun run() {
            if (PlayerController.isPlaying()) {
                val position = PlayerController.getCurrentPosition()
                val duration = PlayerController.getDuration()

                _currentPosition.value = position
                if (duration > 0 && _songDuration.value != duration) {
                    _songDuration.value = duration
                    // Update song object dengan durasi yang benar
                    _currSong.value?.let { song ->
                        _currSong.value = song.copy(duration = duration)
                    }
                }
            }
            positionHandler.postDelayed(this, 1000) // Update setiap detik
        }
    }

    // ===== ANALYTICS TRACKING VARIABLES =====
    private val analyticsRepository by lazy {
        val database = AppDatabase.getDatabase(getApplication())
        AnalyticsRepository(database.analyticsDao(), database.songDao())
    }

    private var currentSessionId: Long? = null
    private var sessionStartTime: Long = 0
    private var isPaused: Boolean = false
    private var pauseStartTime: Long = 0
    private var totalPauseTime: Long = 0

    // ===== PERBAIKAN DURASI - METHODS UNTUK TRACKING =====
    private fun startPositionTracking() {
        positionHandler.removeCallbacks(positionRunnable)
        positionHandler.post(positionRunnable)
    }

    private fun stopPositionTracking() {
        positionHandler.removeCallbacks(positionRunnable)
    }

//    private fun getCurrentSongInQueue(): SongInQueue? {
//        val currId = _currSong.value?.id ?: return null
//        return _manualQueue.value?.find { it.song.id == currId }
//    }

    // ===== PERBAIKAN DURASI - PLAYSONG METHOD YANG DIPERBAIKI =====
    fun playSong(song: Song, context: Context) {
        // ===== END PREVIOUS ANALYTICS SESSION =====
        endCurrentAnalyticsSession()

        _isPlaying.value = true
        viewModelScope.launch {
            val songWithLike = song.copy(isLiked = false)
            _currSong.postValue(songWithLike)

            withContext(Dispatchers.Main) {
                val success = PlayerController.play(songWithLike, context)
                if (success) {
                    // Set callback untuk durasi siap
                    PlayerController.onDurationReady = { duration ->
                        Log.d("NowPlayingViewModel", "Duration ready: $duration ms")
                        _songDuration.postValue(duration)
                        _currSong.value?.let { currentSong ->
                            _currSong.postValue(currentSong.copy(duration = duration))
                        }
                    }

                    PlayerController.onPrepared = {
                        val duration = PlayerController.getDuration()
                        Log.d("NowPlayingViewModel", "OnPrepared - Duration: $duration ms")

                        if (duration > 0) {
                            _songDuration.postValue(duration)
                            _currSong.value?.let { currentSong ->
                                _currSong.postValue(currentSong.copy(duration = duration))
                            }
                        }

                        _isPlaying.postValue(PlayerController.isPlaying())

                        // Mulai tracking posisi setelah prepared
                        startPositionTracking()

                        // Coba dapatkan durasi lagi setelah delay singkat
                        Handler(Looper.getMainLooper()).postDelayed({
                            val finalDuration = PlayerController.ensureDurationAvailable()
                            if (finalDuration > 0 && finalDuration != _songDuration.value) {
                                _songDuration.postValue(finalDuration)
                                _currSong.value?.let { currentSong ->
                                    _currSong.postValue(currentSong.copy(duration = finalDuration))
                                }
                            }
                        }, 500)
                    }

                    PlayerController.onCompletion = {
                        stopPositionTracking()
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

                    // ===== START ANALYTICS TRACKING =====
                    startAnalyticsTracking(songWithLike, context)

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

    // ===== ANALYTICS TRACKING METHODS =====
    private fun startAnalyticsTracking(song: Song, context: Context) {
        val sessionManager = SessionManager(context)
        val userId = sessionManager.getUserId()

        Log.d("NowPlayingAnalytics", "🎵 STARTING ANALYTICS: ${song.title}")
        Log.d("NowPlayingAnalytics", "User ID: $userId")

        if (userId != null) {
            viewModelScope.launch {
                try {
                    currentSessionId = analyticsRepository.startListeningSession(userId, song)
                    sessionStartTime = System.currentTimeMillis()
                    totalPauseTime = 0
                    isPaused = false

                    Log.d("Analytics", "Started session ${currentSessionId} for song: ${song.title}")
                } catch (e: Exception) {
                    Log.e("Analytics", "Failed to start analytics session: ${e.message}")
                }
            }
        }
    }

    private fun endCurrentAnalyticsSession() {
        Log.d("NowPlayingAnalytics", "🛑 ENDING SESSION: $currentSessionId")
        currentSessionId?.let { sessionId ->
            val currentTime = System.currentTimeMillis()
            val totalDuration = if (isPaused) {
                // If currently paused, add current pause time
                (pauseStartTime - sessionStartTime) - totalPauseTime
            } else {
                (currentTime - sessionStartTime) - totalPauseTime
            }

            // Only count sessions longer than 30 seconds
            if (totalDuration >= 30000) {
                viewModelScope.launch {
                    try {
                        analyticsRepository.endListeningSession(sessionId, totalDuration)
                        Log.d("Analytics", "Ended session $sessionId, duration: ${totalDuration}ms")
                    } catch (e: Exception) {
                        Log.e("Analytics", "Failed to end analytics session: ${e.message}")
                    }
                }
            }

            // Reset tracking variables
            currentSessionId = null
            sessionStartTime = 0
            totalPauseTime = 0
            isPaused = false
        }
    }

    // ===== PERBAIKAN DURASI - PAUSE/RESUME METHODS =====
    private fun pauseSong() {
        PlayerController.pause()
        _isPlaying.value = false
        stopPositionTracking()

        // ===== ANALYTICS: Track pause =====
        if (!isPaused && currentSessionId != null) {
            pauseStartTime = System.currentTimeMillis()
            isPaused = true
        }
    }

    private fun resumeSong() {
        if (!PlayerController.isPlaying()) {
            PlayerController.resume()
            _isPlaying.value = true
            startPositionTracking()

            // ===== ANALYTICS: Track resume =====
            if (isPaused && currentSessionId != null) {
                totalPauseTime += System.currentTimeMillis() - pauseStartTime
                isPaused = false
            }
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

    fun seekTo(positionMs: Int) {
        PlayerController.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            val userId = getUserIdBlocking()
            if (userId == null) {
                Log.e("NowPlayingViewModel", "User not logged in.")
                return@launch
            }

            val db = AppDatabase.getDatabase(getApplication())
            val songExists = db.songDao().getById(song.id) != null
            if (!songExists) {
                db.songDao().insert(song)
            }

            val isLiked = db.LikedSongDao().isSongLiked(userId, song.id)
            val updatedSong = song.copy(isLiked = !isLiked)

            if (!isLiked) {
                db.LikedSongDao().likeSong(LikedSong(userId = userId, songId = song.id))
                _isLiked.postValue(true)
            } else {
                db.LikedSongDao().unlikeSong(userId, song.id)
                _isLiked.postValue(false)
            }

            _currSong.postValue(updatedSong)
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

    // ===== PERBAIKAN DURASI - CLEAR QUEUE DENGAN TRACKING =====
    fun clearQueue() {
        // ===== END ANALYTICS SESSION BEFORE CLEARING =====
        endCurrentAnalyticsSession()

        stopPositionTracking()
        _mainQueue.value = emptyList()
        _manualQueue.value = mutableListOf()
        _currSong.value = null
        _isPlaying.value = false
        _currentPosition.value = 0
        _songDuration.value = 0
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

    // Tambahkan method ini ke NowPlayingViewModel Anda, gantikan method yang ada

    // ===== FIXED SONG FETCHING METHODS =====

    /**
     * Fetch song by UUID/String ID (untuk lagu lokal)
     */
    fun fetchSongByUUID(uuid: String, context: Context) {
        Log.d("NowPlayingVM", "fetchSongByUUID called with: $uuid")
        viewModelScope.launch {
            try {
                val song = songDao.getById(uuid)
                if (song != null) {
                    Log.d("NowPlayingVM", "Local song found: ${song.title}")
                    _currSong.postValue(song)
                    playSong(song, context)

                    val userId = getUserIdBlocking()
                    if (userId != null) {
                        val isLiked = likedSongDao.isSongLiked(userId, uuid)
                        _isLiked.postValue(isLiked)
                    }
                } else {
                    _errorMessage.postValue("Lagu lokal tidak ditemukan.")
                    Log.e("NowPlayingVM", "Lagu lokal tidak ditemukan: $uuid")
                }
            } catch (e: Exception) {
                _errorMessage.postValue("Gagal memuat lagu lokal: ${e.message}")
                Log.e("NowPlayingVM", "Error fetchSongByUUID: ${e.message}", e)
            }
        }
    }

    fun fetchSongById(serverId: Int, context: Context) {
        Log.d("NowPlayingVM", "fetchSongById called with serverId: $serverId")
        viewModelScope.launch {
            try {
                val response = apiService.getSongById(serverId)
                if (response.isSuccessful) {
                    response.body()?.let { raw ->
                        val converted = Song(
                            id = "srv-${raw.id}",
                            serverId = raw.id,
                            title = raw.title,
                            artist = raw.artist,
                            filePath = raw.url,
                            coverPath = raw.artwork,
                            duration = parseDuration(raw.duration),
                            isLocal = false,
                            isLiked = false
                        )
                        Log.d("NowPlayingVM", "Server song converted: ${converted.title} (ID: ${converted.id})")
                        _currSong.postValue(converted)
                        playSong(converted, context)

                        // Check if liked
                        val userId = getUserIdBlocking()
                        if (userId != null) {
                            val isLiked = likedSongDao.isSongLiked(userId, converted.id)
                            _isLiked.postValue(isLiked)
                        }
                    }
                } else {
                    _errorMessage.postValue("Gagal memuat lagu dari server: ${response.code()}")
                    Log.e("NowPlayingVM", "Server error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                _errorMessage.postValue("Error saat mengambil lagu: ${e.message}")
                Log.e("NowPlayingVM", "Error saat fetch lagu dari server: ${e.message}", e)
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

    fun fetchSong(songId: String, isLocal: Boolean, serverId: Int? = null, context: Context) {
        Log.d("NowPlayingVM", "fetchSong called - songId: $songId, isLocal: $isLocal, serverId: $serverId")

        if (isLocal) {
            fetchSongByUUID(songId, context)
        } else {
            // Untuk lagu server, coba extract serverId dari songId jika tidak disediakan
            val serverIdToUse = serverId ?: run {
                if (songId.startsWith("srv-")) {
                    try {
                        songId.removePrefix("srv-").toInt()
                    } catch (e: NumberFormatException) {
                        Log.e("NowPlayingVM", "Cannot extract serverId from songId: $songId", e)
                        null
                    }
                } else {
                    // Mungkin ID langsung berupa angka
                    try {
                        songId.toInt()
                    } catch (e: NumberFormatException) {
                        Log.e("NowPlayingVM", "Cannot parse songId as serverId: $songId", e)
                        null
                    }
                }
            }

            if (serverIdToUse != null) {
                fetchSongById(serverIdToUse, context)
            } else {
                _errorMessage.postValue("Invalid server song ID format: $songId")
                Log.e("NowPlayingVM", "Invalid serverId for song: $songId")
            }
        }
    }

    fun dismissPlayer() {
        pauseSong()
        clearQueue()
        PlayerController.stop()
        PlayerController.release()

        Log.d("NowPlayingViewModel", "Player dismissed and cleared")
    }

    fun formatDurationMs(durationMs: Int): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionTracking()
        // ===== END ANALYTICS SESSION WHEN VIEWMODEL IS CLEARED =====
        endCurrentAnalyticsSession()
    }
}