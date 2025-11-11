package app.rolla.bluetoothSdk.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.services.BleCharacteristic
import app.rolla.bluetoothSdk.utils.extensions.executeWithPermissionCheck
import app.rolla.bluetoothSdk.utils.extensions.log
import java.util.UUID

class WriteManager(
    private val context: Context,
    private val deviceManager: DeviceManager,
    private val operationQueue: OperationQueue,
    private val onError: (ConnectionError) -> Unit,
    private val onEvent: (CharacteristicEvent) -> Unit
) {

    companion object {
        const val TAG = "Write Manager"
    }

    @SuppressLint("MissingPermission")
    fun handleCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        log(TAG, "Characteristic write - UUID: ${characteristic.uuid}, Status: $status")
        operationQueue.operationComplete(gatt.device.address, status == BluetoothGatt.GATT_SUCCESS, status)

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
                            "Characteristic write: $characteristicUuid for device: ${device.getFullName()}, value size: ${characteristic.value?.size}"
                        )

                        val bleCharacteristic = BleCharacteristic.entries.find {
                            it.uuid.equals(characteristic.uuid.toString(), ignoreCase = true)
                        }
                        if (bleCharacteristic != null) {
                            try {
                                log(TAG, "✅ Processing write data for ${bleCharacteristic.name}")
                                bleCharacteristic.impl.onWrite(characteristic.value, device)
                                onEvent(
                                    CharacteristicEvent.CharacteristicWrite(
                                        device,
                                        characteristic.uuid
                                    )
                                )
                            } catch (e: Exception) {
                                log(
                                    TAG,
                                    "❌ Error processing characteristic write data: ${e.message}"
                                )
                                onError(
                                    ConnectionError(
                                        device = device,
                                        errorCode = ConnectionError.ERROR_CHARACTERISTIC_PROCESSING_FAILED,
                                        operation = Operation.WRITE_CHARACTERISTIC,
                                        message = "Characteristic data processing failed for $characteristicUuid for write operation",
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
                                    operation = Operation.WRITE_CHARACTERISTIC,
                                    message = "Unknown characteristic: $characteristicUuid for characteristic write"
                                )
                            )
                        }
                    },
                    onPermissionDenied = { exception ->
                        log(TAG, "Missing BLUETOOTH_CONNECT permission for characteristic write ${characteristic.uuid}")
                        onError(
                            ConnectionError(
                                device = device,
                                errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                                operation = Operation.WRITE_CHARACTERISTIC,
                                message = "BLUETOOTH_CONNECT permission is missing",
                                exception = exception
                            )
                        )
                    },
                    operationName = Operation.WRITE_CHARACTERISTIC.name
                )
            } else {
                log(
                    TAG,
                    "❌ Characteristic write failed for ${characteristic.uuid} with status: $status"
                )
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_GATT_STATUS_FAILED,
                        operation = Operation.WRITE_CHARACTERISTIC,
                        message = "Characteristic write failed for ${characteristic.uuid} with status: $status"
                    )
                )
            }
        } else {
            onError(
                ConnectionError(
                    device = null,
                    errorCode = ConnectionError.ERROR_DEVICE_NOT_FOUND,
                    operation = Operation.WRITE_CHARACTERISTIC,
                    message = "Device not found for characteristic write event"
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(bleDevice: BleDevice, characteristic: BleCharacteristic, data: ByteArray): Boolean {
        val deviceAddress = bleDevice.btDevice.address
        val device = deviceManager.getDevice(deviceAddress)
        if (device?.isConnected() != true) {
            onError(ConnectionError(
                device = device,
                errorCode = ConnectionError.ERROR_DEVICE_NOT_CONNECTED,
                operation = Operation.WRITE_CHARACTERISTIC,
                message = "Device not connected for characteristic write"
            ))
            return false
        }

        val gatt = device.gatt
        if (gatt == null) {
            onError(ConnectionError(
                device = device,
                errorCode = ConnectionError.ERROR_GATT_NULL,
                operation = Operation.WRITE_CHARACTERISTIC,
                message = "GATT is null for characteristic write"
            ))
            return false
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
                    onError(
                        ConnectionError(
                            device = device,
                            errorCode = ConnectionError.ERROR_SERVICE_NOT_FOUND,
                            operation = Operation.WRITE_CHARACTERISTIC,
                            message = "Service for characteristic ${characteristic.uuid} not found"
                        )
                    )
                    return false
                }


                val gattCharacteristic = service.getCharacteristic(UUID.fromString(characteristic.uuid))
                if (gattCharacteristic == null) {
                    onError(ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_CHARACTERISTIC_NOT_FOUND,
                        operation = Operation.WRITE_CHARACTERISTIC,
                        message = "Characteristic ${characteristic.uuid} not found"
                    ))
                    return false
                }

                val operation = BleOperation.WriteCharacteristic(
                    deviceAddress = deviceAddress,
                    gatt = gatt,
                    characteristic = gattCharacteristic,
                    data = data,
                    onComplete = { success, status ->
                        if (!success) {
                            onError(ConnectionError(
                                device = device,
                                errorCode = ConnectionError.ERROR_CHARACTERISTIC_OPERATION_FAILED,
                                message = "Characteristic read failed for ${characteristic.uuid}",
                                operation = Operation.WRITE_CHARACTERISTIC,
                            ))
                        }
                    }
                )

                operationQueue.queueOperation(operation)
                return true
            },
            onPermissionDenied = {
                log(TAG, "Missing BLUETOOTH_CONNECT permission for characteristic write")
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                        operation = Operation.WRITE_CHARACTERISTIC,
                        message = "BLUETOOTH_CONNECT permission is missing"
                    )
                )
                false
            },
            operationName = Operation.WRITE_CHARACTERISTIC.name
        ) ?: false
    }
}