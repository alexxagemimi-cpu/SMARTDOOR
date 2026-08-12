package com.exhibition.smartdoorlock.ui.doorcontrol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.exhibition.smartdoorlock.bluetooth.ConnectionState

@Composable
fun DoorControlScreen(
    viewModel: DoorControlViewModel,
    hasBluetoothPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRequestEnableBluetooth: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDevicePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                DoorEvent.AccessGranted -> "Access granted — door unlocked"
                DoorEvent.AccessDenied -> "Access denied"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, shape = RoundedCornerShape(14.dp))
            }
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Door Control", style = MaterialTheme.typography.headlineMedium)

            ConnectionStatusCard(
                state = uiState.connectionState,
                hasPermission = hasBluetoothPermission,
                onRequestPermission = onRequestPermission,
                onConnectClick = {
                    when {
                        !viewModel.isBluetoothSupported() -> { /* surfaced inline below */ }
                        !viewModel.isBluetoothEnabled() -> onRequestEnableBluetooth()
                        else -> {
                            viewModel.refreshPairedDevices()
                            showDevicePicker = true
                        }
                    }
                },
                onDisconnectClick = viewModel::disconnect,
                bluetoothSupported = viewModel.isBluetoothSupported()
            )

            DoorStateCard(doorState = uiState.doorState, lastMessage = uiState.lastMessage)

            PasscodeCard(
                pin = uiState.pinInput,
                enabled = uiState.connectionState is ConnectionState.Connected,
                onPinChange = viewModel::onPinChanged,
                onUnlock = viewModel::sendUnlock,
                onLock = viewModel::sendLock
            )
        }
    }

    if (showDevicePicker) {
        DevicePickerDialog(
            devices = uiState.pairedDevices,
            onDismiss = { showDevicePicker = false },
            onSelect = { device ->
                showDevicePicker = false
                viewModel.connect(device)
            }
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    state: ConnectionState,
    hasPermission: Boolean,
    bluetoothSupported: Boolean,
    onRequestPermission: () -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val (icon, label, tint) = when {
                !bluetoothSupported -> Triple(Icons.Default.BluetoothDisabled, "Bluetooth not supported on this device", MaterialTheme.colorScheme.error)
                state is ConnectionState.Connected -> Triple(Icons.Default.Bluetooth, "Connected · ${state.deviceName}", MaterialTheme.colorScheme.primary)
                state is ConnectionState.Connecting -> Triple(Icons.Default.Bluetooth, "Connecting…", MaterialTheme.colorScheme.onSurfaceVariant)
                state is ConnectionState.Error -> Triple(Icons.Default.BluetoothDisabled, "Error: ${state.message}", MaterialTheme.colorScheme.error)
                else -> Triple(Icons.Default.BluetoothDisabled, "Disconnected", MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = tint)
                Text(label, style = MaterialTheme.typography.titleMedium)
            }

            when {
                !bluetoothSupported -> { /* Nothing more to offer — hardware isn't present. */ }
                !hasPermission -> {
                    Text(
                        "Bluetooth permission is required to connect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    FilledTonalButton(onClick = onRequestPermission) { Text("Grant permission") }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onConnectClick,
                            enabled = state !is ConnectionState.Connected && state !is ConnectionState.Connecting
                        ) { Text("Connect") }
                        OutlinedButton(
                            onClick = onDisconnectClick,
                            enabled = state is ConnectionState.Connected
                        ) { Text("Disconnect") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoorStateCard(doorState: DoorState, lastMessage: String) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (icon, label, color) = when (doorState) {
                DoorState.UNLOCKED -> Triple(Icons.Default.LockOpen, "Unlocked", MaterialTheme.colorScheme.primary)
                DoorState.LOCKED -> Triple(Icons.Default.Lock, "Locked", MaterialTheme.colorScheme.onSurface)
                DoorState.DENIED -> Triple(Icons.Default.Lock, "Access Denied", MaterialTheme.colorScheme.error)
                DoorState.UNKNOWN -> Triple(Icons.Default.Lock, "Unknown", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = color)
                Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Last message: $lastMessage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PasscodeCard(
    pin: String,
    enabled: Boolean,
    onPinChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onLock: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Passcode", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = pin,
                onValueChange = onPinChange,
                label = { Text("Enter passcode") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onUnlock,
                    enabled = enabled && pin.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Unlock") }
                OutlinedButton(
                    onClick = onLock,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { Text("Lock") }
            }
        }
    }
}

@SuppressLint("MissingPermission") // Reading names of already-bonded devices; caller only shows this dialog once BLUETOOTH_CONNECT is granted.
@Composable
private fun DevicePickerDialog(
    devices: List<BluetoothDevice>,
    onDismiss: () -> Unit,
    onSelect: (BluetoothDevice) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select paired device") },
        text = {
            if (devices.isEmpty()) {
                Text("No paired devices found. Pair your HC-05 module in Android Bluetooth settings first, then come back and tap Connect.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(devices, key = { it.address }) { device ->
                        TextButton(onClick = { onSelect(device) }, modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(device.name ?: device.address)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
