package com.offlinetracker.protocol

import org.junit.Assert.*
import org.junit.Test

class SmsSenderTest {

    @Test
    fun testSanitizePhoneNumberWithCountryCode() {
        assertEquals("+9779812345678", SmsSender.sanitizePhoneNumber("+977 981 234 5678"))
        assertEquals("+1234567890", SmsSender.sanitizePhoneNumber("+1 (234) 567-890"))
    }

    @Test
    fun testSanitizePhoneNumberWithoutCountryCode() {
        assertEquals("9812345678", SmsSender.sanitizePhoneNumber("9812345678"))
        assertEquals("9812345678", SmsSender.sanitizePhoneNumber("98-1234-5678"))
        assertEquals("09812345678", SmsSender.sanitizePhoneNumber("098-1234-5678"))
    }

    @Test
    fun testSanitizePhoneNumberNullOrBlank() {
        assertEquals("", SmsSender.sanitizePhoneNumber(null))
        assertEquals("", SmsSender.sanitizePhoneNumber(""))
        assertEquals("", SmsSender.sanitizePhoneNumber("   "))
    }
}
