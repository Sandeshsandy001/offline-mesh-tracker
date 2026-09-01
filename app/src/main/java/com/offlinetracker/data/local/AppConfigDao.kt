package com.offlinetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppConfigDao {
    @Query("SELECT configValue FROM app_config WHERE configKey = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(config: AppConfigEntity)

    @Query("DELETE FROM app_config WHERE configKey = :key")
    suspend fun delete(key: String)
}
