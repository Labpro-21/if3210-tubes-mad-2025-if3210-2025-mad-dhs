package com.tubes.purry.utils

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.tubes.purry.data.model.AudioDevice

object AudioOutputManager {
    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    private fun isConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    fun getAvailableAudioDevices(context: Context): List<AudioDevice> {
        val devices = mutableListOf<AudioDevice>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("AudioOutputManager", "BLUETOOTH_CONNECT permission not granted.")
            return devices
        }

        val bluetoothAdapter = getBluetoothAdapter(context)
        val pairedDevices = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            devices.add(AudioDevice(device.name ?: "Unknown", device.address, isConnected(device)))
        }

        return devices
    }

    fun openBluetoothSettings(context: Context) {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        context.startActivity(intent)
        Toast.makeText(context, "Pilih perangkat dari pengaturan Bluetooth", Toast.LENGTH_LONG).show()
    }
}
