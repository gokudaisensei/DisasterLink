package com.example.disasterlink.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build // Added import for Build version
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Main Screen Composable ---

@Composable
fun HomePage(navController: NavController) { // Added NavController
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val coroutineScope = rememberCoroutineScope()

    var locationText by remember { mutableStateOf("Loading...") }
    var lastUpdatedText by remember { mutableStateOf("Last updated: 08:30 AM") }

    val GREETING_NAME = "Shantanu" // You can make this dynamic later

    val fetchAndUpdateLocation = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // API 33+ (TIRAMISU)
                        geocoder.getFromLocation(
                            location.latitude,
                            location.longitude,
                            1, // maxResults
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addressesList: List<Address>) {
                                    try {
                                        val address = addressesList.firstOrNull()
                                        locationText = if (address != null) {
                                            val city = address.locality
                                            val country = address.countryName
                                            if (!city.isNullOrEmpty() && !country.isNullOrEmpty()) {
                                                "$city, $country"
                                            } else if (!city.isNullOrEmpty()) {
                                                city
                                            } else if (!country.isNullOrEmpty()) {
                                                country
                                            } else {
                                                "${location.latitude.format(2)}, ${location.longitude.format(2)}"
                                            }
                                        } else {
                                            "${location.latitude.format(2)}, ${location.longitude.format(2)}"
                                        }
                                        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                        lastUpdatedText = "Last updated: ${sdf.format(Date(location.time))}"
                                    } catch (e: Exception) {
                                        locationText = "${location.latitude.format(2)}, ${location.longitude.format(2)}"
                                        lastUpdatedText = "Address processing error"
                                    }
                                }

                                override fun onError(errorMessage: String?) {
                                    locationText = "${location.latitude.format(2)}, ${location.longitude.format(2)}"
                                    lastUpdatedText = errorMessage ?: "Geocoding error"
                                }
                            }
                        )
                    } else {
                        // API < 33 (Legacy)
                        @Suppress("DEPRECATION")
                        coroutineScope.launch {
                            try {
                                val addresses = withContext(Dispatchers.IO) {
                                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                }
                                val address = addresses?.firstOrNull()
                                locationText = if (address != null) {
                                    val city = address.locality
                                    val country = address.countryName
                                    if (!city.isNullOrEmpty() && !country.isNullOrEmpty()) {
                                        "$city, $country"
                                    } else if (!city.isNullOrEmpty()) {
                                        city
                                    } else if (!country.isNullOrEmpty()) {
                                        country
                                    } else {
                                        "${location.latitude.format(2)}, ${location.longitude.format(2)}"
                                    }
                                } else {
                                    "${location.latitude.format(2)}, ${location.longitude.format(2)}"
                                }
                                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                lastUpdatedText = "Last updated: ${sdf.format(Date(location.time))}"
                            } catch (e: Exception) {
                                locationText = "${location.latitude.format(2)}, ${location.longitude.format(2)}"
                                lastUpdatedText = "Geocoding error (legacy)"
                            }
                        }
                    }
                } else {
                    locationText = "Location not available"
                    lastUpdatedText = "Enable location services"
                }
            }.addOnFailureListener {
                locationText = "Failed to get location"
                lastUpdatedText = ""
            }
        } else {
            // Permission not granted, handled by launcher
            locationText = "Location permission needed"
            lastUpdatedText = "Tap refresh to grant"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            fetchAndUpdateLocation()
        } else {
            locationText = "Permission Denied"
            lastUpdatedText = "Enable permission in settings"
        }
    }

    val requestLocationPermissionAndFetch = {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) -> {
                fetchAndUpdateLocation()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    LaunchedEffect(Unit) {
        requestLocationPermissionAndFetch()
    }

    Scaffold(
        topBar = { HomeAppBar() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* Does nothing */ },
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Post")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            GreetingHeader(name = GREETING_NAME)
            Spacer(modifier = Modifier.height(16.dp))
            LocationSection(
                location = locationText,
                lastUpdated = lastUpdatedText,
                onRefreshClick = { requestLocationPermissionAndFetch() }
            )
            Spacer(modifier = Modifier.height(24.dp))
            QuickActionsGrid(navController = navController) // Pass NavController
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Community Feed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            CommunityFeed()
        }
    }
}

