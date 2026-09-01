package com.offlinetracker.protocol

import org.junit.Assert.*
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun testNormalizerMatchesExact() {
        assertTrue(PhoneNumberNormalizer.matches("9812345678", "9812345678"))
    }

    @Test
    fun testNormalizerMatchesWithCountryCode() {
        assertTrue(PhoneNumberNormalizer.matches("+9779812345678", "9812345678"))
        assertTrue(PhoneNumberNormalizer.matches("+1-9812345678", "9812345678"))
    }

    @Test
    fun testNormalizerMatchesWithSpecialCharacters() {
        assertTrue(PhoneNumberNormalizer.matches("+977 (981) 234-5678", "09812345678"))
        assertTrue(PhoneNumberNormalizer.matches("98-1234-5678", "9812345678"))
    }

    @Test
    fun testNormalizerRejectsDifferentNumbers() {
        assertFalse(PhoneNumberNormalizer.matches("9812345678", "9812345679"))
        assertFalse(PhoneNumberNormalizer.matches("+1234567890", "+1234567891"))
    }

    @Test
    fun testNormalizerHandlesNullAndBlank() {
        assertFalse(PhoneNumberNormalizer.matches(null, "9812345678"))
        assertFalse(PhoneNumberNormalizer.matches("9812345678", null))
        assertFalse(PhoneNumberNormalizer.matches("", "9812345678"))
        assertFalse(PhoneNumberNormalizer.matches("   ", "9812345678"))
    }
}
