package com.tubes.purry.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class PermissionManager {

    companion object {
        const val BLUETOOTH_PERMISSION_REQUEST = 1001
        const val AUDIO_PERMISSION_REQUEST = 1002

        private val BLUETOOTH_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        private val AUDIO_PERMISSIONS = arrayOf(
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.RECORD_AUDIO
        )
    }

    fun checkBluetoothPermissions(activity: Activity): Boolean {
        return BLUETOOTH_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkAudioPermissions(activity: Activity): Boolean {
        return AUDIO_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestBluetoothPermissions(activity: Activity) {
        val missingPermissions = BLUETOOTH_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions,
                BLUETOOTH_PERMISSION_REQUEST
            )
        }
    }

    fun requestAudioPermissions(activity: Activity) {
        val missingPermissions = AUDIO_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions,
                AUDIO_PERMISSION_REQUEST
            )
        }
    }

    fun checkBluetoothPermissions(fragment: Fragment): Boolean {
        return BLUETOOTH_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(fragment.requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkAudioPermissions(fragment: Fragment): Boolean {
        return AUDIO_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(fragment.requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestBluetoothPermissions(fragment: Fragment) {
        val missingPermissions = BLUETOOTH_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(fragment.requireContext(), permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            fragment.requestPermissions(
                missingPermissions,
                BLUETOOTH_PERMISSION_REQUEST
            )
        }
    }

    fun requestAudioPermissions(fragment: Fragment) {
        val missingPermissions = AUDIO_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(fragment.requireContext(), permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            fragment.requestPermissions(
                missingPermissions,
                AUDIO_PERMISSION_REQUEST
            )
        }
    }

    fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray,
        onBluetoothGranted: () -> Unit = {},
        onBluetoothDenied: () -> Unit = {},
        onAudioGranted: () -> Unit = {},
        onAudioDenied: () -> Unit = {}
    ) {
        when (requestCode) {
            BLUETOOTH_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    onBluetoothGranted()
                } else {
                    onBluetoothDenied()
                }
            }
            AUDIO_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    onAudioGranted()
                } else {
                    onAudioDenied()
                }
            }
        }
    }
}