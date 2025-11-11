package app.rolla.bluetoothSdk.services

import app.rolla.bluetoothSdk.services.impl.CharacteristicImpl

/**
 * Interface representing a Bluetooth GATT characteristic with its UUID, implementation, and metadata.
 */
interface Characteristic {
    /** Bluetooth UUID of the characteristic */
    val uuid: String
    /** Implementation handling data processing for this characteristic */
    val impl: CharacteristicImpl
    /** Human-readable name for display purposes */
    val displayName: String
}