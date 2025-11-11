package app.rolla.bluetoothSdk.connect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.content.Context
import androidx.annotation.RequiresPermission
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceConnectionState
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.device.DeviceType
import app.rolla.bluetoothSdk.device.DeviceTypeState
import app.rolla.bluetoothSdk.di.BleScopeContext
import app.rolla.bluetoothSdk.utils.extensions.executeWithPermissionCheck
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class DisconnectManager(
    private val context: Context,
    private val deviceManager: DeviceManager,
    private val timeoutManager: TimeoutManager,
    private val unSubscribeManager: UnSubscribeManager,
    @BleScopeContext private val bleScopeContext: CoroutineContext,
    private val onError: (ConnectionError) -> Unit
) {

    companion object {
        const val TAG = "DisconnectManager"
    }

    private val disconnectScope = CoroutineScope(bleScopeContext)


    @SuppressLint("MissingPermission")
    fun disconnectAll() {
        context.executeWithPermissionCheck(
            operation = {
                deviceManager.getConnectedDevices().forEach { device ->
                    disconnectScope.launch {
                        disconnectFromDevice(device)
                    }
                }
            },
            onPermissionDenied = {
                log(TAG, "Missing BLUETOOTH_CONNECT permission for disconnectAll")
                onError(
                    ConnectionError(
                        device = null,
                        errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                        operation = Operation.DISCONNECT,
                        message = "BLUETOOTH_CONNECT permission is missing"
                    )
                )
            },
            operationName = Operation.DISCONNECT.name
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectFromDevice(bleDevice: BleDevice) {
        disconnectScope.launch {
            val address = bleDevice.getMacAddress()
            val device = deviceManager.getDevice(address)

            if (device == null) {
                onError(
                    ConnectionError(
                        device = bleDevice,
                        errorCode = ConnectionError.ERROR_DEVICE_NOT_FOUND,
                        operation = Operation.DISCONNECT,
                        message = "Device not found for disconnection"
                    )
                )
                return@launch
            }

            if (!device.isConnected()) {
                onError(
                    ConnectionError(
                        device = device,
                        errorCode = ConnectionError.ERROR_DEVICE_NOT_CONNECTED,
                        operation = Operation.DISCONNECT,
                        message = "Device not connected for disconnection"
                    )
                )
                return@launch
            }

            // Disconnect all subscribed device types
            val subscribedTypes = device.getSubscribedTypes()
            if (subscribedTypes.isNotEmpty()) {
                disconnectFromDevice(device, subscribedTypes.toSet())
            } else {
                // Force complete disconnection if no subscribed types
                performCompleteDisconnection(device)
            }
        }
    }

    fun disconnectFromDevice(
        bleDevice: BleDevice,
        deviceTypes: Set<DeviceType>
    ) {
        disconnectScope.launch {
            context.executeWithPermissionCheck(
                operation = {
                    ConnectionValidator.validateDeviceTypes(bleDevice, deviceTypes)?.let {
                        onError(
                            ConnectionError(
                                device = bleDevice,
                                errorCode = ConnectionError.ERROR_INVALID_DEVICE_TYPE,
                                operation = Operation.DISCONNECT,
                                message = "Invalid device types for disconnection"
                            )
                        )
                        return@launch
                    }

                    var device = deviceManager.updateDevice(bleDevice.getMacAddress()) { device ->
                        device.withGatt( device.gatt)
                    }

                    if (device == null) {
                        onError(
                            ConnectionError(
                                device = device,
                                errorCode = ConnectionError.ERROR_DEVICE_NOT_FOUND,
                                operation = Operation.DISCONNECT,
                                message = "Device not found for disconnection"
                            )
                        )
                        return@launch
                    }

                    if (!device.isConnected()) {
                        onError(
                            ConnectionError(
                                device = device,
                                errorCode = ConnectionError.ERROR_DEVICE_NOT_CONNECTED,
                                operation = Operation.DISCONNECT,
                                message = "Device not connected for disconnection"
                            )
                        )
                        return@launch
                    }

                    device = deviceManager.updateDevice(bleDevice.getMacAddress()) { device ->
                        device.withNewRequestedTypes(deviceTypes)
                            .withUpdatedDeviceTypeStates(DeviceTypeState.UNSUBSCRIBING)
                    }
                    // Start disconnection timeout
                    timeoutManager.startDisconnectionTimeout(device!!.getMacAddress(), onDisconnectionTimeout = {
                        handleDisconnectionTimeout(device.getMacAddress(), device)
                    })
                    unSubscribeManager.unsubscribeFromCharacteristics(device)
                },
                onPermissionDenied = { exception ->
                    log(TAG, "Missing BLUETOOTH_CONNECT permission for disconnectFromDevice")
                    onError(
                        ConnectionError(
                            device = bleDevice,
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
    }


    @SuppressLint("MissingPermission")
    private fun performCompleteDisconnection(bleDevice: BleDevice) {
        val address = bleDevice.getMacAddress()

        // Update state to disconnecting
        val device = deviceManager.updateDevice(address) { device ->
            device.withConnectionState(DeviceConnectionState.DISCONNECTING)
                  .withGatt( device.gatt)
        }
        context.executeWithPermissionCheck(
            operation = {
                device?.gatt?.disconnect()
            },
            onPermissionDenied = { exception ->
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

    private fun handleDisconnectionTimeout(deviceAddress: String, bleDevice: BleDevice) {
        unSubscribeManager.handleDisconnectionTimeout(bleDevice)
        log(TAG, "Disconnection timeout for: $deviceAddress, forcing cleanup")

        val device = deviceManager.getDevice(bleDevice.getMacAddress())
        closeGatt(device?.gatt)

        // Clean up manager
        deviceManager.removeDevice(deviceAddress)
    }

    fun handleConnectionFailure(gatt: BluetoothGatt, deviceAddress: String, device: BleDevice, status: Int) {
        // Cancel timeout on failure
        timeoutManager.cancelTimeout(deviceAddress, TimeoutType.CONNECTION)
        timeoutManager.cancelTimeout(deviceAddress, TimeoutType.EARLY_CONNECTION)

        // Clean up device registry
        deviceManager.removeDevice(deviceAddress)

        // Close GATT on connection failure
        closeGatt(gatt)
        onError(
            ConnectionError(
                device = device,
                errorCode = ConnectionError.ERROR_CONNECTION_FAILED,
                operation = Operation.CONNECT,
                message = "Connection failed with status: $status"
            )
        )
    }

    fun handleDeviceDisconnected(gatt: BluetoothGatt?, device: BleDevice, onDisconnected: (String) -> Unit) {
        // Cancel characteristic subscriptions
        val deviceAddress = device.getMacAddress()
        unSubscribeManager.disableNotification(device)

        // Clean up pending subscriptions and unsubscriptions and operation queue
        unSubscribeManager.cleanForDevice(deviceAddress)

        onDisconnected(deviceAddress)

        // Cancel timeouts on disconnection
        timeoutManager.cancelTimeout(deviceAddress, TimeoutType.CONNECTION)
        timeoutManager.cancelTimeout(deviceAddress, TimeoutType.EARLY_CONNECTION)
        timeoutManager.cancelTimeout(deviceAddress, TimeoutType.DISCONNECTION)

        // Handle disconnection
        deviceManager.removeDevice(deviceAddress)

        // Close GATT with proper permission handling
        closeGatt(gatt)
    }

    @SuppressLint("MissingPermission")
    fun closeGatt(gatt: BluetoothGatt?) {
        context.executeWithPermissionCheck(
            operation = {
                gatt?.close()
            },
            onPermissionDenied = { exception ->
                onError(
                    ConnectionError(
                        device = null,
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

    fun disconnectIfThereIsNoDeviceTypesLeft(deviceAddress: String) {
        val device = deviceManager.getDevice(deviceAddress)
        // If no types left, complete disconnection
        if (device?.getSubscribedTypes()?.isEmpty() == true) {
            performCompleteDisconnection(device)
        }
    }
}