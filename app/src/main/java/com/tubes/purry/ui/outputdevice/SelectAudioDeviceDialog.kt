package com.tubes.purry.ui.outputdevice

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tubes.purry.R
import com.tubes.purry.data.model.AudioDevice
import com.tubes.purry.utils.AudioOutputManager

class SelectAudioDeviceDialog : DialogFragment() {

    private lateinit var audioDevices: List<AudioDevice>

    // Permission launcher for Bluetooth
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("SelectAudioDeviceDialog", "BLUETOOTH_CONNECT permission granted.")
            // Now that permission is granted, re-attempt to show the dialog content
            // This might require dismissing and re-showing, or updating the current dialog.
            // For simplicity, we'll assume the dialog will be re-triggered by the user.
            dismiss() // Dismiss current (likely empty) dialog
            // Optionally, try to re-show programmatically if you have a reference or a callback
            Toast.makeText(requireContext(), "Permission granted. Please try opening audio devices again.", Toast.LENGTH_LONG).show()

        } else {
            Log.d("SelectAudioDeviceDialog", "BLUETOOTH_CONNECT permission denied.")
            Toast.makeText(requireContext(), "Bluetooth permission is required to show devices.", Toast.LENGTH_LONG).show()
            dismiss()
        }
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.bluetooth_audio, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerAudioDevices)

        builder.setTitle(getString(R.string.action_select_audio_output))
            .setView(view)
            .setNegativeButton(R.string.cancel, null)

        // Check for BLUETOOTH_CONNECT permission before getting devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted, request it
            Log.d("SelectAudioDeviceDialog", "BLUETOOTH_CONNECT permission not granted. Requesting...")
            requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            // Show a placeholder or message in the dialog while waiting for permission
            builder.setMessage("Bluetooth permission required to list devices. Please grant the permission.")
        } else {
            // Permission granted or not needed (older OS)
            Log.d("SelectAudioDeviceDialog", "BLUETOOTH_CONNECT permission granted or not required.")
            audioDevices = AudioOutputManager.getAvailableAudioDevices(context)

            if (audioDevices.isEmpty()) {
                builder.setMessage("No paired Bluetooth audio devices found. Go to Bluetooth settings to pair a new device.")
                builder.setPositiveButton("Open Settings") { _, _ ->
                    AudioOutputManager.openBluetoothSettings(context)
                }
            } else {
                recyclerView.layoutManager = LinearLayoutManager(context)
                recyclerView.adapter = AudioDeviceAdapter(audioDevices) { device ->
                    // When a device is clicked, open Bluetooth settings for the user to connect/disconnect
                    AudioOutputManager.openBluetoothSettings(context)
                    dismiss() // Dismiss this dialog
                }
            }
        }
        return builder.create()
    }
}