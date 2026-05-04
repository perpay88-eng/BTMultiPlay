package com.btmultiplay.app.utils

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Helper for Samsung Dual Audio feature, present on many Samsung Galaxy devices
 * including the Galaxy A9 tablet. Samsung Dual Audio lets you play to two BT
 * speakers simultaneously from Settings > Connections > Bluetooth > Advanced.
 */
object SamsungDualAudioHelper {

    private const val TAG = "SamsungDualAudio"

    val isSamsungDevice: Boolean
        get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun getSamsungModel(): String = Build.MODEL

    fun getSamsungAndroidVersion(): Int = Build.VERSION.SDK_INT

    /**
     * Samsung Dual Audio is available on Galaxy devices running Android 9+
     * (One UI 1.0+). Galaxy A9 runs Android 8 but can be updated to 10.
     */
    fun isDualAudioLikelySupported(): Boolean {
        if (!isSamsungDevice) return false
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P // Android 9+
    }

    /**
     * Attempt to detect if Samsung Dual Audio is actually enabled via system props.
     */
    fun isDualAudioEnabled(context: Context): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val result = method.invoke(null, "ro.bluetooth.a2dp_offload.supported", "false") as String
            Log.d(TAG, "Dual audio prop: $result")
            result == "true"
        } catch (e: Exception) {
            isDualAudioLikelySupported()
        }
    }

    /**
     * Returns a human-readable description of audio capability for this device.
     */
    fun getCapabilityDescription(context: Context): String {
        return when {
            !isSamsungDevice -> "Standard Android device — single BT audio output at a time. Software sync mode available."
            isDualAudioLikelySupported() ->
                "Samsung Galaxy detected (${Build.MODEL}). Samsung Dual Audio may be available — " +
                "enable it in Settings → Connections → Bluetooth → Advanced → Dual Audio."
            else ->
                "Samsung Galaxy detected (${Build.MODEL}, Android ${Build.VERSION.RELEASE}). " +
                "Update to Android 9+ (One UI 1.0+) to enable Dual Audio support."
        }
    }

    fun getDualAudioInstructions(): String =
        "1. Open Settings\n" +
        "2. Tap Connections → Bluetooth\n" +
        "3. Tap the three-dot menu → Advanced\n" +
        "4. Enable 'Dual Audio'\n" +
        "5. Connect two Bluetooth speakers\n" +
        "6. Audio will play through both simultaneously"
}
