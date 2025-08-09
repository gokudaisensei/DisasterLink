package com.example.disasterlink.ble.central

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.disasterlink.ble.model.BleDevice
import com.example.disasterlink.ble.model.ConnectionState
import com.example.disasterlink.ble.util.BlePermissionHelper
import com.example.disasterlink.ble.util.FragmentationHelper
import com.example.disasterlink.ble.util.MtuManager
import com.example.disasterlink.ble.util.UuidConstants
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

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

    private lateinit var fragmentationHelper: FragmentationHelper

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (!BlePermissionHelper.hasScanPermission(context)) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission - cannot scan")
            return
        }

        if (scanner == null) {
            Log.e(TAG, "BLE Scanner not supported")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(UuidConstants.SERVICE_DISASTER_LINK))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning.set(true)
        scanner.startScan(filters, settings, scanCallback)
        Log.i(TAG, "Central scanning started")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
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
     * Fragment and write payload to the connected device (as a client).
     * Will write fragments sequentially using writeCharacteristic.
     */
    @SuppressLint("MissingPermission")
    fun sendPacket(payload: ByteArray) {
        if (!::fragmentationHelper.isInitialized) {
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

        val fragments = fragmentationHelper.fragment(payload)
        for (fragment in fragments) {
            char.value = fragment
            val writeStarted = bluetoothGatt?.writeCharacteristic(char) ?: false
            if (writeStarted == false) {
                Log.w(TAG, "writeCharacteristic returned false for a fragment")
            }
            // Note: we don't wait for onCharacteristicWrite here — Android BLE stack handles queueing.
            // If you need strict sequencing, implement a write queue that waits for onCharacteristicWrite callbacks.
            Thread.sleep(5) // small throttle to avoid overwhelming the stack
        }
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                onDeviceFound(BleDevice(result.device.address, result.device.name, result.rssi))
            } catch (t: Throwable) {
                Log.w(TAG, "onDeviceFound threw: ${t.message}")
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
        // Establish GATT connection
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        onConnectionStateChange(ConnectionState.CONNECTING)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val state = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> ConnectionState.CONNECTED
                BluetoothProfile.STATE_CONNECTING -> ConnectionState.CONNECTING
                else -> ConnectionState.DISCONNECTED
            }
            onConnectionStateChange(state)

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                // Immediately request max MTU and discover services
                mtuManager.requestMaxMtu(gatt)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                try {
                    gatt.close()
                } catch (t: Throwable) {
                    Log.w(TAG, "gatt.close() error: ${t.message}")
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged: mtu=$mtu status=$status")
            mtuManager.onMtuChanged(mtu)
            // initialize FragmentationHelper with negotiated mtu
            fragmentationHelper = FragmentationHelper(mtu) { packet ->
                try {
                    onPacketReceived(packet)
                } catch (t: Throwable) {
                    Log.w(TAG, "onPacketReceived threw: ${t.message}")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UuidConstants.SERVICE_DISASTER_LINK)
                val characteristic = service?.getCharacteristic(UuidConstants.CHARACTERISTIC_DEVICE_STATUS)
                if (characteristic != null) {
                    // enable notifications locally
                    val success = gatt.setCharacteristicNotification(characteristic, true)
                    if (!success) {
                        Log.w(TAG, "setCharacteristicNotification returned false")
                    }

                    // write CCCD to enable notifications on the remote peripheral
                    val cccd = characteristic.getDescriptor(CCCD_UUID)
                        ?: BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val wrote = gatt.writeDescriptor(cccd)
                    if (!wrote) {
                        Log.w(TAG, "writeDescriptor(ENABLED) returned false")
                    }
                } else {
                    Log.w(TAG, "DisasterLink service/characteristic not found on device")
                }
            } else {
                Log.w(TAG, "onServicesDiscovered status != GATT_SUCCESS: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value ?: return
            if (::fragmentationHelper.isInitialized) {
                fragmentationHelper.addFragment(bytes)
            } else {
                Log.w(TAG, "fragmentationHelper not initialized yet; dropping fragment")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // Optional: handle write confirmation if you implement a write queue.
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Characteristic write returned status $status")
            }
        }
    }
}
