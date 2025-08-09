package com.example.disasterlink.ble.util

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles BLE MTU negotiation and stores the agreed MTU.
 */
class MtuManager {

    private val _mtuSize = MutableStateFlow(DEFAULT_MTU)
    val mtuSize = _mtuSize.asStateFlow()

    /**
     * Provides the current negotiated MTU synchronously.
     */
    val currentMtu: Int
        get() = _mtuSize.value

    /**
     * Requests the maximum supported MTU from the GATT server.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun requestMaxMtu(gatt: BluetoothGatt) {
        val success = gatt.requestMtu(MAX_MTU)
        Log.d(TAG, "Requested MTU: $MAX_MTU, success: $success")
    }

    /**
     * Updates the stored MTU when negotiation completes.
     */
    fun onMtuChanged(newMtu: Int) {
        _mtuSize.value = newMtu
        Log.d(TAG, "Negotiated MTU: $newMtu")
    }

    companion object {
        private const val TAG = "MtuManager"
        const val DEFAULT_MTU = 23      // Default ATT MTU
        const val MAX_MTU = 517         // Android max supported MTU
    }
}
