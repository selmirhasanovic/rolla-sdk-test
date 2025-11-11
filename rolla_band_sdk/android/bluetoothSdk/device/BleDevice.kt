package app.rolla.bluetoothSdk.device

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlin.collections.plus

/**
 * Data class representing a Bluetooth device with its properties and connection state.
 * Encapsulates device information, discovered services, signal strength, device types, and connection state.
 */
data class BleDevice(
    val btDevice: BluetoothDevice,
    val serviceUuids: List<ParcelUuid>,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceTypes: Set<DeviceType> = emptySet(),
    val requestedTypes: Set<DeviceType> = emptySet(),
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val gatt: BluetoothGatt? = null,
    val servicesDiscovered: Boolean = false
) {
    /**
     * Creates a BleDevice from a ScanResult.
     */
    constructor(scanResult: ScanResult) : this(
        scanResult.device,
        scanResult.scanRecord?.serviceUuids?.toList() ?: emptyList(),
        scanResult.rssi
    )

    // Connection state helpers
    fun isConnected(): Boolean = connectionState == DeviceConnectionState.CONNECTED
    fun isConnecting(): Boolean = connectionState == DeviceConnectionState.CONNECTING
    fun isDisconnecting(): Boolean = connectionState == DeviceConnectionState.DISCONNECTING
    fun isDisconnected(): Boolean = connectionState == DeviceConnectionState.DISCONNECTED

    fun isServicesDiscovered(): Boolean = servicesDiscovered

    /**
     * Returns the device's unique identifier (Bluetooth address).
     */
    fun getMacAddress(): String = btDevice.address

    /**
     * Checks if this device is of a particular device type.
     */
    fun isDeviceType(type: DeviceType): Boolean = deviceTypes.contains(type)

    /**
     * Checks if device has a specific device type
     */
    fun hasDeviceType(deviceType: DeviceType): Boolean = deviceTypes.contains(deviceType)

    /**
     * Creates a copy with updated connection state.
     */
    fun withConnectionState(
        newState: DeviceConnectionState
    ): BleDevice {
        return copy(connectionState = newState)
    }

    fun withGatt(
        newGatt: BluetoothGatt?
    ): BleDevice {
        return copy(gatt = newGatt)
    }

    fun withServicesDiscovered(
        servicesDiscovered: Boolean
    ): BleDevice {
        return copy(servicesDiscovered = servicesDiscovered)
    }

    fun withRssi(
        newRssi: Int
    ): BleDevice {
        return copy(rssi = newRssi)
    }

    fun withServiceUuids(
        newServiceUuids: List<ParcelUuid>
    ): BleDevice {
        return copy(serviceUuids = newServiceUuids)
    }

    fun withRequestedTypes(
        newRequestedTypes: Set<DeviceType>
    ): BleDevice {
        val updatedRequestedTypes = (requestedTypes + newRequestedTypes).distinct().toSet()
        log(this.javaClass.simpleName, "Updated requested device types for ${getMacAddress()}: ${(updatedRequestedTypes).joinToString { it.typeName }}")
        return copy(requestedTypes = updatedRequestedTypes)
    }

    fun withNewRequestedTypes(
        newRequestedTypes: Set<DeviceType>
    ): BleDevice {
        val updatedRequestedTypes = newRequestedTypes.toSet()
        log(this.javaClass.simpleName, "Updated new requested device types for ${getMacAddress()}: ${(updatedRequestedTypes).joinToString { it.typeName }}")
        return copy(requestedTypes = updatedRequestedTypes)
    }

    fun getRequestedDeviceTypes(): Set<DeviceType> = requestedTypes

    /**
     * Creates a copy with updated device types.
     */
    fun withDeviceTypes(newDeviceTypes: Set<DeviceType>, updateRequestedTypes: Boolean = true): BleDevice {
        val updateTypes = (deviceTypes + newDeviceTypes).distinct().toSet()
        val device = copy(deviceTypes = updateTypes)
        if (updateRequestedTypes) {
            return device.withRequestedTypes(newDeviceTypes)
        }
        return device
    }

    fun withNewDeviceTypes(newDeviceTypes: Set<DeviceType>): BleDevice {
        return copy(deviceTypes = newDeviceTypes.toSet())
    }

    /**
     * Updates device type states and returns this BleDevice
     */
    fun withUpdatedDeviceTypeStates(
        newState: DeviceTypeState
    ): BleDevice {
        log(this.javaClass.simpleName, "withUpdatedDeviceTypeStates called for ${getMacAddress()}")
        log(this.javaClass.simpleName, "  New state: $newState")
        log(this.javaClass.simpleName, "  Requested types: ${requestedTypes.joinToString { it.typeName }}")
        log(this.javaClass.simpleName, "  Device types before: ${deviceTypes.joinToString { "${it.typeName}(${it.typeState})" }}")
        
        deviceTypes.forEach { deviceType ->
            if (requestedTypes.contains(deviceType)) {
                log(this.javaClass.simpleName, "  Updating ${deviceType.typeName} from ${deviceType.typeState} to $newState")
                deviceType.typeState = newState
            } else {
                log(this.javaClass.simpleName, "  Skipping ${deviceType.typeName} (not in requested types)")
            }
        }
        
        log(this.javaClass.simpleName, "  Device types after: ${deviceTypes.joinToString { "${it.typeName}(${it.typeState})" }}")
        return this
    }

    /**
     * Get unsubscribed types (supported but not subscribed)
     */
    fun getUnsubscribedTypes(): List<DeviceType> = deviceTypes.filter { it.typeState == DeviceTypeState.UNSUBSCRIBED }

    fun getSubscribedTypes(): List<DeviceType> = deviceTypes.filter { it.typeState == DeviceTypeState.SUBSCRIBED }

    /**
     * Get device full name (name + address)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getFullName(): String {
        return try {
            val name = btDevice.name ?: "Unknown"
            val address = btDevice.address
            "$name ($address)"
        } catch (e: SecurityException) {
            "Permission Denied (${btDevice.address})"
        }
    }

    /**
     * String representation of the device.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun toString(): String {
        val name = try {
            btDevice.name ?: "Unknown"
        } catch (e: SecurityException) {
            "Permission denied"
        }
        return "BleDevice(name=$name, address=${btDevice.address}, " +
                "rssi=$rssi, state=$connectionState, " +
                "types=${deviceTypes.joinToString { it.typeName }}, " +
                "subscribed=${getSubscribedTypes().joinToString { it.typeName }})"
    }
}