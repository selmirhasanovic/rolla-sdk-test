package app.rolla.bluetoothSdk.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import app.rolla.bluetoothSdk.MethodResult
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.services.BleCharacteristic
import app.rolla.bluetoothSdk.services.commands.Command
import app.rolla.bluetoothSdk.utils.extensions.executeWithPermissionCheck
import app.rolla.bluetoothSdk.utils.extensions.log
import java.util.UUID

class ReadManager(
    private val context: Context,
    private val deviceManager: DeviceManager,
    private val operationQueue: OperationQueue,
    private val onError: (ConnectionError) -> Unit,
    private val onEvent: (CharacteristicEvent) -> Unit
) {

    companion object {
        const val TAG = "Read manager"
    }

    @SuppressLint("MissingPermission")
    fun handleCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        log(
            TAG,
            "Characteristic read - UUID: ${characteristic.uuid}, Status: ${status}, Value size: ${value.size}"
        )
        operationQueue.operationComplete(
            gatt.device?.address ?: "Unknown",
            status == BluetoothGatt.GATT_SUCCESS,
            status
        )

        val device = deviceManager.updateDevice(gatt.device.address) { device ->
            device.withGatt(gatt)
        }
        if (device != null) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                context.executeWithPermissionCheck(
                    operation = {
                        val characteristicUuid = characteristic.uuid

                        log(
                            TAG,
                            "Characteristic read: $characteristicUuid for device: ${device.getFullName()}, value size: ${value.size}"
                        )

                        val bleCharacteristic = BleCharacteristic.entries.find {
                            it.uuid.equals(characteristic.uuid.toString(), ignoreCase = true)
                        }
                        if (bleCharacteristic != null) {
                            try {
                                log(TAG, "✅ Processing read data for ${bleCharacteristic.name}")
                                bleCharacteristic.impl.onRead(value, device)
                                onEvent(
                                    CharacteristicEvent.CharacteristicRead(
                                        device,
                                        characteristic.uuid,
                                        value
                                    )
                                )
                            } catch (e: Exception) {
                                log(
                                    TAG,
                                    "❌ Error processing characteristic read data: ${e.message}"
                                )
                                onError(
                                    ConnectionError(
                                        device = device,
                                        errorCode = ConnectionError.ERROR_CHARACTERISTIC_PROCESSING_FAILED,
                                        operation = Operation.READ_CHARACTERISTIC,
                                        message = "Characteristic data processing failed for $characteristicUuid for read operation",
                                        exception = e,
                                    )
                                )
                            }
                        } else {
                            log(TAG, "⚠️ Unknown characteristic UUID: ${characteristic.uuid}")
                            onError(
                                ConnectionError(
                                    device = device,
                                    errorCode = ConnectionError.ERROR_UNKNOWN_CHARACTERISTIC,
                                    operation = Operation.READ_CHARACTERISTIC,
                                    message = "Unknown characteristic: $characteristicUuid for characteristic read"
                                )
                            )
                        }
                    },
                    onPermissionDenied = { exception ->
                        log(TAG, "Missing BLUETOOTH_CONNECT permission for characteristic read")
                        onError(
                            ConnectionError(
                                device = device,
                                errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                                operation = Operation.READ_CHARACTERISTIC,
                                message = "BLUETOOTH_CONNECT permission is missing",
                                exception = exception
                            )
                        )
                    },
                    operationName = Operation.READ_CHARACTERISTIC.name
                )
            } else {
                log(
                    TAG,
                    "❌ Characteristic raad failed for ${characteristic.uuid} with status: $status"
                )
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_GATT_STATUS_FAILED,
                        message = "Descriptor write failed for ${characteristic.uuid} with status: $status",
                        operation = Operation.READ_CHARACTERISTIC
                    )
                )
            }
        } else {
            log(TAG, "❌ Characteristic read failed with status: $status")
            onError(
                ConnectionError(
                    device = null,
                    errorCode = ConnectionError.ERROR_DEVICE_NOT_FOUND,
                    operation = Operation.READ_CHARACTERISTIC,
                    message = "Device not found for characteristic read event"
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(bleDevice: BleDevice, characteristic: BleCharacteristic): MethodResult {
        val deviceAddress = bleDevice.btDevice.address
        val device = deviceManager.getDevice(deviceAddress)
        if (device?.isConnected() != true) {
            return MethodResult(false, "Device not connected for characteristic read")
        }
        val gatt = device.gatt
        if (gatt == null) {
            return MethodResult(false, "GATT is null for characteristic read")
        }

        return context.executeWithPermissionCheck(
            operation = {
                // Find the service and characteristic
                val service = gatt.services?.find { service ->
                    service.characteristics.any {
                        it.uuid.toString().equals(characteristic.uuid, ignoreCase = true)
                    }
                }

                if (service == null) {
                    return@executeWithPermissionCheck MethodResult(
                        false,
                        "Service for characteristic ${characteristic.uuid} not found"
                    )
                }

                val gattCharacteristic =
                    service.getCharacteristic(UUID.fromString(characteristic.uuid))
                if (gattCharacteristic == null) {
                    return@executeWithPermissionCheck MethodResult(false, "Characteristic ${characteristic.uuid} not found")
                }

                val operation = BleOperation.ReadCharacteristic(
                    deviceAddress = deviceAddress,
                    gatt = gatt,
                    characteristic = gattCharacteristic,
                    onComplete = { success, status ->
                        if (!success) {
                            onError(
                                ConnectionError(
                                    device = device,
                                    errorCode = ConnectionError.ERROR_CHARACTERISTIC_OPERATION_FAILED,
                                    message = "Characteristic read failed for ${characteristic.uuid}",
                                    operation = Operation.READ_CHARACTERISTIC,
                                )
                            )
                        }
                    }
                )

                operationQueue.queueOperation(operation)
                return@executeWithPermissionCheck MethodResult(true)
            },
            onPermissionDenied = { exception ->
                log(TAG, "Missing BLUETOOTH_CONNECT permission for characteristic read")
                return@executeWithPermissionCheck MethodResult(false, "BLUETOOTH_CONNECT permission is missing")
            },
            operationName = Operation.READ_CHARACTERISTIC.name
        ) ?: return MethodResult(false, "Unknown error")
    }

    @SuppressLint("MissingPermission")
    fun readRollaBandCommand(bleDevice: BleDevice, characteristic: BleCharacteristic, command: Command<*>): MethodResult {
        val deviceAddress = bleDevice.btDevice.address
        val device = deviceManager.getDevice(deviceAddress)
        if (device?.isConnected() != true) {
            return MethodResult(false, "Device not connected for characteristic read")
        }
        val gatt = device.gatt
        if (gatt == null) {
            return MethodResult(false, "GATT is null for characteristic read")
        }

        return context.executeWithPermissionCheck(
            operation = {
                // Find the service and characteristic
                val service = gatt.services?.find { service ->
                    service.characteristics.any {
                        it.uuid.toString().equals(characteristic.uuid, ignoreCase = true)
                    }
                }

                if (service == null) {
                    return@executeWithPermissionCheck MethodResult(
                        false,
                        "Service for characteristic ${characteristic.uuid} not found"
                    )
                }

                val gattCharacteristic =
                    service.getCharacteristic(UUID.fromString(characteristic.uuid))
                if (gattCharacteristic == null) {
                    return@executeWithPermissionCheck MethodResult(false, "Characteristic ${characteristic.uuid} not found")
                }

                val operation = BleOperation.WriteCharacteristic(
                    deviceAddress = deviceAddress,
                    gatt = gatt,
                    characteristic = gattCharacteristic,
                    data = command.bytesToWrite(),
                    onComplete = { success, status ->
                        if (!success) {
                            onError(
                                ConnectionError(
                                    device = device,
                                    errorCode = ConnectionError.ERROR_CHARACTERISTIC_OPERATION_FAILED,
                                    message = "Characteristic read failed for ${characteristic.uuid}",
                                    operation = Operation.READ_CHARACTERISTIC,
                                )
                            )
                        }
                    }
                )

                operationQueue.queueOperation(operation)
                return@executeWithPermissionCheck MethodResult(true)
            },
            onPermissionDenied = { exception ->
                log(TAG, "Missing BLUETOOTH_CONNECT permission for characteristic read")
                return@executeWithPermissionCheck MethodResult(false, "BLUETOOTH_CONNECT permission is missing")
            },
            operationName = Operation.READ_CHARACTERISTIC.name
        ) ?: return MethodResult(false, "Unknown error")
    }
}