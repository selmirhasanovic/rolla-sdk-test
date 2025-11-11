package app.rolla.bluetoothSdk.connect

import app.rolla.bluetoothSdk.device.BleDevice

/**
 * BLE connection error types
 */
sealed class BleConnectionError(val message: String) {
    data class SecurityException(val device: BleDevice, val operation: String) : BleConnectionError("Security exception during $operation for ${device.btDevice.address}")

    data class ConnectionFailed(val device: BleDevice, val reason: String) : BleConnectionError("Connection failed for ${device.btDevice.address}: $reason")
    data class ConnectionTimeout(val device: BleDevice) : BleConnectionError("Connection timeout for ${device.btDevice.address}")
}
