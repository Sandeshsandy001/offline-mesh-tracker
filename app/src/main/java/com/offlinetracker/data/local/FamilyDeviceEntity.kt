package com.offlinetracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_devices")
data class FamilyDeviceEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val phoneNumber: String,
    val isLocalDevice: Boolean = false
)
