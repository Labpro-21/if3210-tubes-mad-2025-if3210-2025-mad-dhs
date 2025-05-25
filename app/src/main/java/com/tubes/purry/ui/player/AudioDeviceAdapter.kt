package com.tubes.purry.ui.player
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tubes.purry.R
import com.tubes.purry.data.model.AudioDevice
import com.tubes.purry.data.model.AudioDeviceType
import com.tubes.purry.databinding.ItemAudioDeviceBinding
class AudioDeviceAdapter(
    private val onDeviceClick: (AudioDevice) -> Unit
) : ListAdapter<AudioDevice, AudioDeviceAdapter.AudioDeviceViewHolder>(AudioDeviceDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioDeviceViewHolder {
        val binding = ItemAudioDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AudioDeviceViewHolder(binding)
    }
    override fun onBindViewHolder(holder: AudioDeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    inner class AudioDeviceViewHolder(
        private val binding: ItemAudioDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: AudioDevice) {
            binding.apply {
                tvDeviceName.text = device.name
                tvDeviceStatus.text = when {
                    device.isActive -> "Active"
                    device.isConnected -> "Connected"
                    else -> "Not Connected"
                }
                // Set device icon based on type
                ivDeviceIcon.setImageResource(getDeviceIcon(device.type))
                // Set status color
                tvDeviceStatus.setTextColor(
                    ContextCompat.getColor(
                        root.context,
                        when {
                            device.isActive -> R.color.green
                            device.isConnected -> android.R.color.white
                            else -> android.R.color.darker_gray
                        }
                    )
                )
                // Set active indicator
                ivActiveIndicator.visibility = if (device.isActive)
                    android.view.View.VISIBLE else android.view.View.GONE
                // Set click listener
                root.setOnClickListener {
                    onDeviceClick(device)
                }
                // Disable click if not connected
                root.isEnabled = device.isConnected
                root.alpha = if (device.isConnected) 1.0f else 0.6f
            }
        }
        private fun getDeviceIcon(type: AudioDeviceType): Int {
            return when (type) {
                AudioDeviceType.INTERNAL_SPEAKER -> R.drawable.ic_phone_speaker
                AudioDeviceType.WIRED_HEADPHONES -> R.drawable.ic_headphones_wired
                AudioDeviceType.BLUETOOTH_HEADPHONES -> R.drawable.ic_headphones_bluetooth
                AudioDeviceType.BLUETOOTH_SPEAKER -> R.drawable.ic_speaker_bluetooth
                AudioDeviceType.USB_AUDIO -> R.drawable.ic_usb_audio
                AudioDeviceType.UNKNOWN -> R.drawable.ic_audio_device_unknown
            }
        }
    }
}
class AudioDeviceDiffCallback : DiffUtil.ItemCallback<AudioDevice>() {
    override fun areItemsTheSame(oldItem: AudioDevice, newItem: AudioDevice): Boolean {
        return oldItem.id == newItem.id
    }
    override fun areContentsTheSame(oldItem: AudioDevice, newItem: AudioDevice): Boolean {
        return oldItem == newItem
    }
}