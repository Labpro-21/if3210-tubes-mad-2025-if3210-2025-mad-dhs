package com.tubes.purry.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.data.repository.AuthRepository
import com.tubes.purry.utils.SessionManager

class ProfileViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            val sessionManager = SessionManager(context)
            val authRepository = AuthRepository(ApiClient.apiService, sessionManager)
            return ProfileViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

