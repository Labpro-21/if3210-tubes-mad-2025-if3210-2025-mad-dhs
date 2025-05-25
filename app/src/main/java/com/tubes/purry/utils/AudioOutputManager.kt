package com.tubes.purry.utils

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.tubes.purry.R
import com.tubes.purry.data.model.AudioDevice as AppAudioDevice // Alias to avoid name clash

object AudioOutputManager {
    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    private fun isBluetoothDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            Log.w("AudioOutputManager", "Failed to check isConnected status via reflection", e)
            false
        }
    }

    fun getAvailableAudioDevices(context: Context): List<AppAudioDevice> {
        val devices = mutableListOf<AppAudioDevice>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w("AudioOutputManager", "BLUETOOTH_CONNECT permission not granted for getAvailableAudioDevices.")
            // Consider requesting permission or returning an empty list with a message
            return devices
        }

        val bluetoothAdapter = getBluetoothAdapter(context)
        if (bluetoothAdapter?.isEnabled == true) {
            try {
                val pairedDevices = bluetoothAdapter.bondedDevices
                pairedDevices?.forEach { device ->
                    // Check if device supports A2DP (Audio Sink)
                    if (device.bluetoothClass != null &&
                        (device.bluetoothClass.hasService(android.bluetooth.BluetoothClass.Service.AUDIO) ||
                                device.bluetoothClass.deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                                device.bluetoothClass.deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ||
                                device.bluetoothClass.deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ||
                                device.bluetoothClass.deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                                device.bluetoothClass.deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO
                                )) {
                        devices.add(AppAudioDevice(device.name ?: context.getString(R.string.audio_output_bluetooth) , device.address, isBluetoothDeviceConnected(device)))
                    }
                }
            } catch (se: SecurityException) {
                Log.e("AudioOutputManager", "SecurityException while getting bonded devices: ${se.message}")
            }
        } else {
            Log.w("AudioOutputManager", "Bluetooth is not enabled.")
        }
        return devices
    }

    data class ActiveOutputInfo(val name: String, val iconResId: Int)

    fun getActiveAudioOutputInfo(context: Context): ActiveOutputInfo {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.w("AudioOutputManager", "BLUETOOTH_CONNECT permission not granted for getActiveAudioOutputInfo.")
            // Fallback to simpler checks if permission is missing for detailed BT check
        }

        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (deviceInfo in outputDevices) {
            if (deviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || deviceInfo.type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val bluetoothAdapter = getBluetoothAdapter(context)
                    bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                            if (profile == BluetoothProfile.A2DP) {
                                val a2dp = proxy as? BluetoothA2dp
                                a2dp?.connectedDevices?.firstOrNull { it.address == deviceInfo.address }?.let { btDevice ->
                                    // This part won't update LiveData directly, meant for immediate check
                                    // LiveData update should happen via BroadcastReceiver
                                }
                            }
                            bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                        }
                        override fun onServiceDisconnected(profile: Int) {}
                    }, BluetoothProfile.A2DP)
                }
                return ActiveOutputInfo(deviceInfo.productName?.toString() ?: context.getString(R.string.audio_output_bluetooth), R.drawable.ic_bluetooth_audio)
            }
            if (deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                return ActiveOutputInfo(context.getString(R.string.audio_output_headphones), R.drawable.ic_headset)
            }
            if (deviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                return ActiveOutputInfo(deviceInfo.productName?.toString() ?: context.getString(R.string.audio_output_headphones), R.drawable.ic_headset)
            }
        }
        return ActiveOutputInfo(context.getString(R.string.audio_output_internal_speaker), R.drawable.ic_speaker)
    }


    fun openBluetoothSettings(context: Context) {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            Toast.makeText(context, "Connect or select your audio device in Bluetooth settings.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Could not open Bluetooth settings.", Toast.LENGTH_SHORT).show()
        }
    }
}
