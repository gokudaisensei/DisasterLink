package com.example.disasterlink.ui.bluetooth

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.disasterlink.ble.model.ConnectionState

@SuppressLint("MissingPermission")
@Composable
fun BluetoothPage(
    viewModel: BluetoothViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val scannedDevice by viewModel.scannedDevice.collectAsState()
    val logs by viewModel.logMessages.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Bluetooth Manager", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(8.dp))
        Text("Connection State: $connectionState")

        scannedDevice?.let {
            Text("Device: ${it.name ?: "Unknown"} (${it.address})")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.startCentralScan() }) {
                Text("Scan")
            }
            Button(onClick = { viewModel.startPeripheral() }) {
                Text("Start Peripheral")
            }
            Button(onClick = { viewModel.stopPeripheral() }) {
                Text("Stop Peripheral")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.disconnectCentral() }) {
                Text("Disconnect")
            }
            Button(onClick = { viewModel.sendTestPacket(asPeripheral = false) }) {
                Text("Send (Central)")
            }
            Button(onClick = { viewModel.sendTestPacket(asPeripheral = true) }) {
                Text("Send (Peripheral)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Logs:", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(logs) { log ->
                Text("• $log")
            }
        }
    }
}
