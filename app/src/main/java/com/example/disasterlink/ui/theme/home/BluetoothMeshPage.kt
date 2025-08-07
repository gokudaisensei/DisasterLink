package com.example.disasterlink.ui.theme.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DonutSmall
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalCellular0Bar
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.disasterlink.ui.theme.DisasterLinkTheme
import kotlin.math.roundToInt

// --- Data Classes for Device Lists ---
data class DiscoveredBluetoothDevice(
    val id: String,
    val name: String,
    var isConnected: Boolean, // var to allow toggling
    val deviceIcon: ImageVector = Icons.Filled.Router,
    val deviceIconColor: Color = Color.Gray,
    val rssi: Int = -70 // Example RSSI
)

data class PeripheralConnection(
    val id: String,
    val deviceName: String,
    val connectedAt: String, // e.g., "10:35 AM"
    val deviceIcon: ImageVector = Icons.Filled.PhoneAndroid,
    val deviceIconColor: Color = Color.Blue
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothMeshPage(onNavigateBack: () -> Unit) {
    var isBluetoothEnabled by remember { mutableStateOf(true) }
    var connectedCentralDevices by remember { mutableIntStateOf(1) } // Example
    var connectedPeripheralDevices by remember { mutableIntStateOf(2) } // Example
    var isPeripheralActive by remember { mutableStateOf(true) }
    var discoveredDevicesCount by remember { mutableIntStateOf(5) }
    var sentMessagesCount by remember { mutableIntStateOf(120) }
    var receivedMessagesCount by remember { mutableIntStateOf(85) }

    // Placeholder lists for discovered devices and peripheral connections
    val discoveredDevicesList = remember {
        mutableStateListOf(
            DiscoveredBluetoothDevice(id = "AA:BB:CC:11:22:33", name = "Emergency Beacon Alpha", isConnected = false, deviceIcon = Icons.Filled.BluetoothSearching, deviceIconColor = Color(0xFFE91E63)),
            DiscoveredBluetoothDevice(id = "DD:EE:FF:44:55:66", name = "Responder Unit 7", isConnected = true, deviceIcon = Icons.Filled.BluetoothConnected, deviceIconColor = Color.Green),
            DiscoveredBluetoothDevice(id = "GG:HH:II:77:88:99", name = "Field Sensor Beta", isConnected = false)
        )
    }
    val peripheralConnectionsList = remember {
        mutableStateListOf(
            PeripheralConnection(id = "ZZ:YY:XX:00:11:22", deviceName = "Relay Node 1", connectedAt = "10:30 AM", deviceIcon = Icons.Filled.Router, deviceIconColor = Color(0xFF00BCD4)),
            PeripheralConnection(id = "UU:VV:WW:33:44:55", deviceName = "Command Phone", connectedAt = "11:15 AM", deviceIconColor = Color(0xFF795548))
        )
    }
    var isLoadingDeviceAction by remember { mutableStateOf<String?>(null) } // Store ID of device being actioned

    val totalConnectedDevices = connectedCentralDevices + connectedPeripheralDevices + discoveredDevicesList.count { it.isConnected }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Mesh Network") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(0.dp)) // First item for correct spacing with Arrangement.spacedBy

            BluetoothOverallStatusCard(
                isBluetoothEnabled = isBluetoothEnabled,
                onToggleBluetooth = { isBluetoothEnabled = !isBluetoothEnabled },
                totalConnectedDevices = totalConnectedDevices
            )

            if (isBluetoothEnabled) {
                NetworkStatisticsCard(
                    totalConnectedDevices = totalConnectedDevices,
                    discoveredDevicesCount = discoveredDevicesCount,
                    centralDevicesCount = connectedCentralDevices, // You might want to adjust this based on discovered connected
                    peripheralDevicesCount = peripheralConnectionsList.size,
                    sentMessagesCount = sentMessagesCount,
                    receivedMessagesCount = receivedMessagesCount,
                    isBluetoothEnabled = isBluetoothEnabled,
                    isPeripheralActive = isPeripheralActive
                )

                // Discovered Devices Section
                DeviceListSectionHeader(title = "Discovered Devices", count = discoveredDevicesList.size)
                if (discoveredDevicesList.isEmpty()) {
                    EmptyListPlaceholder(message = "No devices found nearby. Ensure Bluetooth is on and try scanning.", icon = Icons.Filled.BluetoothDisabled)
                } else {
                    discoveredDevicesList.forEach { device ->
                        DiscoveredDeviceCard(
                            device = device,
                            isLoading = isLoadingDeviceAction == device.id,
                            onConnectToggle = {
                                // Placeholder: Toggle connection state
                                val index = discoveredDevicesList.indexOf(it)
                                if (index != -1) {
                                    discoveredDevicesList[index] = it.copy(isConnected = !it.isConnected)
                                }
                                // Simulate network call
                                isLoadingDeviceAction = device.id
                                // In a real app, you'd launch a coroutine for the action
                                // and set isLoadingDeviceAction = null when done.
                                // For now, just clear it after a delay (not shown here for brevity)
                            }
                        )
                    }
                }

                // Peripheral Connections Section
                DeviceListSectionHeader(title = "Peripheral Connections", count = peripheralConnectionsList.size)
                if (peripheralConnectionsList.isEmpty()) {
                    EmptyListPlaceholder(message = "No devices connected as peripheral. Enable peripheral mode if needed.", icon = Icons.Filled.ErrorOutline)
                } else {
                    peripheralConnectionsList.forEach { connection ->
                        PeripheralConnectionCard(connection = connection)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp)) // Padding at the bottom
        }
    }
}

