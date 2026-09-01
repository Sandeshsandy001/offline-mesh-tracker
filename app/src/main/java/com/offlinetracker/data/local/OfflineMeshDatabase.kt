package com.offlinetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FamilyDeviceEntity::class, LocationLogEntity::class, AppConfigEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OfflineMeshDatabase : RoomDatabase() {
    abstract fun familyDeviceDao(): FamilyDeviceDao
    abstract fun locationLogDao(): LocationLogDao
    abstract fun appConfigDao(): AppConfigDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineMeshDatabase? = null

        fun get(context: Context): OfflineMeshDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    OfflineMeshDatabase::class.java,
                    "offline_mesh_tracker.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
