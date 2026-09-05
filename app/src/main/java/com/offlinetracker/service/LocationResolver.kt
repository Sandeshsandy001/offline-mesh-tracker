package com.offlinetracker.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.offlinetracker.data.local.OfflineMeshDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

data class ResolvedLocationResult(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val batteryPercent: Int,
    val timestamp: Long,
    val isFreshGps: Boolean
)

object LocationResolver {
    private const val TAG = "LocationResolver"

    @SuppressLint("MissingPermission")
    suspend fun resolveLocation(
        context: Context,
        db: OfflineMeshDatabase,
        localDeviceId: String,
        timeoutMs: Long = 7000L
    ): ResolvedLocationResult = withContext(Dispatchers.IO) {
        val battery = getBatteryPercentage(context)
        val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        // 1. Try Google Play Services fresh high-accuracy GPS fix with timeout
        var bestLocation: Location? = null
        var isFresh = false

        try {
            val cts = CancellationTokenSource()
            val freshDeferred = async {
                try {
                    fusedClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cts.token
                    ).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Fused getCurrentLocation failed: ${e.message}")
                    null
                }
            }

            val freshLocation = withTimeoutOrNull(timeoutMs) { freshDeferred.await() }
            cts.cancel()

            if (freshLocation != null) {
                bestLocation = freshLocation
                isFresh = true
                Log.d(TAG, "Obtained fresh high-accuracy GPS fix: ${freshLocation.latitude}, ${freshLocation.longitude}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error waiting for fresh GPS fix", e)
        }

        // 2. Fallback: Google Play Services cached last location
        if (bestLocation == null) {
            try {
                val cached = fusedClient.lastLocation.await()
                if (cached != null) {
                    bestLocation = cached
                    Log.d(TAG, "Using Play Services cached location: ${cached.latitude}, ${cached.longitude}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fused lastLocation failed: ${e.message}")
            }
        }

        // 3. Fallback: Native Android LocationManager (GPS, Network, Passive)
        if (bestLocation == null) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (locationManager != null) {
                    val providers = listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER
                    )
                    for (provider in providers) {
                        try {
                            if (locationManager.isProviderEnabled(provider)) {
                                val loc = locationManager.getLastKnownLocation(provider)
                                if (loc != null) {
                                    if (bestLocation == null || loc.time > bestLocation.time) {
                                        bestLocation = loc
                                        Log.d(TAG, "Using LocationManager ($provider) cached location")
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            Log.w(TAG, "No permission for provider $provider")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error querying provider $provider", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Native LocationManager check failed", e)
            }
        }

        // 4. Fallback: Check local Room DB for previous known position
        if (bestLocation != null) {
            ResolvedLocationResult(
                latitude = bestLocation.latitude,
                longitude = bestLocation.longitude,
                accuracy = bestLocation.accuracy,
                batteryPercent = battery,
                timestamp = if (isFresh) System.currentTimeMillis() else bestLocation.time,
                isFreshGps = isFresh
            )
        } else {
            val lastDbLog = try {
                db.locationLogDao().getLatestLocation(localDeviceId)
            } catch (e: Exception) {
                null
            }

            if (lastDbLog != null) {
                Log.d(TAG, "Using DB fallback location: ${lastDbLog.latitude}, ${lastDbLog.longitude}")
                ResolvedLocationResult(
                    latitude = lastDbLog.latitude,
                    longitude = lastDbLog.longitude,
                    accuracy = lastDbLog.accuracyMeters + 50f, // Add uncertainty indicator for stale DB cache
                    batteryPercent = battery,
                    timestamp = lastDbLog.timestamp,
                    isFreshGps = false
                )
            } else {
                // If completely no GPS data exists yet on device, return 0,0 with accuracy 999
                Log.w(TAG, "No GPS or cached location available anywhere on device")
                ResolvedLocationResult(
                    latitude = 0.0,
                    longitude = 0.0,
                    accuracy = 999f,
                    batteryPercent = battery,
                    timestamp = System.currentTimeMillis(),
                    isFreshGps = false
                )
            }
        }
    }

    fun getBatteryPercentage(context: Context): Int {
        return try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else -1
        } catch (e: Exception) {
            -1
        }
    }
}
