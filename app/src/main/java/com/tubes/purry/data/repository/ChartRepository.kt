package com.tubes.purry.data.repository

import android.util.Log
import com.tubes.purry.data.model.OnlineSong
import com.tubes.purry.data.remote.ApiClient

class ChartRepository {
    private val apiService = ApiClient.apiService

    suspend fun getTopSongs(isGlobal: Boolean, countryCode: String?): List<OnlineSong> {
        return if (isGlobal) {
            apiService.getTopSongsGlobal()
        } else {
            Log.d("ChartRepository", "Country Code used: $countryCode")
            apiService.getTopSongsByCountry(countryCode ?: "ID")
        }
    }
}
