package com.example.disasterlink.ble.model

import com.example.disasterlink.ble.util.UuidConstants
import java.util.UUID

/**
 * Maps known characteristic UUIDs to semantic roles.
 */
enum class BleCharacteristicType(val uuid: UUID) {
    STATUS(UuidConstants.CHARACTERISTIC_DEVICE_STATUS),
    MESSAGE(UuidConstants.CHARACTERISTIC_MESSAGE_EXCHANGE),
    NETWORK_INFO(UuidConstants.CHARACTERISTIC_NETWORK_INFO);

    companion object {
        /**
         * Resolves a characteristic UUID to its type, or null if unknown.
         */
        fun fromUuid(uuid: UUID): BleCharacteristicType? {
            return BleCharacteristicType.entries.find { it.uuid == uuid }
        }
    }
}