// Helper to format double to a certain number of decimal places
private fun Double.format(digits: Int) = String.format(Locale.US, "%.${digits}f", this)

// --- Top App Bar ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAppBar() {
    TopAppBar(
        title = {
            Text(
                "DisasterLink",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Mesh active",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.tertiary)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Language,
                    contentDescription = "Mesh Status",
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    )
}

// --- Greeting Section ---

@Composable
fun GreetingHeader(name: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Hello, $name!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "How can we help today?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "User Profile",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// --- Location Section ---

@Composable
fun LocationSection(location: String, lastUpdated: String, onRefreshClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = location,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = lastUpdated,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        IconButton(onClick = { onRefreshClick() }) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Refresh Location",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// --- Quick Actions Grid ---

@Composable
fun QuickActionsGrid(navController: NavController) { // Added NavController
    val actions = listOf(
        QuickAction("Send Message", Icons.Default.Message, "sendMessage"), 
        QuickAction("Broadcast SOS", Icons.Default.Warning, "broadcastSOS"),
        QuickAction("Report Resource Need", Icons.Default.Report, "reportResourceNeed"),
        QuickAction("Share My Status", Icons.Default.Share, "shareMyStatus"),
        QuickAction("Nearby Alerts", Icons.Default.Notifications, "nearbyAlerts"),
        QuickAction("Bluetooth Mesh", Icons.Default.Bluetooth, "BluetoothPage") // MODIFIED: Route for Bluetooth Mesh
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // First row of actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            actions.subList(0, 3).forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    QuickActionCard(
                        title = action.title,
                        icon = action.icon,
                        onTap = {
                            if (action.route == "BluetoothPage") {
                                navController.navigate(action.route)
                            } else if (action.route == "nearbyAlerts") {
                                navController.navigate(action.route)
                            } else {
                                // Handle other actions or leave as does nothing for now
                            }
                        }
                    )
                }
            }
        }
        // Second row of actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            actions.subList(3, 6).forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    QuickActionCard(
                        title = action.title,
                        icon = action.icon,
                        onTap = {
                            if (action.route == "BluetoothPage") {
                                navController.navigate(action.route)
                            } else if (action.route == "nearbyAlerts") {
                                navController.navigate(action.route)
                            } else {
                                // Handle other actions or leave as does nothing for now
                            }
                        }
                    )
                }
            }
        }
    }
}

data class QuickAction(val title: String, val icon: ImageVector, val route: String) // Added route to data class

@Composable
fun QuickActionCard(title: String, icon: ImageVector, onTap: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f) // Ensures the card is square
            .fillMaxWidth() // Fills the width provided by the parent Box
            .clickable { onTap() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                textAlign = TextAlign.Center
            )
        }
    }
}


// --- Community Feed Section ---

@Composable
fun CommunityFeed() {
    LazyColumn(modifier = Modifier.fillMaxHeight()) { // Consider constraints if inside another scrollable
        item {
            FeedItem(
                icon = Icons.Default.Warning,
                title = "Injured at Main Rd.",
                subtitle = "12:05 SOS",
                iconColor = MaterialTheme.colorScheme.error
            )
        }
        item {
            FeedItem(
                icon = Icons.Default.Group,
                title = "Water available at School",
                subtitle = "12:03 Group",
                iconColor = MaterialTheme.colorScheme.primary
            )
        }
        item {
            FeedItem(
                icon = Icons.Default.Verified,
                title = "Riya marked indoors, Storm alert!",
                subtitle = "11:01 Official",
                iconColor = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun FeedItem(icon: ImageVector, title: String, subtitle: String, iconColor: Color) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor
            )
        },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) }
    )
}

// --- Preview ---

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun HomePagePreview() {
    // Wrap with your app's theme for a consistent preview
    // import com.example.disasterlink.ui.theme.YourAppTheme
    // YourAppTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        HomePage(navController = rememberNavController()) // Pass a dummy NavController for preview
    }
    // }
}