@Composable
fun BluetoothOverallStatusCard(
    isBluetoothEnabled: Boolean,
    onToggleBluetooth: () -> Unit,
    totalConnectedDevices: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            BluetoothToggleSection(
                isBluetoothEnabled = isBluetoothEnabled,
                onToggleBluetooth = onToggleBluetooth,
                totalConnectedDevices = totalConnectedDevices
            )
        }
    }
}

@Composable
fun BluetoothToggleSection(
    isBluetoothEnabled: Boolean,
    onToggleBluetooth: () -> Unit,
    totalConnectedDevices: Int
) {
    val theme = MaterialTheme.colorScheme
    val textTheme = MaterialTheme.typography

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .padding(12.dp)
                .background(
                    color = if (isBluetoothEnabled) theme.primary.copy(alpha = 0.1f) else theme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isBluetoothEnabled) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                contentDescription = if (isBluetoothEnabled) "Bluetooth Enabled" else "Bluetooth Disabled",
                tint = if (isBluetoothEnabled) theme.primary else theme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isBluetoothEnabled) "Bluetooth Enabled" else "Bluetooth Disabled",
                style = textTheme.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = getBluetoothStatusDescription(isBluetoothEnabled, totalConnectedDevices),
                style = textTheme.bodySmall.copy(color = theme.onSurface.copy(alpha = 0.7f))
            )
        }
        Switch(
            checked = isBluetoothEnabled,
            onCheckedChange = { onToggleBluetooth() },
            colors = SwitchDefaults.colors(checkedThumbColor = theme.primary)
        )
    }
}

