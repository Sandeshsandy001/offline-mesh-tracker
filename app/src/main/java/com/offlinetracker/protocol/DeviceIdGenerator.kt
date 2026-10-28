package com.offlinetracker.protocol

import java.util.Locale
import java.util.UUID

object DeviceIdGenerator {

    /**
     * Converts a display name (e.g. "Mom", "Dad", "Uncle Bob") into a clean,
     * uppercase alphanumeric identifier component (e.g. "MOM", "DAD", "UNCLE_BOB").
     */
    fun cleanNameForId(displayName: String?): String {
        if (displayName.isNullOrBlank()) return ""
        val cleaned = displayName.trim()
            .replace(Regex("[^a-zA-Z0-9]+"), "_")
            .trim('_')
            .uppercase(Locale.US)
        return cleaned.take(16)
    }

    /**
     * Generates a unique, non-conflicting Device ID from a display name.
     * E.g. "Mom" -> "DEV_MOM".
     * If "DEV_MOM" already exists in [existingIds], it generates "DEV_MOM_2", "DEV_MOM_3", etc.
     */
    fun generateFromDisplayName(
        displayName: String?,
        existingIds: Collection<String> = emptyList()
    ): String {
        val uppercaseExisting = existingIds.map { it.trim().uppercase(Locale.US) }.toSet()
        val cleanedName = cleanNameForId(displayName)

        val baseId = if (cleanedName.isNotBlank()) {
            "DEV_$cleanedName"
        } else {
            "DEV_MEMBER"
        }

        if (!uppercaseExisting.contains(baseId)) {
            return baseId
        }

        var counter = 2
        while (true) {
            val candidate = "${baseId}_$counter"
            if (!uppercaseExisting.contains(candidate)) {
                return candidate
            }
            counter++
        }
    }

    /**
     * Generates a short, random unique Device ID (e.g. "DEV_4A8F").
     */
    fun generateRandomId(existingIds: Collection<String> = emptyList()): String {
        val uppercaseExisting = existingIds.map { it.trim().uppercase(Locale.US) }.toSet()
        while (true) {
            val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(4).uppercase(Locale.US)
            val candidate = "DEV_$randomSuffix"
            if (!uppercaseExisting.contains(candidate)) {
                return candidate
            }
        }
    }

    /**
     * Checks if a candidate ID conflicts with existing IDs (case-insensitive).
     */
    fun isConflict(candidateId: String?, existingIds: Collection<String>): Boolean {
        if (candidateId.isNullOrBlank()) return false
        val normalizedCandidate = candidateId.trim().uppercase(Locale.US)
        return existingIds.any { it.trim().uppercase(Locale.US) == normalizedCandidate }
    }

    /**
     * Resolves a conflicting ID by appending _2, _3, etc. if needed.
     */
    fun resolveConflict(candidateId: String, existingIds: Collection<String>): String {
        val uppercaseExisting = existingIds.map { it.trim().uppercase(Locale.US) }.toSet()
        val normalized = candidateId.trim().uppercase(Locale.US)
        if (!uppercaseExisting.contains(normalized)) {
            return normalized
        }
        var counter = 2
        while (true) {
            val candidate = "${normalized}_$counter"
            if (!uppercaseExisting.contains(candidate)) {
                return candidate
            }
            counter++
        }
    }
}
