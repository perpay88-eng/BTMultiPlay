package com.btmultiplay.app.bluetooth

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
}

data class BtDeviceInfo(
    val address: String,
    val name: String,
    val deviceClass: Int = 0,
    val isJbl: Boolean = false,
    val jblModel: JblModel? = null,
    val supportsPartyBoost: Boolean = false,
    val supportsA2dp: Boolean = true,
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    var isActiveOutput: Boolean = false,
    var rssi: Int = 0
) {
    val displayName: String get() = name.ifBlank { address }

    val deviceTypeLabel: String get() = when {
        jblModel != null -> jblModel.displayName
        isJbl -> "JBL Speaker"
        supportsA2dp -> "Bluetooth Speaker"
        else -> "Bluetooth Device"
    }
}

enum class JblModel(val displayName: String, val supportsPartyBoost: Boolean) {
    BOOMBOX_3("JBL Boombox 3", true),
    BOOMBOX_2("JBL Boombox 2", true),
    CHARGE_4("JBL Charge 4", true),
    CHARGE_5("JBL Charge 5", true),
    CHARGE_ESSENTIAL("JBL Charge Essential", false),
    FLIP_6("JBL Flip 6", true),
    FLIP_5("JBL Flip 5", true),
    FLIP_4("JBL Flip 4", false),
    XTREME_3("JBL Xtreme 3", true),
    PULSE_5("JBL Pulse 5", true),
    CLIP_4("JBL Clip 4", false),
    GO_3("JBL Go 3", false),
    PARTYBOX_310("JBL PartyBox 310", true),
    PARTYBOX_110("JBL PartyBox 110", true),
    UNKNOWN_JBL("JBL Speaker", false);

    companion object {
        fun fromDeviceName(name: String): JblModel? {
            val upper = name.uppercase()
            if (!upper.contains("JBL")) return null
            return when {
                upper.contains("BOOMBOX 3") || upper.contains("BOOMBOX3") -> BOOMBOX_3
                upper.contains("BOOMBOX 2") || upper.contains("BOOMBOX2") -> BOOMBOX_2
                upper.contains("BOOMBOX") -> BOOMBOX_3
                upper.contains("CHARGE 5") || upper.contains("CHARGE5") -> CHARGE_5
                upper.contains("CHARGE 4") || upper.contains("CHARGE4") -> CHARGE_4
                upper.contains("CHARGE ESSENTIAL") -> CHARGE_ESSENTIAL
                upper.contains("CHARGE") -> CHARGE_5
                upper.contains("FLIP 6") || upper.contains("FLIP6") -> FLIP_6
                upper.contains("FLIP 5") || upper.contains("FLIP5") -> FLIP_5
                upper.contains("FLIP 4") || upper.contains("FLIP4") -> FLIP_4
                upper.contains("FLIP") -> FLIP_6
                upper.contains("XTREME 3") || upper.contains("XTREME3") -> XTREME_3
                upper.contains("XTREME") -> XTREME_3
                upper.contains("PULSE 5") || upper.contains("PULSE5") -> PULSE_5
                upper.contains("PULSE") -> PULSE_5
                upper.contains("CLIP 4") || upper.contains("CLIP4") -> CLIP_4
                upper.contains("CLIP") -> CLIP_4
                upper.contains("GO 3") || upper.contains("GO3") -> GO_3
                upper.contains("GO") -> GO_3
                upper.contains("PARTYBOX 310") -> PARTYBOX_310
                upper.contains("PARTYBOX 110") -> PARTYBOX_110
                upper.contains("PARTYBOX") -> PARTYBOX_310
                else -> UNKNOWN_JBL
            }
        }
    }
}
