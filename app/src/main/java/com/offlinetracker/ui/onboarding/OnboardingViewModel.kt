package com.offlinetracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinetracker.data.local.FamilyDeviceEntity
import com.offlinetracker.data.local.OfflineMeshDatabase
import com.offlinetracker.data.repository.TrackerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val groupId: String = "FAM_01",
    val authKey: String = "SECRET_KEY_123",
    val localDeviceId: String = "DEV_01",
    val localDisplayName: String = "My Device",
    val localPhoneNumber: String = "",
    val isCompleted: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class OnboardingViewModel(private val database: OfflineMeshDatabase) : ViewModel() {

    private val repository = TrackerRepository(database)
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        loadExistingSetup()
    }

    private fun loadExistingSetup() {
        viewModelScope.launch(Dispatchers.IO) {
            val isDone = repository.isSetupCompleted()
            val savedGroupId = repository.getConfig(TrackerRepository.KEY_FAMILY_GROUP_ID)
            val savedAuthKey = repository.getConfig(TrackerRepository.KEY_AUTH_KEY)
            val localDev = repository.getLocalDevice()

            _uiState.value = _uiState.value.copy(
                isCompleted = isDone,
                groupId = savedGroupId ?: _uiState.value.groupId,
                authKey = savedAuthKey ?: _uiState.value.authKey,
                localDeviceId = localDev?.deviceId ?: _uiState.value.localDeviceId,
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
        _uiState.value = _uiState.value.copy(localDeviceId = value, errorMessage = null)
    }

    fun onLocalDisplayNameChange(value: String) {
        _uiState.value = _uiState.value.copy(localDisplayName = value, errorMessage = null)
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
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.completeSetup(
                    groupId = state.groupId,
                    authKey = state.authKey,
                    localDeviceId = state.localDeviceId,
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
