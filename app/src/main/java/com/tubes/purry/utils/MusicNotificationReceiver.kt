package com.tubes.purry.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tubes.purry.ui.player.NowPlayingManager

class MusicNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY = "com.tubes.purry.ACTION_PLAY"
        const val ACTION_PAUSE = "com.tubes.purry.ACTION_PAUSE"
        const val ACTION_NEXT = "com.tubes.purry.ACTION_NEXT"
        const val ACTION_PREV = "com.tubes.purry.ACTION_PREV"
        const val ACTION_STOP = "com.tubes.purry.ACTION_STOP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val viewModel = NowPlayingManager.viewModel

        if (viewModel == null) {
            Log.e("MusicNotifReceiver", "ViewModel not initialized")
            return
        }

        Log.d("MusicNotifReceiver", "Received action: $action")

        when (action) {
            ACTION_PLAY, ACTION_PAUSE -> {
                viewModel.togglePlayPause()
                Log.d("MusicNotifReceiver", "Toggle play/pause dipanggil")
            }

            ACTION_NEXT -> {
                viewModel.nextSong(context)
                Log.d("MusicNotifReceiver", "Next dipanggil")
            }

            ACTION_PREV -> {
                viewModel.previousSong(context)
                Log.d("MusicNotifReceiver", "Prev dipanggil")
            }

            ACTION_STOP -> {
                context.stopService(Intent(context, MusicNotificationService::class.java))
                Log.d("MusicNotifReceiver", "Stop service dipanggil")
            }
        }

        // Refresh notifikasi agar tampilannya update
        val serviceIntent = Intent(context, MusicNotificationService::class.java)
        context.startService(serviceIntent)
    }
}
