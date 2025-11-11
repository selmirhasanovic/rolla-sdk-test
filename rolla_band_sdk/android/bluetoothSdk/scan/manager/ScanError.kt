package app.rolla.bluetoothSdk.scan.manager

/**
 * Represents an error that occurred during Bluetooth scanning.
 *
 * @property errorCode The Android system error code or a custom error code
 * @property message A human-readable description of the error
 * @property exception Optional exception that caused this error, if applicable
 */
data class ScanError(
    val errorCode: Int,
    val message: String,
    val exception: Throwable? = null
) {
    companion object {
        // Standard Android Bluetooth LE scan error codes
        const val SCAN_FAILED_ALREADY_STARTED = 1
        const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        const val SCAN_FAILED_INTERNAL_ERROR = 3
        const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
        const val SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5

        // Custom error codes
        const val ERROR_BLUETOOTH_DISABLED = 100
        const val ERROR_LOCATION_PERMISSION_MISSING = 101
        const val ERROR_BLUETOOTH_PERMISSION_MISSING = 102
        const val ERROR_INVALID_PARAMETERS = 103
        const val ERROR_SCAN_THROTTLED = 104
        const val ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING = 105
        const val ERROR_INTERNAL_ERROR = 999

        /**
         * Returns a human-readable description for standard error codes.
         *
         * @param errorCode The error code to get a description for
         * @return A human-readable description of the error code
         */
        fun getErrorDescription(errorCode: Int): String = when (errorCode) {
            SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
            SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed"
            SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
            SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
            SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Out of hardware resources"
            ERROR_BLUETOOTH_DISABLED -> "Bluetooth is disabled"
            ERROR_LOCATION_PERMISSION_MISSING -> "Location permission is required for scanning"
            ERROR_BLUETOOTH_PERMISSION_MISSING -> "Bluetooth permission is required for scanning"
            ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING -> "Bluetooth connect permission is missing"
            ERROR_INVALID_PARAMETERS -> "Invalid parameters provided"
            ERROR_SCAN_THROTTLED -> "Scan throttled due to too many scan attempts"
            else -> "Unknown error code: $errorCode"
        }
    }
}