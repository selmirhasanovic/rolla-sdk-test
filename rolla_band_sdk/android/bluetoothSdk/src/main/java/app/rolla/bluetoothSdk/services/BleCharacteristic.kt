package app.rolla.bluetoothSdk.services

import app.rolla.bluetoothSdk.services.impl.CharacteristicImpl
import app.rolla.bluetoothSdk.services.impl.FirmwareRevisionCharacteristicImpl
import app.rolla.bluetoothSdk.services.impl.HeartRateCharacteristicImpl
import app.rolla.bluetoothSdk.services.impl.RollaBandCharacteristicImpl
import app.rolla.bluetoothSdk.services.impl.RunningSpeedCadenceCharacteristicImpl
import app.rolla.bluetoothSdk.services.impl.SerialNumberCharacteristicImpl
import app.rolla.bluetoothSdk.services.utils.getFullUUID

object CharacteristicImpls {
    val rollaBand = RollaBandCharacteristicImpl()
    val heartRate = HeartRateCharacteristicImpl()
    val runningSpeedCadence = RunningSpeedCadenceCharacteristicImpl()
    val serialNumber = SerialNumberCharacteristicImpl()
    val firmwareRevision = FirmwareRevisionCharacteristicImpl()
}

/**
 * Enumeration of standard Bluetooth characteristics with their UUIDs and implementations.
 * Based on Bluetooth SIG assigned numbers and custom device protocols.
 */
enum class BleCharacteristic(
    override val uuid: String,
    override val impl: CharacteristicImpl,
    override val displayName: String
) : Characteristic {

    RUNNING_SPEED_AND_CADENCE_MEASUREMENT(
        uuid = "2A53".getFullUUID(),
        impl = CharacteristicImpls.runningSpeedCadence,
        displayName = "Running Speed & Cadence Measurement"
    ),
    HEART_RATE_MEASUREMENT(
        uuid = "2A37".getFullUUID(),
        impl = CharacteristicImpls.heartRate,
        displayName = "Heart Rate Measurement"
    ),
    ROLLA_BAND_READ(
        uuid = "FFF7".getFullUUID(),
        impl = CharacteristicImpls.rollaBand,
        displayName = "Rolla Band Read"
    ),
    ROLLA_BAND_WRITE(
        uuid = "FFF6".getFullUUID(),
        impl = CharacteristicImpls.rollaBand,
        displayName = "Rolla Band Write"
    ),
    SERIAL_NUMBER(
        uuid = "2A25".getFullUUID(),
        impl = CharacteristicImpls.serialNumber,
        displayName = "Serial Number"
    ),
    FIRMWARE_REVISION(
        uuid = "2A26".getFullUUID(),
        impl = CharacteristicImpls.firmwareRevision,
        displayName = "Firmware Revision"
    )
}