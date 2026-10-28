package com.offlinetracker.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinetracker.data.local.FamilyDeviceEntity
import com.offlinetracker.data.local.OfflineMeshDatabase
import com.offlinetracker.protocol.DeviceIdGenerator
import com.offlinetracker.protocol.MeshProtocolParser
import com.offlinetracker.protocol.PhoneNumberNormalizer
import com.offlinetracker.protocol.QrPairingData
import com.offlinetracker.protocol.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(private val database: OfflineMeshDatabase) : ViewModel() {

    private val _locatingDevices = MutableStateFlow<Set<String>>(emptySet())

    val allEntities: StateFlow<List<FamilyDeviceEntity>> = database.familyDeviceDao().observeAll()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val familyMembers: StateFlow<List<FamilyMemberUiModel>> = combine(
        database.familyDeviceDao().observeAll(),
        database.locationLogDao().observeAllLogs(),
        _locatingDevices
    ) { devices, logs, locatingSet ->
        devices.map { device ->
            val latestLog = logs.firstOrNull { it.deviceId == device.deviceId }
            FamilyMemberUiModel(
                deviceId = device.deviceId,
                displayName = device.displayName,
                phoneNumber = device.phoneNumber,
                isLocalDevice = device.isLocalDevice,
                lastLatitude = latestLog?.latitude,
                lastLongitude = latestLog?.longitude,
                accuracyMeters = latestLog?.accuracyMeters,
                batteryPercent = latestLog?.batteryPercent,
                lastSeenTimestamp = latestLog?.timestamp,
                isLocating = locatingSet.contains(device.deviceId)
            )
        }
    }.flowOn(Dispatchers.IO)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localDevice: StateFlow<FamilyDeviceEntity?> = database.familyDeviceDao().observeAll()
        .map { list -> list.find { it.isLocalDevice } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _familyId = MutableStateFlow("FAM_01")
    val familyId: StateFlow<String> = _familyId.asStateFlow()

    private val _authKey = MutableStateFlow("SECRET_KEY_123")
    val authKey: StateFlow<String> = _authKey.asStateFlow()

    init {
        // Load initial config
        viewModelScope.launch(Dispatchers.IO) {
            database.appConfigDao().get("FAMILY_GROUP_ID")?.let { _familyId.value = it }
            database.appConfigDao().get("AUTH_KEY")?.let { _authKey.value = it }
        }

        // Auto-dismiss loading spinner as soon as a fresh location log arrives for a device
        viewModelScope.launch(Dispatchers.IO) {
            database.locationLogDao().observeAllLogs().collect { logs ->
                val recentDeviceIds = logs
                    .filter { System.currentTimeMillis() - it.timestamp < 30000L }
                    .map { it.deviceId }
                    .toSet()
                if (recentDeviceIds.isNotEmpty()) {
                    _locatingDevices.update { current -> current - recentDeviceIds }
                }
            }
        }
    }

    fun sendLocationPing(context: Context, target: FamilyMemberUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            val famId = database.appConfigDao().get("FAMILY_GROUP_ID") ?: _familyId.value
            val key = database.appConfigDao().get("AUTH_KEY") ?: _authKey.value
            val localDev = database.familyDeviceDao().getLocalDevice() ?: return@launch

            val payload = MeshProtocolParser.buildRequest(
                familyId = famId,
                from = localDev.deviceId,
                to = target.deviceId,
                auth = key
            )

            _locatingDevices.update { it + target.deviceId }

            val success = SmsSender.sendSms(
                context = context,
                destinationPhone = target.phoneNumber,
                message = payload
            )

            if (!success) {
                _locatingDevices.update { it - target.deviceId }
                return@launch
            }

            // Automatically reset locating state after 25 seconds if no response
            launch {
                kotlinx.coroutines.delay(25000L)
                _locatingDevices.update { it - target.deviceId }
            }
        }
    }

    fun addFamilyDevice(id: String, name: String, phone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val allDevices = database.familyDeviceDao().getAll()
            val existingIds = allDevices.map { it.deviceId }
            val cleanId = if (id.isBlank()) {
                DeviceIdGenerator.generateFromDisplayName(name, existingIds)
            } else {
                id.trim().uppercase()
            }

            database.familyDeviceDao().insertOrUpdate(
                FamilyDeviceEntity(
                    deviceId = cleanId,
                    displayName = name.trim().ifBlank { cleanId },
                    phoneNumber = SmsSender.sanitizePhoneNumber(phone),
                    isLocalDevice = false
                )
            )
        }
    }

    fun addScannedDevice(qrData: QrPairingData) {
        viewModelScope.launch(Dispatchers.IO) {
            val allDevices = database.familyDeviceDao().getAll()
            val localDev = allDevices.find { it.isLocalDevice }
            val existingIds = allDevices.map { it.deviceId }

            val rawDevId = qrData.deviceId.trim().uppercase()
            // If the scanned QR has the same ID as local device, or an existing peer with a different phone number,
            // resolve conflict so we never overwrite!
            val safeId = if (rawDevId.equals(localDev?.deviceId, ignoreCase = true)) {
                DeviceIdGenerator.generateFromDisplayName(qrData.displayName, existingIds)
            } else {
                val existingPeer = allDevices.find { it.deviceId.equals(rawDevId, ignoreCase = true) }
                if (existingPeer != null && !PhoneNumberNormalizer.matches(existingPeer.phoneNumber, qrData.phoneNumber)) {
                    DeviceIdGenerator.generateFromDisplayName(qrData.displayName, existingIds)
                } else {
                    rawDevId
                }
            }

            database.familyDeviceDao().insertOrUpdate(
                FamilyDeviceEntity(
                    deviceId = safeId,
                    displayName = qrData.displayName.trim(),
                    phoneNumber = SmsSender.sanitizePhoneNumber(qrData.phoneNumber),
                    isLocalDevice = false
                )
            )
        }
    }

    fun deleteFamilyDevice(deviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.familyDeviceDao().deleteById(deviceId)
        }
    }

    fun openInGoogleMaps(context: Context, member: FamilyMemberUiModel) {
        val lat = member.lastLatitude ?: return
        val lon = member.lastLongitude ?: return
        if (lat == 0.0 && lon == 0.0) return
        val label = Uri.encode("${member.displayName}'s Location")
        val uri = "geo:$lat,$lon?q=$lat,$lon($label)"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val fallbackUri = "https://www.google.com/maps/search/?api=1&query=$lat,$lon"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}
