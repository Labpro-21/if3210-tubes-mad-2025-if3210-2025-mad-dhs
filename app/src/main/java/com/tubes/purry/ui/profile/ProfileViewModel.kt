package com.tubes.purry.ui.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubes.purry.data.model.ProfileData
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.data.repository.ProfileRepository
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val repository = ProfileRepository(ApiClient.apiService)

    private val _profileData = MutableLiveData<ProfileData>()
    val profileData: LiveData<ProfileData> = _profileData

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    fun getProfileData(token: String) {
        _loading.value = true
        viewModelScope.launch {
            try {
                Log.d("ProfileViewModel", "Making profile API request")
                val response = repository.getProfile(token)
                if (response.isSuccessful && response.body() != null) {
                    Log.d("ProfileViewModel", "Profile data retrieved successfully")
                    _profileData.value = response.body()!!
                } else {
                    Log.e("ProfileViewModel", "Failed to fetch profile: ${response.code()} - ${response.message()}")
                    _errorMessage.value = "Failed to fetch profile: ${response.message()}"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error fetching profile: ${e.message}", e)
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }
}