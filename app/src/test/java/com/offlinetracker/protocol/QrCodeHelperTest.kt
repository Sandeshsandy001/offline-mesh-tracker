package com.offlinetracker.protocol

import org.junit.Assert.*
import org.junit.Test

class QrCodeHelperTest {

    @Test
    fun testEncodeAndParseDeviceQr() {
        val encoded = QrCodeHelper.encodeDeviceQr(
            familyId = "FAM_01",
            authKey = "SECRET_KEY_123",
            deviceId = "DEV_02",
            displayName = "Mom",
            phoneNumber = "+977 981 234 5678"
        )

        assertTrue(encoded.startsWith("OMT:PAIR|"))

        val parsed = QrCodeHelper.parseDeviceQr(encoded)
        assertNotNull(parsed)
        assertEquals("FAM_01", parsed?.familyId)
        assertEquals("SECRET_KEY_123", parsed?.authKey)
        assertEquals("DEV_02", parsed?.deviceId)
        assertEquals("Mom", parsed?.displayName)
        assertEquals("+9779812345678", parsed?.phoneNumber)
    }

    @Test
    fun testParseInvalidQr() {
        assertNull(QrCodeHelper.parseDeviceQr(null))
        assertNull(QrCodeHelper.parseDeviceQr(""))
        assertNull(QrCodeHelper.parseDeviceQr("INVALID_PAYLOAD"))
        assertNull(QrCodeHelper.parseDeviceQr("OMT:PAIR|FAM:FAM_01"))
    }

    @Test
    fun testQrEncodingSanitizesPhone() {
        val encoded = QrCodeHelper.encodeDeviceQr(
            familyId = "FAM_01",
            authKey = "KEY",
            deviceId = "DEV_01",
            displayName = "Dad",
            phoneNumber = "(984) 123-4567"
        )

        val parsed = QrCodeHelper.parseDeviceQr(encoded)
        assertNotNull(parsed)
        assertEquals("9841234567", parsed?.phoneNumber)
    }
}
