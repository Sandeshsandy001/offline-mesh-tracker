package com.offlinetracker.protocol

sealed class MeshMessage {
    data class Request(
        val familyId: String,
        val fromDeviceId: String,
        val toDeviceId: String,
        val authKey: String
    ) : MeshMessage()

    data class Response(
        val familyId: String,
        val fromDeviceId: String,
        val toDeviceId: String,
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val battery: Int,
        val timestamp: Long
    ) : MeshMessage()
}
