package com.offlinetracker.ui.dashboard

data class FamilyMemberUiModel(
    val deviceId: String,
    val displayName: String,
    val phoneNumber: String,
    val isLocalDevice: Boolean = false,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val accuracyMeters: Float? = null,
    val batteryPercent: Int? = null,
    val lastSeenTimestamp: Long? = null,
    val isLocating: Boolean = false
)
