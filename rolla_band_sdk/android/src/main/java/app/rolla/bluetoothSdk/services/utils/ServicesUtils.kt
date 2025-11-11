package app.rolla.bluetoothSdk.services.utils

import android.os.ParcelUuid
import app.rolla.bluetoothSdk.services.exceptions.InvalidUUIDException

/**
 * Extension functions for dealing with UUIDs in various formats.
 */

/**
 * Converts various UUID string formats to a full UUID.
 * Handles both short (16-bit) and full (128-bit) UUID formats.
 */
@Throws(InvalidUUIDException::class)
fun String.getFullUUID(): String {
    return when (length) {
        // Handle short 16-bit UUID format (e.g., "180D")
        4 -> {
            val regex = Regex("^[a-fA-F0-9]{4}$")
            if (regex.matches(this)) {
                "0000${this.uppercase()}-0000-1000-8000-00805F9B34FB"
            } else {
                throw InvalidUUIDException("Short UUID contains invalid characters")
            }
        }

        // Handle full 128-bit UUID format
        36 -> {
            val regex = Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$")
            if (regex.matches(this)) {
                this.uppercase()
            } else {
                throw InvalidUUIDException("UUID doesn't match required pattern (8-4-4-4-12)")
            }
        }

        // Invalid UUID format
        else -> throw InvalidUUIDException("Invalid UUID format: must be 4 characters (short) or 36 characters (full)")
    }
}

internal fun ParcelUuid.getFullUUID(): String {
    return this.uuid.toString().uppercase()
}
