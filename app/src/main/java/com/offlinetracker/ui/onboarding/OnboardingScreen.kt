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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.offlinetracker.MainActivity
import com.offlinetracker.util.PermanentReliabilityHelper

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBatteryIgnored by remember { mutableStateOf(PermanentReliabilityHelper.isBatteryOptimizationIgnored(context)) }
    var hasBgLocation by remember { mutableStateOf(PermanentReliabilityHelper.hasBackgroundLocation(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryIgnored = PermanentReliabilityHelper.isBatteryOptimizationIgnored(context)
                hasBgLocation = PermanentReliabilityHelper.hasBackgroundLocation(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                value = uiState.localDisplayName,
                onValueChange = { viewModel.onLocalDisplayNameChange(it) },
                label = { Text("Device / User Name (e.g. Dad, Mom, My Phone)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )

            OutlinedTextField(
                value = uiState.localDeviceId,
                onValueChange = { viewModel.onLocalDeviceIdChange(it) },
                label = { Text("Device ID") },
                supportingText = { Text("Auto-generated unique ID for mesh routing") },
                trailingIcon = {
                    IconButton(onClick = { viewModel.regenerateDeviceId() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate ID")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
            )

            OutlinedTextField(
                value = uiState.localPhoneNumber,
                onValueChange = { viewModel.onLocalPhoneNumberChange(it) },
                label = { Text("SIM Phone Number (e.g. +1234567890)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
            )

            // Zero-Maintenance / 1-Year Reliability Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Permanent Background Reliability",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        "OMT stores network keys permanently in SQLite disk storage and requires zero periodic check-ins. Configure the permissions below so Android never hibernates the background listener:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    ReliabilityActionRow(
                        title = "Battery: Unrestricted",
                        desc = "Prevents Android OEM deep sleep from killing background SMS receivers.",
                        isConfigured = isBatteryIgnored,
                        actionLabel = "Allow",
                        onAction = { PermanentReliabilityHelper.requestIgnoreBatteryOptimization(context) }
                    )

                    ReliabilityActionRow(
                        title = "Location: Allow all the time",
                        desc = "Enables background GPS fixes when pings arrive with screen off.",
                        isConfigured = hasBgLocation,
                        actionLabel = "Allow",
                        onAction = {
                            (context as? MainActivity)?.requestBackgroundLocation()
                                ?: PermanentReliabilityHelper.openAppDetailsSettings(context)
                        }
                    )

                    ReliabilityActionRow(
                        title = "Unused App Hibernation",
                        desc = "Turn off 'Pause app activity if unused' so Android never revokes permissions.",
                        isConfigured = false,
                        configuredLabel = "Settings",
                        actionLabel = "Configure",
                        onAction = { PermanentReliabilityHelper.openUnusedAppRestrictionsSettings(context) }
                    )
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
private fun ReliabilityActionRow(
    title: String,
    desc: String,
    isConfigured: Boolean,
    actionLabel: String,
    configuredLabel: String = "Active",
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isConfigured) {
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(configuredLabel, fontSize = 12.sp)
            }
        } else {
            Button(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(actionLabel, fontSize = 12.sp)
            }
        }
    }
}
