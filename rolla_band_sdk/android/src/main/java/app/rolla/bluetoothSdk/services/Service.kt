package app.rolla.bluetoothSdk.services

/**
 * Interface representing a Bluetooth GATT service with its UUID and characteristics.
 */
interface Service {
    /** Bluetooth UUID of the service */
    val uuid: String
    /** List of characteristics supported by this service */
    val characteristics: List<Characteristic>
}