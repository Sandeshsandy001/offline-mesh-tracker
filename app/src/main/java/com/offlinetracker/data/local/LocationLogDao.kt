package com.offlinetracker.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationLogDao {
    @Query("SELECT * FROM location_logs WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    fun observeLogsForDevice(deviceId: String): Flow<List<LocationLogEntity>>

    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC")
    fun observeAllLogs(): Flow<List<LocationLogEntity>>

    @Query("SELECT * FROM location_logs WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLocation(deviceId: String): LocationLogEntity?

    @Query("SELECT * FROM location_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<LocationLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LocationLogEntity)

    @Query("DELETE FROM location_logs WHERE deviceId = :deviceId")
    suspend fun clearLogsForDevice(deviceId: String)

    @Query("DELETE FROM location_logs")
    suspend fun clearAll()
}