@Composable
fun NetworkStatisticsCard(
    totalConnectedDevices: Int,
    discoveredDevicesCount: Int,
    centralDevicesCount: Int,
    peripheralDevicesCount: Int,
    sentMessagesCount: Int,
    receivedMessagesCount: Int,
    isBluetoothEnabled: Boolean,
    isPeripheralActive: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Network Statistics",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCardItem(
                    title = "Total Devices",
                    value = "$totalConnectedDevices",
                    icon = Icons.Filled.Devices,
                    color = getStatColor(totalConnectedDevices, 3, 1),
                    modifier = Modifier.weight(1f)
                )
                StatCardItem(
                    title = "Discovered",
                    value = "$discoveredDevicesCount",
                    icon = Icons.Filled.DonutSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatCardItem(
                    title = "Central",
                    value = "$centralDevicesCount",
                    icon = Icons.Filled.BluetoothConnected,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCardItem(
                    title = "Peripheral",
                    value = "$peripheralDevicesCount",
                    icon = Icons.Filled.Bluetooth,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                StatCardItem(
                    title = "Msgs Sent",
                    value = "$sentMessagesCount",
                    icon = Icons.AutoMirrored.Filled.Send,
                    color = Color(0xFFFFA000),
                    modifier = Modifier.weight(1f)
                )
                StatCardItem(
                    title = "Msgs Received",
                    value = "$receivedMessagesCount",
                    icon = Icons.Filled.Inbox,
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            NetworkHealthIndicator(
                isBluetoothEnabled = isBluetoothEnabled,
                totalConnectedDevices = totalConnectedDevices,
                isPeripheralActive = isPeripheralActive,
                sentMessagesCount = sentMessagesCount,
                receivedMessagesCount = receivedMessagesCount
            )
        }
    }
}

@Composable
fun StatCardItem(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val theme = MaterialTheme.colorScheme
    val textTheme = MaterialTheme.typography

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = textTheme.titleLarge.copy(fontWeight = FontWeight.Bold, color = color),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                title,
                style = textTheme.labelSmall.copy(color = theme.onSurface.copy(alpha = 0.7f)),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun NetworkHealthIndicator(
    isBluetoothEnabled: Boolean,
    totalConnectedDevices: Int,
    isPeripheralActive: Boolean,
    sentMessagesCount: Int,
    receivedMessagesCount: Int
) {
    val healthScore = calculateNetworkHealth(
        isBluetoothEnabled,
        totalConnectedDevices,
        isPeripheralActive,
        sentMessagesCount > 0,
        receivedMessagesCount > 0
    )
    val healthColor = getHealthColor(healthScore)
    val healthText = getHealthText(healthScore)
    val healthIcon = getHealthIcon(healthScore)
    val textTheme = MaterialTheme.typography

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = healthColor.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, healthColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(healthIcon, contentDescription = "Network Health Status", tint = healthColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Network Health",
                    style = textTheme.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    healthText,
                    style = textTheme.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                )
            }
            Box(
                modifier = Modifier
                    .background(healthColor, shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${(healthScore * 100).roundToInt()}%",
                    style = textTheme.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

// --- Device List Composables ---

@Composable
fun DeviceListSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
fun DiscoveredDeviceCard(
    device: DiscoveredBluetoothDevice,
    isLoading: Boolean,
    onConnectToggle: (DiscoveredBluetoothDevice) -> Unit
) {
    val theme = MaterialTheme.colorScheme
    val textTheme = MaterialTheme.typography

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp), // Spacing between cards
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = BorderStroke(
            1.dp,
            if (device.isConnected) Color.Green.copy(alpha = 0.5f) else theme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(device.deviceIconColor.copy(alpha = 0.1f), shape = MaterialTheme.shapes.medium)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = device.deviceIcon,
                        contentDescription = "Device Type",
                        tint = device.deviceIconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name.ifEmpty { "Unknown Device" },
                        style = textTheme.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ID: ${device.id}",
                        style = textTheme.bodySmall.copy(color = theme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Bluetooth,
                            contentDescription = "Status",
                            tint = if (device.isConnected) Color.Green else theme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (device.isConnected) "Connected" else "Available",
                            style = textTheme.labelSmall.copy(
                                color = if (device.isConnected) Color.Green else theme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Button(
                        onClick = { onConnectToggle(device) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (device.isConnected) theme.errorContainer else theme.primaryContainer,
                            contentColor = if (device.isConnected) theme.onErrorContainer else theme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (device.isConnected) Icons.Filled.LinkOff else Icons.Filled.Link,
                            contentDescription = if (device.isConnected) "Disconnect" else "Connect",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (device.isConnected) "Disconnect" else "Connect", style = textTheme.labelMedium)
                    }
                }
            }
            // You can add more device info here if needed, like RSSI, services, etc.
            // Text("RSSI: ${device.rssi} dBm", style = textTheme.bodySmall)
        }
    }
}

@Composable
fun PeripheralConnectionCard(connection: PeripheralConnection) {
    val theme = MaterialTheme.colorScheme
    val textTheme = MaterialTheme.typography

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = BorderStroke(1.dp, connection.deviceIconColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(connection.deviceIconColor.copy(alpha = 0.1f), shape = MaterialTheme.shapes.medium)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = connection.deviceIcon,
                    contentDescription = "Device Type",
                    tint = connection.deviceIconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connection.deviceName.ifEmpty { "Unknown Device" },
                    style = textTheme.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID: ${connection.id}",
                    style = textTheme.bodySmall.copy(color = theme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connected at: ${connection.connectedAt}",
                    style = textTheme.bodySmall.copy(color = theme.onSurfaceVariant)
                )
            }
            Icon(
                imageVector = Icons.Filled.BluetoothConnected,
                contentDescription = "Connected",
                tint = Color.Green,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun EmptyListPlaceholder(message: String, icon: ImageVector) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Empty List",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// --- Helper Functions ---
fun calculateNetworkHealth(
    isBluetoothEnabled: Boolean,
    totalConnectedDevices: Int,
    isPeripheralActive: Boolean,
    hasSentMessages: Boolean,
    hasReceivedMessages: Boolean
): Double {
    if (!isBluetoothEnabled) return 0.0

    var health = 0.0
    health += 0.2 // Base for BT enabled

    if (totalConnectedDevices > 0) health += 0.3
    if (totalConnectedDevices > 2) health += 0.1 // Bonus for more connections

    if (isPeripheralActive) health += 0.2
    if (hasSentMessages || hasReceivedMessages) health += 0.2

    return health.coerceIn(0.0, 1.0)
}

fun getHealthColor(score: Double): Color {
    return when {
        score >= 0.8 -> Color(0xFF4CAF50) // Green
        score >= 0.6 -> Color(0xFFFFA000) // Orange
        score >= 0.4 -> Color(0xFFFFEB3B) // Yellow (Adjusted for better visibility with white text)
        else -> Color(0xFFF44336) // Red
    }
}

fun getHealthText(score: Double): String {
    return when {
        score >= 0.8 -> "Excellent mesh connectivity"
        score >= 0.6 -> "Good network performance"
        score >= 0.4 -> "Moderate connectivity"
        score >= 0.2 -> "Limited network access"
        else -> "Network unavailable"
    }
}

fun getHealthIcon(score: Double): ImageVector {
    return when {
        score >= 0.8 -> Icons.Filled.SignalCellular4Bar
        score >= 0.6 -> Icons.Filled.SignalCellularAlt // Approx 3 bars
        score >= 0.4 -> Icons.Filled.SignalCellularAlt // Approx 2 bars (using same for variety)
        score >= 0.2 -> Icons.Filled.SignalCellular0Bar
        else -> Icons.Filled.SignalCellular0Bar
    }
}

fun getStatColor(count: Int, greenThreshold: Int, orangeThreshold: Int): Color {
    return when {
        count >= greenThreshold -> Color(0xFF4CAF50) // Green
        count >= orangeThreshold -> Color(0xFFFFA000) // Orange
        else -> Color.Gray // Neutral for low/zero counts
    }
}

fun getBluetoothStatusDescription(isBluetoothEnabled: Boolean, totalDevices: Int): String {
    return if (!isBluetoothEnabled) {
        "Enable Bluetooth to start emergency mesh network"
    } else if (totalDevices > 0) {
        "$totalDevices device(s) connected/available in mesh"
    } else {
        "Ready to connect to emergency response devices"
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 1200)
@Composable
fun BluetoothMeshPagePreview() {
    DisasterLinkTheme {
        BluetoothMeshPage(onNavigateBack = {})
    }
}
