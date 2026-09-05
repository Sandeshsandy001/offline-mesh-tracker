package com.offlinetracker.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinetracker.data.local.FamilyDeviceEntity
import com.offlinetracker.data.local.OfflineMeshDatabase
import com.offlinetracker.protocol.MeshProtocolParser
import com.offlinetracker.protocol.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(private val database: OfflineMeshDatabase) : ViewModel() {

    private val _locatingDevices = MutableStateFlow<Set<String>>(emptySet())

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

    init {
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
            val familyId = database.appConfigDao().get("FAMILY_GROUP_ID") ?: return@launch
            val authKey = database.appConfigDao().get("AUTH_KEY") ?: return@launch
            val localDev = database.familyDeviceDao().getLocalDevice() ?: return@launch

            val payload = MeshProtocolParser.buildRequest(
                familyId = familyId,
                from = localDev.deviceId,
                to = target.deviceId,
                auth = authKey
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
            database.familyDeviceDao().insertOrUpdate(
                FamilyDeviceEntity(
                    deviceId = id.trim().uppercase(),
                    displayName = name.trim(),
                    phoneNumber = SmsSender.sanitizePhoneNumber(phone),
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
