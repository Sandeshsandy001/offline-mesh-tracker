package com.offlinetracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinetracker.data.local.OfflineMeshDatabase
import com.offlinetracker.data.repository.TrackerRepository
import com.offlinetracker.protocol.DeviceIdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val groupId: String = "FAM_01",
    val authKey: String = "SECRET_KEY_123",
    val localDeviceId: String = "",
    val localDisplayName: String = "My Device",
    val localPhoneNumber: String = "",
    val isCompleted: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class OnboardingViewModel(private val database: OfflineMeshDatabase) : ViewModel() {

    private val repository = TrackerRepository(database)
    private val _uiState = MutableStateFlow(OnboardingUiState(localDeviceId = DeviceIdGenerator.generateRandomId()))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    private var isDeviceIdManuallyEdited = false

    init {
        loadExistingSetup()
    }

    private fun loadExistingSetup() {
        viewModelScope.launch(Dispatchers.IO) {
            val isDone = repository.isSetupCompleted()
            val savedGroupId = repository.getConfig(TrackerRepository.KEY_FAMILY_GROUP_ID)
            val savedAuthKey = repository.getConfig(TrackerRepository.KEY_AUTH_KEY)
            val localDev = repository.getLocalDevice()

            val initialDevId = localDev?.deviceId ?: DeviceIdGenerator.generateRandomId()
            if (localDev != null) {
                isDeviceIdManuallyEdited = true
            }

            _uiState.value = _uiState.value.copy(
                isCompleted = isDone,
                groupId = savedGroupId ?: _uiState.value.groupId,
                authKey = savedAuthKey ?: _uiState.value.authKey,
                localDeviceId = initialDevId,
                localDisplayName = localDev?.displayName ?: _uiState.value.localDisplayName,
                localPhoneNumber = localDev?.phoneNumber ?: _uiState.value.localPhoneNumber
            )
        }
    }

    fun onGroupIdChange(value: String) {
        _uiState.value = _uiState.value.copy(groupId = value, errorMessage = null)
    }

    fun onAuthKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(authKey = value, errorMessage = null)
    }

    fun onLocalDeviceIdChange(value: String) {
        isDeviceIdManuallyEdited = true
        _uiState.value = _uiState.value.copy(localDeviceId = value, errorMessage = null)
    }

    fun onLocalDisplayNameChange(value: String) {
        val nextDevId = if (!isDeviceIdManuallyEdited && value.isNotBlank()) {
            DeviceIdGenerator.generateFromDisplayName(value)
        } else {
            _uiState.value.localDeviceId
        }
        _uiState.value = _uiState.value.copy(
            localDisplayName = value,
            localDeviceId = nextDevId,
            errorMessage = null
        )
    }

    fun regenerateDeviceId() {
        val name = _uiState.value.localDisplayName
        val generated = if (name.isNotBlank()) {
            DeviceIdGenerator.generateFromDisplayName(name)
        } else {
            DeviceIdGenerator.generateRandomId()
        }
        isDeviceIdManuallyEdited = false
        _uiState.value = _uiState.value.copy(localDeviceId = generated, errorMessage = null)
    }

    fun onLocalPhoneNumberChange(value: String) {
        _uiState.value = _uiState.value.copy(localPhoneNumber = value, errorMessage = null)
    }

    fun saveConfiguration(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.groupId.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Family Group ID cannot be empty.")
            return
        }
        if (state.authKey.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Secret Auth Key cannot be empty.")
            return
        }
        if (state.localDeviceId.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Device ID cannot be empty.")
            return
        }
        if (state.localPhoneNumber.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Phone Number cannot be empty.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val allDevices = repository.getAllDevices()
            val cleanLocalId = state.localDeviceId.trim().uppercase()
            val conflictingPeer = allDevices.find { !it.isLocalDevice && it.deviceId.equals(cleanLocalId, ignoreCase = true) }
            if (conflictingPeer != null) {
                _uiState.value = state.copy(
                    errorMessage = "Device ID '$cleanLocalId' is already used by '${conflictingPeer.displayName}' in your network. Please choose a unique ID."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.completeSetup(
                    groupId = state.groupId,
                    authKey = state.authKey,
                    localDeviceId = cleanLocalId,
                    localDisplayName = state.localDisplayName,
                    localPhoneNumber = state.localPhoneNumber
                )
                _uiState.value = _uiState.value.copy(isCompleted = true, isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to save configuration."
                )
            }
        }
    }
}
