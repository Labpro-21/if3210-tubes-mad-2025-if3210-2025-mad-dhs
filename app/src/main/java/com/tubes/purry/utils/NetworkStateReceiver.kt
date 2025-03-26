package com.tubes.purry.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class NetworkStateReceiver : BroadcastReceiver() {

    private var listeners: MutableList<NetworkStateListener> = mutableListOf()

    interface NetworkStateListener {
        fun onNetworkAvailable()
        fun onNetworkUnavailable()
    }

    fun addListener(listener: NetworkStateListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: NetworkStateListener) {
        listeners.remove(listener)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            val isConnected = capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))

            if (isConnected) {
                listeners.forEach { it.onNetworkAvailable() }
            } else {
                listeners.forEach { it.onNetworkUnavailable() }
            }
        }
    }
}