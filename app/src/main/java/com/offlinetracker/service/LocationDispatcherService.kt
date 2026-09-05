package com.offlinetracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.offlinetracker.data.local.OfflineMeshDatabase
import com.offlinetracker.protocol.MeshProtocolParser
import com.offlinetracker.protocol.SmsSender
import kotlinx.coroutines.*

class LocationDispatcherService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "offline_tracker_location_channel"
        const val NOTIFICATION_ID = 2001
        const val EXTRA_REPLY_PHONE = "EXTRA_REPLY_PHONE"
        const val EXTRA_REQ_DEV_ID = "EXTRA_REQ_DEV_ID"
        const val EXTRA_LOCAL_DEV_ID = "EXTRA_LOCAL_DEV_ID"
        const val EXTRA_FAMILY_ID = "EXTRA_FAMILY_ID"
        const val EXTRA_SUB_ID = "EXTRA_SUB_ID"
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildSilentNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            cleanupAndStop()
            return START_NOT_STICKY
        }

        val replyPhone = intent.getStringExtra(EXTRA_REPLY_PHONE)
        val requesterDevId = intent.getStringExtra(EXTRA_REQ_DEV_ID)
        val localDevId = intent.getStringExtra(EXTRA_LOCAL_DEV_ID)
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID)
        val subId = intent.getIntExtra(EXTRA_SUB_ID, -1)

        if (replyPhone.isNullOrBlank() || requesterDevId.isNullOrBlank() ||
            localDevId.isNullOrBlank() || familyId.isNullOrBlank()
        ) {
            cleanupAndStop()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            try {
                val db = OfflineMeshDatabase.get(applicationContext)
                val locationResult = LocationResolver.resolveLocation(
                    context = applicationContext,
                    db = db,
                    localDeviceId = localDevId,
                    timeoutMs = 7000L
                )

                val responsePayload = MeshProtocolParser.buildResponse(
                    familyId = familyId,
                    from = localDevId,
                    to = requesterDevId,
                    lat = locationResult.latitude,
                    lon = locationResult.longitude,
                    acc = locationResult.accuracy,
                    bat = locationResult.batteryPercent,
                    ts = locationResult.timestamp
                )

                SmsSender.sendSms(
                    context = applicationContext,
                    destinationPhone = replyPhone,
                    message = responsePayload,
                    subId = subId
                )
            } finally {
                cleanupAndStop()
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OMT:LocationExecutionWakeLock"
        ).apply {
            acquire(25000L) // Hard fail-safe 25s limit
        }
    }

    private fun cleanupAndStop() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mesh Location Responder",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Handles incoming offline family ping verification"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun buildSilentNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Processing Location Request")
            .setContentText("Responding to authorized family mesh query")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
