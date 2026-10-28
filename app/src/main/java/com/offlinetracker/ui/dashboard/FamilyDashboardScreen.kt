package com.offlinetracker.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.offlinetracker.MainActivity
import com.offlinetracker.data.local.FamilyDeviceEntity
import com.offlinetracker.protocol.DeviceIdGenerator
import com.offlinetracker.protocol.PhoneNumberNormalizer
import com.offlinetracker.protocol.QrPairingData
import com.offlinetracker.util.PermanentReliabilityHelper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val familyList by viewModel.familyMembers.collectAsState()
    val allEntities by viewModel.allEntities.collectAsState()
    val localDevice by viewModel.localDevice.collectAsState()
    val familyId by viewModel.familyId.collectAsState()
    val authKey by viewModel.authKey.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    var isBatteryIgnored by remember { mutableStateOf(PermanentReliabilityHelper.isBatteryOptimizationIgnored(context)) }
    var hasBgLocation by remember { mutableStateOf(PermanentReliabilityHelper.hasBackgroundLocation(context)) }
    var showReliabilityBanner by remember { mutableStateOf(true) }

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

    var showAddDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Offline Mesh Tracker", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Zero-Data SMS Network",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (localDevice != null) {
                        IconButton(onClick = { showQrDialog = true }) {
                            Icon(Icons.Default.QrCode2, contentDescription = "Show My QR")
                        }
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Member")
                    }
                    if (onNavigateToSettings != null) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        if (familyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (showReliabilityBanner && (!isBatteryIgnored || !hasBgLocation)) {
                        ReliabilityBanner(
                            isBatteryIgnored = isBatteryIgnored,
                            hasBgLocation = hasBgLocation,
                            onFixBattery = { PermanentReliabilityHelper.requestIgnoreBatteryOptimization(context) },
                            onFixLocation = {
                                (context as? MainActivity)?.requestBackgroundLocation()
                                    ?: PermanentReliabilityHelper.openAppDetailsSettings(context)
                            },
                            onOpenSettings = { PermanentReliabilityHelper.openUnusedAppRestrictionsSettings(context) },
                            onDismiss = { showReliabilityBanner = false }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    Icon(
                        Icons.Default.PermDeviceInformation,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No devices paired in local network.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pair First Device")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showReliabilityBanner && (!isBatteryIgnored || !hasBgLocation)) {
                    item(key = "reliability_banner") {
                        ReliabilityBanner(
                            isBatteryIgnored = isBatteryIgnored,
                            hasBgLocation = hasBgLocation,
                            onFixBattery = { PermanentReliabilityHelper.requestIgnoreBatteryOptimization(context) },
                            onFixLocation = {
                                (context as? MainActivity)?.requestBackgroundLocation()
                                    ?: PermanentReliabilityHelper.openAppDetailsSettings(context)
                            },
                            onOpenSettings = { PermanentReliabilityHelper.openUnusedAppRestrictionsSettings(context) },
                            onDismiss = { showReliabilityBanner = false }
                        )
                    }
                }

                items(items = familyList, key = { it.deviceId }) { member ->
                    FamilyMemberCard(
                        member = member,
                        onLocateClick = { viewModel.sendLocationPing(context, member) },
                        onMapClick = { viewModel.openInGoogleMaps(context, member) },
                        onDeleteClick = { viewModel.deleteFamilyDevice(member.deviceId) },
                        onShowQrClick = if (member.isLocalDevice) { { showQrDialog = true } } else null
                    )
                }
            }
        }

        if (showAddDialog) {
            AddDeviceDialog(
                existingDevices = allEntities,
                localDevice = localDevice,
                onDismiss = { showAddDialog = false },
                onManualConfirm = { id, name, phone ->
                    viewModel.addFamilyDevice(id, name, phone)
                    showAddDialog = false
                },
                onQrScanned = { qrData ->
                    viewModel.addScannedDevice(qrData)
                    showAddDialog = false
                }
            )
        }

        if (showQrDialog && localDevice != null) {
            ShowQrDialog(
                localDevice = localDevice!!,
                familyId = familyId,
                authKey = authKey,
                onDismiss = { showQrDialog = false }
            )
        }
    }
}

