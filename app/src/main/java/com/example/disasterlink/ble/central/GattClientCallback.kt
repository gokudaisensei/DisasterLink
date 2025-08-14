package com.example.disasterlink.ble.central

import android.Manifest
import android.bluetooth.*
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.disasterlink.ble.util.FragmentationHelper
import com.example.disasterlink.ble.util.MtuManager
import com.example.disasterlink.ble.util.UuidConstants
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Client-side GATT callback. Creates/updates a FragmentationHelper when MTU is known,
 * collects incoming fragments via addFragment(), and notifies the manager via onPacketReceived.
 */
class GattClientCallback(
    private val mtuManager: MtuManager,
    private val onConnectionStateChange: (ConnectionState) -> Unit,
    private val onPacketReceived: (ByteArray) -> Unit,
    private val onFragmentationHelperReady: (FragmentationHelper) -> Unit
) : BluetoothGattCallback() {

    companion object {
        private const val TAG = "GattClientCallback"
        private val CCCD_UUID: UUID = UuidConstants.DESCRIPTOR_CCCD
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var helper: FragmentationHelper? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.v(
            TAG,
            "onConnectionStateChange device=${gatt.device.address} status=$status newState=$newState"
        )
        val state = when (newState) {
            BluetoothProfile.STATE_CONNECTED -> ConnectionState.CONNECTED
            BluetoothProfile.STATE_CONNECTING -> ConnectionState.CONNECTING
            else -> ConnectionState.DISCONNECTED
        }
        onConnectionStateChange(state)

        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG, "connected -> request MTU and discover services")
            try {
                gatt.requestMtu(MtuManager.MAX_MTU)
            } catch (t: Throwable) {
                Log.w(TAG, "requestMtu threw: ${t.message}")
            }
            gatt.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i(TAG, "disconnected: closing gatt")
            try {
                gatt.close()
            } catch (_: Throwable) {
            }
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.v(TAG, "onMtuChanged mtu=$mtu status=$status")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            mtuManager.onMtuChanged(mtu)
            helper = FragmentationHelper(mtu) { full ->
                scope.launch { onPacketReceived(full) }
            }
            helper?.let { onFragmentationHelperReady(it) }
            Log.d(TAG, "FragmentationHelper initialized (mtu=$mtu)")
        } else {
            Log.w(TAG, "MTU negotiation failed with status $status")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.v(TAG, "onServicesDiscovered status=$status")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val service = gatt.getService(UuidConstants.SERVICE_DISASTER_LINK)
            val characteristic =
                service?.getCharacteristic(UuidConstants.CHARACTERISTIC_DEVICE_STATUS)
            if (characteristic != null) {
                val ok = gatt.setCharacteristicNotification(characteristic, true)
                Log.d(TAG, "setCharacteristicNotification returned $ok")
                val cccd = characteristic.getDescriptor(CCCD_UUID) ?: BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_WRITE
                )
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val wrote = gatt.writeDescriptor(cccd)
                Log.d(TAG, "writeDescriptor(ENABLE) returned $wrote")
            } else {
                Log.w(TAG, "DisasterLink characteristic not found")
            }
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
    ) {
        val bytes = characteristic.value
        Log.v(TAG, "onCharacteristicChanged len=${bytes?.size ?: 0}")
        bytes?.let {
            if (helper != null) {
                helper!!.addFragment(it)
            } else {
                // If helper isn't ready yet, create a temporary helper using current MTU so we won't drop fragments
                val tmp = FragmentationHelper(mtuManager.mtuSize.value) { full ->
                    scope.launch {
                        onPacketReceived(full)
                    }
                }
                tmp.addFragment(it)
            }
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
    ) {
        Log.v(TAG, "onCharacteristicRead status=$status")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            characteristic.value?.let { bytes ->
                if (helper != null) helper!!.addFragment(bytes) else onPacketReceived(bytes)
            }
        }
    }
}
