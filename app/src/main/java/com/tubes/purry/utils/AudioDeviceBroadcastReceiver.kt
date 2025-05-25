package com.tubes.purry.utils

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.tubes.purry.MainActivity
import com.tubes.purry.R
import com.tubes.purry.ui.player.NowPlayingViewModel
import com.tubes.purry.ui.player.NowPlayingViewModelFactory
import com.tubes.purry.data.local.AppDatabase
import com.tubes.purry.ui.profile.ProfileViewModel
import com.tubes.purry.ui.profile.ProfileViewModelFactory


class AudioDeviceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("AudioDeviceReceiver", "Received action: $action")

        // Lazy way to get ViewModel, not ideal for all BroadcastReceiver scenarios
        // but can work if the receiver is tied to an activity's lifecycle for updates.
        // A better approach might involve a shared service or a direct callback to an active activity.
        val mainActivityInstance = MainActivity.activityInstance // Requires MainActivity to expose its instance

        if (mainActivityInstance != null && mainActivityInstance.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            val appContext = mainActivityInstance.applicationContext
            val db = AppDatabase.getDatabase(appContext)
            val likedSongDao = db.LikedSongDao()
            val songDao = db.songDao()

            val profileFactory = ProfileViewModelFactory(appContext)
            val profileViewModel = ViewModelProvider(mainActivityInstance, profileFactory)[ProfileViewModel::class.java]

            val nowPlayingFactory = NowPlayingViewModelFactory(likedSongDao, songDao, profileViewModel, appContext)
            val nowPlayingViewModel = ViewModelProvider(mainActivityInstance, nowPlayingFactory)[NowPlayingViewModel::class.java]


            val previousOutputName = nowPlayingViewModel.activeAudioOutputInfo.value?.name

            nowPlayingViewModel.updateActiveAudioOutput() // This will internally call getActiveAudioOutputInfo

            // Handle specific disconnection for Toast message
            if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                device?.let {
                    var deviceName = context.getString(R.string.audio_output_bluetooth) // Default name
                    var canGetName = false

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            canGetName = true
                        } else {
                            Log.w("AudioDeviceReceiver", "BLUETOOTH_CONNECT permission not granted to get device name on disconnect.")
                        }
                    } else { // For older APIs, permission is typically granted via BLUETOOTH_ADMIN at install time or implicitly
                        canGetName = true
                    }

                    if(canGetName) {
                        try {
                            deviceName = it.name ?: context.getString(R.string.audio_output_bluetooth)
                        } catch (se: SecurityException) {
                            Log.e("AudioDeviceReceiver", "SecurityException getting disconnected device name: ${se.message}")
                            // deviceName remains the default
                        }
                    }

                    if (previousOutputName == deviceName) {
                        // Update ViewModel first to reflect the change quickly
                        nowPlayingViewModel.updateActiveAudioOutput()
                        // Then show Toast with the *new* output info
                        val newOutputInfo = AudioOutputManager.getActiveAudioOutputInfo(context) // Get potentially updated info
                        val toastMessage = context.getString(R.string.audio_output_disconnected_message, newOutputInfo.name)
                        Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
                        Log.d("AudioDeviceReceiver", "Bluetooth device '$deviceName' disconnected. Fallback to ${newOutputInfo.name}")
                    } else {
                        // Disconnected device wasn't the active one, still update the output state
                        nowPlayingViewModel.updateActiveAudioOutput()
                    }
                } ?: run {
                    // Device is null, still update output state as a general precaution
                    nowPlayingViewModel.updateActiveAudioOutput()
                }
            }

        } else {
            Log.w("AudioDeviceReceiver", "MainActivity instance not available or not in a suitable state to update ViewModel.")
        }
    }
}