package me.araib.statusshare.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MyService : Service() {
    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            SERVICE_ID,
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            null -> START_NOT_STICKY
            ACTION_START -> START_STICKY
            ACTION_STOP -> START_NOT_STICKY
            else -> throw IllegalArgumentException("Unexpected action received: ${intent.action}")
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Default",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        val manager = getSystemService(NotificationManager::class.java) as NotificationManager
        manager.createNotificationChannel(serviceChannel)
    }

    companion object {
        private const val SERVICE_ID = 123
        private const val NOTIFICATION_CHANNEL_ID = "default channel id"

        const val ACTION_START = "MyService:Start"
        const val ACTION_STOP = "MyService:Stop"
    }
}