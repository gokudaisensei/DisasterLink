package com.example.disasterlink.ui.bluetooth

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.disasterlink.ble.central.BleDevice
import com.example.disasterlink.ble.central.ConnectionState
import com.example.disasterlink.proto.DisasterLinkPacket
import com.example.disasterlink.service.DisasterLinkService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BluetoothViewModel"
    }

    private var disasterLinkService: DisasterLinkService? = null
    private var isBound = false

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _scannedDevice = MutableStateFlow<BleDevice?>(null)
    val scannedDevice: StateFlow<BleDevice?> = _scannedDevice

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? DisasterLinkService.LocalBinder
            disasterLinkService = binder?.getService()
            isBound = true
            log("Service bound")

            // Observe flows from the service
            observeServiceFlows()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            disasterLinkService = null
            isBound = false
            log("Service unbound")
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        val intent = Intent(getApplication(), DisasterLinkService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun observeServiceFlows() {
        disasterLinkService?.let { service ->
            viewModelScope.launch {
                service.connectionStateFlow.collect { state ->
                    _connectionState.value = state
                }
            }
            viewModelScope.launch {
                service.deviceFoundFlow.collect { device ->
                    _scannedDevice.value = device
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startCentralScan() {
        disasterLinkService?.startCentralScan()
        log("Central scan started")
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    fun startPeripheral() {
        disasterLinkService?.startPeripheral()
        log("Peripheral started")
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    fun stopPeripheral() {
        disasterLinkService?.stopPeripheral()
        log("Peripheral stopped")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectCentral() {
        disasterLinkService?.disconnectCentral()
        log("Central disconnected")
    }

    fun sendTestPacket(asPeripheral: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            val message = com.example.disasterlink.proto.Message.newBuilder()
                .setMessageId(java.util.UUID.randomUUID().toString())
                .setSourceId("TestSource")
                .setDestinationId("BROADCAST")
                .setTimestampMs(now)
                .setTtl(5)
                .setContent(
                    "Hello from ${if (asPeripheral) "Peripheral" else "Central"}"
                        .toByteArray(Charsets.UTF_8)
                        .let { com.google.protobuf.ByteString.copyFrom(it) }
                )
                .build()

            val packet = DisasterLinkPacket.newBuilder()
                .setMessage(message)
                .build()

            disasterLinkService?.sendPacket(packet, asPeripheral)
            log("Sent test packet: ${message.content.toStringUtf8()}")
        }
    }

    private fun log(message: String) {
        _logMessages.value = _logMessages.value + message
        Log.d(TAG, message)
    }
}
