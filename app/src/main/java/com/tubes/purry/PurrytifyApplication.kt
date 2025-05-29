package com.tubes.purry

import android.app.Application
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.util.Log
import com.tubes.purry.data.remote.ApiClient
import com.tubes.purry.utils.NetworkStateReceiver
import com.tubes.purry.utils.NetworkUtil
import com.tubes.purry.utils.SessionManager

class PurrytifyApplication : Application() {
    lateinit var nowPlayingViewModel: com.tubes.purry.ui.player.NowPlayingViewModel
    private val networkStateReceiver = com.tubes.purry.utils.NetworkStateReceiver()

    override fun onCreate() {
        super.onCreate()
        com.tubes.purry.data.remote.ApiClient.init(this)

        val db = com.tubes.purry.data.local.AppDatabase.getDatabase(this)
        val songDao = db.songDao()
        val likedSongDao = db.likedSongDao()

        val sessionManager = com.tubes.purry.utils.SessionManager(this)

        val authRepository = com.tubes.purry.data.repository.AuthRepository(
            com.tubes.purry.data.remote.ApiClient.apiService,
            sessionManager
        )

        val songRepository = com.tubes.purry.data.repository.SongRepository(
            songDao,
            likedSongDao
        )

        val profileViewModel = com.tubes.purry.ui.profile.ProfileViewModel(
            this,
            songRepository,
            authRepository
        )

        // FIXED: Load profile data when app starts if user is logged in
        val authToken = sessionManager.fetchAuthToken()
        val userId = sessionManager.getUserId()
        if (!authToken.isNullOrEmpty() && userId != null) {
            Log.d("PurrytifyApplication", "User is logged in (userId: $userId), loading profile")
            profileViewModel.getProfileData()
        } else {
            Log.d("PurrytifyApplication", "User not logged in - token: ${authToken != null}, userId: $userId")
        }

        nowPlayingViewModel = com.tubes.purry.ui.player.NowPlayingViewModel(
            this,
            likedSongDao,
            songDao,
            profileViewModel
        )

        // Warning CONNECTIVITY_ACTION boleh diabaikan atau diganti ke API level 24+
        com.tubes.purry.utils.NetworkUtil.startNetworkCallback(this)
        registerReceiver(
            networkStateReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterReceiver(networkStateReceiver)
    }
}