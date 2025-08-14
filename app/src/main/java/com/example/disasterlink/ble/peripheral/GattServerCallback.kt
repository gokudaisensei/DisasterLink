package com.example.disasterlink.ble.peripheral

import android.Manifest
import android.bluetooth.*
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.disasterlink.ble.central.ConnectionState
import com.example.disasterlink.ble.util.FragmentationHelper
import com.example.disasterlink.ble.util.MtuManager
import java.util.concurrent.ConcurrentHashMap

class GattServerCallback(
    private val mtuManager: MtuManager,
    private val onConnectionStateChange: (ConnectionState) -> Unit,
    private val onPacketReceived: (ByteArray) -> Unit,
    private val connectedDevices: ConcurrentHashMap<String, BluetoothDevice>,
    private val onFragmentationHelperReady: (FragmentationHelper) -> Unit,
    private val gattServerProvider: () -> BluetoothGattServer?
) : BluetoothGattServerCallback() {

    companion object {
        private const val TAG = "GattServerCallback"
    }

    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        Log.v(
            TAG,
            "onConnectionStateChange device=${device.address} status=$status newState=$newState"
        )
        val state = when (newState) {
            BluetoothProfile.STATE_CONNECTED -> ConnectionState.CONNECTED
            BluetoothProfile.STATE_CONNECTING -> ConnectionState.CONNECTING
            else -> ConnectionState.DISCONNECTED
        }
        onConnectionStateChange(state)
        if (state == ConnectionState.CONNECTED) connectedDevices[device.address] =
            device else connectedDevices.remove(device.address)
    }

    override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
        Log.v(TAG, "onMtuChanged device=${device.address} mtu=$mtu")
        mtuManager.onMtuChanged(mtu)
        val helper = FragmentationHelper(mtu) { bytes -> onPacketReceived(bytes) }
        onFragmentationHelperReady(helper)
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
        Log.v(TAG, "onCharacteristicWriteRequest from ${device.address} len=${value.size}")
        // Use a helper derived from current MTU to add fragment and reassemble
        val helper = FragmentationHelper(mtuManager.mtuSize.value) { full ->
            onPacketReceived(full)
        }
        // notify manager that a helper exists (so manager can use it for sending if desired)
        onFragmentationHelperReady(helper)
        helper.addFragment(value)

        if (responseNeeded) {
            try {
                gattServerProvider()?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null
                )
            } catch (t: Throwable) {
                Log.w(TAG, "sendResponse failed: ${t.message}")
            }
        }
    }
}
