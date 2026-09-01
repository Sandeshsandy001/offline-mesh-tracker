package com.offlinetracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_logs",
    indices = [
        Index(value = ["deviceId"]),
        Index(value = ["timestamp"])
    ]
)
data class LocationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val batteryPercent: Int,
    val timestamp: Long
)
