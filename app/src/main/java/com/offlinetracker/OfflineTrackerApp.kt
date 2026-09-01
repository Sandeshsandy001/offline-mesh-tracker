package com.offlinetracker

import android.app.Application
import com.offlinetracker.data.local.OfflineMeshDatabase

class OfflineTrackerApp : Application() {

    lateinit var database: OfflineMeshDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = OfflineMeshDatabase.get(this)
    }
}
