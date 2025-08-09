package com.example.disasterlink.ble.central

import android.Manifest
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.disasterlink.ble.util.UuidConstants

/**
 * Lightweight ScanCallback that filters are already applied to; forwards results to a listener.
 */
class ScanCallbackHandler(
    private val onDeviceFound: (ScanResult) -> Unit
) : ScanCallback() {

    companion object {
        private const val TAG = "ScanCallbackHandler"
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        // The ScanFilter in CentralManager already narrowed results by service UUID; forward it.
        Log.d(TAG, "Scan result: ${result.device.address} / ${result.device.name}")
        onDeviceFound(result)
    }

    override fun onScanFailed(errorCode: Int) {
        Log.e(TAG, "Scan failed: $errorCode")
    }
}
