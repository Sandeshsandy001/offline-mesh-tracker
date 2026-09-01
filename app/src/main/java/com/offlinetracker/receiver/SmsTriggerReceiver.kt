package com.offlinetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.offlinetracker.data.local.LocationLogEntity
import com.offlinetracker.data.local.OfflineMeshDatabase
import com.offlinetracker.protocol.MeshMessage
import com.offlinetracker.protocol.MeshProtocolParser
import com.offlinetracker.protocol.PhoneNumberNormalizer
import com.offlinetracker.service.LocationDispatcherService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = OfflineMeshDatabase.get(context)
                val expectedFamilyId = db.appConfigDao().get("FAMILY_GROUP_ID") ?: return@launch
                val expectedAuthKey = db.appConfigDao().get("AUTH_KEY") ?: return@launch
                val localDevice = db.familyDeviceDao().getLocalDevice() ?: return@launch
                val allDevices = db.familyDeviceDao().getAll()

                for (sms in messages) {
                    val rawSender = sms.originatingAddress ?: continue
                    val body = sms.messageBody ?: continue
                    val subId = sms.subscriptionId

                    val parsed = MeshProtocolParser.parse(body) ?: continue

                    // Validate Sender Phone via 10-digit normalization
                    val senderDevice = allDevices.find {
                        PhoneNumberNormalizer.matches(it.phoneNumber, rawSender)
                    } ?: continue

                    when (parsed) {
                        is MeshMessage.Request -> {
                            if (parsed.familyId != expectedFamilyId) continue
                            if (parsed.toDeviceId != localDevice.deviceId) continue
                            if (parsed.authKey != expectedAuthKey) continue

                            // Launch Foreground Service for on-demand GPS acquisition
                            val serviceIntent = Intent(context, LocationDispatcherService::class.java).apply {
                                putExtra(LocationDispatcherService.EXTRA_REPLY_PHONE, senderDevice.phoneNumber)
                                putExtra(LocationDispatcherService.EXTRA_REQ_DEV_ID, parsed.fromDeviceId)
                                putExtra(LocationDispatcherService.EXTRA_LOCAL_DEV_ID, localDevice.deviceId)
                                putExtra(LocationDispatcherService.EXTRA_FAMILY_ID, expectedFamilyId)
                                putExtra(LocationDispatcherService.EXTRA_SUB_ID, subId)
                            }
                            ContextCompat.startForegroundService(context, serviceIntent)
                        }

                        is MeshMessage.Response -> {
                            if (parsed.familyId != expectedFamilyId) continue
                            if (parsed.toDeviceId != localDevice.deviceId) continue

                            // Persist coordinates from targeted device
                            db.locationLogDao().insertLog(
                                LocationLogEntity(
                                    deviceId = parsed.fromDeviceId,
                                    latitude = parsed.latitude,
                                    longitude = parsed.longitude,
                                    accuracyMeters = parsed.accuracy,
                                    batteryPercent = parsed.battery,
                                    timestamp = parsed.timestamp
                                )
                            )
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
