package com.example.disasterlink.ui.status

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMyStatusScreen() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val coroutineScope = rememberCoroutineScope()

    var locationText by remember { mutableStateOf("Tap to get location") }
    var healthStatus by remember { mutableStateOf("Safe") }
    var customMessage by remember { mutableStateOf("") }
    var isShared by remember { mutableStateOf(false) }
    var showSettingsButton by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            coroutineScope.launch {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    locationText = if (location != null) {
                        "Lat: ${location.latitude.format(4)}, Lon: ${location.longitude.format(4)}"
                    } else {
                        "Location not available. Please ensure location services (GPS) are enabled."
                    }
                }
            }
            showSettingsButton = false
        } else {
            locationText = "Permission denied. Please enable location in app settings."
            showSettingsButton = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share My Status") },
                navigationIcon = {
                    Icon(Icons.Default.Person, contentDescription = "Share Status")
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Let others know your current status and location.", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(
                onClick = {
                    when {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                            coroutineScope.launch {
                                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                                    locationText = if (location != null) {
                                        "Lat: ${location.latitude.format(4)}, Lon: ${location.longitude.format(4)}"
                                    } else {
                                        "Location not available. Please ensure location services (GPS) are enabled."
                                    }
                                }
                            }
                            showSettingsButton = false
                        }
                        else -> {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "Get Location")
                Spacer(Modifier.width(8.dp))
                Text(locationText)
            }
            if (showSettingsButton) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Go to Settings")
                }
            }
            Divider()
            Text("Health Status", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HealthStatusRadio("Safe", healthStatus) { healthStatus = it }
                HealthStatusRadio("Injured", healthStatus) { healthStatus = it }
                HealthStatusRadio("Need Help", healthStatus) { healthStatus = it }
            }
            OutlinedTextField(
                value = customMessage,
                onValueChange = { customMessage = it },
                label = { Text("Custom Message (optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Button(
                onClick = { isShared = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = locationText != "Tap to get location"
            ) {
                Text("Share Status")
            }
            if (isShared) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Status Shared!", style = MaterialTheme.typography.titleMedium)
                        Text("Location: $locationText")
                        Text("Health: $healthStatus")
                        if (customMessage.isNotBlank()) Text("Message: $customMessage")
                    }
                }
            }
        }
    }
}

@Composable
fun HealthStatusRadio(option: String, selected: String, onSelect: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected == option,
            onClick = { onSelect(option) }
        )
        Text(option)
    }
}

private fun Double.format(digits: Int) = String.format("%.${digits}f", this)
