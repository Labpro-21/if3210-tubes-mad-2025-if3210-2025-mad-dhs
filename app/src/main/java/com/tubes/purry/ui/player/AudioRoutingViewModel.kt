package com.tubes.purry.ui.player
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tubes.purry.data.model.AudioDevice
import com.tubes.purry.utils.AudioRoutingManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

class AudioRoutingViewModel(application: Application) : AndroidViewModel(application) {
    private val audioRoutingManager = AudioRoutingManager(application)
    val availableDevices: LiveData<List<AudioDevice>> = audioRoutingManager.availableDevices
    val activeDevice: LiveData<AudioDevice?> = audioRoutingManager.activeDevice
    val connectionError: LiveData<String?> = audioRoutingManager.connectionError
    private val _isDeviceSwitching = MutableLiveData<Boolean>(false)
    val isDeviceSwitching: LiveData<Boolean> = _isDeviceSwitching
    // Callback to restart MediaPlayer when audio routing changes
    var onMediaPlayerRestartNeeded: ((Context, AudioDevice) -> Unit)? = null
    // Handler for delayed operations
    private val handler = Handler(Looper.getMainLooper())
    init {
        setupAudioRoutingCallback()
    }
    private fun setupAudioRoutingCallback() {
        audioRoutingManager.onAudioRoutingChanged = { device ->
            Log.d("AudioRoutingViewModel", "🔄 Audio routing changed to: ${device.name}")
            _isDeviceSwitching.postValue(true)
            // Give some time for the audio system to stabilize
            handler.postDelayed({
                Log.d("AudioRoutingViewModel", "🎵 Requesting MediaPlayer restart for: ${device.name}")
                onMediaPlayerRestartNeeded?.invoke(getApplication(), device)
                // Reset switching state after a delay
                handler.postDelayed({
                    _isDeviceSwitching.postValue(false)
                }, 500)
            }, 200) // 200ms delay for audio system to stabilize
        }
    }
    fun refreshDevices() {
        Log.d("AudioRoutingViewModel", "🔄 Refreshing audio devices")
        audioRoutingManager.refreshDeviceList()
    }
    fun selectAudioDevice(device: AudioDevice): Boolean {
        if (_isDeviceSwitching.value == true) {
            Log.w("AudioRoutingViewModel", "⚠️ Device switching in progress, ignoring request")
            return false
        }
        if (!device.isConnected) {
            Log.w("AudioRoutingViewModel", "⚠️ Device not connected: ${device.name}")
            return false
        }
        Log.d("AudioRoutingViewModel", "🎯 Selecting audio device: ${device.name}")
        _isDeviceSwitching.postValue(true)
        val success = audioRoutingManager.selectAudioDevice(device)
        if (!success) {
            // Reset switching state if selection failed
            handler.postDelayed({
                _isDeviceSwitching.postValue(false)
            }, 100)
        }
        return success
    }
    fun clearError() {
        audioRoutingManager.clearError()
    }
    /**
     * Force refresh after MediaPlayer restart
     */
    fun onMediaPlayerRestarted() {
        Log.d("AudioRoutingViewModel", "✅ MediaPlayer restarted, refreshing devices")
        handler.postDelayed({
            refreshDevices()
        }, 100)
    }
    /**
     * Get current active device
     */
    fun getCurrentActiveDevice(): AudioDevice? {
        return activeDevice.value
    }
    /**
     * Check if a specific device type is available
     */
    fun isDeviceTypeAvailable(deviceType: com.tubes.purry.data.model.AudioDeviceType): Boolean {
        return availableDevices.value?.any {
            it.type == deviceType && it.isConnected
        } ?: false
    }
    /**
     * Get the best available audio device (priority: Bluetooth > Wired > Speaker)
     */
    fun getBestAvailableDevice(): AudioDevice? {
        val devices = availableDevices.value ?: return null
        // Priority order: Bluetooth Headphones -> Wired Headphones -> Internal Speaker
        return devices.find {
            it.isConnected && it.type == com.tubes.purry.data.model.AudioDeviceType.BLUETOOTH_HEADPHONES
        } ?: devices.find {
            it.isConnected && it.type == com.tubes.purry.data.model.AudioDeviceType.WIRED_HEADPHONES
        } ?: devices.find {
            it.isConnected && it.type == com.tubes.purry.data.model.AudioDeviceType.INTERNAL_SPEAKER
        }
    }
    /**
     * Auto-select the best available device
     */
    fun autoSelectBestDevice(): Boolean {
        val bestDevice = getBestAvailableDevice()
        return if (bestDevice != null && !bestDevice.isActive) {
            selectAudioDevice(bestDevice)
        } else {
            false
        }
    }
    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacksAndMessages(null)
        audioRoutingManager.cleanup()
        Log.d("AudioRoutingViewModel", "🧹 AudioRoutingViewModel cleared")
    }
}