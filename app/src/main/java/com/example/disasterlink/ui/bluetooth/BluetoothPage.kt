package com.example.disasterlink.ui.bluetooth

import android.annotation.SuppressLint
import androidx.compose.foundation.background
// import androidx.compose.foundation.clickable // Unused import
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.disasterlink.ble.central.BleDevice
import com.example.disasterlink.ble.central.ConnectionState
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class) // Added for TopAppBar
@Composable
fun BluetoothPage(
    viewModel: BluetoothViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDevice by viewModel.scannedDevice.collectAsState()
    val logs by viewModel.logMessages.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val scannedDevices by viewModel.discoveredDevices.collectAsState() // 🔹 New flow in ViewModel

    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar messages
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar( // Changed from SmallTopAppBar
                title = { Text("Bluetooth Manager") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Connection Status
            StatusSection(connectionState, connectedDevice)

            Spacer(Modifier.height(16.dp))

            // Central Controls
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Central Mode", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            viewModel.startCentralScan()
                            snackbarMessage = "Started scanning for devices"
                        }) {
                            Text(if (isScanning) "Scanning..." else "Scan")
                        }
                        Button(onClick = {
                            viewModel.disconnectCentral()
                            snackbarMessage = "Disconnected from device"
                        }) {
                            Text("Disconnect")
                        }
                        Button(onClick = {
                            viewModel.sendTestPacket(asPeripheral = false)
                            snackbarMessage = "Sent test packet (Central)"
                        }) {
                            Text("Send Data")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Discovered Devices:", style = MaterialTheme.typography.titleSmall)
                    if (scannedDevices.isEmpty()) {
                        Text("No devices found yet...")
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .padding(top = 8.dp)
                        ) {
                            items(scannedDevices) { device ->
                                DeviceItem(
                                    device = device,
                                    isConnected = connectedDevice?.address == device.address,
                                    onConnect = {
                                        viewModel.connectToDevice(device)
                                        snackbarMessage = "Connecting to ${device.name ?: "Unknown"}"
                                    },
                                    onDisconnect = {
                                        viewModel.disconnectCentral()
                                        snackbarMessage = "Disconnected from ${device.name ?: "Unknown"}"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Peripheral Controls
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Peripheral Mode", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            viewModel.startPeripheral()
                            snackbarMessage = "Peripheral advertising started"
                        }) {
                            Text(if (isAdvertising) "Advertising..." else "Start Peripheral")
                        }
                        Button(onClick = {
                            viewModel.stopPeripheral()
                            snackbarMessage = "Peripheral stopped"
                        }) {
                            Text("Stop Peripheral")
                        }
                        Button(onClick = {
                            viewModel.sendTestPacket(asPeripheral = true)
                            snackbarMessage = "Sent test packet (Peripheral)"
                        }) {
                            Text("Send Data")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Logs
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Logs", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (logs.isEmpty()) {
                        Text("No logs yet...")
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .background(Color(0xFF1E1E1E))
                                .padding(8.dp)
                        ) {
                            items(logs) { log ->
                                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                Text(
                                    text = "[$timestamp] $log",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSection(
    connectionState: ConnectionState,
    scannedDevice: BleDevice?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (connectionState) {
                    ConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                    ConnectionState.CONNECTING -> Icons.Default.Bluetooth
                    else -> Icons.Default.BluetoothDisabled
                },
                contentDescription = null,
                tint = when (connectionState) {
                    ConnectionState.CONNECTED -> Color.Green
                    ConnectionState.CONNECTING -> Color.Blue
                    else -> Color.Gray
                }
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Status: $connectionState", style = MaterialTheme.typography.bodyMedium)
                scannedDevice?.let {
                    Text("Connected to: ${it.name ?: "Unknown"} (${it.address})")
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: BleDevice,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(device.name ?: "Unknown Device", style = MaterialTheme.typography.bodyMedium)
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (isConnected) {
                Button(onClick = onDisconnect, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("Disconnect", color = Color.White)
                }
            } else {
                Button(onClick = onConnect) {
                    Text("Connect")
                }
            }
        }
    }
}
