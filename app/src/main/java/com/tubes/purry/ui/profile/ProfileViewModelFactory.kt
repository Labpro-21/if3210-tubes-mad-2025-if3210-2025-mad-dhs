package com.tubes.purry.ui.profile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.data.repository.ProfileRepository
import com.tubes.purry.data.repository.SongRepository

class ProfileViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val songRepository: SongRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(profileRepository, songRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}