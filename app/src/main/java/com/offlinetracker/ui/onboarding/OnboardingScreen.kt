package com.offlinetracker.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    isSettingsMode: Boolean = false,
    onSetupComplete: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    if (onCancel != null) {
        BackHandler(onBack = onCancel)
    }

    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSettingsMode) "Network Settings" else "Mesh Device Setup",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onCancel != null) {
                        IconButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (onCancel != null) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CellTower,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Zero-Internet Cellular Tracking",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Configure this device to participate in the encrypted offline mesh network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Provisioning Form
            Text("Mesh Network Credentials", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = uiState.groupId,
                onValueChange = { viewModel.onGroupIdChange(it) },
                label = { Text("Family Group ID (e.g. FAM_01)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) }
            )

            OutlinedTextField(
                value = uiState.authKey,
                onValueChange = { viewModel.onAuthKeyChange(it) },
                label = { Text("Shared Secret Auth Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("This Device Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = uiState.localDeviceId,
                onValueChange = { viewModel.onLocalDeviceIdChange(it) },
                label = { Text("Device ID (e.g. DEV_01)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
            )

            OutlinedTextField(
                value = uiState.localDisplayName,
                onValueChange = { viewModel.onLocalDisplayNameChange(it) },
                label = { Text("Device / User Name (e.g. My Phone)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )

            OutlinedTextField(
                value = uiState.localPhoneNumber,
                onValueChange = { viewModel.onLocalPhoneNumberChange(it) },
                label = { Text("SIM Phone Number (e.g. +1234567890)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
            )

            // Setup Checklist
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Provisioning Checklist", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    ChecklistItem(title = "Location Permission", desc = "Choose 'Allow all the time' for zero-interaction background pings.")
                    ChecklistItem(title = "SMS Permissions", desc = "Allow SEND_SMS and RECEIVE_SMS to transmit encrypted coordinate packets.")
                    ChecklistItem(title = "Battery Unrestricted", desc = "Disable battery optimization so OS OEM killers don't terminate receiver.")
                }
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.saveConfiguration(onSuccess = onSetupComplete) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isSettingsMode) "Save Configuration" else "Initialize Mesh Node",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistItem(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
