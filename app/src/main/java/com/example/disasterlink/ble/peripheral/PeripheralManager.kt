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

    private val bluetoothManager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private var gattServer: BluetoothGattServer? = null
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()

    private lateinit var fragmentationHelper: FragmentationHelper

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startGattServer() {
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(
            UuidConstants.SERVICE_DISASTER_LINK,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val statusChar = BluetoothGattCharacteristic(
            UuidConstants.CHARACTERISTIC_DEVICE_STATUS,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // Add CCCD descriptor so clients can subscribe
        val cccd = BluetoothGattDescriptor(
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
        )
        statusChar.addDescriptor(cccd)

        service.addCharacteristic(statusChar)
        gattServer?.addService(service)

        // Initialize fragmentation helper from current MTU state
        fragmentationHelper = FragmentationHelper(mtuManager.mtuSize.value) { fullPacket ->
            try {
                onPacketReceived(fullPacket)
            } catch (t: Throwable) {
                Log.w(TAG, "onPacketReceived threw: ${t.message}")
            }
        }

        Log.i(TAG, "GATT server started (service added)")
    }

    fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(UuidConstants.SERVICE_DISASTER_LINK))
            .setIncludeDeviceName(true)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        Log.i(TAG, "Advertising started")
    }

    /**
     * Send a full payload to all connected centrals using notifications and fragmentation.
     */
    @SuppressLint("MissingPermission")
    fun sendPacket(payload: ByteArray) {
        if (!::fragmentationHelper.isInitialized) {
            Log.e(TAG, "FragmentationHelper not initialized; cannot send")
            return
        }

        val service = gattServer?.getService(UuidConstants.SERVICE_DISASTER_LINK) ?: return
        val char = service.getCharacteristic(UuidConstants.CHARACTERISTIC_DEVICE_STATUS) ?: return
        val fragments = fragmentationHelper.fragment(payload)

        for (fragment in fragments) {
            char.value = fragment
            connectedDevices.values.forEach { device ->
                try {
                    gattServer?.notifyCharacteristicChanged(device, char, false)
                    // small throttle
                    Thread.sleep(8)
                } catch (t: Throwable) {
                    Log.w(TAG, "notifyCharacteristicChanged failed: ${t.message}")
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    fun stop() {
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
            Log.i(TAG, "Advertising success: $settingsInEffect")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed: $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val state = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> ConnectionState.CONNECTED
                BluetoothProfile.STATE_CONNECTING -> ConnectionState.CONNECTING
                else -> ConnectionState.DISCONNECTED
            }
            onConnectionStateChange(state)

            if (state == ConnectionState.CONNECTED) {
                connectedDevices[device.address] = device
            } else {
                connectedDevices.remove(device.address)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            // incoming fragment from client -> feed to fragmentation helper
            if (::fragmentationHelper.isInitialized) {
                fragmentationHelper.addFragment(value)
            } else {
                Log.w(TAG, "FragmentationHelper not initialized; dropping incoming fragment")
            }

            if (responseNeeded) {
                try {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                } catch (t: Throwable) {
                    Log.w(TAG, "sendResponse failed: ${t.message}")
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.d(TAG, "Peripheral onMtuChanged: $mtu")
            mtuManager.onMtuChanged(mtu)
            // update fragmentation helper to new mtu
            if (::fragmentationHelper.isInitialized) {
                fragmentationHelper.mtu = mtu
            } else {
                fragmentationHelper = FragmentationHelper(mtu) { fullPacket ->
                    onPacketReceived(fullPacket)
                }
            }
        }
    }
}
