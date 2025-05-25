package com.tubes.purry

import android.app.Application
import android.content.IntentFilter
import android.net.ConnectivityManager
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
        val likedSongDao = db.LikedSongDao()

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
