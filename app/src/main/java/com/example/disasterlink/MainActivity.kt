package com.example.disasterlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme // Added import
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.disasterlink.ui.theme.DisasterLinkTheme
import com.example.disasterlink.ui.theme.home.BluetoothMeshPage
import com.example.disasterlink.ui.theme.home.HomePage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DisasterLinkTheme {
                // Set the window background color
                window.decorView.setBackgroundColor(MaterialTheme.colorScheme.background.hashCode()) // Added this line

                DisasterLinkApp()
            }
        }
    }
}

@Composable
fun DisasterLinkApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomePage(navController = navController)
        }
        composable("bluetoothMesh") {
            BluetoothMeshPage(onNavigateBack = { navController.popBackStack() })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    DisasterLinkTheme {
        HomePage(navController = rememberNavController())
    }
}
