package app.rolla.bluetoothSdk.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.content.Context
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.device.DeviceTypeState
import app.rolla.bluetoothSdk.utils.extensions.executeWithPermissionCheck
import app.rolla.bluetoothSdk.utils.extensions.log

class ServiceDiscoveryManager(
    private val context: Context,
    private val deviceManager: DeviceManager,
    private val onError: (ConnectionError) -> Unit,
    private val onEvent: (CharacteristicEvent) -> Unit
) {

    companion object {
        private const val TAG = "ServiceDiscoveryManager"
    }

    @SuppressLint("MissingPermission")
    fun startServiceDiscovery(gatt: BluetoothGatt) {
        val deviceAddress = gatt.device.address

        val device = deviceManager.updateDevice(deviceAddress) { device ->
            device.withGatt(gatt)
        }

        context.executeWithPermissionCheck(
            operation = {
                if (device?.isConnecting() != true) {
                    log(
                        TAG,
                        "Device not connected for service discovery: $deviceAddress"
                    )
                    onError(ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_DEVICE_NOT_CONNECTED,
                        operation = Operation.SERVICE_DISCOVERY,
                        message = "Device not connected for service discovery"
                    ))
                    return
                }

                log(
                    TAG,
                    "Starting service discovery for device: $deviceAddress"
                )
                if (!gatt.discoverServices()) {
                    onError(
                        ConnectionError(
                            device = device,
                            errorCode = ConnectionError.ERROR_SERVICE_DISCOVERY_FAILED,
                            operation = Operation.SERVICE_DISCOVERY,
                            message = "Failed to start service discovery"
                        )
                    )
                }
            },
            onPermissionDenied = { exception ->
                log(TAG, "Missing BLUETOOTH_CONNECT permission for service discovery")
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                        operation = Operation.SERVICE_DISCOVERY,
                        message = "BLUETOOTH_CONNECT permission is missing",
                        exception = exception
                    )
                )
            },
            operationName = Operation.SERVICE_DISCOVERY.name
        )
    }

    fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int, onDiscovered: (BleDevice) -> Unit) {
        log(TAG, "Services discovered - Status: $status")
        val deviceAddress = gatt.device.address

        val device = deviceManager.updateDevice(deviceAddress) { device ->
            device.withGatt(gatt)
                  .withServicesDiscovered(true)
        }

        context.executeWithPermissionCheck(
            operation = {
                if (device?.isConnecting() != true) {
                    log(TAG, "Device not connected during service discovery: $deviceAddress")
                    onError(ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_DEVICE_NOT_CONNECTED,
                        operation = Operation.SERVICE_DISCOVERY,
                        message = "Device not connected for service discovery"
                    ))
                    return
                }
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log(TAG, "Found ${device.gatt?.services?.size} services for device: ${device.btDevice.address}")
                    log(TAG, "Services: ${device.gatt?.services?.map { it.uuid }}")

                    deviceManager.updateDevice(device.getMacAddress()) { device ->
                        device.withUpdatedDeviceTypeStates(DeviceTypeState.SUBSCRIBING)
                    }

                    val serviceUuids = gatt.services?.map { it.uuid } ?: emptyList()
                    onEvent(CharacteristicEvent.ServicesDiscovered(device, serviceUuids))

                    onDiscovered(device)
                } else {
                    log(TAG, "Service discovery failed with status: $status")
                    onError(
                        ConnectionError(
                            device = device,
                            errorCode = ConnectionError.ERROR_SERVICE_DISCOVERY_FAILED_STATUS_UNSUCCESSFUL,
                            operation = Operation.SERVICE_DISCOVERY,
                            message = "Service discovery failed with status: $status"
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
                        operation = Operation.SERVICE_DISCOVERY,
                        message = "BLUETOOTH_CONNECT permission is missing",
                        exception = exception
                    )
                )
            },
            operationName = Operation.SERVICE_DISCOVERY.name
        )
    }
}