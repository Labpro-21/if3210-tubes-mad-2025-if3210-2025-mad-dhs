package com.tubes.purry.ui.outputdevice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tubes.purry.R
import com.tubes.purry.data.model.AudioDevice

class AudioDeviceAdapter(
    private val devices: List<AudioDevice>,
    private val onClick: (AudioDevice) -> Unit
) : RecyclerView.Adapter<AudioDeviceAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(device: AudioDevice) {
            itemView.findViewById<TextView>(R.id.deviceName).text = device.name
            itemView.findViewById<TextView>(R.id.deviceStatus).text =
                if (device.isConnected) "Tersambung" else "Tidak tersambung"

            itemView.setOnClickListener { onClick(device) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.audio_device_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount() = devices.size
}
