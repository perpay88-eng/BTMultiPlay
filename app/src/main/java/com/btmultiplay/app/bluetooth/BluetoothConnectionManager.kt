package com.btmultiplay.app.bluetooth

import android.Manifest
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BluetoothConnectionManager(private val context: Context) {

    private val TAG = "BT_ConnManager"

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _connectedDevices = MutableStateFlow<Map<String, BtDeviceInfo>>(emptyMap())
    val connectedDevices: StateFlow<Map<String, BtDeviceInfo>> = _connectedDevices.asStateFlow()

    private val _deviceStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val deviceStates: StateFlow<Map<String, ConnectionState>> = _deviceStates.asStateFlow()

    private var a2dpProxy: BluetoothA2dp? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // A2DP UUID for fallback socket connection
    private val A2DP_UUID = UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb")
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

            device ?: return
            if (!hasConnectPermission()) return

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(TAG, "ACL Connected: ${device.address}")
                    updateDeviceState(device.address, ConnectionState.CONNECTED)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d(TAG, "ACL Disconnected: ${device.address}")
                    updateDeviceState(device.address, ConnectionState.DISCONNECTED)
                    removeConnectedDevice(device.address)
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.d(TAG, "A2DP Connected: ${device.address}")
                            updateDeviceState(device.address, ConnectionState.CONNECTED)
                        }
                        BluetoothProfile.STATE_CONNECTING -> {
                            updateDeviceState(device.address, ConnectionState.CONNECTING)
                        }
                        BluetoothProfile.STATE_DISCONNECTING -> {
                            updateDeviceState(device.address, ConnectionState.DISCONNECTING)
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            updateDeviceState(device.address, ConnectionState.DISCONNECTED)
                            removeConnectedDevice(device.address)
                        }
                    }
                }
                BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
                    Log.d(TAG, "A2DP Playing state changed: $state for ${device.address}")
                }
            }
        }
    }

    init {
        registerConnectionReceiver()
        initA2dpProxy()
    }

    private fun registerConnectionReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)
        }
        context.registerReceiver(connectionReceiver, filter)
    }

    private fun initA2dpProxy() {
        adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProxy = proxy as BluetoothA2dp
                    Log.d(TAG, "A2DP proxy connected")
                    syncConnectedDevicesFromSystem()
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) {
                    a2dpProxy = null
                    Log.d(TAG, "A2DP proxy disconnected")
                }
            }
        }, BluetoothProfile.A2DP)
    }

    private fun syncConnectedDevicesFromSystem() {
        if (!hasConnectPermission()) return
        try {
            val a2dpDevices = a2dpProxy?.connectedDevices ?: emptyList()
            a2dpDevices.forEach { device ->
                val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
                val jblModel = JblModel.fromDeviceName(name)
                val info = BtDeviceInfo(
                    address = device.address,
                    name = name,
                    isJbl = jblModel != null || name.uppercase().contains("JBL"),
                    jblModel = jblModel,
                    supportsPartyBoost = jblModel?.supportsPartyBoost ?: false,
                    supportsA2dp = true,
                    connectionState = ConnectionState.CONNECTED
                )
                addConnectedDevice(info)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception syncing devices", e)
        }
    }

    fun connectDevice(deviceInfo: BtDeviceInfo) {
        scope.launch {
            updateDeviceState(deviceInfo.address, ConnectionState.CONNECTING)
            val device = adapter?.getRemoteDevice(deviceInfo.address) ?: run {
                updateDeviceState(deviceInfo.address, ConnectionState.DISCONNECTED)
                return@launch
            }

            if (!hasConnectPermission()) {
                updateDeviceState(deviceInfo.address, ConnectionState.DISCONNECTED)
                return@launch
            }

            // Try A2DP profile connection first
            val a2dpConnected = tryA2dpConnect(device, deviceInfo)
            if (a2dpConnected) {
                Log.d(TAG, "A2DP connection initiated for ${deviceInfo.address}")
            } else {
                Log.d(TAG, "Falling back to socket connection for ${deviceInfo.address}")
                trySocketConnect(device, deviceInfo)
            }
        }
    }

    private suspend fun tryA2dpConnect(device: BluetoothDevice, info: BtDeviceInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val proxy = a2dpProxy ?: return@withContext false
                if (!hasConnectPermission()) return@withContext false

                // Use reflection to call connect() on A2DP proxy (hidden API)
                val connectMethod = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                connectMethod.invoke(proxy, device)
                addConnectedDevice(info.copy(connectionState = ConnectionState.CONNECTING))
                true
            } catch (e: Exception) {
                Log.e(TAG, "A2DP connect failed: ${e.message}")
                false
            }
        }
    }

    private suspend fun trySocketConnect(device: BluetoothDevice, info: BtDeviceInfo) {
        withContext(Dispatchers.IO) {
            if (!hasConnectPermission()) return@withContext
            try {
                val socket = device.createRfcommSocketToServiceRecord(A2DP_UUID)
                socket.connect()
                addConnectedDevice(info.copy(connectionState = ConnectionState.CONNECTED))
                updateDeviceState(info.address, ConnectionState.CONNECTED)
                Log.d(TAG, "Socket connected to ${info.address}")
            } catch (e1: Exception) {
                try {
                    // Fallback SPP
                    val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                    addConnectedDevice(info.copy(connectionState = ConnectionState.CONNECTED))
                    updateDeviceState(info.address, ConnectionState.CONNECTED)
                } catch (e2: Exception) {
                    Log.e(TAG, "All connection attempts failed for ${info.address}")
                    updateDeviceState(info.address, ConnectionState.DISCONNECTED)
                }
            }
        }
    }

    fun disconnectDevice(address: String) {
        if (!hasConnectPermission()) return
        scope.launch {
            updateDeviceState(address, ConnectionState.DISCONNECTING)
            try {
                val device = adapter?.getRemoteDevice(address)
                val proxy = a2dpProxy
                if (proxy != null && device != null) {
                    val disconnectMethod = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                    disconnectMethod.invoke(proxy, device)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error: ${e.message}")
            }
            removeConnectedDevice(address)
            updateDeviceState(address, ConnectionState.DISCONNECTED)
        }
    }

    fun getA2dpConnectedDevices(): List<BluetoothDevice> {
        if (!hasConnectPermission()) return emptyList()
        return try { a2dpProxy?.connectedDevices ?: emptyList() } catch (e: SecurityException) { emptyList() }
    }

    private fun addConnectedDevice(info: BtDeviceInfo) {
        val current = _connectedDevices.value.toMutableMap()
        current[info.address] = info
        _connectedDevices.value = current
    }

    private fun removeConnectedDevice(address: String) {
        val current = _connectedDevices.value.toMutableMap()
        current.remove(address)
        _connectedDevices.value = current
    }

    private fun updateDeviceState(address: String, state: ConnectionState) {
        val current = _deviceStates.value.toMutableMap()
        current[address] = state
        _deviceStates.value = current
    }

    fun destroy() {
        scope.cancel()
        try { context.unregisterReceiver(connectionReceiver) } catch (e: Exception) { }
        if (a2dpProxy != null) {
            adapter?.closeProfileProxy(BluetoothProfile.A2DP, a2dpProxy)
        }
    }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
}
