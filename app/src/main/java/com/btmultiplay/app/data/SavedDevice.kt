package com.btmultiplay.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_devices")
data class SavedDevice(
    @PrimaryKey
    val address: String,
    val name: String,
    val deviceClass: Int = 0,
    val isJbl: Boolean = false,
    val jblModel: String? = null,
    val supportsPartyBoost: Boolean = false,
    val supportsA2dp: Boolean = true,
    val lastConnectedMs: Long = System.currentTimeMillis(),
    val autoReconnect: Boolean = true,
    val connectionCount: Int = 0,
    val notes: String? = null
)
