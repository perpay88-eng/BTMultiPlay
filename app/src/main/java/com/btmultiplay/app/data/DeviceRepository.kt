package com.btmultiplay.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DeviceRepository(private val db: AppDatabase) {

    private val _devices = MutableStateFlow<List<SavedDevice>>(emptyList())

    init {
        _devices.value = db.loadAll().values.sortedByDescending { it.lastConnectedMs }
    }

    fun getAllDevices(): Flow<List<SavedDevice>> = _devices.asStateFlow()

    suspend fun getAutoReconnectDevices(): List<SavedDevice> = withContext(Dispatchers.IO) {
        db.loadAll().values.filter { it.autoReconnect }.sortedByDescending { it.lastConnectedMs }
    }

    suspend fun getDevice(address: String): SavedDevice? = withContext(Dispatchers.IO) {
        db.loadAll()[address]
    }

    suspend fun saveDevice(device: SavedDevice) = withContext(Dispatchers.IO) {
        val map = db.loadAll()
        val existing = map[device.address]
        map[device.address] = if (existing != null) {
            device.copy(connectionCount = existing.connectionCount + 1)
        } else {
            device
        }
        db.saveAll(map)
        _devices.value = map.values.sortedByDescending { it.lastConnectedMs }
    }

    suspend fun markConnected(address: String) = withContext(Dispatchers.IO) {
        val map = db.loadAll()
        map[address]?.let { existing ->
            map[address] = existing.copy(
                lastConnectedMs = System.currentTimeMillis(),
                connectionCount = existing.connectionCount + 1
            )
            db.saveAll(map)
            _devices.value = map.values.sortedByDescending { it.lastConnectedMs }
        }
    }

    suspend fun setAutoReconnect(address: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val map = db.loadAll()
        map[address]?.let { existing ->
            map[address] = existing.copy(autoReconnect = enabled)
            db.saveAll(map)
            _devices.value = map.values.sortedByDescending { it.lastConnectedMs }
        }
    }

    suspend fun deleteDevice(address: String) = withContext(Dispatchers.IO) {
        val map = db.loadAll()
        map.remove(address)
        db.saveAll(map)
        _devices.value = map.values.sortedByDescending { it.lastConnectedMs }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.saveAll(emptyMap())
        _devices.value = emptyList()
    }
}
