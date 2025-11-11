package app.rolla.bluetoothSdk.utils.extensions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Check if BLUETOOTH_CONNECT permission is granted
 */
fun Context.hasBluetoothConnectPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        // For API < 31, BLUETOOTH_CONNECT doesn't exist, check legacy permissions
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Check if BLUETOOTH_SCAN permission is granted
 */
fun Context.hasBluetoothScanPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Check if all required Bluetooth permissions are granted
 */
fun Context.hasAllBluetoothPermissions(): Boolean {
    return hasBluetoothConnectPermission() && hasBluetoothScanPermission()
}

/**
 * Execute operation with BLUETOOTH_CONNECT permission check
 */
inline fun <T> Context.executeWithBluetoothConnect(
    operation: () -> T,
    onPermissionDenied: (e: Exception) -> T
): T {
    return if (hasBluetoothConnectPermission()) {
        try {
            operation()
        } catch (e: SecurityException) {
            onPermissionDenied(e)
        }
    } else {
        onPermissionDenied(Exception("Missing BLUETOOTH_CONNECT permission"))
    }
}

inline fun <T> Context.executeWithPermissionCheck(
    operation: () -> T,
    onPermissionDenied: (e: Exception) -> T,
    operationName: String
): T? {
    return try {
        this.executeWithBluetoothConnect(
            operation = operation,
            onPermissionDenied = { exception ->
                log(this.javaClass.simpleName, "Missing BLUETOOTH_CONNECT permission for $operationName")
                onPermissionDenied(exception)
            }
        )
    } catch (e: Exception) {
        log(this.javaClass.simpleName, "Error in $operationName: ${e.message}")
        onPermissionDenied(e)
        null
    }
}

/**
 * Execute operation with BLUETOOTH_SCAN permission check
 */
inline fun <T> Context.executeWithBluetoothScan(
    operation: () -> T,
    onPermissionDenied: () -> T
): T {
    return if (hasBluetoothScanPermission()) {
        try {
            operation()
        } catch (e: SecurityException) {
            onPermissionDenied()
        }
    } else {
        onPermissionDenied()
    }
}
