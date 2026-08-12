package com.exhibition.smartdoorlock.bluetooth

/**
 * The plain-text, newline-terminated protocol shared with the Arduino firmware.
 * Keeping every literal in one place means the Arduino-side sketch and this app
 * can be diffed against a single source of truth.
 *
 * Matching is exact-case, post-trim (see BluetoothConnectionManager.startListening). If the
 * Arduino ever sends different casing or extra text on the line, it won't match —
 * that's intentional strictness, not a bug, so a typo on either side fails loudly
 * instead of silently doing the wrong thing.
 */
object ArduinoProtocol {
    // Arduino -> Phone
    const val READY = "READY"
    const val DETECTED = "DETECTED"
    const val ACCESS_GRANTED = "ACCESS_GRANTED"
    const val ACCESS_DENIED = "ACCESS_DENIED"
    const val LOCKED = "LOCKED"

    // Phone -> Arduino
    const val CMD_LOCK = "LOCK"
    const val CMD_PIN_PREFIX = "PIN:"

    const val DETECTED_PROMPT = "Please enter passcode or scan RFID below."
}
