package app.rolla.bluetoothSdk.connect

import app.rolla.bluetoothSdk.device.BleDevice

class ConnectionError (
    val device: BleDevice?,
    val errorCode: Int,
    val operation: Operation,
    val message: String,
    val exception: Throwable? = null
) {
    companion object {
        // Custom error codes
        const val ERROR_BLUETOOTH_DISABLED = 100
        const val ERROR_LOCATION_PERMISSION_MISSING = 101
        const val ERROR_BLUETOOTH_PERMISSION_MISSING = 102
        const val ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING = 103
        const val ERROR_MAX_CONNECTIONS = 104
        const val ERROR_DEVICE_TYPE_CONFLICT = 105
        const val ERROR_SERVICE_DISCOVERY_FAILED = 106
        const val ERROR_SERVICE_DISCOVERY_FAILED_STATUS_UNSUCCESSFUL = 107
        const val ERROR_INVALID_DEVICE_TYPE = 108
        const val ERROR_CONNECTION_FAILED = 109

        const val ERROR_UNKNOWN_CHARACTERISTIC = 110
        const val ERROR_CHARACTERISTIC_PROCESSING_FAILED = 111
        const val ERROR_DESCRIPTOR_WRITE_FAILED = 112
        const val ERROR_SERVICE_NOT_FOUND = 113
        const val ERROR_CHARACTERISTIC_NOT_FOUND = 114
        const val ERROR_CHARACTERISTIC_OPERATION_FAILED = 115
        const val ERROR_INVALID_SERVICE_UUID = 116
        const val ERROR_SUBSCRIPTION_FAILED = 117
        const val ERROR_CHARACTERISTIC_NOT_SUBSCRIBABLE = 118

        const val ERROR_NOTIFICATION_ENABLE_FAILED = 119

        const val ERROR_DESCRIPTOR_NOT_FOUND = 120
        const val ERROR_UNSUBSCRIPTION_FAILED = 121
        const val ERROR_SUBSCRIBING_TIMEOUT = 122

        const val ERROR_GATT_STATUS_FAILED = 123
        const val ERROR_GATT_NULL = 124
        const val ERROR_DEVICE_TYPE_ALREADY_SUBSCRIBED = 125
        const val ERROR_ALREADY_CONNECTING = 126
        const val ERROR_CONNECTION_TIMEOUT = 127

        const val ERROR_DEVICE_NOT_FOUND = 130
        const val ERROR_DEVICE_NOT_CONNECTED = 131
        const val ERROR_POST_SUBSCRIPTION_COMMAND_FAILED = 132

        /**
         * Returns a human-readable description for standard error codes.
         *
         * @param errorCode The error code to get a description for
         * @return A human-readable description of the error code
         */
        fun getErrorDescription(errorCode: Int): String = when (errorCode) {
            ERROR_BLUETOOTH_DISABLED -> "Bluetooth is disabled"
            ERROR_LOCATION_PERMISSION_MISSING -> "Location permission is required for scanning"
            ERROR_BLUETOOTH_PERMISSION_MISSING -> "Bluetooth permission is required for scanning"
            ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING -> "Bluetooth connect permission is missing"
            ERROR_UNKNOWN_CHARACTERISTIC -> "Unknown characteristic"
            ERROR_DEVICE_NOT_FOUND -> "Device not found"
            else -> "Unknown error code: $errorCode"
        }
    }

}