package com.tubes.purry.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.data.local.LikedSongDao
import com.tubes.purry.data.local.SongDao
import com.tubes.purry.ui.profile.ProfileViewModel

class NowPlayingViewModelFactory(
    private val likedSongDao: LikedSongDao,
    private val songDao: SongDao,
    private val profileViewModel: ProfileViewModel // add this as a parameter
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NowPlayingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NowPlayingViewModel(likedSongDao, songDao, profileViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

