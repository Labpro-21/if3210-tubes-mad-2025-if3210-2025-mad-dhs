package com.tubes.purry.ui.outputdevice

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tubes.purry.R
import com.tubes.purry.data.model.AudioDevice
import com.tubes.purry.utils.AudioOutputManager

class SelectAudioDeviceDialog : DialogFragment() {

    private lateinit var audioDevices: List<AudioDevice>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        audioDevices = AudioOutputManager.getAvailableAudioDevices(context)

        val view = layoutInflater.inflate(R.layout.bluetooth_audio, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerAudioDevices)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = AudioDeviceAdapter(audioDevices) {
            AudioOutputManager.openBluetoothSettings(context)
            dismiss()
        }

        return AlertDialog.Builder(context)
            .setTitle("Pilih Perangkat Audio")
            .setView(view)
            .setNegativeButton("Tutup", null)
            .create()
    }
}
