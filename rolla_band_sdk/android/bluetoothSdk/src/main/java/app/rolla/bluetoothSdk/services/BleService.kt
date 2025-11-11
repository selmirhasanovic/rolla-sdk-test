package app.rolla.bluetoothSdk.services

import app.rolla.bluetoothSdk.services.utils.getFullUUID

/**
 * Enumeration of standard Bluetooth services with their UUIDs and characteristics.
 * Based on Bluetooth SIG assigned numbers and custom device protocols.
 */
enum class BleService(
    override val uuid: String,
    override val characteristics: List<Characteristic>
) : Service {
    RUNNING_SPEED_AND_CADENCE(
        uuid = "1814".getFullUUID(),
        characteristics = listOf(
            BleCharacteristic.RUNNING_SPEED_AND_CADENCE_MEASUREMENT
        )
    ),
    HEART_RATE(
        uuid = "180D".getFullUUID(),
        characteristics = listOf(
            BleCharacteristic.HEART_RATE_MEASUREMENT
        )
    ),
    ROLLA_BAND(
        uuid = "FFF0".getFullUUID(),
        characteristics = listOf(
            BleCharacteristic.ROLLA_BAND_READ,
            BleCharacteristic.ROLLA_BAND_WRITE
        )
    ),
    DEVICE_FIRMWARE_UPDATE(
        uuid = "FE59".getFullUUID(),
        characteristics = emptyList()
    ),
    DEVICE_INFORMATION(
        uuid = "180A".getFullUUID(),
        characteristics = listOf(
            BleCharacteristic.SERIAL_NUMBER,
            BleCharacteristic.FIRMWARE_REVISION
        )
    )
}