@Composable
fun FamilyMemberCard(
    member: FamilyMemberUiModel,
    onLocateClick: () -> Unit,
    onMapClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShowQrClick: (() -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isValidCoordinates = member.lastLatitude != null && member.lastLongitude != null &&
            !(member.lastLatitude == 0.0 && member.lastLongitude == 0.0)
    val isLocationDisabled = member.lastLatitude == 0.0 && member.lastLongitude == 0.0

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Device") },
            text = { Text("Are you sure you want to remove ${member.displayName} (${member.deviceId}) from your trusted network?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (member.isLocalDevice)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (member.isLocalDevice) Icons.Default.Smartphone else Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            member.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        if (member.isLocalDevice) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("This Phone", fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    Text(
                        "${member.deviceId} • ${member.phoneNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                member.batteryPercent?.let { bat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (bat < 20) Icons.Default.BatteryAlert else Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (bat < 20) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "$bat%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bat < 20) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (member.isLocalDevice && onShowQrClick != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onShowQrClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = "Show QR",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (!member.isLocalDevice) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Device",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (isValidCoordinates) {
                val timeSpan = member.lastSeenTimestamp?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    )
                } ?: "Unknown"

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = String.format(
                                Locale.US,
                                "Lat: %.5f, Lon: %.5f",
                                member.lastLatitude,
                                member.lastLongitude
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Accuracy: ${member.accuracyMeters?.toInt()}m • $timeSpan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isLocationDisabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationDisabled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Location is turned OFF on device",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Phone received ping (Battery: ${member.batteryPercent ?: "?"}%), but its GPS toggle is disabled.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "No location ping received yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!member.isLocalDevice) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onLocateClick,
                        modifier = Modifier.weight(1f),
                        enabled = !member.isLocating,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (member.isLocating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Requesting...", fontSize = 13.sp)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Locate", fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onMapClick,
                        modifier = Modifier.weight(1f),
                        enabled = isValidCoordinates,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Google Maps", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceDialog(
    existingDevices: List<FamilyDeviceEntity>,
    localDevice: FamilyDeviceEntity?,
    onDismiss: () -> Unit,
    onManualConfirm: (id: String, name: String, phone: String) -> Unit,
    onQrScanned: (QrPairingData) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Scan QR, 1: Manual
    var devId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isDevIdManuallyEdited by remember { mutableStateOf(false) }

    val existingIds = remember(existingDevices) { existingDevices.map { it.deviceId } }
    val cleanDevId = devId.trim().uppercase(Locale.US)
    val isLocalConflict = localDevice != null && cleanDevId.isNotBlank() && cleanDevId == localDevice.deviceId.uppercase(Locale.US)
    val conflictingPeer = remember(cleanDevId, existingDevices) {
        if (cleanDevId.isNotBlank()) {
            existingDevices.find { !it.isLocalDevice && it.deviceId.uppercase(Locale.US) == cleanDevId }
        } else null
    }
    val isDevIdConflict = isLocalConflict || conflictingPeer != null

    val conflictingPhonePeer = remember(phone, existingDevices) {
        if (phone.isNotBlank()) {
            existingDevices.find { PhoneNumberNormalizer.matches(it.phoneNumber, phone) }
        } else null
    }

    if (selectedTab == 0) {
        // Full screen camera QR scanner modal
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                QrScannerView(
                    onQrScanned = onQrScanned,
                    onDismiss = onDismiss
                )

                // Tab Switcher overlay at top
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp)
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.width(260.dp),
                        containerColor = Color.Transparent
                    ) {
                        Tab(
                            selected = true,
                            onClick = { selectedTab = 0 },
                            text = { Text("Scan QR", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = false,
                            onClick = { selectedTab = 1 },
                            text = { Text("Manual", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }
    } else {
        // Manual form dialog
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column {
                    Text("Pair Family Device", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = false,
                            onClick = { selectedTab = 0 },
                            text = { Text("Scan QR") },
                            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = true,
                            onClick = { selectedTab = 1 },
                            text = { Text("Manual") },
                            icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (!isDevIdManuallyEdited) {
                                devId = DeviceIdGenerator.generateFromDisplayName(it, existingIds)
                            }
                        },
                        label = { Text("Member Name (e.g. Dad, Mom, Sister)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )

                    OutlinedTextField(
                        value = devId,
                        onValueChange = {
                            devId = it
                            isDevIdManuallyEdited = true
                        },
                        label = { Text("Device ID") },
                        supportingText = {
                            if (isDevIdConflict) {
                                Text(
                                    if (isLocalConflict) "Error: Matches this phone's own ID!"
                                    else "Error: Already used by ${conflictingPeer?.displayName}!",
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("Auto-generated unique ID for mesh routing")
                            }
                        },
                        isError = isDevIdConflict,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    devId = DeviceIdGenerator.generateFromDisplayName(name.ifBlank { "MEMBER" }, existingIds)
                                    isDevIdManuallyEdited = false
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Regenerate unique ID")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
                    )

                    // Error Warning Banner for Device ID Conflict
                    if (isDevIdConflict) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isLocalConflict) {
                                        "Cannot use '$cleanDevId': This is already this phone's own ID. Please pick a different ID."
                                    } else {
                                        "Conflict: '$cleanDevId' is already registered for ${conflictingPeer?.displayName} (${conflictingPeer?.phoneNumber}). Saving would overwrite them! Tap the refresh button above to generate a unique ID."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Notice Banner for Duplicate Phone Number
                    if (conflictingPhonePeer != null && !isDevIdConflict) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Notice: This phone number is already saved as '${conflictingPhonePeer.displayName}' (${conflictingPhonePeer.deviceId}).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (devId.isNotBlank() && phone.isNotBlank() && !isDevIdConflict) {
                            onManualConfirm(devId, name.ifBlank { devId }, phone)
                        }
                    },
                    enabled = devId.isNotBlank() && phone.isNotBlank() && !isDevIdConflict
                ) {
                    Text("Save Device")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ReliabilityBanner(
    isBatteryIgnored: Boolean,
    hasBgLocation: Boolean,
    onFixBattery: () -> Unit,
    onFixLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Permanent Background Connectivity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "To ensure this device replies even if the app hasn't been opened for 1 month or 1 year, complete these 1-time settings:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!isBatteryIgnored) {
                    Button(
                        onClick = onFixBattery,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("No Battery Sleep", fontSize = 12.sp)
                    }
                }
                if (!hasBgLocation) {
                    Button(
                        onClick = onFixLocation,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Always Allow GPS", fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Auto-Revoke Off", fontSize = 12.sp)
                }
            }
        }
    }
}
