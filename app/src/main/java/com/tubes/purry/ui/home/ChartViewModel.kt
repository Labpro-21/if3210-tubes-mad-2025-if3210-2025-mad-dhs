package com.tubes.purry.ui.chart

import androidx.lifecycle.*
import com.tubes.purry.data.model.OnlineSong
import com.tubes.purry.data.repository.ChartRepository
import kotlinx.coroutines.launch

class ChartViewModel(private val repository: ChartRepository) : ViewModel() {
    private val _songs = MutableLiveData<List<OnlineSong>>()
    val songs: LiveData<List<OnlineSong>> = _songs

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun fetchSongs(isGlobal: Boolean, countryCode: String?) {
        viewModelScope.launch {
            try {
                val result = repository.getTopSongs(isGlobal, countryCode)
                _songs.value = result
            } catch (e: Exception) {
                _error.value = "Gagal memuat lagu: ${e.localizedMessage}"
            }
        }
    }
}

class ChartViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChartViewModel(ChartRepository()) as T
    }
}
