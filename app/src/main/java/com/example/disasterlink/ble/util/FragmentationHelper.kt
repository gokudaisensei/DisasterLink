package com.example.disasterlink.ble.util

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * BLE Fragmentation & Reassembly helper for MTU-limited transfers.
 *
 * Header format: [packetId, totalFragments, fragmentIndex] + payload
 */
class FragmentationHelper(
    mtu: Int,
    private val onCompleteMessage: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "FragmentationHelper"
        private const val HEADER_SIZE = 3

        /**
         * Reassembles a list of raw BLE fragments (with headers) into a single payload.
         * Strips the 3-byte header from each fragment before combining.
         * Assumes fragments are provided in correct order.
         */
        fun reassemble(fragments: List<ByteArray>): ByteArray {
            if (fragments.isEmpty()) return ByteArray(0)
            return fragments.fold(ByteArray(0)) { acc, fragment ->
                if (fragment.size > HEADER_SIZE) {
                    acc + fragment.copyOfRange(HEADER_SIZE, fragment.size)
                } else {
                    acc // skip invalid fragments
                }
            }
        }
    }

    /** Current negotiated MTU (default given at creation). */
    var mtu: Int = mtu
        set(value) {
            Log.d(TAG, "MTU updated from $field to $value")
            field = value
        }

    private val incomingBuffers = ConcurrentHashMap<Int, MutableList<ByteArray>>()

    /**
     * Splits the given payload into MTU-sized fragments.
     */
    fun fragment(payload: ByteArray): List<ByteArray> {
        val maxPayloadPerFragment = mtu - HEADER_SIZE
        if (maxPayloadPerFragment <= 0) {
            Log.e(TAG, "Invalid MTU ($mtu) for fragmentation")
            return emptyList()
        }

        val totalFragments = ceil(payload.size / maxPayloadPerFragment.toDouble()).toInt()
        val packetId = (System.currentTimeMillis() and 0xFF).toInt()

        val fragments = mutableListOf<ByteArray>()
        for (i in 0 until totalFragments) {
            val start = i * maxPayloadPerFragment
            val end = minOf(start + maxPayloadPerFragment, payload.size)
            val chunk = payload.copyOfRange(start, end)

            val header = byteArrayOf(
                packetId.toByte(),
                totalFragments.toByte(),
                i.toByte()
            )

            fragments.add(header + chunk)
        }
        Log.d(TAG, "Fragmented ${payload.size} bytes into $totalFragments (Packet ID=$packetId)")
        return fragments
    }

    /**
     * Adds an incoming fragment to the reassembly buffer.
     * If the full message is complete, triggers the callback.
     */
    fun addFragment(fragment: ByteArray) {
        if (fragment.size < HEADER_SIZE) return

        val packetId = fragment[0].toInt() and 0xFF
        val totalFragments = fragment[1].toInt() and 0xFF
        val index = fragment[2].toInt() and 0xFF
        val payload = fragment.copyOfRange(HEADER_SIZE, fragment.size)

        val buffer = incomingBuffers.getOrPut(packetId) {
            MutableList(totalFragments) { ByteArray(0) }
        }
        buffer[index] = payload

        if (buffer.all { it.isNotEmpty() }) {
            incomingBuffers.remove(packetId)
            val fullMessage = buffer.reduce { acc, bytes -> acc + bytes }
            Log.d(TAG, "Reassembled full message (${fullMessage.size} bytes) from Packet ID=$packetId")
            onCompleteMessage(fullMessage)
        }
    }

    /**
     * Sends a raw payload as BLE notifications to a connected device.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendPayloadAsNotifications(
        gattServer: BluetoothGattServer,
        characteristic: BluetoothGattCharacteristic,
        device: BluetoothDevice,
        payload: ByteArray,
        interFragmentDelayMs: Long = 10
    ) {
        val fragments = fragment(payload)
        for (fragment in fragments) {
            characteristic.value = fragment
            gattServer.notifyCharacteristicChanged(device, characteristic, false)
            if (interFragmentDelayMs > 0) {
                Thread.sleep(interFragmentDelayMs)
            }
        }
    }
}
