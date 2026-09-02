package com.offlinetracker.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val familyList by viewModel.familyMembers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                items(items = familyList, key = { it.deviceId }) { member ->
                    FamilyMemberCard(
                        member = member,
                        onLocateClick = { viewModel.sendLocationPing(context, member) },
                        onMapClick = { viewModel.openInGoogleMaps(context, member) },
                        onDeleteClick = { viewModel.deleteFamilyDevice(member.deviceId) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddDeviceDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { id, name, phone ->
                    viewModel.addFamilyDevice(id, name, phone)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun FamilyMemberCard(
    member: FamilyMemberUiModel,
    onLocateClick: () -> Unit,
    onMapClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val hasLocation = member.lastLatitude != null && member.lastLongitude != null

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

            if (hasLocation) {
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
                                Icons.Default.Send,
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
                        enabled = hasLocation,
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

@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onConfirm: (id: String, name: String, phone: String) -> Unit
) {
    var devId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair Family Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = devId,
                    onValueChange = { devId = it },
                    label = { Text("Device ID (e.g. DEV_02)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Member Name (e.g. Dad)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (devId.isNotBlank() && phone.isNotBlank()) {
                        onConfirm(devId, name, phone)
                    }
                },
                enabled = devId.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
