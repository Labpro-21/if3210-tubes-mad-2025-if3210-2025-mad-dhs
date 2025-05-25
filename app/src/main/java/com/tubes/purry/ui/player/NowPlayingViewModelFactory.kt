package com.tubes.purry.ui.player

import android.content.Context // Import Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.data.local.LikedSongDao
import com.tubes.purry.data.local.SongDao
import com.tubes.purry.ui.profile.ProfileViewModel

class NowPlayingViewModelFactory(
    private val likedSongDao: LikedSongDao,
    private val songDao: SongDao,
    private val profileViewModel: ProfileViewModel,
    private val applicationContext: Context // Added application context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NowPlayingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NowPlayingViewModel(likedSongDao, songDao, profileViewModel, applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

