package com.example.disasterlink.ble.peripheral

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.disasterlink.ble.central.ConnectionState
import com.example.disasterlink.ble.util.FragmentationHelper
import com.example.disasterlink.ble.util.MtuManager
import com.example.disasterlink.ble.util.UuidConstants
import java.util.concurrent.ConcurrentHashMap

class PeripheralManager(
    private val context: Context,
    private val mtuManager: MtuManager,
    private val onPacketReceived: (ByteArray) -> Unit,
    private val onConnectionStateChange: (ConnectionState) -> Unit
) {

    companion object {
        private const val TAG = "PeripheralManager"
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private var gattServer: BluetoothGattServer? = null
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()

    private var fragmentationHelper: FragmentationHelper? = null

    private val gattServerCallback = GattServerCallback(
        mtuManager = mtuManager,
        onConnectionStateChange = { state -> onConnectionStateChange(state) },
        onPacketReceived = { bytes -> onPacketReceived(bytes) },
        connectedDevices = connectedDevices,
        onFragmentationHelperReady = { helper ->
            fragmentationHelper = helper
            Log.d(TAG, "FragmentationHelper ready in PeripheralManager (mtu=${helper.mtu})")
        },
        gattServerProvider = { gattServer })

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startGattServer() {
        Log.d(TAG, "startGattServer() called")
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
        if (gattServer == null) {
            Log.e(
                TAG, "openGattServer returned null - device may not support acting as GATT server"
            )
            return
        }
        Log.d(TAG, "GATT server opened")

        val service = BluetoothGattService(
            UuidConstants.SERVICE_DISASTER_LINK, BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val statusChar = BluetoothGattCharacteristic(
            UuidConstants.CHARACTERISTIC_DEVICE_STATUS,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // CCCD descriptor
        val cccd = BluetoothGattDescriptor(
            UuidConstants.DESCRIPTOR_CCCD,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        statusChar.addDescriptor(cccd)

        service.addCharacteristic(statusChar)
        try {
            val added = gattServer!!.addService(service)
            Log.i(TAG, "Service added to GATT server: $added")
        } catch (t: Throwable) {
            Log.e(TAG, "addService failed: ${t.message}")
        }

        // create initial fragmentation helper from current MTU
        fragmentationHelper = FragmentationHelper(mtuManager.mtuSize.value) { full ->
            try {
                onPacketReceived(full)
            } catch (t: Throwable) {
                Log.w(TAG, "onPacketReceived threw: ${t.message}")
            }
        }
        Log.i(TAG, "GATT server started (service added)")
    }

    fun startAdvertising() {
        Log.d(TAG, "startAdvertising() called")
        val adv = advertiser
        if (adv == null) {
            Log.e(
                TAG, "BluetoothLeAdvertiser not available (device may not support BLE advertising)"
            )
            return
        }

        val settings =
            AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).setConnectable(true)
                .build()

        val advertiseData =
            AdvertiseData.Builder().addServiceUuid(ParcelUuid(UuidConstants.SERVICE_DISASTER_LINK))
                .setIncludeTxPowerLevel(true) // Including Tx Power Level is a common practice
                .build()

        val scanResponseData =
            AdvertiseData.Builder()
                .setIncludeDeviceName(true).build()

        try {
            adv.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
            Log.i(TAG, "Advertising started - waiting for callback")
        } catch (t: Throwable) {
            Log.e(TAG, "startAdvertising threw: ${t.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendPacket(payload: ByteArray) {
        Log.d(TAG, "sendPacket(len=${payload.size})")
        val helper = fragmentationHelper
        if (helper == null) {
            Log.e(TAG, "FragmentationHelper not initialized; cannot send")
            return
        }
        val service = gattServer?.getService(UuidConstants.SERVICE_DISASTER_LINK) ?: run {
            Log.e(TAG, "Service not available on gattServer")
            return
        }
        val char = service.getCharacteristic(UuidConstants.CHARACTERISTIC_DEVICE_STATUS) ?: run {
            Log.e(TAG, "Characteristic not found")
            return
        }

        val fragments = helper.fragment(payload)
        Log.d(
            TAG, "Sending ${fragments.size} fragments to ${connectedDevices.size} connected devices"
        )
        for (fragment in fragments) {
            char.value = fragment
            connectedDevices.values.forEach { device ->
                try {
                    gattServer?.notifyCharacteristicChanged(device, char, false)
                    Thread.sleep(8) // consider making this delay configurable or removing if not strictly necessary
                } catch (t: Throwable) {
                    Log.w(TAG, "notifyCharacteristicChanged failed: ${t.message}")
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    fun stop() {
        Log.d(TAG, "stop()")
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (t: Throwable) {
            Log.w(TAG, "stopAdvertising failed: ${t.message}")
        }
        try {
            gattServer?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "gattServer.close failed: ${t.message}")
        }
        connectedDevices.clear()
        Log.i(TAG, "Peripheral stopped")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "Advertising onStartSuccess: $settingsInEffect")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed: $errorCode")
        }
    }
}
