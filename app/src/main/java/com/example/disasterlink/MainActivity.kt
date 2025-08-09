package com.example.disasterlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.disasterlink.ui.theme.DisasterLinkTheme
import com.example.disasterlink.ui.bluetooth.BluetoothPage
import com.example.disasterlink.ui.bluetooth.BluetoothViewModel
import com.example.disasterlink.ui.home.HomePage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DisasterLinkTheme {
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
        composable("BluetoothPage") {
            val bluetoothViewModel: BluetoothViewModel = viewModel()
            BluetoothPage(
                onNavigateBack = { navController.popBackStack() },
                viewModel = bluetoothViewModel
            )
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
