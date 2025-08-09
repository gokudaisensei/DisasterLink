package com.example.disasterlink.ble.peripheral

import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.util.Log

class AdvertiseCallbackHandler(
    private val onStarted: (AdvertiseSettings) -> Unit = {},
    private val onFailed: (Int) -> Unit = {}
) : AdvertiseCallback() {

    companion object { private const val TAG = "AdvertiseCbHandler" }

    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
        Log.i(TAG, "Advertise started: $settingsInEffect")
        onStarted(settingsInEffect)
    }

    override fun onStartFailure(errorCode: Int) {
        Log.e(TAG, "Advertise failed: $errorCode")
        onFailed(errorCode)
    }
}
