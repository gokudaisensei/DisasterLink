package com.example.disasterlink.ble.util

import java.util.UUID

object UuidConstants {
    // Service UUID
    val SERVICE_DISASTER_LINK: UUID =
        UUID.fromString("00001101-821a-4224-b073-a2d343717783") // Custom service UUID

    // Characteristic UUIDs
    val CHARACTERISTIC_DEVICE_STATUS: UUID =
        UUID.fromString("00002A01-821a-4224-b073-a2d343717783") // Custom characteristic UUID
    val CHARACTERISTIC_MESSAGE_EXCHANGE: UUID =
        UUID.fromString("00002A02-821a-4224-b073-a2d343717783") // Custom characteristic UUID
    val CHARACTERISTIC_NETWORK_INFO: UUID =
        UUID.fromString("00002A03-821a-4224-b073-a2d343717783") // Custom characteristic UUID

    // Descriptor UUIDs
    val DESCRIPTOR_CCCD: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Standard CCCD UUID
}
