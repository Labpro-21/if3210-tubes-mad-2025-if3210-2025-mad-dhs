package com.tubes.purry.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.model.Song
import com.tubes.purry.data.repository.RecommendationRepository
import kotlinx.coroutines.launch

class RecommendationViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = RecommendationRepository(
        database.songDao(),
        database.LikedSongDao()
    )

    fun getDailyPlaylist(userId: Int): LiveData<List<Song>> {
        return repository.getDailyPlaylist(userId).asLiveData()
    }

    fun getTopMixes(userId: Int): LiveData<List<Song>> {
        return repository.getTopMixes(userId).asLiveData()
    }

    suspend fun hasEnoughDataForRecommendations(userId: Int): Boolean {
        return repository.hasEnoughDataForRecommendations(userId)
    }
}