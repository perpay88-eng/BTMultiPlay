package com.btmultiplay.app.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _scanResults = MutableStateFlow<List<BtDeviceInfo>>(emptyList())
    val scanResults: StateFlow<List<BtDeviceInfo>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val discoveredMap = mutableMapOf<String, BtDeviceInfo>()

    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        else
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                    device?.let { d ->
                        if (!hasConnectPermission()) return
                        val name = try { d.name ?: "" } catch (e: SecurityException) { "" }
                        val info = buildDeviceInfo(d, name, rssi)
                        discoveredMap[d.address] = info
                        _scanResults.value = discoveredMap.values.sortedByDescending { it.rssi }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                }
            }
        }
    }

    fun startScan(): Boolean {
        if (!isBluetoothEnabled) return false
        if (!hasScanPermission()) return false

        discoveredMap.clear()

        // Add already-paired devices
        if (hasConnectPermission()) {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                val name = try { device.name ?: "" } catch (e: SecurityException) { "" }
                val info = buildDeviceInfo(device, name, 0).copy(rssi = -50)
                discoveredMap[device.address] = info
            }
            _scanResults.value = discoveredMap.values.sortedByDescending { it.rssi }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)

        return try {
            bluetoothAdapter?.startDiscovery() == true
        } catch (e: SecurityException) {
            false
        }
    }

    fun stopScan() {
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: SecurityException) { /* ignore */ }
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: IllegalArgumentException) { /* already unregistered */ }
        _isScanning.value = false
    }

    fun getPairedDevices(): List<BtDeviceInfo> {
        if (!hasConnectPermission()) return emptyList()
        return bluetoothAdapter?.bondedDevices?.map { device ->
            val name = try { device.name ?: "" } catch (e: SecurityException) { "" }
            buildDeviceInfo(device, name, -50)
        } ?: emptyList()
    }

    private fun buildDeviceInfo(device: BluetoothDevice, name: String, rssi: Int): BtDeviceInfo {
        val deviceClass = try { device.bluetoothClass?.deviceClass ?: 0 } catch (e: SecurityException) { 0 }
        val jblModel = JblModel.fromDeviceName(name)
        val isJbl = jblModel != null || name.uppercase().contains("JBL")
        val supportsPartyBoost = jblModel?.supportsPartyBoost ?: false
        val supportsA2dp = isAudioDevice(deviceClass)

        return BtDeviceInfo(
            address = device.address,
            name = name,
            deviceClass = deviceClass,
            isJbl = isJbl,
            jblModel = jblModel,
            supportsPartyBoost = supportsPartyBoost,
            supportsA2dp = supportsA2dp,
            rssi = rssi
        )
    }

    private fun isAudioDevice(deviceClass: Int): Boolean {
        // BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES = 0x0404
        // BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER = 0x0414
        // BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO = 0x0408
        // BluetoothClass.Device.Major.AUDIO_VIDEO = 0x0400
        return (deviceClass and 0x1F00) == 0x0400
    }

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
}
