package app.rolla.bluetoothSdk.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.services.BleCharacteristic
import app.rolla.bluetoothSdk.utils.extensions.executeWithPermissionCheck
import app.rolla.bluetoothSdk.utils.extensions.log

class NotifyManager(
    private val context: Context,
    private val deviceManager: DeviceManager,
    private val onError: (ConnectionError) -> Unit,
    private val onEvent: (CharacteristicEvent) -> Unit
) {

    companion object {
        const val TAG = "Notify manager"
    }

    @SuppressLint("MissingPermission")
    fun handleCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        log(TAG, "Characteristic changed - UUID: ${characteristic.uuid}, Value size: ${value.size}")
        val device = deviceManager.updateDevice(gatt.device.address) { device ->
            device.withGatt(gatt)
        }
        if (device != null) {
            context.executeWithPermissionCheck(
                operation = {
                    val characteristicUuid = characteristic.uuid

                    log(TAG, "Characteristic changed: $characteristicUuid for device: ${device.getFullName()}" +
                            ", value size: ${value.size}")

                    // Find the BleCharacteristic enum and process data
                    val bleCharacteristic = BleCharacteristic.entries.find {
                        it.uuid.equals(characteristicUuid.toString(), ignoreCase = true)
                    }

                    if (bleCharacteristic != null) {
                        try {
                            // Process the data through the characteristic implementation
                            bleCharacteristic.impl.onNotify(value, device)

                            // Emit event with processed data
                            onEvent(CharacteristicEvent.CharacteristicChanged(device, characteristicUuid, value))
                        } catch (e: Exception) {
                            log(TAG, "Error processing characteristic data for $characteristicUuid: ${e.message}")
                            onError(ConnectionError(
                                device = device,
                                errorCode = ConnectionError.ERROR_CHARACTERISTIC_PROCESSING_FAILED,
                                operation = Operation.NOTIFY_CHARACTERISTIC,
                                message = "Characteristic data processing failed for $characteristicUuid for changed operation",
                                exception = e,
                            ))
                        }
                    } else {
                        log(TAG, "Unknown characteristic: $characteristicUuid")
                        onError(ConnectionError(
                            device = device,
                            errorCode = ConnectionError.ERROR_UNKNOWN_CHARACTERISTIC,
                            operation = Operation.NOTIFY_CHARACTERISTIC,
                            message = "Unknown characteristic: $characteristicUuid for characteristic changed"
                        ))
                    }
                },
                onPermissionDenied = { exception ->
                    log(TAG, "Missing BLUETOOTH_CONNECT permission for characteristic changed")
                    onError(ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                        operation = Operation.NOTIFY_CHARACTERISTIC,
                        message = "BLUETOOTH_CONNECT permission is missing",
                        exception = exception)
                    )
                },
                operationName = Operation.NOTIFY_CHARACTERISTIC.name
            )
        } else {
            log(TAG, "⚠️ Device not found for characteristic changed event")
            onError(
                ConnectionError(
                    device = null,
                    errorCode = ConnectionError.ERROR_DEVICE_NOT_FOUND,
                    operation = Operation.NOTIFY_CHARACTERISTIC,
                    message = "Device not found for characteristic changed event"
                )
            )
        }
    }
}