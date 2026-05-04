package com.btmultiplay.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.btmultiplay.app.bluetooth.JblModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DeviceCapabilityInfo(
    val modelKey: String,
    val supportsPartyBoost: Boolean,
    val supportsA2dpSink: Boolean,
    val maxVolume: Int,
    val notes: String
)

data class UpdateResult(
    val success: Boolean,
    val message: String,
    val devicesUpdated: Int = 0,
    val appUpdateAvailable: Boolean = false,
    val latestVersion: String? = null
)

class CapabilityUpdater(private val context: Context) {

    private val TAG = "CapabilityUpdater"
    private val PREFS = "capability_prefs"
    private val KEY_LAST_CHECK = "last_check_ms"
    private val KEY_CACHED_DATA = "cached_capability_data"
    private val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    // Public endpoint — replace with your own server if desired
    private val CAPABILITY_URL =
        "https://raw.githubusercontent.com/btmultiplay/device-db/main/capabilities.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun shouldCheckForUpdates(): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL_MS
    }

    suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext UpdateResult(false, "No internet connection")
        }

        return@withContext try {
            val request = Request.Builder().url(CAPABILITY_URL).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext UpdateResult(false, "Server returned ${response.code}")
            }

            val body = response.body?.string()
                ?: return@withContext UpdateResult(false, "Empty response")

            val json = JSONObject(body)
            val version = json.optString("version", "unknown")
            val appLatest = json.optString("app_latest_version", "1.0")
            val devices = json.optJSONObject("devices")

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .putString(KEY_CACHED_DATA, body)
                .apply()

            val updateAvailable = isNewerVersion(appLatest, getCurrentAppVersion())

            UpdateResult(
                success = true,
                message = "Updated capability data (v$version)",
                devicesUpdated = devices?.length() ?: 0,
                appUpdateAvailable = updateAvailable,
                latestVersion = appLatest
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed: ${e.message}")
            UpdateResult(false, "Check failed: ${e.message}")
        }
    }

    fun getCachedCapabilities(): Map<String, DeviceCapabilityInfo> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CACHED_DATA, null) ?: return getBuiltinCapabilities()

        return try {
            val json = JSONObject(cached)
            val devices = json.optJSONObject("devices") ?: return getBuiltinCapabilities()
            val result = mutableMapOf<String, DeviceCapabilityInfo>()
            devices.keys().forEach { key ->
                val obj = devices.getJSONObject(key)
                result[key] = DeviceCapabilityInfo(
                    modelKey = key,
                    supportsPartyBoost = obj.optBoolean("party_boost", false),
                    supportsA2dpSink = obj.optBoolean("a2dp_sink", true),
                    maxVolume = obj.optInt("max_volume", 100),
                    notes = obj.optString("notes", "")
                )
            }
            result
        } catch (e: Exception) {
            getBuiltinCapabilities()
        }
    }

    private fun getBuiltinCapabilities(): Map<String, DeviceCapabilityInfo> {
        return mapOf(
            "jbl_boombox_3" to DeviceCapabilityInfo("jbl_boombox_3", true, true, 100, "PartyBoost + 24h battery"),
            "jbl_boombox_2" to DeviceCapabilityInfo("jbl_boombox_2", true, true, 100, "PartyBoost + 24h battery"),
            "jbl_charge_4" to DeviceCapabilityInfo("jbl_charge_4", true, true, 100, "PartyBoost + 20h battery"),
            "jbl_charge_5" to DeviceCapabilityInfo("jbl_charge_5", true, true, 100, "PartyBoost + 20h battery"),
            "jbl_flip_5" to DeviceCapabilityInfo("jbl_flip_5", true, true, 100, "PartyBoost + 12h battery"),
            "jbl_flip_6" to DeviceCapabilityInfo("jbl_flip_6", true, true, 100, "PartyBoost + 12h battery"),
            "jbl_xtreme_3" to DeviceCapabilityInfo("jbl_xtreme_3", true, true, 100, "PartyBoost + 15h battery"),
            "jbl_pulse_5" to DeviceCapabilityInfo("jbl_pulse_5", true, true, 100, "PartyBoost + 12h battery"),
            "jbl_partybox_310" to DeviceCapabilityInfo("jbl_partybox_310", true, true, 100, "PartyBoost + 18h battery"),
            "jbl_partybox_110" to DeviceCapabilityInfo("jbl_partybox_110", true, true, 100, "PartyBoost + 12h battery")
        )
    }

    private fun getCurrentAppVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) { "1.0" }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        return try {
            val r = remote.split(".").map { it.toInt() }
            val l = local.split(".").map { it.toInt() }
            for (i in 0 until maxOf(r.size, l.size)) {
                val rv = r.getOrElse(i) { 0 }
                val lv = l.getOrElse(i) { 0 }
                if (rv > lv) return true
                if (rv < lv) return false
            }
            false
        } catch (e: Exception) { false }
    }
}
