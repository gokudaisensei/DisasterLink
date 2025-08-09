package com.example.disasterlink.ble.model

/**
 * Represents a BLE device discovered during scanning.
 */
data class BleDevice(
    val id: String,          // MAC address or unique identifier
    val name: String?,       // Advertised device name (may be null)
    val rssi: Int            // Signal strength in dBm
)
