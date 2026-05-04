package com.btmultiplay.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDeviceDao {

    @Query("SELECT * FROM saved_devices ORDER BY lastConnectedMs DESC")
    fun getAllDevices(): Flow<List<SavedDevice>>

    @Query("SELECT * FROM saved_devices WHERE autoReconnect = 1 ORDER BY lastConnectedMs DESC")
    suspend fun getAutoReconnectDevices(): List<SavedDevice>

    @Query("SELECT * FROM saved_devices WHERE address = :address LIMIT 1")
    suspend fun getDevice(address: String): SavedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SavedDevice)

    @Update
    suspend fun updateDevice(device: SavedDevice)

    @Query("UPDATE saved_devices SET lastConnectedMs = :timestamp, connectionCount = connectionCount + 1 WHERE address = :address")
    suspend fun updateLastConnected(address: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE saved_devices SET autoReconnect = :enabled WHERE address = :address")
    suspend fun setAutoReconnect(address: String, enabled: Boolean)

    @Query("DELETE FROM saved_devices WHERE address = :address")
    suspend fun deleteDevice(address: String)

    @Query("DELETE FROM saved_devices")
    suspend fun deleteAll()
}
