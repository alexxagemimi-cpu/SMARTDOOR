package com.exhibition.smartdoorlock.ui.doorcontrol

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exhibition.smartdoorlock.bluetooth.ArduinoProtocol
import com.exhibition.smartdoorlock.bluetooth.BluetoothConnectionManager
import com.exhibition.smartdoorlock.bluetooth.ConnectionState
import com.exhibition.smartdoorlock.speech.TextToSpeechManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Current door state, derived entirely from the Arduino's last relevant message. */
enum class DoorState { UNKNOWN, UNLOCKED, LOCKED, DENIED }

data class DoorControlUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val doorState: DoorState = DoorState.UNKNOWN,
    val lastMessage: String = "—",
    val pinInput: String = "",
    val pairedDevices: List<BluetoothDevice> = emptyList()
)

/** One-off UI events that shouldn't re-fire on recomposition/rotation, unlike persistent state. */
sealed class DoorEvent {
    data object AccessGranted : DoorEvent()
    data object AccessDenied : DoorEvent()
}

class DoorControlViewModel(
    private val bluetoothManager: BluetoothConnectionManager,
    private val ttsManager: TextToSpeechManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoorControlUiState())
    val uiState: StateFlow<DoorControlUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DoorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<DoorEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
        viewModelScope.launch {
            // incomingMessages is a SharedFlow of discrete events (see BluetoothConnectionManager) —
            // every emitted line is handled, nothing is conflated.
            bluetoothManager.incomingMessages.collect(::handleIncoming)
        }
    }

    private fun handleIncoming(message: String) {
        _uiState.value = _uiState.value.copy(lastMessage = message)
        when (message) {
            ArduinoProtocol.DETECTED -> {
                ttsManager.speak(ArduinoProtocol.DETECTED_PROMPT)
            }
            ArduinoProtocol.ACCESS_GRANTED -> {
                _uiState.value = _uiState.value.copy(doorState = DoorState.UNLOCKED)
                _events.tryEmit(DoorEvent.AccessGranted)
            }
            ArduinoProtocol.ACCESS_DENIED -> {
                _uiState.value = _uiState.value.copy(doorState = DoorState.DENIED)
                _events.tryEmit(DoorEvent.AccessDenied)
            }
            ArduinoProtocol.LOCKED -> {
                _uiState.value = _uiState.value.copy(doorState = DoorState.LOCKED)
            }
            ArduinoProtocol.READY -> {
                // Arduino signaling boot/ready; no door-state change required by spec.
            }
        }
    }

    fun isBluetoothSupported(): Boolean = bluetoothManager.isBluetoothSupported()

    fun isBluetoothEnabled(): Boolean = bluetoothManager.isBluetoothEnabled()

    fun refreshPairedDevices() {
        _uiState.value = _uiState.value.copy(pairedDevices = bluetoothManager.getPairedDevices())
    }

    fun connect(device: BluetoothDevice) {
        bluetoothManager.connect(device)
    }

    fun disconnect() {
        bluetoothManager.disconnect()
        _uiState.value = _uiState.value.copy(doorState = DoorState.UNKNOWN, lastMessage = "—")
    }

    /** Keypad-style input: digits only, capped at a sane length. The Arduino remains the sole judge of correctness. */
    fun onPinChanged(pin: String) {
        if (pin.length <= 12 && pin.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(pinInput = pin)
        }
    }

    fun sendUnlock() {
        val pin = _uiState.value.pinInput
        if (pin.isNotEmpty()) {
            bluetoothManager.sendPin(pin)
        }
    }

    fun sendLock() {
        bluetoothManager.sendLock()
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.release()
        ttsManager.shutdown()
    }
}
