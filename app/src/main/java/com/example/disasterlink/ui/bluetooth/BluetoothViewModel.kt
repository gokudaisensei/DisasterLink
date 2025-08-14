package com.example.disasterlink.ui.bluetooth

import android.Manifest
import android.app.Application
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.disasterlink.ble.central.BleDevice
import com.example.disasterlink.ble.central.CentralManager
import com.example.disasterlink.ble.central.ConnectionState
import com.example.disasterlink.ble.peripheral.PeripheralManager
import com.example.disasterlink.ble.util.MtuManager
import com.example.disasterlink.proto.DisasterLinkPacket
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel that owns BLE managers (CentralManager and PeripheralManager) directly.
 * No Service used — BLE runs while ViewModel is alive.
 */
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BluetoothViewModel"
    }

    private val appContext = application.applicationContext

    // MTU manager shared by both managers
    private val mtuManager = MtuManager()

    // UI state flows
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevice = MutableStateFlow<BleDevice?>(null) // currently connected device
    val scannedDevice: StateFlow<BleDevice?> = _scannedDevice.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    // instantiate managers and wire callbacks to ViewModel flows
    private val centralManager: CentralManager = CentralManager(
        context = appContext,
        mtuManager = mtuManager,
        onDeviceFound = { device -> onDeviceFound(device) },
        onPacketReceived = { bytes -> processIncomingPayload(bytes) },
        onConnectionStateChange = { state ->
            log("Central connection state: $state")
            _connectionState.value = state
        })

    private val peripheralManager: PeripheralManager = PeripheralManager(
        context = appContext,
        mtuManager = mtuManager,
        onPacketReceived = { bytes -> processIncomingPayload(bytes) },
        onConnectionStateChange = { state ->
            log("Peripheral connection state: $state")
            // we reflect peer state as overall connection state for UI
            _connectionState.value = state
        })

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    init {
        log("BluetoothViewModel initialized (service-free).")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startCentralScan() {
        log("startCentralScan() requested")
        _isScanning.value = true
        viewModelScope.launch {
            centralManager.startScan()
        }
    }

    private fun onDeviceFound(device: BleDevice) {
        log("Device found: ${device.name ?: "Unknown"} (${device.address})")
        // Avoid duplicates
        if (_discoveredDevices.value.none { it.address == device.address }) {
            _discoveredDevices.value = _discoveredDevices.value + device
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BleDevice) {
        log("connectToDevice(${device.name ?: "Unknown"}) requested")
        viewModelScope.launch {
            try {
                centralManager.connect(device)
                _scannedDevice.value = device
            } catch (t: Throwable) {
                log("connectToDevice failed: ${t.message}")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectCentral() {
        log("disconnectCentral() requested")
        viewModelScope.launch {
            centralManager.disconnect()
            _scannedDevice.value = null
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT]
    )
    fun startPeripheral() {
        log("startPeripheral() requested")
        viewModelScope.launch {
            try {
                peripheralManager.startGattServer()
                peripheralManager.startAdvertising()
                _isAdvertising.value = true
            } catch (t: Throwable) {
                log("startPeripheral failed: ${t.message}")
            }
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT]
    )
    fun stopPeripheral() {
        log("stopPeripheral() requested")
        viewModelScope.launch {
            peripheralManager.stop()
            _isAdvertising.value = false
        }
    }

    fun sendTestPacket(asPeripheral: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val msgBuilder = com.example.disasterlink.proto.Message.newBuilder()
                .setMessageId(java.util.UUID.randomUUID().toString()).setSourceId("android-device")
                .setDestinationId("BROADCAST").setTimestampMs(now).setTtl(5).setContent(
                    ByteString.copyFrom(
                        "Hello from ${if (asPeripheral) "Peripheral" else "Central"}".toByteArray()
                    )
                )

            val packet = DisasterLinkPacket.newBuilder().setMessage(msgBuilder).build()

            val payload = packet.toByteArray()
            log("Sending test packet (len=${payload.size}) asPeripheral=$asPeripheral")

            try {
                if (asPeripheral) peripheralManager.sendPacket(payload)
                else centralManager.sendPacket(payload)
            } catch (t: Throwable) {
                log("sendTestPacket failed: ${t.message}")
            }
        }
    }

    private fun processIncomingPayload(bytes: ByteArray) {
        viewModelScope.launch {
            try {
                val packet = DisasterLinkPacket.parseFrom(bytes)
                when (packet.payloadCase) {
                    DisasterLinkPacket.PayloadCase.MESSAGE -> {
                        val m = packet.message
                        val contentStr = try {
                            m.content.toStringUtf8()
                        } catch (_: Exception) {
                            "<binary>"
                        }
                        log("Received MESSAGE from ${m.sourceId}: $contentStr ttl=${m.ttl}")
                    }

                    DisasterLinkPacket.PayloadCase.STATUS -> {
                        val s = packet.status
                        log("Received STATUS from ${s.deviceId} battery=${s.batteryLevel} role=${s.currentRole}")
                    }

                    DisasterLinkPacket.PayloadCase.NETWORK_INFO -> {
                        val n = packet.networkInfo
                        log("Received NETWORK_INFO from ${n.deviceId} neighbors=${n.neighborsCount}")
                    }

                    else -> {
                        log("Received unknown payload or empty packet")
                    }
                }
            } catch (e: Exception) {
                log("Failed to parse DisasterLinkPacket: ${e.message}")
            }
        }
    }

    private fun log(message: String) {
        _logMessages.value = _logMessages.value + message
        Log.d(TAG, message)
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onCleared() {
        super.onCleared()
        try {
            centralManager.disconnect()
        } catch (_: Throwable) {
        }
        try {
            peripheralManager.stop()
        } catch (_: Throwable) {
        }
        log("BluetoothViewModel cleared; managers stopped")
    }
}
