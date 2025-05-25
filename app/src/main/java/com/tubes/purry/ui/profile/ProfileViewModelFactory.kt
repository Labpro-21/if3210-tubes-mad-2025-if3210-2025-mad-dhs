package com.tubes.purry.ui.profile

import android.app.Application
import android.content.Context
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.repository.SongRepository
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.data.repository.AuthRepository
import com.tubes.purry.utils.SessionManager

class ProfileViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            val sessionManager = SessionManager(application)
            val db = AppDatabase.getDatabase(application)
            val songRepository = SongRepository(db.songDao(), db.LikedSongDao())
            val authRepository = AuthRepository(ApiClient.apiService, sessionManager)

            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(application, songRepository, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
