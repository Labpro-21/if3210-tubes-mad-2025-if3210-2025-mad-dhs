package com.tubes.purry.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.tubes.purry.data.local.LikedSongDao
import com.tubes.purry.data.local.SongDao
import com.tubes.purry.data.model.LikedSong
import com.tubes.purry.data.model.ProfileData
import com.tubes.purry.data.model.Song
import com.tubes.purry.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class NowPlayingViewModel(
    private val likedSongDao: LikedSongDao,
    private val songDao: SongDao,
    private val profileViewModel: ProfileViewModel
) : ViewModel() {

    private val _currSong = MutableLiveData<Song>()
    val currSong: LiveData<Song> = _currSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    fun playSong(song: Song, context: Context) {
        Log.d("NowPlayingViewModel", "playSong called with song: ${song.title}")
        _isPlaying.value = true
        _currSong.value = song // Optimistic update

        viewModelScope.launch {
            val userId = getUserIdBlocking() ?: return@launch
            val isLiked = likedSongDao.isLiked(userId, song.id)
            val songWithLike = song.copy(isLiked = isLiked)

            if (_currSong.value?.id == song.id) {
                _currSong.postValue(songWithLike)
            }

            Log.d("NowPlayingViewModel", "Song data: filePath=${song.filePath}, resId=${song.resId}")

            // Ensure PlayerController.play runs on the main thread
            withContext(Dispatchers.Main) {
                PlayerController.play(songWithLike, context)
            }
        }
    }

    private fun pauseSong() {
        _isPlaying.value = false
        PlayerController.pause()
    }

    private fun resumeSong() {
        _isPlaying.value = true
        PlayerController.resume()
    }

    fun togglePlayPause() {
        if (_isPlaying.value == true) pauseSong() else resumeSong()
    }

    fun toggleLikeStatus() {
        val song = _currSong.value ?: return
        val newLikedStatus = !song.isLiked

        viewModelScope.launch {
            // Ensure the userId is not null before proceeding
            val userId = getUserIdBlocking() ?: return@launch

            val updatedSong = song.copy(isLiked = newLikedStatus)
            songDao.update(updatedSong)

            if (newLikedStatus) {
                likedSongDao.likeSong(LikedSong(userId, song.id))
            } else {
                likedSongDao.unlikeSong(userId, song.id)
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

    override fun onCleared() {
        super.onCleared()
        PlayerController.release()
    }
}