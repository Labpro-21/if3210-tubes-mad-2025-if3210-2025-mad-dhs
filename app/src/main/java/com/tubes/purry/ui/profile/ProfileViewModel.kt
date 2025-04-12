package com.tubes.purry.ui.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubes.purry.data.model.ProfileData
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.data.repository.ProfileRepository
import com.tubes.purry.data.repository.SongRepository
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _profileData = MutableLiveData<ProfileData>()
    val profileData: LiveData<ProfileData> = _profileData

    private val _songStats = MutableLiveData<SongStats>()
    val songStats: LiveData<SongStats> = _songStats

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    init {
        fetchSongStats()
    }

    fun getProfileData(token: String) {
        _loading.value = true
        viewModelScope.launch {
            try {
                val response = profileRepository.getProfile(token)
                if (response.isSuccessful && response.body() != null) {
                    _profileData.value = response.body()!!
                } else {
                    _errorMessage.value = "Failed to fetch profile: ${response.message()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun fetchSongStats() {
        viewModelScope.launch {
            try {
                songRepository.getTotalSongCount().collect { totalCount ->
                    songRepository.getLikedSongsCount().collect { likedCount ->
                        songRepository.getListenedSongsCount().collect { listenedCount ->
                            _songStats.value = SongStats(
                                totalCount = totalCount,
                                likedCount = likedCount,
                                listenedCount = listenedCount
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error fetching song stats: ${e.message}"
            }
        }
    }

    data class SongStats(
        val totalCount: Int = 0,
        val likedCount: Int = 0,
        val listenedCount: Int = 0
    )
}