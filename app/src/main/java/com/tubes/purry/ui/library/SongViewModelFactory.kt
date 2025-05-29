package com.tubes.purry.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.data.repository.SongRepository

class SongViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val repository: SongRepository

    init {
        val db = AppDatabase.getDatabase(context)
        val songDao = db.songDao()
        val likedSongDao = db.likedSongDao()
        repository = SongRepository(songDao, likedSongDao)
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SongViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SongViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
