package com.offlinetracker.protocol

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

object SmsSender {
    private const val TAG = "SmsSender"

    /**
     * Sanitizes phone number by removing spaces, brackets, hyphens.
     * Preserves leading '+' for international format.
     */
    fun sanitizePhoneNumber(rawPhone: String?): String {
        if (rawPhone.isNullOrBlank()) return ""
        val trimmed = rawPhone.trim()
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }

    /**
     * Dispatches an SMS message with support for specific subscription IDs,
     * Android 12+ (API 31+) SmsManager APIs, and legacy fallback.
     */
    fun sendSms(
        context: Context,
        destinationPhone: String,
        message: String,
        subId: Int = -1
    ): Boolean {
        val cleanPhone = sanitizePhoneNumber(destinationPhone)
        if (cleanPhone.isBlank()) {
            Log.e(TAG, "Cannot send SMS: destination phone is blank")
            return false
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val systemSmsManager = context.getSystemService(SmsManager::class.java)
                if (subId != -1 && systemSmsManager != null) {
                    systemSmsManager.createForSubscriptionId(subId)
                } else {
                    systemSmsManager ?: @Suppress("DEPRECATION") SmsManager.getDefault()
                }
            } else {
                @Suppress("DEPRECATION")
                if (subId != -1) {
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                } else {
                    SmsManager.getDefault()
                }
            }

            if (message.length > 160) {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanPhone, null, message, null, null)
            }
            Log.d(TAG, "Dispatched SMS to $cleanPhone (length: ${message.length}, subId: $subId)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $cleanPhone", e)
            false
        }
    }
}
