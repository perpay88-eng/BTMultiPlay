package com.btmultiplay.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.btmultiplay.app.BTMultiPlayApp
import com.btmultiplay.app.R
import com.btmultiplay.app.audio.AudioRoutingManager
import com.btmultiplay.app.audio.SyncPlaybackManager
import com.btmultiplay.app.bluetooth.BluetoothConnectionManager
import com.btmultiplay.app.bluetooth.BluetoothScanner
import com.btmultiplay.app.bluetooth.BtDeviceInfo
import com.btmultiplay.app.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class BluetoothConnectionService : Service() {

    private val TAG = "BtConnectionService"
    private val NOTIF_ID = 1

    val scanner by lazy { BluetoothScanner(this) }
    val connectionManager by lazy { BluetoothConnectionManager(this) }
    val audioRoutingManager by lazy { AudioRoutingManager(this) }
    val syncPlaybackManager by lazy { SyncPlaybackManager(this, audioRoutingManager) }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothConnectionService = this@BluetoothConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Managing Bluetooth connections…"))
        observeConnectedDevices()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_AUTO_RECONNECT -> performAutoReconnect()
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun observeConnectedDevices() {
        scope.launch {
            connectionManager.connectedDevices.collectLatest { devices ->
                val count = devices.size
                val text = when (count) {
                    0 -> "No speakers connected"
                    1 -> "Connected: ${devices.values.first().displayName}"
                    else -> "Connected: $count speakers"
                }
                updateNotification(text)
            }
        }
    }

    private fun performAutoReconnect() {
        scope.launch {
            val app = application as BTMultiPlayApp
            val savedDevices = app.deviceRepository.getAutoReconnectDevices()
            Log.d(TAG, "Auto-reconnect: ${savedDevices.size} devices")

            savedDevices.forEach { saved ->
                val info = BtDeviceInfo(
                    address = saved.address,
                    name = saved.name,
                    isJbl = saved.isJbl,
                    supportsA2dp = saved.supportsA2dp,
                    supportsPartyBoost = saved.supportsPartyBoost
                )
                delay(1500) // Stagger reconnects
                connectionManager.connectDevice(info)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val mainIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BluetoothConnectionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, BTMultiPlayApp.CHANNEL_BT_SERVICE)
            .setContentTitle("BT MultiPlay")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(mainIntent)
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        scope.cancel()
        connectionManager.destroy()
        syncPlaybackManager.destroy()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    companion object {
        const val ACTION_AUTO_RECONNECT = "com.btmultiplay.ACTION_AUTO_RECONNECT"
        const val ACTION_STOP = "com.btmultiplay.ACTION_STOP"
    }
}
