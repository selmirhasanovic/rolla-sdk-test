package app.rolla.bluetoothSdk.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import app.rolla.bluetoothSdk.MethodResult
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.device.DeviceType
import app.rolla.bluetoothSdk.di.BleScopeContext
import app.rolla.bluetoothSdk.utils.extensions.executeWithPermissionCheck
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

class ConnectManager(
    private val context: Context,
    @BleScopeContext bleScopeContext: CoroutineContext,
    private val deviceManager: DeviceManager,
    private val subscribeManager: SubscribeManager,
    private val timeoutManager: TimeoutManager,
    private val gattCallbackHandler: GattCallbackHandler,
    private val onError: (ConnectionError) -> Unit
) {

    companion object {
        const val TAG = "ConnectManager"

        const val DEVICE_TYPE_CONFLICTS = "Device type conflict for connection"
        const val MAX_CONNECTIONS_REACHED = "Maximum concurrent connections reached"

    }

    private val connectScope = CoroutineScope(bleScopeContext)

    fun connectToDevice(bleDevice: BleDevice, deviceTypes: Set<DeviceType>): MethodResult {
        val address = bleDevice.getMacAddress()

        log(
            TAG,
            "Connecting to $address with REQUESTED types: ${deviceTypes.joinToString { it.typeName }}"
        )
        log(
            TAG,
            "Device SUPPORTS types: ${bleDevice.deviceTypes.joinToString { it.typeName }}"
        )

        // Validate device types
        ConnectionValidator.validateDeviceTypes(bleDevice, deviceTypes)?.let { error ->
            log(TAG, "Invalid device types for connection: $error")
            return MethodResult(false, error)
        }



        // Check for device type conflicts
        val conflictingTypes = deviceManager.checkDeviceTypeConflicts(deviceTypes, address)
        if (conflictingTypes.isNotEmpty()) {
            log(TAG, "Device type conflict for connection: $conflictingTypes")
            return MethodResult(false, DEVICE_TYPE_CONFLICTS)
        }

        // Check maximum concurrent connections
        if (!deviceManager.canCreateNewConnection(address)) {
            log(
                TAG,
                "Maximum concurrent connections reached"
            )
            return MethodResult(false, MAX_CONNECTIONS_REACHED)
        }

        log(TAG, "Connecting to device: $address")
        val device = deviceManager.getDevice(address)

        return when {
            device?.isConnected() == true -> {
                log(TAG, "Device already connected: $address")
                deviceManager.updateNewDeviceTypesForConnectedDevice(deviceTypes, address,
                    onSuccess = {
                        connectScope.launch {
                            try {
                                subscribeManager.subscribeToCharacteristics(device)
                            } catch (e: Exception) {
                                // Handle errors and update device manager
                                deviceManager.removeDevice(bleDevice.getMacAddress())
                                onError(
                                    ConnectionError(
                                        device = bleDevice,
                                        errorCode = ConnectionError.ERROR_CONNECTION_FAILED,
                                        operation = Operation.CONNECT,
                                        message = "Connection failed with exception: ${e.message}",
                                        exception = e
                                    )
                                )
                            }
                        }
                    })
            }
            device?.isConnecting() == true -> {
                log(TAG, "Device already connecting: $address")
                deviceManager.updateNewDeviceTypesForConnectingDevice(deviceTypes, address)
            }
            else -> {
                log(TAG, "Starting new connection for device: $address")
                connectScope.launch {
                    try {
                        startNewConnection(bleDevice, deviceTypes, address)
                    } catch (e: Exception) {
                        // Handle errors and update device manager
                        log(TAG, "Connection failed with exception: ${e.message}")
                        deviceManager.removeDevice(bleDevice.getMacAddress())
                        onError(
                            ConnectionError(
                                device = bleDevice,
                                errorCode = ConnectionError.ERROR_CONNECTION_FAILED,
                                operation = Operation.CONNECT,
                                message = "Connection failed with exception: ${e.message}",
                                exception = e
                            )
                        )
                    }
                }
                MethodResult(true)
            }
        }
    }

    private fun startNewConnection(
        bleDevice: BleDevice,
        deviceTypes: Set<DeviceType>,
        deviceAddress: String
    ) {
        log(TAG, "Starting new connection for device: $deviceAddress")
        deviceManager.addDevice(bleDevice, deviceTypes)
        // Start connection timeout
        timeoutManager.startConnectionTimeout(deviceAddress, onConnectionTimeout = { device ->
            handleConnectionTimeout(device)
        })

        timeoutManager.startEarlyConnectionTimeout(deviceAddress, onConnectionTimeout = { device ->
            handleConnectionTimeout(device)
        })

        // Create GATT callback and connect
        val gattCallback = gattCallbackHandler.createGattCallback(bleDevice)

        performGattConnection(bleDevice, gattCallback, deviceAddress)
    }

    /**
     * Handle connection timeout
     */
    private fun handleConnectionTimeout(device: BleDevice) {
        // Clean up pending subscriptions and report error if any were pending
        subscribeManager.handleConnectionTimeout(device)

        // Clean up device state
        deviceManager.removeDevice(device.getMacAddress())

        // Report general connection timeout
        onError(ConnectionError(
            device = device,
            errorCode = ConnectionError.ERROR_CONNECTION_TIMEOUT,
            operation = Operation.CONNECT,
            message = "Connection timeout"
        ))

        log(TAG, "Connection timeout handled for device: ${device.getMacAddress()}")
    }


    @SuppressLint("MissingPermission")
    private fun performGattConnection(
        bleDevice: BleDevice,
        gattCallback: BluetoothGattCallback,
        deviceAddress: String
    ) {
        context.executeWithPermissionCheck(
            operation = {
                log(TAG, "Device type: ${bleDevice.btDevice.type}")
                val gatt = bleDevice.btDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                log(TAG, "connectGatt returned: ${gatt != null}")
                if (gatt == null) {
                    log(TAG, "Failed to create GATT connection for $deviceAddress")
                    handleConnectionSetupFailure(deviceAddress)
                } else {
                    log(TAG, "GATT connection created successfully for $deviceAddress")
                }
            },
            onPermissionDenied = { exception ->
                log(TAG, "Missing BLUETOOTH_CONNECT permission for GATT connection")
                onError(
                    ConnectionError(
                        device = bleDevice,
                        errorCode = ConnectionError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                        operation = Operation.CONNECT,
                        message = "BLUETOOTH_CONNECT permission is missing",
                        exception = exception
                    )
                )
            },
            operationName = Operation.CONNECT.name
        )
    }

    private fun handleConnectionSetupFailure(deviceAddress: String) {
        log(TAG, "Connection setup failed for $deviceAddress: Failed to create GATT connection")
        timeoutManager.cancelTimeout(deviceAddress, TimeoutType.CONNECTION)
        timeoutManager.cancelTimeout(deviceAddress, TimeoutType.EARLY_CONNECTION)
        deviceManager.removeDevice(deviceAddress)
    }
}