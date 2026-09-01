package com.offlinetracker.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDeviceDao {
    @Query("SELECT * FROM family_devices ORDER BY isLocalDevice DESC, displayName ASC")
    fun observeAll(): Flow<List<FamilyDeviceEntity>>

    @Query("SELECT * FROM family_devices")
    suspend fun getAll(): List<FamilyDeviceEntity>

    @Query("SELECT * FROM family_devices WHERE isLocalDevice = 1 LIMIT 1")
    suspend fun getLocalDevice(): FamilyDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: FamilyDeviceEntity)

    @Delete
    suspend fun delete(device: FamilyDeviceEntity)

    @Query("DELETE FROM family_devices WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)
}
