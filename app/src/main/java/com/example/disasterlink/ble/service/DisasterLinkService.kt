package com.example.disasterlink.ble.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.disasterlink.ble.central.CentralManager
import com.example.disasterlink.ble.central.ConnectionState
import com.example.disasterlink.ble.central.BleDevice
import com.example.disasterlink.ble.peripheral.PeripheralManager
import com.example.disasterlink.ble.util.BlePermissionHelper
import com.example.disasterlink.ble.util.MtuManager
import com.example.disasterlink.proto.DisasterLinkPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DisasterLinkService : Service() {

    companion object {
        private const val TAG = "DisasterLinkService"
    }

    private val binder = LocalBinder()

    // BLE & MTU managers
    private lateinit var mtuManager: MtuManager
    private lateinit var centralManager: CentralManager
    private lateinit var peripheralManager: PeripheralManager

    private val _connectionStateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionStateFlow: StateFlow<ConnectionState> = _connectionStateFlow

    private val _deviceFoundFlow = MutableStateFlow<BleDevice?>(null)
    val deviceFoundFlow: StateFlow<BleDevice?> = _deviceFoundFlow

    override fun onBind(intent: Intent): IBinder = binder

    inner class LocalBinder : Binder() {
        fun getService(): DisasterLinkService = this@DisasterLinkService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        mtuManager = MtuManager()

        centralManager = CentralManager(
            context = this,
            mtuManager = mtuManager,
            onDeviceFound = { device -> onDeviceFound(device) },
            onPacketReceived = { fullPayload -> processIncomingPacket(fullPayload) },
            onConnectionStateChange = { state -> onConnectionStateChange(state) })

        peripheralManager = PeripheralManager(
            context = this,
            mtuManager = mtuManager,
            onPacketReceived = { fullPayload -> processIncomingPacket(fullPayload) },
            onConnectionStateChange = { state -> onConnectionStateChange(state) })
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startCentralScan() {
        Log.d(TAG, "startCentralScan()")
        if (!BlePermissionHelper.hasScanPermission(this)) {
            Log.e(TAG, "Missing scan permission")
            return
        }
        centralManager.startScan()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    fun startPeripheral() {
        Log.d(TAG, "startPeripheral() called")
        if (!BlePermissionHelper.hasAdvertisePermission(this) || !BlePermissionHelper.hasConnectPermission(
                this
            )
        ) {
            Log.e(TAG, "Missing advertise or connect permission — aborting peripheral start")
            return
        }
        Log.d(TAG, "Permissions OK — starting GATT server and advertising")
        peripheralManager.startGattServer()
        peripheralManager.startAdvertising()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectCentral() {
        Log.d(TAG, "disconnectCentral()")
        centralManager.disconnect()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    fun stopPeripheral() {
        Log.d(TAG, "stopPeripheral()")
        peripheralManager.stop()
    }

    private fun processIncomingPacket(fullPayload: ByteArray) {
        try {
            val packet = DisasterLinkPacket.parseFrom(fullPayload)
            Log.i(TAG, "Received packet: $packet")
            // TODO: Broadcast via LiveData or ViewModel for UI
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse DisasterLinkPacket", e)
        }
    }

    fun sendPacket(packet: DisasterLinkPacket, asPeripheral: Boolean) {
        val payload = packet.toByteArray()
        if (asPeripheral) peripheralManager.sendPacket(payload) else centralManager.sendPacket(
            payload
        )
    }

    private fun onDeviceFound(device: BleDevice) {
        Log.i(TAG, "Device found: ${device.name} (${device.address})")
        _deviceFoundFlow.value = device
    }

    private fun onConnectionStateChange(state: ConnectionState) {
        Log.i(TAG, "Connection state changed: $state")
        _connectionStateFlow.value = state
    }
}
