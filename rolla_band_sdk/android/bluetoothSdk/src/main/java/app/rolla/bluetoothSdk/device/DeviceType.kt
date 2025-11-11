package app.rolla.bluetoothSdk.device

import app.rolla.bluetoothSdk.services.Service
import app.rolla.bluetoothSdk.services.BleService

/**
 * Implementations define the service UUIDs that identify a specific device type.
 */
enum class DeviceType(
    val scanningServices: List<String>, // List of service UUIDs to scan for when discovering this device type.
    val detectingTypeServices: List<String>, // Used for filtering scan results.
    val allowedForSubscribeServices: List<Service>, // Allowed services for subscribe
    val typeName: String, // Device type state
    var typeState: DeviceTypeState = DeviceTypeState.UNSUBSCRIBED // Name of this device type for display purposes.
) {

    RUNNING_SPEED_AND_CADENCE(
        listOf(
            BleService.RUNNING_SPEED_AND_CADENCE.uuid
        ),
        listOf(
            BleService.RUNNING_SPEED_AND_CADENCE.uuid
        ),
        listOf(
            BleService.RUNNING_SPEED_AND_CADENCE
        ),
        "Running Speed and Cadence"
    ),

    HEART_RATE(
        listOf(
            BleService.HEART_RATE.uuid
        ),
        listOf(
            BleService.HEART_RATE.uuid
        ),
        listOf(
            BleService.HEART_RATE
        ),
        "Heart Rate"
    ),

    ROLLA_BAND(
        listOf(
            BleService.ROLLA_BAND.uuid,
            BleService.DEVICE_FIRMWARE_UPDATE.uuid
        ),
        listOf(
            BleService.ROLLA_BAND.uuid,
            BleService.HEART_RATE.uuid,
            BleService.RUNNING_SPEED_AND_CADENCE.uuid
        ),
        listOf(
            BleService.ROLLA_BAND,
            BleService.HEART_RATE,
            BleService.RUNNING_SPEED_AND_CADENCE
        ),
        "Rolla Band"
    )
}