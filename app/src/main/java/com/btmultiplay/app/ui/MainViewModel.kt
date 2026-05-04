package com.btmultiplay.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.btmultiplay.app.BTMultiPlayApp
import com.btmultiplay.app.audio.AudioOutputCapability
import com.btmultiplay.app.audio.AudioRoutingManager
import com.btmultiplay.app.audio.SyncPlaybackManager
import com.btmultiplay.app.audio.SyncStatus
import com.btmultiplay.app.bluetooth.BluetoothConnectionManager
import com.btmultiplay.app.bluetooth.BluetoothScanner
import com.btmultiplay.app.bluetooth.BtDeviceInfo
import com.btmultiplay.app.bluetooth.ConnectionState
import com.btmultiplay.app.data.DeviceRepository
import com.btmultiplay.app.data.SavedDevice
import com.btmultiplay.app.utils.CapabilityUpdater
import com.btmultiplay.app.utils.UpdateResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val isBluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val scanResults: List<BtDeviceInfo> = emptyList(),
    val connectedDevices: Map<String, BtDeviceInfo> = emptyMap(),
    val savedDevices: List<com.btmultiplay.app.data.SavedDevice> = emptyList(),
    val audioCapability: AudioOutputCapability? = null,
    val syncStatus: SyncStatus? = null,
    val updateResult: UpdateResult? = null,
    val isCheckingUpdates: Boolean = false,
    val deviceStates: Map<String, ConnectionState> = emptyMap(),
    val permissionsGranted: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BTMultiPlayApp
    val repository: DeviceRepository = app.deviceRepository

    lateinit var scanner: BluetoothScanner
        private set
    lateinit var connectionManager: BluetoothConnectionManager
        private set
    lateinit var audioRoutingManager: AudioRoutingManager
        private set
    lateinit var syncPlaybackManager: SyncPlaybackManager
        private set
    lateinit var capabilityUpdater: CapabilityUpdater
        private set

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        scanner = BluetoothScanner(getApplication())
        connectionManager = BluetoothConnectionManager(getApplication())
        audioRoutingManager = AudioRoutingManager(getApplication())
        syncPlaybackManager = SyncPlaybackManager(getApplication(), audioRoutingManager)
        capabilityUpdater = CapabilityUpdater(getApplication())

        observeFlows()
        loadInitialState()

        if (capabilityUpdater.isNetworkAvailable() && capabilityUpdater.shouldCheckForUpdates()) {
            checkForUpdates()
        }
    }

    private fun observeFlows() {
        viewModelScope.launch {
            scanner.scanResults.collectLatest { results ->
                _uiState.update { it.copy(scanResults = results) }
            }
        }
        viewModelScope.launch {
            scanner.isScanning.collectLatest { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }
        viewModelScope.launch {
            connectionManager.connectedDevices.collectLatest { devices ->
                _uiState.update { it.copy(connectedDevices = devices) }
            }
        }
        viewModelScope.launch {
            connectionManager.deviceStates.collectLatest { states ->
                _uiState.update { it.copy(deviceStates = states) }
            }
        }
        viewModelScope.launch {
            syncPlaybackManager.syncStatus.collectLatest { status ->
                _uiState.update { it.copy(syncStatus = status) }
            }
        }
        viewModelScope.launch {
            repository.getAllDevices().collectLatest { saved ->
                _uiState.update { it.copy(savedDevices = saved) }
            }
        }
    }

    private fun loadInitialState() {
        _uiState.update {
            it.copy(
                isBluetoothEnabled = scanner.isBluetoothEnabled,
                audioCapability = audioRoutingManager.capability.value
            )
        }
    }

    fun onPermissionsGranted() {
        _uiState.update { it.copy(permissionsGranted = true, isBluetoothEnabled = scanner.isBluetoothEnabled) }
        audioRoutingManager.refreshCapability()
        _uiState.update { it.copy(audioCapability = audioRoutingManager.capability.value) }
    }

    fun startScan() {
        if (!scanner.isBluetoothEnabled) return
        scanner.startScan()
    }

    fun stopScan() = scanner.stopScan()

    fun connectDevice(device: BtDeviceInfo) {
        connectionManager.connectDevice(device)
        viewModelScope.launch {
            val savedDevice = SavedDevice(
                address = device.address,
                name = device.name,
                deviceClass = device.deviceClass,
                isJbl = device.isJbl,
                jblModel = device.jblModel?.name,
                supportsPartyBoost = device.supportsPartyBoost,
                supportsA2dp = device.supportsA2dp
            )
            repository.saveDevice(savedDevice)
        }
    }

    fun disconnectDevice(address: String) = connectionManager.disconnectDevice(address)

    fun forgetDevice(address: String) {
        viewModelScope.launch { repository.deleteDevice(address) }
    }

    fun setAutoReconnect(address: String, enabled: Boolean) {
        viewModelScope.launch { repository.setAutoReconnect(address, enabled) }
    }

    fun startSyncTest() {
        val connected = _uiState.value.connectedDevices.values.toList()
        syncPlaybackManager.startSyncTestTone(connected)
    }

    fun stopSyncTest() = syncPlaybackManager.stopPlayback()

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdates = true, updateResult = null) }
            val result = capabilityUpdater.checkForUpdates()
            if (result.success) {
                audioRoutingManager.refreshCapability()
                _uiState.update {
                    it.copy(
                        audioCapability = audioRoutingManager.capability.value,
                        isCheckingUpdates = false,
                        updateResult = result
                    )
                }
            } else {
                _uiState.update { it.copy(isCheckingUpdates = false, updateResult = result) }
            }
        }
    }

    fun dismissUpdateResult() {
        _uiState.update { it.copy(updateResult = null) }
    }

    fun getConnectionState(address: String): ConnectionState {
        return _uiState.value.deviceStates[address] ?: ConnectionState.DISCONNECTED
    }

    override fun onCleared() {
        super.onCleared()
        if (initialized) {
            scanner.stopScan()
            connectionManager.destroy()
            syncPlaybackManager.destroy()
        }
    }
}
