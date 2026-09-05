package com.offlinetracker.data.repository

import com.offlinetracker.data.local.AppConfigEntity
import com.offlinetracker.data.local.FamilyDeviceEntity
import com.offlinetracker.data.local.LocationLogEntity
import com.offlinetracker.data.local.OfflineMeshDatabase
import kotlinx.coroutines.flow.Flow

class TrackerRepository(private val database: OfflineMeshDatabase) {

    // Config Keys
    companion object {
        const val KEY_FAMILY_GROUP_ID = "FAMILY_GROUP_ID"
        const val KEY_AUTH_KEY = "AUTH_KEY"
        const val KEY_INITIAL_SETUP_DONE = "INITIAL_SETUP_DONE"
    }

    val familyDevices: Flow<List<FamilyDeviceEntity>> =
        database.familyDeviceDao().observeAll()

    suspend fun getLocalDevice(): FamilyDeviceEntity? =
        database.familyDeviceDao().getLocalDevice()

    suspend fun getAllDevices(): List<FamilyDeviceEntity> =
        database.familyDeviceDao().getAll()

    suspend fun insertOrUpdateDevice(device: FamilyDeviceEntity) =
        database.familyDeviceDao().insertOrUpdate(device)

    suspend fun deleteDevice(device: FamilyDeviceEntity) =
        database.familyDeviceDao().delete(device)

    suspend fun deleteDeviceById(deviceId: String) =
        database.familyDeviceDao().deleteById(deviceId)

    suspend fun getLatestLocation(deviceId: String): LocationLogEntity? =
        database.locationLogDao().getLatestLocation(deviceId)

    fun observeLogsForDevice(deviceId: String): Flow<List<LocationLogEntity>> =
        database.locationLogDao().observeLogsForDevice(deviceId)

    suspend fun insertLocationLog(log: LocationLogEntity) =
        database.locationLogDao().insertLog(log)

    suspend fun getConfig(key: String): String? =
        database.appConfigDao().get(key)

    suspend fun setConfig(key: String, value: String) =
        database.appConfigDao().set(AppConfigEntity(configKey = key, configValue = value))

    suspend fun isSetupCompleted(): Boolean =
        database.appConfigDao().get(KEY_INITIAL_SETUP_DONE) == "true"

    suspend fun completeSetup(
        groupId: String,
        authKey: String,
        localDeviceId: String,
        localDisplayName: String,
        localPhoneNumber: String
    ) {
        setConfig(KEY_FAMILY_GROUP_ID, groupId.trim().uppercase())
        setConfig(KEY_AUTH_KEY, authKey.trim())
        setConfig(KEY_INITIAL_SETUP_DONE, "true")

        val oldLocal = getLocalDevice()
        if (oldLocal != null && oldLocal.deviceId != localDeviceId.trim().uppercase()) {
            deleteDeviceById(oldLocal.deviceId)
        }

        val localDevice = FamilyDeviceEntity(
            deviceId = localDeviceId.trim().uppercase(),
            displayName = localDisplayName.trim(),
            phoneNumber = com.offlinetracker.protocol.SmsSender.sanitizePhoneNumber(localPhoneNumber),
            isLocalDevice = true
        )
        insertOrUpdateDevice(localDevice)
    }
}
