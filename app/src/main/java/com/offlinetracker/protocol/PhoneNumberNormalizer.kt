package com.offlinetracker.protocol

object PhoneNumberNormalizer {
    /**
     * Extracts only digits and matches the last 10 digits
     * to safely compare variations: "+977-98XXXXXXXX", "098XXXXXXXX", "98XXXXXXXX"
     */
    fun matches(phoneA: String?, phoneB: String?): Boolean {
        if (phoneA.isNullOrBlank() || phoneB.isNullOrBlank()) return false
        val cleanA = phoneA.filter { it.isDigit() }
        val cleanB = phoneB.filter { it.isDigit() }
        return if (cleanA.length >= 10 && cleanB.length >= 10) {
            cleanA.takeLast(10) == cleanB.takeLast(10)
        } else {
            cleanA == cleanB
        }
    }
}
