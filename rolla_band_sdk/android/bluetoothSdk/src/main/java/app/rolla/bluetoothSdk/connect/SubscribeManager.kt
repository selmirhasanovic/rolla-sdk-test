package app.rolla.bluetoothSdk.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceConnectionState
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.device.DeviceTypeState
import app.rolla.bluetoothSdk.services.Characteristic
import app.rolla.bluetoothSdk.utils.extensions.executeWithPermissionCheck
import app.rolla.bluetoothSdk.utils.extensions.log
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SubscribeManager(
    private val context: Context,
    private val deviceManager: DeviceManager,
    private val operationQueue: OperationQueue,
    private val onError: (ConnectionError) -> Unit,
    private val onEvent: (CharacteristicEvent) -> Unit
) {

    companion object {
        const val TAG = "SubscribeManager"

        private const val CLIENT_CHARACTERISTIC_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    }

    private val pendingSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()
    private val postSubscriptionManager = PostSubscriptionManager(operationQueue, onError, onEvent)

    @SuppressLint("MissingPermission")
    fun subscribeToCharacteristics(device: BleDevice) {
        context.executeWithPermissionCheck(
            operation = {
                val fullName = device.getFullName()
                val deviceTypes = device.getRequestedDeviceTypes()
                log(
                    TAG,
                    "Starting characteristic subscriptions for $fullName with types: ${deviceTypes.joinToString { it.typeName }}"
                )

                if (device.gatt == null) {
                    log(TAG, "No GATT connection for $fullName")
                    onError(
                        ConnectionError(
                            device = device,
                            errorCode = ConnectionError.ERROR_GATT_NULL,
                            operation = Operation.SUBSCRIBE_CHARACTERISTIC,
                            message = "No GATT connection for $fullName"
                        )
                    )
                    return
                }

                var hasSubscriptionErrors = false
                val failedCharacteristics = mutableListOf<String>()

                val allCharacteristics = mutableSetOf<Pair<Characteristic, BluetoothGattCharacteristic>>()
                deviceTypes.forEach { deviceType ->
                    deviceType.allowedForSubscribeServices.forEach { service ->
                        try {
                            val bleService = device.gatt.getService(UUID.fromString(service.uuid))
                            if (bleService != null) {
                                service.characteristics.forEach { characteristic ->
                                    try {
                                        val bleCharacteristic =
                                            bleService.getCharacteristic(
                                                UUID.fromString(
                                                    characteristic.uuid
                                                )
                                            )
                                        if (bleCharacteristic != null) {
                                            allCharacteristics.add(Pair(characteristic, bleCharacteristic))
                                        } else {
                                            log(
                                                TAG,
                                                "Characteristic ${characteristic.uuid} not found for $fullName"
                                            )
                                            failedCharacteristics.add(characteristic.uuid)
                                            hasSubscriptionErrors = true
                                        }
                                    } catch (e: IllegalArgumentException) {
                                        log(
                                            TAG,
                                            "Invalid characteristic UUID: ${characteristic.uuid}"
                                        )
                                        failedCharacteristics.add(characteristic.uuid)
                                        hasSubscriptionErrors = true
                                    }
                                }
                                if (hasSubscriptionErrors) {
                                    onError(
                                        ConnectionError(
                                            device = device,
                                            errorCode = ConnectionError.ERROR_SUBSCRIPTION_FAILED,
                                            operation = Operation.SUBSCRIBE_CHARACTERISTIC,
                                            message = "Failed to subscribe to characteristics for $fullName: ${
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
                                        operation = Operation.SUBSCRIBE_CHARACTERISTIC,
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
                                    operation = Operation.SUBSCRIBE_CHARACTERISTIC,
                                    message = "Invalid service UUID: ${service.uuid}",
                                    exception = e
                                )
                            )
                        }
                    }
                }
                for (characteristic in allCharacteristics) {
                    subscribeToCharacteristicInternal(
                        device,
                        characteristic.second,
                        characteristic.first
                    )
                }
            },
            onPermissionDenied = { exception ->
                log(TAG, "Missing BLUETOOTH_CONNECT permission for characteristic subscription")
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                        operation = Operation.SUBSCRIBE_CHARACTERISTIC,
                        message = "BLUETOOTH_CONNECT permission is missing",
                        exception = exception
                    )
                )
            },
            operationName = Operation.SUBSCRIBE_CHARACTERISTIC.name
        )

    }


    @SuppressLint("MissingPermission")
    private fun subscribeToCharacteristicInternal(
        device: BleDevice,
        bleCharacteristic: BluetoothGattCharacteristic,
        characteristic: Characteristic
    ) {
        val deviceAddress = device.getMacAddress()
        val characteristicUuid = bleCharacteristic.uuid.toString()
        val gatt = device.gatt!!

        log(TAG, "Subscribe to $characteristicUuid for $deviceAddress")

        if (!canSubscribeToCharacteristic(bleCharacteristic)) {
            log(
                TAG,
                "Characteristic $characteristicUuid does not support notifications/indications"
            )
            onError(
                ConnectionError(
                    device = device,
                    errorCode = ConnectionError.ERROR_CHARACTERISTIC_NOT_SUBSCRIBABLE,
                    operation = Operation.SUBSCRIBE_CHARACTERISTIC,
                    message = "Characteristic $characteristicUuid does not support notifications/indications"
                )
            )
            postSubscriptionManager.addCommands(device, bleCharacteristic, characteristic)
            return
        }

        addToPendingSubscriptions(deviceAddress, characteristicUuid)


        val success = gatt.setCharacteristicNotification(bleCharacteristic, true)
        if (!success) {
            log(TAG, "Failed to enable notifications for $characteristicUuid on $deviceAddress")
            completeSubscription(deviceAddress, characteristicUuid)
            onError(
                ConnectionError(
                    device = device,
                    errorCode = ConnectionError.ERROR_NOTIFICATION_ENABLE_FAILED,
                    operation = Operation.SUBSCRIBE_CHARACTERISTIC,
                    message = "Failed to enable notifications for $characteristicUuid on $deviceAddress"
                )
            )
            return
        }

        val descriptor =
            bleCharacteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG_UUID))
        if (descriptor != null) {
            log(TAG, "Queueing descriptor write for $characteristicUuid on $deviceAddress")

            val operation = BleOperation.WriteDescriptor(
                deviceAddress = deviceAddress,
                gatt = gatt,
                descriptor = descriptor,
                onComplete = { success, status ->
                    if (success) {
                        log(TAG, "Descriptor write queued successfully for $characteristicUuid")
                    } else {
                        log(TAG, "Failed to queue descriptor write for $characteristicUuid")
                        completeSubscription(deviceAddress, characteristicUuid)
                        onError(
                            ConnectionError(
                                device = device,
                                errorCode = ConnectionError.ERROR_DESCRIPTOR_WRITE_FAILED,
                                operation = Operation.SUBSCRIBE_CHARACTERISTIC,
                                message = "Failed to queue descriptor write for $characteristicUuid"
                            )
                        )
                    }
                }
            )

            operationQueue.queueOperation(operation)
        } else {
            log(TAG, "Notification descriptor not found for $characteristicUuid on $deviceAddress")
            completeSubscription(deviceAddress, characteristicUuid)
            onError(
                ConnectionError(
                    device = device,
                    errorCode = ConnectionError.ERROR_DESCRIPTOR_NOT_FOUND,
                    operation = Operation.SUBSCRIBE_CHARACTERISTIC,
                    message = "Notification descriptor not found for $characteristicUuid on $deviceAddress"
                )
            )
        }

        postSubscriptionManager.addCommands(device, bleCharacteristic, characteristic)
    }

    private fun canSubscribeToCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        val properties = characteristic.properties
        return (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
    }

    /**
     * Add characteristic to pending subscriptions (thread-safe)
     */
    private fun addToPendingSubscriptions(deviceAddress: String, characteristicUuid: String) {
        pendingSubscriptions.computeIfAbsent(deviceAddress) {
            Collections.synchronizedSet(mutableSetOf())
        }.add(characteristicUuid)
    }

    /**
     * Mark characteristic subscription as complete (thread-safe)
     */
    private fun completeSubscription(deviceAddress: String, characteristicUuid: String) {
        val pending = pendingSubscriptions[deviceAddress]
        if (pending != null) {
            synchronized(pending) {
                pending.remove(characteristicUuid)

                // If all subscriptions complete, emit completion event
                if (pending.isEmpty()) {
                    pendingSubscriptions.remove(deviceAddress)
                    log(TAG, "All subscriptions completed for device: $deviceAddress")
                    // Get the requested types that were just subscribed
                    deviceManager.updateDevice(deviceAddress) { device ->
                        device.withUpdatedDeviceTypeStates(DeviceTypeState.SUBSCRIBED)
                    }
                    onEvent(CharacteristicEvent.AllSubscriptionsComplete(deviceAddress))
                    setDeviceFullyConnected(deviceAddress)
                }
            }
        }
    }

    fun setDeviceFullyConnected(deviceAddress: String) {
        log(TAG, "setDeviceFullyConnected called for $deviceAddress")
        if (!postSubscriptionManager.hasPendingPostCommands(deviceAddress)) {
            log(TAG, "Device fully connected complete subscription: $deviceAddress")
            deviceManager.updateDevice(deviceAddress) { device ->
                device.withConnectionState(DeviceConnectionState.CONNECTED)
                    .withNewRequestedTypes(emptySet())
            }
            onEvent(CharacteristicEvent.DeviceFullyConnected(deviceAddress))
        } else {
            log(TAG, "Processing post-subscription commands")
            postSubscriptionManager.processPostSubscriptionCommands()
        }
    }

    /**
     * Handle connection timeout - clean up and report pending subscriptions (thread-safe)
     */
    fun handleConnectionTimeout(device: BleDevice) {
        val deviceAddress = device.getMacAddress()
        val pending = pendingSubscriptions.remove(deviceAddress)
        if (pending != null) {
            val pendingList = synchronized(pending) { pending.toList() }
            if (pendingList.isNotEmpty()) {
                log(
                    TAG,
                    "Connection timeout - cleaning up pending subscriptions for ${deviceAddress}: $pendingList"
                )
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_SUBSCRIBING_TIMEOUT,
                        operation = Operation.SUBSCRIBE_CHARACTERISTIC,
                        message = "Connection timeout - cleaning up pending subscriptions for $deviceAddress: $pendingList"
                    )
                )
            }
        }
    }

    fun cleanup() {
        pendingSubscriptions.clear()
        postSubscriptionManager.cleanup()
    }

    fun cleanForDevice(deviceAddress: String) {
        pendingSubscriptions.remove(deviceAddress)
        postSubscriptionManager.cleanForDevice(deviceAddress)
    }

    fun isDescriptorWriteFromSubscription(
        deviceAddress: String,
        characteristicUuid: String
    ): Boolean {
        return pendingSubscriptions[deviceAddress]?.contains(characteristicUuid) ?: false
    }

    fun handleDescriptorWrite(device: BleDevice, descriptor: BluetoothGattDescriptor, status: Int) {
        context.executeWithPermissionCheck(
            operation = {
                val deviceAddress = device.getMacAddress()
                val characteristicUuid = descriptor.characteristic.uuid.toString()

                log(TAG, "Characteristic: $characteristicUuid")
                log(TAG, "Descriptor: ${descriptor.uuid}")
                log(TAG, "Status: $status (${if (status == BluetoothGatt.GATT_SUCCESS) "SUCCESS" else "FAILED"})")


                log(TAG, "All pending subscriptions: ${pendingSubscriptions[deviceAddress]}")


                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log(TAG, "✅ Processing successful SUBSCRIPTION for $characteristicUuid")
                    completeSubscription(deviceAddress, characteristicUuid)
                    onEvent(
                        CharacteristicEvent.CharacteristicSubscribed(
                            device,
                            descriptor.characteristic.uuid
                        )
                    )

                } else {
                    log(TAG, "❌ SUBSCRIPTION failed for $characteristicUuid with status: $status")
                    completeSubscription(deviceAddress, characteristicUuid)
                    onError(
                        ConnectionError(
                            device = device,
                            errorCode = ConnectionError.ERROR_DESCRIPTOR_WRITE_FAILED,
                            operation = Operation.SUBSCRIBE_CHARACTERISTIC,
                            message = "Descriptor write failed for $characteristicUuid with status: $status"
                        )
                    )
                }
            },
            onPermissionDenied = { exception ->
                log(TAG, "Missing BLUETOOTH_CONNECT permission for characteristic subscription")
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