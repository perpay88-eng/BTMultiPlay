package com.btmultiplay.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.btmultiplay.app.data.AppDatabase
import com.btmultiplay.app.data.DeviceRepository

class BTMultiPlayApp : Application() {

    lateinit var deviceRepository: DeviceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val db = AppDatabase.getInstance(this)
        deviceRepository = DeviceRepository(db)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_BT_SERVICE,
                "Bluetooth Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains Bluetooth speaker connections"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_BT_ALERTS,
                "Bluetooth Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for Bluetooth events"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(serviceChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val CHANNEL_BT_SERVICE = "bt_service_channel"
        const val CHANNEL_BT_ALERTS = "bt_alerts_channel"

        lateinit var instance: BTMultiPlayApp
            private set
    }
}
