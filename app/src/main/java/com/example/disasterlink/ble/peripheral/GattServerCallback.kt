package com.example.disasterlink.ble.peripheral

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.disasterlink.ble.util.FragmentationHelper
import com.example.disasterlink.proto.DisasterLinkPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * GATT server callback that handles reads, writes and subscriptions.
 * Performs basic reassembly of fragmented writes and processes incoming DisasterLinkPacket messages.
 */
class GattServerCallback(
    private val gattServerGetter: () -> BluetoothGattServer?,
    private val onPacketReceived: (DisasterLinkPacket) -> Unit
) : BluetoothGattServerCallback() {

    companion object { private const val TAG = "GattServerCallback" }

    // Simple reassembly: map by device hash to list of fragments
    private val reassembly = ConcurrentHashMap<String, MutableList<ByteArray>>()
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        super.onConnectionStateChange(device, status, newState)
        val addr = device.address
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.i(TAG, "Device connected: $addr")
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i(TAG, "Device disconnected: $addr")
            reassembly.remove(addr)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic
    ) {
        super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
        Log.d(TAG, "Read request for ${characteristic.uuid} from ${device.address}")
        @Suppress("DEPRECATION") // characteristic.value is deprecated
        val value = characteristic.value ?: ByteArray(0)
        val response = if (offset <= 0) value else value.copyOfRange(offset, value.size)
        try {
            gattServerGetter()?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
        } catch (t: Throwable) {
            Log.e(TAG, "sendResponse error: ${t.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
        super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
        val addr = device.address
        // Simplified condition: check against standard descriptor values
        val enable = value.isNotEmpty() &&
                (value[0].toInt() == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE[0].toInt() ||
                 value[0].toInt() == BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[0].toInt())
        Log.i(TAG, "Descriptor write from $addr for ${descriptor.characteristic.uuid}, enabled=$enable")
        if (responseNeeded) {
            try {
                gattServerGetter()?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            } catch (t: Throwable) {
                Log.e(TAG, "Descriptor sendResponse error: ${t.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
        // Corrected super call arguments
        super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
        val addr = device.address
        // Removed unnecessary safe call for value.size
        Log.i(TAG, "Write request from $addr for ${characteristic.uuid} length=${value.size}")

        // For simplicity, perform naive append/reassemble on device address and attempt to parse as proto
        scope.launch @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) {
            val list = reassembly.getOrPut(addr) { mutableListOf() }
            list.add(value)
            val assembled = FragmentationHelper.reassemble(list)
            try {
                val packet = DisasterLinkPacket.parseFrom(assembled)
                onPacketReceived(packet)
                // clear buffer for that device
                reassembly.remove(addr)
            } catch (_: Exception) { // Changed 'e' to '_' as it's unused
                // Not yet a complete packet — wait for more fragments
                Log.d(TAG, "Fragment received (so far ${list.size} fragments). Waiting for more.")
            }

            if (responseNeeded) {
                try {
                    gattServerGetter()?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error sending write response: ${t.message}")
                }
            }
        }
    }

    /**
     * Notify all subscribed centrals about a characteristic update.
     * This method expects the characteristic's value to already be set.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun notifyAllClients(characteristic: BluetoothGattCharacteristic, subscribedDevices: Collection<BluetoothDevice>) {
        @Suppress("DEPRECATION") // characteristic.value is deprecated
        val charValue = characteristic.value ?: ByteArray(0)
        for (dev in subscribedDevices) {
            try {
                // Use the newer notifyCharacteristicChanged method available from API 33 (TIRAMISU)
                // For older APIs, the deprecated method is used, which is fine as it's the only option.
                // The @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) on the method handles the permission.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gattServerGetter()?.notifyCharacteristicChanged(dev, characteristic, false, charValue)
                } else {
                    // This is the deprecated call, but it's necessary for older API levels.
                    // Suppressing deprecation warning locally as it's a conditional fallback.
                    @Suppress("DEPRECATION")
                    gattServerGetter()?.notifyCharacteristicChanged(dev, characteristic, false)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "notifyCharacteristicChanged failed for ${dev.address}: ${t.message}")
            }
        }
    }
}
