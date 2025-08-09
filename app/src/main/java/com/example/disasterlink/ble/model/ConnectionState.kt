package com.example.disasterlink.ble.model

/**
 * Represents the current BLE connection state with a device.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}
