package com.offlinetracker.protocol

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap
import java.util.Locale

data class QrPairingData(
    val familyId: String,
    val authKey: String,
    val deviceId: String,
    val displayName: String,
    val phoneNumber: String
)

object QrCodeHelper {
    private const val PREFIX = "OMT:PAIR"
    private const val DELIMITER = "|"
    private const val KV_SPLIT = ":"

    fun encodeDeviceQr(
        familyId: String,
        authKey: String,
        deviceId: String,
        displayName: String,
        phoneNumber: String
    ): String {
        val cleanPhone = SmsSender.sanitizePhoneNumber(phoneNumber)
        return "$PREFIX$DELIMITER" +
                "FAM:${familyId.trim().uppercase()}$DELIMITER" +
                "KEY:${authKey.trim()}$DELIMITER" +
                "DEV:${deviceId.trim().uppercase()}$DELIMITER" +
                "NAME:${displayName.trim()}$DELIMITER" +
                "PHONE:$cleanPhone"
    }

    fun parseDeviceQr(rawText: String?): QrPairingData? {
        if (rawText.isNullOrBlank()) return null
        val trimmed = rawText.trim()
        if (!trimmed.startsWith(PREFIX)) return null

        val tokens = trimmed.split(DELIMITER)
        if (tokens.size < 5) return null

        val kvMap = mutableMapOf<String, String>()
        for (i in 1 until tokens.size) {
            val parts = tokens[i].split(KV_SPLIT, limit = 2)
            if (parts.size == 2) {
                kvMap[parts[0].trim().uppercase(Locale.US)] = parts[1].trim()
            }
        }

        val familyId = kvMap["FAM"] ?: return null
        val authKey = kvMap["KEY"] ?: return null
        val deviceId = kvMap["DEV"] ?: return null
        val displayName = kvMap["NAME"] ?: "Device $deviceId"
        val phoneNumber = kvMap["PHONE"] ?: return null

        if (familyId.isBlank() || authKey.isBlank() || deviceId.isBlank() || phoneNumber.isBlank()) {
            return null
        }

        return QrPairingData(
            familyId = familyId.uppercase(Locale.US),
            authKey = authKey,
            deviceId = deviceId.uppercase(Locale.US),
            displayName = displayName,
            phoneNumber = SmsSender.sanitizePhoneNumber(phoneNumber)
        )
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
