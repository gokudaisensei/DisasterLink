package com.example.disasterlink.ble.central

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.disasterlink.ble.util.BlePermissionHelper
import com.example.disasterlink.ble.util.FragmentationHelper
import com.example.disasterlink.ble.util.MtuManager
import com.example.disasterlink.ble.util.UuidConstants
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnectionState {
    CONNECTED, CONNECTING, DISCONNECTED
}

data class BleDevice(val address: String, val name: String?)

class CentralManager(
    private val context: Context,
    private val mtuManager: MtuManager,
    private val onDeviceFound: (BleDevice) -> Unit,
    private val onPacketReceived: (ByteArray) -> Unit,
    private val onConnectionStateChange: (ConnectionState) -> Unit
) {

    companion object {
        private const val TAG = "CentralManager"
        private val CCCD_UUID: UUID = UuidConstants.DESCRIPTOR_CCCD
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private val isScanning = AtomicBoolean(false)

    // manager-level reference to FragmentationHelper (created by callback when MTU known)
    private var fragmentationHelper: FragmentationHelper? = null

    // Externalized GATT client callback (injected with hooks)
    private val gattCallback =
        GattClientCallback(mtuManager = mtuManager, onConnectionStateChange = { state ->
            onConnectionStateChange(state)
        }, onPacketReceived = { bytes ->
            onPacketReceived(bytes)
        }, onFragmentationHelperReady = { helper ->
            fragmentationHelper = helper
            Log.d(TAG, "FragmentationHelper ready in CentralManager (mtu=${helper.mtu})")
        })

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        Log.d(TAG, "startScan()")
        if (!BlePermissionHelper.hasScanPermission(context)) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission - cannot scan")
            return
        }

        if (scanner == null) {
            Log.e(TAG, "BLE Scanner not supported")
            return
        }

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(UuidConstants.SERVICE_DISASTER_LINK))
                .build()
        )
        val settings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        isScanning.set(true)
        scanner.startScan(filters, settings, scanCallback)
        Log.i(TAG, "Central scanning started")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        Log.d(TAG, "stopScan()")
        if (scanner != null && isScanning.get()) {
            try {
                scanner.stopScan(scanCallback)
            } catch (t: Throwable) {
                Log.w(TAG, "stopScan failed: ${t.message}")
            }
            isScanning.set(false)
            Log.i(TAG, "Central scanning stopped")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        Log.d(TAG, "disconnect()")
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "disconnect/close threw: ${t.message}")
        } finally {
            bluetoothGatt = null
            onConnectionStateChange(ConnectionState.DISCONNECTED)
        }
    }

    /**
     * Send a full payload (will be fragmented according to helper.mtu)
     */
    @SuppressLint("MissingPermission")
    fun sendPacket(payload: ByteArray) {
        Log.d(TAG, "sendPacket(len=${payload.size})")
        val helper = fragmentationHelper
        if (helper == null) {
            Log.e(TAG, "FragmentationHelper not initialized; cannot send")
            return
        }

        val service = bluetoothGatt?.getService(UuidConstants.SERVICE_DISASTER_LINK)
        if (service == null) {
            Log.e(TAG, "Service not found; cannot send")
            return
        }

        val char = service.getCharacteristic(UuidConstants.CHARACTERISTIC_DEVICE_STATUS)
        if (char == null) {
            Log.e(TAG, "Characteristic not found; cannot send")
            return
        }

        val fragments = helper.fragment(payload)
        Log.d(TAG, "Sending ${fragments.size} fragments")
        for (fragment in fragments) {
            char.value = fragment
            val started = bluetoothGatt?.writeCharacteristic(char) ?: false
            if (!started) Log.w(TAG, "writeCharacteristic returned false for a fragment")
            // Slight throttle to avoid overwhelming native queue
            try {
                Thread.sleep(6)
            } catch (_: InterruptedException) {
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "onScanResult device=${result.device.address} name=${result.device.name}")
            try {
                onDeviceFound(BleDevice(result.device.address, result.device.name))
            } catch (t: Throwable) {
            }
            stopScan()
            connectToDevice(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice(device: BluetoothDevice) {
        Log.i(TAG, "Connecting to device ${device.address}")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        onConnectionStateChange(ConnectionState.CONNECTING)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BleDevice) {
        Log.i(TAG, "connect() called for ${device.name ?: "Unknown"} (${device.address})")
        val btDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        if (btDevice == null) {
            Log.e(TAG, "BluetoothDevice not found for address=${device.address}")
            return
        }
        connectToDevice(btDevice)
    }

}
