package com.tubes.purry.utils
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tubes.purry.data.model.AudioDevice
import com.tubes.purry.data.model.AudioDeviceType
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

class AudioRoutingManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _availableDevices = MutableLiveData<List<AudioDevice>>()
    val availableDevices: LiveData<List<AudioDevice>> = _availableDevices
    private val _activeDevice = MutableLiveData<AudioDevice?>()
    val activeDevice: LiveData<AudioDevice?> = _activeDevice
    private val _connectionError = MutableLiveData<String?>()
    val connectionError: LiveData<String?> = _connectionError
    // Callback for when audio routing actually changes
    var onAudioRoutingChanged: ((AudioDevice) -> Unit)? = null
    // Keep track of current routing mode
    private var currentRoutingMode: AudioRoutingMode = AudioRoutingMode.AUTO
    private var isBluetoothScoOn = false
    enum class AudioRoutingMode {
        AUTO,           // Let Android decide
        SPEAKER,        // Force speaker
        WIRED_HEADSET,  // Force wired headset
        BLUETOOTH,      // Force Bluetooth
        EARPIECE        // Force earpiece (for calls)
    }
    // BroadcastReceiver to monitor audio device changes
    private val audioDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    Log.d("AudioRouting", "🎧 Headset plug event")
                    refreshDeviceList()
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                    Log.d("AudioRouting", "🔵 Bluetooth SCO state: $state")
                    handleBluetoothScoStateChange(state)
                }
                "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED" -> {
                    Log.d("AudioRouting", "🔵 Bluetooth headset connection changed")
                    refreshDeviceList()
                }
            }
        }
    }
    init {
        registerReceivers()
        refreshDeviceList()
    }
    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
        }
        context.registerReceiver(audioDeviceReceiver, filter)
    }
    fun refreshDeviceList() {
        val devices = mutableListOf<AudioDevice>()
        val seenDevices = mutableSetOf<String>() // To prevent duplicates
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Get all available audio devices
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (deviceInfo in audioDevices) {
                val audioDevice = mapToAudioDevice(deviceInfo)
                val deviceKey = "${audioDevice.type}_${audioDevice.name}"
                if (!seenDevices.contains(deviceKey)) {
                    seenDevices.add(deviceKey)
                    devices.add(audioDevice)
                }
            }
        } else {
            // Fallback for older Android versions
            devices.addAll(getLegacyAudioDevices())
        }
        // Always add internal speaker if not present
        if (!devices.any { it.type == AudioDeviceType.INTERNAL_SPEAKER }) {
            devices.add(0, AudioDevice(
                id = "internal_speaker",
                name = "Phone Speaker",
                type = AudioDeviceType.INTERNAL_SPEAKER,
                isConnected = true,
                isActive = currentRoutingMode == AudioRoutingMode.SPEAKER
            ))
        }
        Log.d("AudioRouting", "📱 Found ${devices.size} audio devices")
        val pairedBluetoothDevices = getAllPairedBluetoothDevices()
        devices.addAll(
            pairedBluetoothDevices.filter { paired ->
                devices.none { it.id == paired.id }
            }
        )
        _availableDevices.postValue(devices)
        // Update active device
        updateActiveDevice(devices)
    }
    private fun mapToAudioDevice(deviceInfo: AudioDeviceInfo): AudioDevice {
        val type = when (deviceInfo.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceType.INTERNAL_SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADPHONES
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioDeviceType.BLUETOOTH_HEADPHONES
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> AudioDeviceType.USB_AUDIO
            else -> AudioDeviceType.UNKNOWN
        }
        return AudioDevice(
            id = "${deviceInfo.type}_${deviceInfo.id}",
            name = getDeviceName(deviceInfo),
            type = type,
            isConnected = true,
            isActive = isDeviceActive(deviceInfo)
        )
    }
    private fun getDeviceName(deviceInfo: AudioDeviceInfo): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            deviceInfo.productName?.toString() ?: getDefaultDeviceName(deviceInfo.type)
        } else {
            getDefaultDeviceName(deviceInfo.type)
        }
    }
    private fun getDefaultDeviceName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone Earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
            else -> "Unknown Audio Device"
        }
    }
    private fun isDeviceActive(deviceInfo: AudioDeviceInfo): Boolean {
        return when (deviceInfo.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                currentRoutingMode == AudioRoutingMode.SPEAKER
            }
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                currentRoutingMode == AudioRoutingMode.WIRED_HEADSET && audioManager.isWiredHeadsetOn
            }
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                currentRoutingMode == AudioRoutingMode.BLUETOOTH && (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn)
            }
            else -> false
        }
    }
    private fun getLegacyAudioDevices(): List<AudioDevice> {
        val devices = mutableListOf<AudioDevice>()
        // Internal speaker (always available)
        devices.add(AudioDevice(
            id = "internal_speaker",
            name = "Phone Speaker",
            type = AudioDeviceType.INTERNAL_SPEAKER,
            isConnected = true,
            isActive = currentRoutingMode == AudioRoutingMode.SPEAKER
        ))
        // Wired headset
        if (audioManager.isWiredHeadsetOn) {
            devices.add(AudioDevice(
                id = "wired_headset",
                name = "Wired Headset",
                type = AudioDeviceType.WIRED_HEADPHONES,
                isConnected = true,
                isActive = currentRoutingMode == AudioRoutingMode.WIRED_HEADSET
            ))
        }
        // Bluetooth
        if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
            devices.add(AudioDevice(
                id = "bluetooth_audio",
                name = "Bluetooth Audio",
                type = AudioDeviceType.BLUETOOTH_HEADPHONES,
                isConnected = true,
                isActive = currentRoutingMode == AudioRoutingMode.BLUETOOTH
            ))
        }
        return devices
    }
    private fun updateActiveDevice(devices: List<AudioDevice>) {
        val activeDevice = devices.find { it.isActive }
        _activeDevice.postValue(activeDevice)
    }
    fun selectAudioDevice(device: AudioDevice): Boolean {
        Log.d("AudioRouting", "🎯 Selecting audio device: ${device.name} (${device.type})")
        return try {
            when (device.type) {
                AudioDeviceType.INTERNAL_SPEAKER -> {
                    setAudioRouting(AudioRoutingMode.SPEAKER)
                }
                AudioDeviceType.WIRED_HEADPHONES -> {
                    if (audioManager.isWiredHeadsetOn) {
                        setAudioRouting(AudioRoutingMode.WIRED_HEADSET)
                    } else {
                        _connectionError.postValue("Wired headset not connected")
                        return false
                    }
                }
                AudioDeviceType.BLUETOOTH_HEADPHONES,
                AudioDeviceType.BLUETOOTH_SPEAKER -> {
                    setBluetoothAudio(true)
                }
                else -> {
                    Log.w("AudioRouting", "⚠️ Unsupported device type: ${device.type}")
                    return false
                }
            }
            // Notify about the routing change
            onAudioRoutingChanged?.invoke(device)
            true
        } catch (e: Exception) {
            Log.e("AudioRouting", "❌ Failed to select audio device: ${e.message}")
            _connectionError.postValue("Failed to switch to ${device.name}: ${e.message}")
            false
        }
    }
    private fun setAudioRouting(mode: AudioRoutingMode) {
        Log.d("AudioRouting", "🔄 Setting audio routing mode: $mode")
        currentRoutingMode = mode
        // Turn off Bluetooth SCO if switching away from Bluetooth
        if (mode != AudioRoutingMode.BLUETOOTH && isBluetoothScoOn) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            isBluetoothScoOn = false
        }
        when (mode) {
            AudioRoutingMode.SPEAKER -> {
                audioManager.isSpeakerphoneOn = true
                Log.d("AudioRouting", "🔊 Switched to speaker")
            }
            AudioRoutingMode.WIRED_HEADSET -> {
                audioManager.isSpeakerphoneOn = false
                Log.d("AudioRouting", "🎧 Switched to wired headset")
            }
            AudioRoutingMode.AUTO -> {
                audioManager.isSpeakerphoneOn = false
                Log.d("AudioRouting", "🤖 Switched to auto mode")
            }
            else -> {
                Log.d("AudioRouting", "🔄 Routing mode set: $mode")
            }
        }
        // Refresh device list to update active states
        refreshDeviceList()
    }
    private fun setBluetoothAudio(enable: Boolean) {
        Log.d("AudioRouting", "🔵 Setting Bluetooth audio: $enable")
        if (enable) {
            // Check if Bluetooth is available
            if (!audioManager.isBluetoothA2dpOn && !audioManager.isBluetoothScoOn) {
                Log.w("AudioRouting", "⚠️ No Bluetooth audio devices connected")
                _connectionError.postValue("No Bluetooth audio devices connected")
                return
            }
            // Start Bluetooth SCO for better audio quality
            if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                isBluetoothScoOn = true
            }
            currentRoutingMode = AudioRoutingMode.BLUETOOTH
            audioManager.isSpeakerphoneOn = false
            Log.d("AudioRouting", "🔵 Bluetooth audio enabled")
        } else {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            isBluetoothScoOn = false
            // Fall back to auto routing
            currentRoutingMode = AudioRoutingMode.AUTO
            Log.d("AudioRouting", "🔵 Bluetooth audio disabled")
        }
        refreshDeviceList()
    }
    private fun getAllPairedBluetoothDevices(): List<AudioDevice> {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        val result = mutableListOf<AudioDevice>()

        pairedDevices?.forEach { device ->
            val name = device.name ?: "Bluetooth Device"
            val address = device.address ?: ""
            val id = "bt_${address.replace(":", "")}"

            val audioDevice = AudioDevice(
                id = id,
                name = name,
                type = AudioDeviceType.BLUETOOTH_HEADPHONES, // or BLUETOOTH_SPEAKER, based on your logic
                isConnected = false,  // not currently connected
                isActive = false,
                bluetoothAddress = address,
                deviceInfo = device.toString()
            )

            // Add only if not already seen in connected devices
            if (!result.any { it.id == audioDevice.id }) {
                result.add(audioDevice)
            }
        }

        return result
    }
    private fun handleBluetoothScoStateChange(state: Int) {
        when (state) {
            AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                Log.d("AudioRouting", "🔵 Bluetooth SCO connected")
                isBluetoothScoOn = true
                refreshDeviceList()
            }
            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                Log.d("AudioRouting", "🔵 Bluetooth SCO disconnected")
                isBluetoothScoOn = false
                if (currentRoutingMode == AudioRoutingMode.BLUETOOTH) {
                    // Auto-fallback to speaker or headset
                    if (audioManager.isWiredHeadsetOn) {
                        setAudioRouting(AudioRoutingMode.WIRED_HEADSET)
                    } else {
                        setAudioRouting(AudioRoutingMode.SPEAKER)
                    }
                }
                refreshDeviceList()
            }
            AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                Log.d("AudioRouting", "🔵 Bluetooth SCO connecting...")
            }
        }
    }
    fun clearError() {
        _connectionError.postValue(null)
    }
    fun cleanup() {
        try {
            context.unregisterReceiver(audioDeviceReceiver)
            // Clean up Bluetooth SCO if active
            if (isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            Log.d("AudioRouting", "🧹 AudioRoutingManager cleaned up")
        } catch (e: Exception) {
            Log.e("AudioRouting", "❌ Error during cleanup: ${e.message}")
        }
    }
}