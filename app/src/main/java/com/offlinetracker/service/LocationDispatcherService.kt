package com.offlinetracker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.offlinetracker.R
import com.offlinetracker.protocol.MeshProtocolParser
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class LocationDispatcherService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
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
                val resolvedLocation = resolveLocationWithTimeout(timeoutMs = 10000L)
                val batteryLevel = getBatteryPercentage()

                if (resolvedLocation != null) {
                    val responsePayload = MeshProtocolParser.buildResponse(
                        familyId = familyId,
                        from = localDevId,
                        to = requesterDevId,
                        lat = resolvedLocation.latitude,
                        lon = resolvedLocation.longitude,
                        acc = resolvedLocation.accuracy,
                        bat = batteryLevel,
                        ts = System.currentTimeMillis()
                    )
                    dispatchSms(replyPhone, responsePayload, subId)
                }
            } finally {
                cleanupAndStop()
            }
        }

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolveLocationWithTimeout(timeoutMs: Long): Location? =
        withContext(Dispatchers.IO) {
            val cts = CancellationTokenSource()

            // 1. Immediately read cached system location
            val cachedDeferred = async {
                try {
                    fusedLocationClient.lastLocation.await()
                } catch (e: Exception) {
                    null
                }
            }

            // 2. Request fresh satellite GPS fix concurrently
            val freshDeferred = async {
                try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cts.token
                    ).await()
                } catch (e: Exception) {
                    null
                }
            }

            // Wait up to timeoutMs for fresh location, fallback to cached
            val freshLocation = withTimeoutOrNull(timeoutMs) { freshDeferred.await() }
            cts.cancel()
            freshLocation ?: cachedDeferred.await()
        }

    private fun dispatchSms(destinationPhone: String, message: String, subId: Int) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (subId != -1) {
                    applicationContext.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                } else {
                    applicationContext.getSystemService(SmsManager::class.java)
                }
            } else {
                @Suppress("DEPRECATION")
                if (subId != -1) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
            }
            smsManager.sendTextMessage(destinationPhone, null, message, null, null)
        } catch (ignored: Exception) {
        }
    }

    private fun getBatteryPercentage(): Int {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else -1
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
