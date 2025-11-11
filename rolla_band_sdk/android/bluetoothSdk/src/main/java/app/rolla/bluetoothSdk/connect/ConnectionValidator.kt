package app.rolla.bluetoothSdk.connect

import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceType

/**
 * Handles validation logic for BLE connections
 */
internal object ConnectionValidator {

    const val DEVICE_TYPE_EMPTY = "Device types cannot be empty"
    const val UNSUPPORTED_DEVICE_TYPES = "Some device types are not supported"

    /**
     * Validate device types for connection
     */
    fun validateDeviceTypes(bleDevice: BleDevice, deviceTypes: Set<DeviceType>): String? {
        if (deviceTypes.isEmpty()) {
            return DEVICE_TYPE_EMPTY
        }

        val unsupportedTypes = deviceTypes.filter { type ->
            !bleDevice.deviceTypes.contains(type)
        }

        if (unsupportedTypes.isNotEmpty()) {
            return UNSUPPORTED_DEVICE_TYPES
        }

        return null
    }

    /**
     * Get new device types that aren't already subscribed
     */
    fun getNewDeviceTypes(
        requestedTypes: Set<DeviceType>,
        existingTypes: Set<DeviceType>,
        deviceAddress: String,
        subscriptions: Map<DeviceType, String>
    ): Set<DeviceType> {
        return requestedTypes.filter { type ->
            // Type is not already assigned to this device
            !existingTypes.contains(type) &&
                    // Type is not subscribed by another device
                    (subscriptions[type] == null || subscriptions[type] == deviceAddress)
        }.toSet()
    }
}