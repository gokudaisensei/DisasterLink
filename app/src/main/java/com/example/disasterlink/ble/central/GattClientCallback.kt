package com.example.disasterlink.ble.central

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.disasterlink.ble.util.MtuManager
import com.example.disasterlink.ble.util.FragmentationHelper
import com.example.disasterlink.proto.DisasterLinkPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Client-side GATT callback. Handles connection events, MTU negotiation,
 * service discovery, and delegates packet reassembly to FragmentationHelper.
 */
class GattClientCallback(
    private val onConnected: (BluetoothGatt) -> Unit,
    private val onDisconnected: (BluetoothGatt) -> Unit,
    private val onPacketReceived: (DisasterLinkPacket) -> Unit,
    private val mtuManager: MtuManager
) : BluetoothGattCallback() {

    companion object {
        private const val TAG = "GattClientCallback"
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    // Create FragmentationHelper instance with MTU from MtuManager
    private val fragmentationHelper by lazy {
        FragmentationHelper(
            mtu = mtuManager.currentMtu,
            onCompleteMessage = { fullPayload ->
                scope.launch {
                    try {
                        val packet = DisasterLinkPacket.parseFrom(fullPayload)
                        onPacketReceived(packet)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse DisasterLinkPacket from reassembled data", e)
                    }
                }
            }
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.i(TAG, "Connected -> requesting MTU & discovering services")
            try {
                gatt.requestMtu(MtuManager.MAX_MTU)
            } catch (t: Throwable) {
                Log.w(TAG, "requestMtu threw: ${t.message}")
            }
            onConnected(gatt)
            gatt.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i(TAG, "Disconnected from device")
            onDisconnected(gatt)
            try {
                gatt.close()
            } catch (_: Throwable) {
            }
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            mtuManager.onMtuChanged(mtu)
            Log.i(TAG, "MTU negotiated: $mtu")
        } else {
            Log.w(TAG, "MTU negotiation failed, status $status")
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        Log.i(TAG, "Services discovered (status=$status)")
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, status)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.w(TAG, "Characteristic read failed: $status")
            return
        }
        val data = characteristic.value ?: return
        handleIncomingData(data)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        super.onCharacteristicChanged(gatt, characteristic)
        val data = characteristic.value ?: return
        handleIncomingData(data)
    }

    /**
     * Handles incoming raw data — either a complete packet or a fragment.
     */
    private fun handleIncomingData(bytes: ByteArray) {
        scope.launch {
            try {
                // Try parsing directly
                val packet = DisasterLinkPacket.parseFrom(bytes)
                onPacketReceived(packet)
            } catch (_: Exception) {
                // Not a complete packet — pass to FragmentationHelper for reassembly
                fragmentationHelper.addFragment(bytes)
            }
        }
    }
}
