package app.rolla.bluetoothSdk.services.impl

import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.services.commands.Command
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * Interface defining the behavior and capabilities of a Bluetooth characteristic implementation.
 * Handles data parsing, validation, and write operations for specific characteristic types.
 */
interface CharacteristicImpl {
    
    // Each implementation must provide its own scope
    val scope: CoroutineScope

    // Default cleanup using the scope
    fun cleanup() {
        scope.cancel()
    }
    
    fun getPostSubscriptionCommands(): List<Command<*>> {
        return emptyList()
    }

    /**
     * Process data received from read operations.
     * @param byteArray Raw data received from the characteristic
     * @throws IllegalArgumentException if data is invalid or malformed
     */
    fun onRead(byteArray: ByteArray, device: BleDevice) {

    }

    /**
     * Process data received from notification operations.
     * @param byteArray Raw data received from the characteristic
     * @throws IllegalArgumentException if data is invalid or malformed
     */
    fun onNotify(byteArray: ByteArray, device: BleDevice) {

    }

    /**
     * Prepare and execute write operation to the characteristic.
     * Only called if canWrite is true.
     */
    fun onWrite(byteArray: ByteArray?, device: BleDevice) {

    }
}