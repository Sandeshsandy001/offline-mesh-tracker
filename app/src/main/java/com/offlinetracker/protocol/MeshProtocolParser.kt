package com.offlinetracker.protocol

import java.util.Locale

object MeshProtocolParser {
    private const val DELIMITER = "|"
    private const val KV_SPLIT = ":"

    fun buildRequest(familyId: String, from: String, to: String, auth: String): String {
        return "$familyId${DELIMITER}TYPE:REQ${DELIMITER}FROM:$from${DELIMITER}TO:$to${DELIMITER}AUTH:$auth"
    }

    fun buildResponse(
        familyId: String,
        from: String,
        to: String,
        lat: Double,
        lon: Double,
        acc: Float,
        bat: Int,
        ts: Long
    ): String {
        val formattedLat = String.format(Locale.US, "%.6f", lat)
        val formattedLon = String.format(Locale.US, "%.6f", lon)
        return "$familyId${DELIMITER}TYPE:RESP${DELIMITER}FROM:$from${DELIMITER}TO:$to${DELIMITER}LAT:$formattedLat${DELIMITER}LON:$formattedLon${DELIMITER}ACC:${acc.toInt()}${DELIMITER}BAT:$bat${DELIMITER}TS:$ts"
    }

    fun parse(raw: String?): MeshMessage? {
        if (raw == null) return null
        val tokens = raw.trim().split(DELIMITER)
        if (tokens.size < 4) return null

        val familyId = tokens[0].trim()
        val kvMap = mutableMapOf<String, String>()

        for (i in 1 until tokens.size) {
            val parts = tokens[i].split(KV_SPLIT, limit = 2)
            if (parts.size == 2) {
                kvMap[parts[0].trim().uppercase(Locale.US)] = parts[1].trim()
            }
        }

        val type = kvMap["TYPE"] ?: return null
        val from = kvMap["FROM"] ?: return null
        val to = kvMap["TO"] ?: return null

        return when (type) {
            "REQ" -> {
                val auth = kvMap["AUTH"] ?: return null
                MeshMessage.Request(familyId, from, to, auth)
            }
            "RESP" -> {
                val lat = kvMap["LAT"]?.toDoubleOrNull() ?: return null
                val lon = kvMap["LON"]?.toDoubleOrNull() ?: return null
                val acc = kvMap["ACC"]?.toFloatOrNull() ?: 0f
                val bat = kvMap["BAT"]?.toIntOrNull() ?: -1
                val ts = kvMap["TS"]?.toLongOrNull() ?: System.currentTimeMillis()
                MeshMessage.Response(familyId, from, to, lat, lon, acc, bat, ts)
            }
            else -> null
        }
    }
}
