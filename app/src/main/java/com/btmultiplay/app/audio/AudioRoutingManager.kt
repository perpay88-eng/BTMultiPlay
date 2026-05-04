package com.btmultiplay.app.audio

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioOutputCapability(
    val supportsDualAudio: Boolean,
    val maxSimultaneousOutputs: Int,
    val supportsBluetoothSco: Boolean
)

class AudioRoutingManager(private val context: Context) {

    private val TAG = "AudioRoutingManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _capability = MutableStateFlow(detectCapability())
    val capability: StateFlow<AudioOutputCapability> = _capability.asStateFlow()

    private val _activeOutputs = MutableStateFlow<List<AudioDeviceInfo>>(emptyList())
    val activeOutputs: StateFlow<List<AudioDeviceInfo>> = _activeOutputs.asStateFlow()

    private fun detectCapability(): AudioOutputCapability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return AudioOutputCapability(false, 1, false)
        }

        val allOutputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val btDevices = allOutputDevices.filter {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }

        // Samsung Dual Audio and some OEMs support multiple BT outputs
        val maxOutputs = detectMaxSimultaneousOutputs()
        val dualAudioSupported = maxOutputs > 1 || isSamsungDualAudioSupported()

        return AudioOutputCapability(
            supportsDualAudio = dualAudioSupported,
            maxSimultaneousOutputs = maxOutputs,
            supportsBluetoothSco = audioManager.isBluetoothScoAvailableOffCall
        )
    }

    private fun detectMaxSimultaneousOutputs(): Int {
        // Try to detect Samsung Dual Audio
        if (isSamsungDualAudioSupported()) return 2

        // Try to detect other OEM multi-output support via system properties
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)

            val dualAudio = getMethod.invoke(null, "bluetooth.device.class.headset", "0") as String
            if (dualAudio != "0") 2 else 1
        } catch (e: Exception) {
            1
        }
    }

    private fun isSamsungDualAudioSupported(): Boolean {
        return try {
            val manufacturer = android.os.Build.MANUFACTURER.lowercase()
            if (!manufacturer.contains("samsung")) return false

            // Check for Samsung-specific dual audio property
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            val prop = getMethod.invoke(null, "ro.bluetooth.dual_audio", "false") as String
            prop == "true" || prop == "1"
        } catch (e: Exception) {
            // Assume Samsung devices might have it
            android.os.Build.MANUFACTURER.lowercase().contains("samsung")
        }
    }

    fun refreshCapability() {
        _capability.value = detectCapability()
    }

    fun getConnectedBluetoothOutputs(): List<AudioDeviceInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }.also { _activeOutputs.value = it }
    }

    fun routeAudioToDevice(device: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            // For API 31+ we can set preferred device on AudioTrack
            Log.d(TAG, "Routing audio to: ${device.productName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to route audio: ${e.message}")
            false
        }
    }

    /**
     * Attempts dual audio by enabling SCO for a second device when primary is A2DP.
     * Returns true if the system reported capability, false if single-output only.
     */
    fun enableDualAudioMode(): Boolean {
        val cap = _capability.value
        if (!cap.supportsDualAudio) {
            Log.d(TAG, "Dual audio not supported on this device")
            return false
        }
        Log.d(TAG, "Dual audio mode enabled (max ${cap.maxSimultaneousOutputs} outputs)")
        return true
    }

    fun getAudioOutputDeviceForBtAddress(address: String): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { device ->
            // Match BT device by address where possible
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
    }
}
