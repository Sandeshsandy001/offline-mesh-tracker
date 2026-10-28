package com.offlinetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.offlinetracker.data.local.OfflineMeshDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "Device rebooted ($action). Initializing background mesh listener.")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = OfflineMeshDatabase.get(context)
                    val localDev = db.familyDeviceDao().getLocalDevice()
                    if (localDev != null) {
                        Log.d(TAG, "Offline Mesh Tracker ready on boot for: ${localDev.deviceId}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
