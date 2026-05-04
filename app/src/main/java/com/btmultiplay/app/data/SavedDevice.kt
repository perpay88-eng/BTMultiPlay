package com.btmultiplay.app.data

data class SavedDevice(
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
