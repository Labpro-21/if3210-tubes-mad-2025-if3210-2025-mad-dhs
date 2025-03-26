package com.tubes.purry

import android.app.Application
import android.content.IntentFilter
import android.net.ConnectivityManager
import com.tubes.purry.utils.NetworkStateReceiver
import com.tubes.purry.utils.NetworkUtil

class PurrytifyApplication : Application() {

    private val networkStateReceiver = NetworkStateReceiver()

    override fun onCreate() {
        super.onCreate()

        // Start network callback for LiveData approach
        NetworkUtil.startNetworkCallback(this)

        // Register broadcast receiver approach
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkStateReceiver, filter)
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterReceiver(networkStateReceiver)
    }
}