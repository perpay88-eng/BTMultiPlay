package com.btmultiplay.app.bluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.btmultiplay.app.service.BluetoothConnectionService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, BluetoothConnectionService::class.java).apply {
                action = BluetoothConnectionService.ACTION_AUTO_RECONNECT
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
