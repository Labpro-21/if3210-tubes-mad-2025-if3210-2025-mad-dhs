package com.tubes.purry.data.model
data class AudioDevice(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    val isConnected: Boolean,
    val isActive: Boolean,
    val bluetoothAddress: String? = null,
    val deviceInfo: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioDevice
        if (id != other.id) return false
        if (type != other.type) return false
        return true
    }
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}
enum class AudioDeviceType(val displayName: String) {
    INTERNAL_SPEAKER("Phone Speaker"),
    WIRED_HEADPHONES("Wired Headphones"),
    BLUETOOTH_HEADPHONES("Bluetooth Headphones"),
    BLUETOOTH_SPEAKER("Bluetooth Speaker"),
    USB_AUDIO("USB Audio"),
    UNKNOWN("Unknown Device");
    companion object {
        fun fromAndroidDeviceType(androidType: Int): AudioDeviceType {
            return when (androidType) {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> INTERNAL_SPEAKER
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> WIRED_HEADPHONES
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> BLUETOOTH_HEADPHONES
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> BLUETOOTH_HEADPHONES
                android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> USB_AUDIO
                else -> UNKNOWN
            }
        }
    }
}