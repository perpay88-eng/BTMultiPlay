package com.btmultiplay.app.data

import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val dao: SavedDeviceDao) {

    fun getAllDevices(): Flow<List<SavedDevice>> = dao.getAllDevices()

    suspend fun getAutoReconnectDevices(): List<SavedDevice> = dao.getAutoReconnectDevices()

    suspend fun getDevice(address: String): SavedDevice? = dao.getDevice(address)

    suspend fun saveDevice(device: SavedDevice) {
        val existing = dao.getDevice(device.address)
        if (existing != null) {
            dao.updateDevice(device.copy(connectionCount = existing.connectionCount))
        } else {
            dao.insertDevice(device)
        }
    }

    suspend fun markConnected(address: String) {
        dao.updateLastConnected(address)
    }

    suspend fun setAutoReconnect(address: String, enabled: Boolean) {
        dao.setAutoReconnect(address, enabled)
    }

    suspend fun deleteDevice(address: String) {
        dao.deleteDevice(address)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
