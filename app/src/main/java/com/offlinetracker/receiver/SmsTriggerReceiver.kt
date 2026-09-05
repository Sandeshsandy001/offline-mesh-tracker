package com.offlinetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import com.offlinetracker.data.local.FamilyDeviceEntity
import com.offlinetracker.data.local.LocationLogEntity
import com.offlinetracker.data.local.OfflineMeshDatabase
import com.offlinetracker.protocol.MeshMessage
import com.offlinetracker.protocol.MeshProtocolParser
import com.offlinetracker.protocol.PhoneNumberNormalizer
import com.offlinetracker.protocol.SmsSender
import com.offlinetracker.service.LocationResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsTriggerReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OMT:SmsTriggerReceiverWakeLock"
        )?.apply {
            acquire(20000L) // 20s safety limit
        }

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
                    val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1).let { id ->
                        if (id != -1) id else intent.getIntExtra("subscription", -1)
                    }

                    val parsed = MeshProtocolParser.parse(body) ?: continue
                    Log.d(TAG, "Parsed mesh packet: ${parsed.javaClass.simpleName} from $rawSender")

                    when (parsed) {
                        is MeshMessage.Request -> {
                            // Verify Group, Target Device, and Cryptographic Shared Secret
                            if (parsed.familyId != expectedFamilyId) {
                                Log.w(TAG, "Family ID mismatch: expected $expectedFamilyId, got ${parsed.familyId}")
                                continue
                            }
                            if (parsed.toDeviceId != localDevice.deviceId) {
                                Log.d(TAG, "Target device ID mismatch: expected ${localDevice.deviceId}, got ${parsed.toDeviceId}")
                                continue
                            }
                            if (parsed.authKey != expectedAuthKey) {
                                Log.w(TAG, "Authentication key mismatch from $rawSender")
                                continue
                            }

                            // Auto-register or update peer device in local list if not already saved
                            val existingSender = allDevices.find {
                                it.deviceId == parsed.fromDeviceId || PhoneNumberNormalizer.matches(it.phoneNumber, rawSender)
                            }
                            if (existingSender == null) {
                                db.familyDeviceDao().insertOrUpdate(
                                    FamilyDeviceEntity(
                                        deviceId = parsed.fromDeviceId,
                                        displayName = "Device ${parsed.fromDeviceId}",
                                        phoneNumber = rawSender,
                                        isLocalDevice = false
                                    )
                                )
                                Log.d(TAG, "Auto-discovered new family device: ${parsed.fromDeviceId} ($rawSender)")
                            } else if (existingSender.phoneNumber.isBlank()) {
                                db.familyDeviceDao().insertOrUpdate(
                                    existingSender.copy(phoneNumber = rawSender)
                                )
                            }

                            // Acquire location using multi-tier fallback (Fused GPS -> Play Services cache -> Native LocationManager -> DB)
                            val locationResult = LocationResolver.resolveLocation(
                                context = context,
                                db = db,
                                localDeviceId = localDevice.deviceId,
                                timeoutMs = 7000L
                            )

                            // Construct and dispatch response packet
                            val responsePayload = MeshProtocolParser.buildResponse(
                                familyId = expectedFamilyId,
                                from = localDevice.deviceId,
                                to = parsed.fromDeviceId,
                                lat = locationResult.latitude,
                                lon = locationResult.longitude,
                                acc = locationResult.accuracy,
                                bat = locationResult.batteryPercent,
                                ts = locationResult.timestamp
                            )

                            // Dispatch reply SMS directly to the originating network address
                            SmsSender.sendSms(
                                context = context,
                                destinationPhone = rawSender,
                                message = responsePayload,
                                subId = subId
                            )
                        }

                        is MeshMessage.Response -> {
                            if (parsed.familyId != expectedFamilyId) continue
                            if (parsed.toDeviceId != localDevice.deviceId) continue

                            // Persist incoming coordinates
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
                            Log.d(TAG, "Persisted location response for ${parsed.fromDeviceId}")

                            // Auto-register peer device if missing
                            val existingPeer = allDevices.find {
                                it.deviceId == parsed.fromDeviceId || PhoneNumberNormalizer.matches(it.phoneNumber, rawSender)
                            }
                            if (existingPeer == null) {
                                db.familyDeviceDao().insertOrUpdate(
                                    FamilyDeviceEntity(
                                        deviceId = parsed.fromDeviceId,
                                        displayName = "Device ${parsed.fromDeviceId}",
                                        phoneNumber = rawSender,
                                        isLocalDevice = false
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling SMS in SmsTriggerReceiver", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (ignored: Exception) {}
                pendingResult.finish()
            }
        }
    }
}
