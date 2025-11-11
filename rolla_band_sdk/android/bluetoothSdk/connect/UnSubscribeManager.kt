package app.rolla.bluetoothSdk.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.device.DeviceTypeState
import app.rolla.bluetoothSdk.utils.extensions.executeWithPermissionCheck
import app.rolla.bluetoothSdk.utils.extensions.log
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.toList

class UnSubscribeManager(
    private val context: Context,
    private val deviceManager: DeviceManager,
    private val operationQueue: OperationQueue,
    private val onError: (ConnectionError) -> Unit,
    private val onEvent: (CharacteristicEvent) -> Unit
) {

    companion object {
        const val TAG = "UnSubscribeManager"

        private const val CLIENT_CHARACTERISTIC_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    }

    private val pendingUnsubscriptions = ConcurrentHashMap<String, MutableSet<String>>()

    @SuppressLint("MissingPermission")
    fun unsubscribeFromCharacteristics(device: BleDevice) {
        context.executeWithPermissionCheck(
            operation = {
                val fullName = device.getFullName()
                val deviceTypes = device.getRequestedDeviceTypes()

                log(
                    TAG,
                    "Starting characteristic unsubscriptions for $fullName with types: ${deviceTypes.joinToString { it.typeName }}"
                )

                if (device.gatt == null) {
                    log(TAG, "No GATT connection for $fullName")
                    onError(
                        ConnectionError(
                            device = device,
                            errorCode = ConnectionError.ERROR_GATT_NULL,
                            operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                            message = "No GATT connection for $fullName"
                        )
                    )
                    return
                }

                var hasUnSubscriptionErrors = false
                val failedCharacteristics = mutableListOf<String>()

                deviceTypes.forEach { deviceType ->
                    deviceType.allowedForSubscribeServices.forEach { service ->
                        try {
                            val bleService = device.gatt.getService(UUID.fromString(service.uuid))
                            if (bleService != null) {
                                service.characteristics.forEach { characteristic ->
                                    try {
                                        val bleCharacteristic = bleService.getCharacteristic(
                                            UUID.fromString(characteristic.uuid)
                                        )
                                        if (bleCharacteristic != null) {
                                            unSubscribeFromCharacteristicInternal(
                                                device,
                                                bleCharacteristic
                                            )
                                        } else {
                                            log(
                                                TAG,
                                                "Characteristic ${characteristic.uuid} not found for $fullName"
                                            )
                                            failedCharacteristics.add(characteristic.uuid)
                                            hasUnSubscriptionErrors = true
                                        }
                                    } catch (e: IllegalArgumentException) {
                                        log(
                                            TAG,
                                            "Invalid characteristic UUID: ${characteristic.uuid}"
                                        )
                                        failedCharacteristics.add(characteristic.uuid)
                                        hasUnSubscriptionErrors = true
                                    }

                                }

                                if (hasUnSubscriptionErrors) {
                                    onError(
                                        ConnectionError(
                                            device = device,
                                            errorCode = ConnectionError.ERROR_UNSUBSCRIPTION_FAILED,
                                            operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                                            message = "Failed to unsubscribe from characteristics for $fullName: ${
                                                failedCharacteristics.toTypedArray()
                                                    .contentToString()
                                            }"
                                        )
                                    )
                                }
                            } else {
                                log(TAG, "Service ${service.uuid} not found for $fullName")
                                val services = device.gatt.services?.map { it.uuid.toString() }
                                log(
                                    TAG,
                                    "Available services for ${device.getFullName()}: $services"
                                )
                                onError(
                                    ConnectionError(
                                        device = device,
                                        errorCode = ConnectionError.ERROR_SERVICE_NOT_FOUND,
                                        operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                                        message = "Service ${service.uuid} not found for $fullName"
                                    )
                                )
                            }
                        } catch (e: IllegalArgumentException) {
                            log(TAG, "Invalid service UUID: ${service.uuid}")
                            onError(
                                ConnectionError(
                                    device = device,
                                    errorCode = ConnectionError.ERROR_INVALID_SERVICE_UUID,
                                    operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                                    message = "Invalid service UUID: ${service.uuid}",
                                    exception = e
                                )
                            )
                        }

                    }
                }
            },
            onPermissionDenied = { exception ->
                log(TAG, "Missing BLUETOOTH_CONNECT permission for characteristic subscription")
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                        operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                        message = "BLUETOOTH_CONNECT permission is missing",
                        exception = exception
                    )
                )
            },
            operationName = Operation.UNSUBSCRIBE_CHARACTERISTIC.name
        )
    }

    @SuppressLint("MissingPermission")
    private fun unSubscribeFromCharacteristicInternal(
        device: BleDevice,
        characteristic: BluetoothGattCharacteristic
    ) {
        val deviceAddress = device.getMacAddress()
        val characteristicUuid = characteristic.uuid.toString()

        log(TAG, "Unsubscribe from $characteristicUuid for $deviceAddress")

        if (!canSubscribeToCharacteristic(characteristic)) {
            log(
                TAG,
                "Characteristic $characteristicUuid does not support notifications/indications"
            )
            onError(
                ConnectionError(
                    device = device,
                    errorCode = ConnectionError.ERROR_CHARACTERISTIC_NOT_SUBSCRIBABLE,
                    operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                    message = "Characteristic $characteristicUuid does not support notifications/indications"
                )
            )
            return
        }

        // Add to pending unsubscriptions
        addToPendingUnsubscriptions(deviceAddress, characteristicUuid)

        // Disable notifications
        val success = device.gatt!!.setCharacteristicNotification(characteristic, false)
        if (!success) {
            log(
                SubscribeManager.Companion.TAG,
                "Failed to enable notifications for $characteristicUuid on $deviceAddress"
            )
            completeUnsubscription(deviceAddress, characteristicUuid)
            onError(
                ConnectionError(
                    device = device,
                    errorCode = ConnectionError.ERROR_NOTIFICATION_ENABLE_FAILED,
                    operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                    message = "Failed to disable notifications for $characteristicUuid on $deviceAddress"
                )
            )
            return
        }

        // Write to descriptor to disable notifications
        val descriptor =
            characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG_UUID))
        if (descriptor != null) {
            log(TAG, "Queueing descriptor write for $characteristicUuid on $deviceAddress")
            val operation = BleOperation.WriteDescriptor(
                deviceAddress = deviceAddress,
                gatt = device.gatt,
                descriptor = descriptor,
                value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
                onComplete = { success, status ->
                    if (success) {
                        log(TAG, "Successfully disabled notifications for $characteristicUuid")
                    } else {
                        log(TAG, "Failed to disable notifications for $characteristicUuid")
                        completeUnsubscription(deviceAddress, characteristicUuid)
                        onError(
                            ConnectionError(
                                device = device,
                                errorCode = ConnectionError.ERROR_DESCRIPTOR_WRITE_FAILED,
                                operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                                message = "Failed to queue descriptor write for $characteristicUuid"
                            )
                        )
                    }
                    // Don't call completeUnsubscription here - let handleDescriptorWrite do it
                }
            )
            operationQueue.queueOperation(operation)
        } else {
            log(TAG, "Notification descriptor not found for $characteristicUuid on $deviceAddress")
            completeUnsubscription(deviceAddress, characteristicUuid)
            onError(
                ConnectionError(
                    device = device,
                    errorCode = ConnectionError.ERROR_DESCRIPTOR_NOT_FOUND,
                    operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                    message = "Notification descriptor not found for $characteristicUuid on $deviceAddress"
                )
            )
        }
    }


    @SuppressLint("MissingPermission")
    fun disableNotification(device: BleDevice) {
        context.executeWithPermissionCheck(
            operation = {
                val fullName = device.getFullName()
                val deviceTypes = device.getRequestedDeviceTypes()

                log(
                    TAG,
                    "Starting characteristic unsubscriptions for $fullName with types: ${deviceTypes.joinToString { it.typeName }}"
                )


                if (device.gatt == null) {
                    log(TAG, "No GATT connection for $fullName")
                    onError(
                        ConnectionError(
                            device = device,
                            errorCode = ConnectionError.ERROR_GATT_NULL,
                            operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                            message = "No GATT connection for $fullName"
                        )
                    )
                    return
                }

                // Unsubscribe from all characteristics if we have permission and GATT connection
                device.deviceTypes.forEach { deviceType ->
                    deviceType.allowedForSubscribeServices.forEach { service ->
                        try {
                            val bleService =
                                device.gatt.getService(UUID.fromString(service.uuid))
                            if (bleService != null) {
                                service.characteristics.forEach { characteristic ->
                                    try {
                                        val bleCharacteristic = bleService.getCharacteristic(
                                            UUID.fromString(characteristic.uuid)
                                        )
                                        if (bleCharacteristic != null && canSubscribeToCharacteristic(
                                                bleCharacteristic
                                            )
                                        ) {
                                            // Disable notifications
                                            device.gatt.setCharacteristicNotification(
                                                bleCharacteristic,
                                                false
                                            )
                                            log(
                                                TAG,
                                                "Disabled notifications for ${characteristic.uuid} on ${device.getMacAddress()}"
                                            )
                                        } else {
                                            log(
                                                TAG,
                                                "Characteristic ${characteristic.uuid} not found for $fullName"
                                            )
                                        }
                                    } catch (e: IllegalArgumentException) {
                                        log(
                                            TAG,
                                            "Invalid characteristic UUID: ${characteristic.uuid}"
                                        )
                                    }
                                }
                            } else {
                                log(TAG, "Service ${service.uuid} not found for $fullName")
                                val services = device.gatt.services?.map { it.uuid.toString() }
                                log(
                                    TAG,
                                    "Available services for ${device.getFullName()}: $services"
                                )
                                onError(
                                    ConnectionError(
                                        device = device,
                                        errorCode = ConnectionError.ERROR_SERVICE_NOT_FOUND,
                                        operation = Operation.DISCONNECT,
                                        message = "Service ${service.uuid} not found for $fullName"
                                    )
                                )
                            }
                        } catch (e: java.lang.IllegalArgumentException) {
                            log(TAG, "Invalid service UUID: ${service.uuid}")
                            onError(
                                ConnectionError(
                                    device = device,
                                    errorCode = ConnectionError.ERROR_INVALID_SERVICE_UUID,
                                    operation = Operation.DISCONNECT,
                                    message = "Invalid service UUID: ${service.uuid}",
                                    exception = e
                                )
                            )
                        }
                    }
                }

            },
            onPermissionDenied = { exception ->
                log(TAG, "Missing BLUETOOTH_CONNECT permission for disableNotification")
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                        operation = Operation.DISCONNECT,
                        message = "BLUETOOTH_CONNECT permission is missing",
                        exception = exception
                    )
                )
            },
            operationName = Operation.DISCONNECT.name
        )
    }

    /**
     * Add characteristic to pending unsubscriptions (thread-safe)
     */
    private fun addToPendingUnsubscriptions(deviceAddress: String, characteristicUuid: String) {
        pendingUnsubscriptions.computeIfAbsent(deviceAddress) {
            Collections.synchronizedSet(mutableSetOf())
        }.add(characteristicUuid)
    }

    /**
     * Mark characteristic unsubscription as complete (thread-safe)
     */
    private fun completeUnsubscription(deviceAddress: String, characteristicUuid: String, onRemove: ((String) -> Unit)? = null) {
        val pending = pendingUnsubscriptions[deviceAddress]
        if (pending != null) {
            synchronized(pending) {
                pending.remove(characteristicUuid)
                onRemove?.invoke(deviceAddress)
                // If all unsubscriptions complete, emit completion event
                if (pending.isEmpty()) {
                    pendingUnsubscriptions.remove(deviceAddress)
                    log(TAG, "All unsubscriptions completed for device: $deviceAddress")
                    deviceManager.updateDevice(deviceAddress) { device ->
                        device.withUpdatedDeviceTypeStates(DeviceTypeState.UNSUBSCRIBED)
                              .withNewRequestedTypes(emptySet())
                    }
                    onEvent(CharacteristicEvent.AllUnsubscriptionsComplete(deviceAddress))
                }
            }
        }
    }

    /**
     * Validate characteristic supports notifications before subscribing
     */
    private fun canSubscribeToCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        val properties = characteristic.properties
        return (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
    }

    /**
     * Handle connection timeout - clean up and report pending subscriptions (thread-safe)
     */
    fun handleDisconnectionTimeout(device: BleDevice) {
        val deviceAddress = device.getMacAddress()
        val pending = pendingUnsubscriptions.remove(deviceAddress)
        if (pending != null) {
            val pendingList = synchronized(pending) { pending.toList() }
            if (pendingList.isNotEmpty()) {
                log(
                    TAG,
                    "Disconnection timeout - cleaning up pending unsubscriptions for ${deviceAddress}: $pendingList"
                )
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_SUBSCRIBING_TIMEOUT,
                        operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                        message = "Disconnection timeout - cleaning up pending unsubscriptions for $deviceAddress: $pendingList"
                    )
                )
            }
        }
    }

    fun cleanup() {
        pendingUnsubscriptions.clear()
    }

    fun cleanForDevice(deviceAddress: String) {
        pendingUnsubscriptions.remove(deviceAddress)
    }

    fun isDescriptorWriteFromUnsubscription(
        deviceAddress: String,
        characteristicUuid: String
    ): Boolean {
        return pendingUnsubscriptions[deviceAddress]?.contains(characteristicUuid) ?: false
    }

    fun handleDescriptorWrite(device: BleDevice, descriptor: BluetoothGattDescriptor, status: Int) {
        context.executeWithPermissionCheck(
            operation = {
                val deviceAddress = device.getMacAddress()
                val characteristicUuid = descriptor.characteristic.uuid.toString()

                log(TAG, "Characteristic: $characteristicUuid")
                log(TAG, "Descriptor: ${descriptor.uuid}")
                log(TAG, "Status: $status (${if (status == BluetoothGatt.GATT_SUCCESS) "SUCCESS" else "FAILED"})")

                log(TAG, "All pending unsubscriptions: ${pendingUnsubscriptions[deviceAddress]}")


                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log(TAG, "✅ Processing successful UNSUBSCRIPTION for $characteristicUuid")
                    completeUnsubscription(deviceAddress, characteristicUuid, onRemove = {
                        onEvent(
                            CharacteristicEvent.CharacteristicUnsubscribed(
                                device,
                                descriptor.characteristic.uuid
                            )
                        )
                    })

                } else {
                    log(TAG, "❌ UNSUBSCRIPTION failed for $characteristicUuid with status: $status")
                    completeUnsubscription(deviceAddress, characteristicUuid)
                    onError(
                        ConnectionError(
                            device = device,
                            errorCode = ConnectionError.ERROR_DESCRIPTOR_WRITE_FAILED,
                            operation = Operation.UNSUBSCRIBE_CHARACTERISTIC,
                            message = "Descriptor write failed for $characteristicUuid with status: $status"
                        )
                    )
                }
            },
            onPermissionDenied = { exception ->
                log(TAG, "Missing BLUETOOTH_CONNECT permission for characteristic unsubscription")
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                        operation = Operation.DESCRIPTOR_WRITE,
                        message = "BLUETOOTH_CONNECT permission is missing",
                        exception = exception
                    )
                )
            },
            operationName = Operation.DESCRIPTOR_WRITE.name
        )
    }

}