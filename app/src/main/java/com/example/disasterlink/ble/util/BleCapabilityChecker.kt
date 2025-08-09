package com.example.disasterlink.ble.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object BleCapabilityChecker {

    /**
     * Checks if the device supports Bluetooth LE.
     */
    fun isBleSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * Returns the BluetoothAdapter, or null if Bluetooth is not supported.
     */
    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        return bluetoothManager?.adapter
    }

    /**
     * Checks if Bluetooth is enabled.
     */
    fun isBluetoothEnabled(context: Context): Boolean {
        return getBluetoothAdapter(context)?.isEnabled == true
    }

    /**
     * Returns an Intent to request enabling Bluetooth.
     */
    fun getEnableBluetoothIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }
}
