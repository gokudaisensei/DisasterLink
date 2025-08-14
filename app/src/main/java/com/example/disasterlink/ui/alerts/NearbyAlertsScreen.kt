package com.example.disasterlink.ui.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// Sample data class for alerts
// Change distanceKm to timeAgo

data class Alert(val title: String, val description: String, val timeAgo: String)

val sampleAlerts = listOf(
    Alert("Flood Warning", "High water levels detected nearby.", "2 min ago"),
    Alert("Road Blocked", "Main Street is blocked due to debris.", "5 min ago"),
    Alert("Medical Help Needed", "Injury reported at Park Avenue.", "10 min ago"),
    Alert("Power Restored", "Electricity restored in Main Street.", "Addressed"),
    Alert("Blocked Road Cleared", "Debris removed from Main Street.", "Over")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyAlertsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby Alerts") },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Nearby Alerts"
                    )
                }
            )
        }
    ) { padding ->
        if (sampleAlerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No nearby alerts found.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(sampleAlerts) { alert ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(alert.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(alert.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(alert.timeAgo, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
