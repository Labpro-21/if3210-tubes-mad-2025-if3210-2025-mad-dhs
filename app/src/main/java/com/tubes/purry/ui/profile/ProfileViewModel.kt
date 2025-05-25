package com.tubes.purry.ui.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubes.purry.data.model.ProfileData
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.data.repository.AuthRepository
import com.tubes.purry.data.repository.ProfileRepository
import com.tubes.purry.data.repository.SongRepository
import kotlinx.coroutines.launch
import java.io.IOException
import androidx.lifecycle.liveData
import com.tubes.purry.data.local.AppDatabase
import kotlinx.coroutines.delay
import com.tubes.purry.data.repository.AnalyticsRepository
import com.tubes.purry.data.model.MonthlyAnalytics
import java.text.SimpleDateFormat
import java.util.*

class ProfileViewModel(
    application: Application,
    private val songRepository: SongRepository,
    authRepository: AuthRepository
) : AndroidViewModel(application) {
    private val repository = ProfileRepository(ApiClient.apiService, authRepository)

    private val analyticsRepository by lazy {
        val database = AppDatabase.getDatabase(getApplication())
        AnalyticsRepository(database.analyticsDao(), database.songDao())
    }

    private val _profileData = MutableLiveData<ProfileData>()
    val profileData: LiveData<ProfileData> = _profileData

    private val _songStats = MutableLiveData<SongStats>()
    val songStats: LiveData<SongStats> = _songStats

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    fun setProfileData(profile: ProfileData) {
        _profileData.value = profile
        fetchSongStats(profile.id)
    }

    fun getCurrentMonthAnalytics(userId: Int): LiveData<MonthlyAnalytics> {
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        return liveData {
            while (true) {
                try {
                    val analytics = analyticsRepository.getMonthlyAnalytics(userId, currentMonth)
                    emit(analytics)
                    delay(30000) // Update every 30 seconds
                } catch (e: Exception) {
                    Log.e("ProfileViewModel", "Failed to get analytics: ${e.message}")
                    // Emit empty analytics on error
                    emit(MonthlyAnalytics(
                        month = currentMonth,
                        totalMinutesListened = 0L,
                        dailyAverage = 0,
                        topArtist = null,
                        topArtistPlayCount = 0,
                        topArtistCover = null,
                        topSong = null,
                        topSongArtist = null,
                        topSongPlayCount = 0,
                        topSongCover = null,
                        totalSongsPlayed = 0,
                        totalArtistsListened = 0
                    ))
                    delay(30000)
                }
            }
        }
    }

    fun getProfileData() {
        _loading.value = true
        viewModelScope.launch {
            try {
                val response = repository.getProfile()
                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    Log.d("ProfileViewModel", "Profile data retrieved successfully")
                    _profileData.value = profile
                    fetchSongStats(profile.id)
                } else {
                    Log.e("ProfileViewModel", "Failed to fetch profile: ${response.code()} - ${response.message()}")
                    _errorMessage.value = "Failed to fetch profile: ${response.message()}"
                }
            } catch (e: IOException) {
                Log.e("ProfileViewModel", "Network error: ${e.message}", e)
                _errorMessage.value = "Network error: ${e.message}"
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error fetching profile: ${e.message}", e)
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun fetchSongStats(userId: Int) {
        viewModelScope.launch {
            try {
                songRepository.getTotalSongCount().collect { totalCount ->
                    songRepository.getLikedCountByUser(userId).collect { likedCount ->
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