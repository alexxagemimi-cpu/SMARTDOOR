package com.exhibition.smartdoorlock.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Owns the Bluetooth Classic (RFCOMM) connection to a single paired HC-05 module.
 *
 * Named `BluetoothConnectionManager` rather than `BluetoothManager` so it never
 * shadows the platform's own `android.bluetooth.BluetoothManager` (used internally
 * below to obtain the adapter) — a small thing, but it keeps every reference in
 * this file unambiguous.
 *
 * Scope, deliberately: this class only ever talks to a device the user already
 * paired in system Bluetooth settings. It never calls startDiscovery() and never
 * requests BLUETOOTH_SCAN / BLUETOOTH_ADVERTISE / location permissions — see
 * README.md "Bluetooth permission model" for why that's correct, not an oversight.
 *
 * All socket I/O runs on Dispatchers.IO. UI observes state via Flow and never
 * touches BluetoothSocket/streams directly.
 */
class BluetoothConnectionManager(context: Context) {

    companion object {
        /** Standard Serial Port Profile UUID. HC-05 modules advertise this SPP UUID. */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val appContext = context.applicationContext

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        manager?.adapter
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var listenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Every newline-terminated line received from the Arduino, in arrival order, newline
     * stripped. This is a SharedFlow rather than a StateFlow on purpose: each line is a
     * discrete protocol event (a door-state transition), not "current state" — a StateFlow
     * would be allowed to conflate two rapid messages into one and silently drop the
     * other. extraBufferCapacity keeps emission non-suspending from the read loop.
     */
    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /** Bonded (paired) devices only — this app never scans for new ones. */
    @SuppressLint("MissingPermission") // Caller (UI layer) only invokes this after BLUETOOTH_CONNECT is granted.
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList().orEmpty()
    }

    @SuppressLint("MissingPermission") // Same as above — gated by the UI layer, not by this function.
    fun connect(device: BluetoothDevice) {
        val current = _connectionState.value
        if (current is ConnectionState.Connected || current is ConnectionState.Connecting) return

        _connectionState.value = ConnectionState.Connecting
        scope.launch {
            try {
                val createdSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                createdSocket.connect() // Blocking; safe here since we're on Dispatchers.IO.
                socket = createdSocket
                inputStream = createdSocket.inputStream
                outputStream = createdSocket.outputStream
                _connectionState.value = ConnectionState.Connected(device.name ?: "HC-05")
                startListening()
            } catch (e: IOException) {
                closeQuietly()
                _connectionState.value = ConnectionState.Error(e.message ?: "Couldn't connect to the device")
            }
        }
    }

    /**
     * Reads raw bytes and reassembles them into newline-terminated lines, per the
     * Arduino -> Phone protocol (READY / DETECTED / ACCESS_GRANTED / ACCESS_DENIED / LOCKED,
     * each terminated with \n). \r is stripped defensively in case the sender uses \r\n;
     * plain Serial.println() on Arduino only emits \n.
     */
    private fun startListening() {
        listenJob?.cancel()
        listenJob = scope.launch {
            val stream = inputStream ?: return@launch
            val buffer = ByteArray(1024)
            val lineBuilder = StringBuilder()
            while (isActive) {
                try {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead == -1) {
                        disconnect()
                        break
                    }
                    for (i in 0 until bytesRead) {
                        // Mask to 0-255 before converting to Char — Byte is signed in
                        // Kotlin/JVM, so toInt() alone sign-extends anything >= 0x80 and
                        // produces the wrong character. Every byte in this ASCII protocol
                        // is <= 0x7F today, so this is currently a no-op in practice, but
                        // it's the correct conversion regardless of future protocol bytes.
                        val c = (buffer[i].toInt() and 0xFF).toChar()
                        when (c) {
                            '\n' -> {
                                val line = lineBuilder.toString().trim()
                                lineBuilder.clear()
                                if (line.isNotEmpty()) {
                                    _incomingMessages.tryEmit(line)
                                }
                            }
                            '\r' -> { /* ignore */ }
                            else -> lineBuilder.append(c)
                        }
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        _connectionState.value = ConnectionState.Error(e.message ?: "Connection lost")
                        disconnect()
                    }
                    break
                }
            }
        }
    }

    /** Sends [command] followed by a single newline, exactly matching the phone -> Arduino protocol. */
    private fun sendCommand(command: String): Boolean {
        val stream = outputStream ?: return false
        if (_connectionState.value !is ConnectionState.Connected) return false
        return try {
            stream.write("$command\n".toByteArray(Charsets.US_ASCII))
            stream.flush()
            true
        } catch (e: IOException) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Couldn't send command")
            disconnect()
            false
        }
    }

    fun sendPin(pin: String): Boolean = sendCommand("${ArduinoProtocol.CMD_PIN_PREFIX}$pin")

    fun sendLock(): Boolean = sendCommand(ArduinoProtocol.CMD_LOCK)

    fun disconnect() {
        listenJob?.cancel()
        listenJob = null
        closeQuietly()
        _connectionState.value = ConnectionState.Disconnected
    }

    /** Call when this manager is no longer needed (see DoorControlViewModel.onCleared). */
    fun release() {
        disconnect()
        scope.cancel()
    }

    private fun closeQuietly() {
        try { inputStream?.close() } catch (_: IOException) { /* already gone */ }
        try { outputStream?.close() } catch (_: IOException) { /* already gone */ }
        try { socket?.close() } catch (_: IOException) { /* already gone */ }
        inputStream = null
        outputStream = null
        socket = null
    }
}

/** Connection lifecycle exposed to the UI. */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
