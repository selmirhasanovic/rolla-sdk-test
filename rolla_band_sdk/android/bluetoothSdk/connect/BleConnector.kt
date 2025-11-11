package app.rolla.bluetoothSdk.connect

import android.bluetooth.BluetoothGatt
import android.content.Context
import androidx.annotation.RequiresPermission
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import app.rolla.bluetoothSdk.MethodResult
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceConnectionState
import app.rolla.bluetoothSdk.device.DeviceEvent
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.device.DeviceType
import app.rolla.bluetoothSdk.di.BleScopeContext
import app.rolla.bluetoothSdk.services.BleCharacteristic
import app.rolla.bluetoothSdk.services.commands.Command
import app.rolla.bluetoothSdk.utils.extensions.log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * Simplified connection manager - acts as facade
 */
@Singleton
class BleConnector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceManager: DeviceManager,
    private val operationQueue: OperationQueue,
    @BleScopeContext bleScopeContext: CoroutineContext
) {

    private val managerScope = CoroutineScope(bleScopeContext)

    companion object {
        private const val TAG = "BleConnector"
    }

    // Flows
    private val _characteristicEvents = MutableSharedFlow<CharacteristicEvent>(replay = 1)
    val characteristicEvents: SharedFlow<CharacteristicEvent> = _characteristicEvents.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<ConnectionError>(replay = 1)
    val errorFlow: SharedFlow<ConnectionError> = _errorFlow.asSharedFlow()

    private val readManager =
        ReadManager(context, deviceManager, operationQueue, ::emitError, ::emitCharacteristicEvent)
    private val writeManager =
        WriteManager(context, deviceManager, operationQueue, ::emitError, ::emitCharacteristicEvent)
    private val notifyManager =
        NotifyManager(context, deviceManager, ::emitError, ::emitCharacteristicEvent)
    private val serviceDiscoveryManager =
        ServiceDiscoveryManager(context, deviceManager, ::emitError, ::emitCharacteristicEvent)
    private val subscribeManager = SubscribeManager(
        context,
        deviceManager,
        operationQueue,
        ::emitError,
        ::emitCharacteristicEvent
    )
    private val unSubscribeManager = UnSubscribeManager(
        context,
        deviceManager,
        operationQueue,
        ::emitError,
        ::emitCharacteristicEvent
    )
    private val timeoutManager = TimeoutManager(bleScopeContext, deviceManager)
    private val gattCallbackHandler = GattCallbackHandler(::handleGattCallback)

    private val connectManager = ConnectManager(
        context,
        bleScopeContext,
        deviceManager,
        subscribeManager,
        timeoutManager,
        gattCallbackHandler,
        ::emitError
    )
    private val disconnectManager = DisconnectManager(
        context,
        deviceManager,
        timeoutManager,
        unSubscribeManager,
        bleScopeContext,
        ::emitError
    )

    init {
        log(TAG, "Starting characteristic events collection")
        managerScope.launch {
            log(TAG, "Event collection coroutine started")
            try {
                _characteristicEvents.collect { event ->
                    log(TAG, "Received characteristic event: ${event.javaClass.simpleName}")
                    when (event) {
                        is CharacteristicEvent.AllSubscriptionsComplete -> {
                            log(TAG, "Processing AllSubscriptionsComplete for ${event.deviceAddress}")
                        }
                        is CharacteristicEvent.AllPostSubscriptionCommandsComplete -> {
                            log(TAG, "Processing AllPostSubscriptionCommandsComplete for ${event.deviceAddress}")
                            subscribeManager.setDeviceFullyConnected(event.deviceAddress)
                        }
                        is CharacteristicEvent.DeviceFullyConnected -> {
                            log(TAG, "Processing DeviceFullyConnected for ${event.deviceAddress}")
                            timeoutManager.cancelTimeout(event.deviceAddress, TimeoutType.CONNECTION)
                        }
                        is CharacteristicEvent.AllUnsubscriptionsComplete -> {
                            disconnectManager.disconnectIfThereIsNoDeviceTypesLeft(event.deviceAddress)
                            timeoutManager.cancelTimeout(event.deviceAddress, TimeoutType.DISCONNECTION)
                        }

                        is CharacteristicEvent.CharacteristicChanged -> {}
                        is CharacteristicEvent.CharacteristicRead -> {}
                        is CharacteristicEvent.CharacteristicWrite -> {}
                        is CharacteristicEvent.Error -> {}
                        is CharacteristicEvent.CharacteristicSubscribed -> {}
                        is CharacteristicEvent.CharacteristicUnsubscribed -> {}
                        is CharacteristicEvent.ServicesDiscovered -> {}
                    }
                }
            } catch (e: Exception) {
                log(TAG, "Event collection coroutine crashed: ${e.message}")
                e.printStackTrace()
            }
        }
        // Listen to device events
        managerScope.launch {
            deviceManager.deviceEventFlow.collect { event ->
                when (event) {
                    is DeviceEvent.RssiReadRequested -> {
                        handleRssiReadRequest(event.deviceAddress)
                    }
                    is DeviceEvent.DeviceUnresponsive -> {
                        log(TAG, "Device became unresponsive: ${event.deviceAddress}")
                        val device = getDeviceByUuid(event.deviceAddress)
                        if (device != null) {
                            // Clean up device state through disconnect manager
                            handleDeviceDisconnected(device.gatt, event.deviceAddress, device)
                            deviceManager.handleUnresponsiveDevice(device)
                        }
                    }
                }
            }
        }
    }

    // Public API - delegates to operations
    fun connectToDevice(bleDevice: BleDevice, deviceTypes: Set<DeviceType>): MethodResult {
        return connectManager.connectToDevice(bleDevice, deviceTypes)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectFromDevice(bleDevice: BleDevice) {
        disconnectManager.disconnectFromDevice(bleDevice)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectFromDevice(macAddress: String) {
        val bleDevice = deviceManager.getDevice(macAddress)
        if (bleDevice != null) {
            disconnectFromDevice(bleDevice, bleDevice.deviceTypes)
        } else {
            emitError(ConnectionError(
                device = null,
                errorCode = ConnectionError.ERROR_DEVICE_NOT_FOUND,
                operation = Operation.DISCONNECT,
                message = "Device not found for disconnection"
            ))
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectFromDevice(bleDevice: BleDevice, deviceTypes: Set<DeviceType>) {
        disconnectManager.disconnectFromDevice(bleDevice, deviceTypes)
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readCharacteristic(bleDevice: BleDevice, characteristic: BleCharacteristic): MethodResult {
        return readManager.readCharacteristic(bleDevice, characteristic)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readRollaBandCommand(bleDevice: BleDevice, characteristic: BleCharacteristic, command: Command<*>): MethodResult {
        return readManager.readRollaBandCommand(bleDevice, characteristic, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeCharacteristic(
        bleDevice: BleDevice,
        characteristic: BleCharacteristic,
        data: ByteArray
    ): Boolean {
        return writeManager.writeCharacteristic(bleDevice, characteristic, data)
    }

    fun disconnectAll() {
        disconnectManager.disconnectAll()
    }

    // Simple getters - use DeviceManager methods
    fun getConnectedDevices(): List<BleDevice> = deviceManager.getConnectedDevices()

    fun isDeviceConnected(macAddress: String): Boolean = deviceManager.isConnected(macAddress)

    fun getConnectionState(macAddress: String): DeviceConnectionState? =
        deviceManager.getConnectionState(macAddress)

    fun getConnectedDeviceForType(deviceType: DeviceType): BleDevice? =
        deviceManager.getDeviceForType(deviceType)

    fun getGattForDevice(bleDevice: BleDevice): BluetoothGatt? =
        deviceManager.getGattForDevice(bleDevice)

    fun getDeviceByUuid(uuid: String): BleDevice? {
        return deviceManager.getDevice(uuid)
    }

    fun handleRssiReadRequest(deviceAddress: String) {
        val device = getDeviceByUuid(deviceAddress)
        device?.gatt?.let { gatt ->
            log(TAG, "Triggering RSSI read for device: $deviceAddress")

            val rssiOperation = BleOperation.ReadRssi(
                deviceAddress = deviceAddress,
                gatt = gatt,
                onComplete = { success, rssi ->
                    if (success && rssi != null) {
                        log(TAG, "RSSI read successful for $deviceAddress: $rssi")
                        // Update device timestamp through scanner
                        deviceManager.updateDevice(deviceAddress) { device ->
                            device.withRssi(rssi).copy(timestamp = System.currentTimeMillis())
                        }
                    } else {
                        log(TAG, "RSSI read failed for $deviceAddress")
                    }
                }
            )
            operationQueue.queueOperation(rssiOperation)
        }
    }

    fun getAllDevices(): List<BleDevice> = deviceManager.getAllDevices()

    private fun emitError(error: ConnectionError) {
        managerScope.launch {
            _errorFlow.emit(error)
        }
    }

    /**
     * Clean up resources when manager is destroyed
     */
    fun destroy() {
        disconnectAll()
        deviceManager.clear()
        timeoutManager.clear()
        subscribeManager.cleanup()
        unSubscribeManager.cleanup()
        operationQueue.cleanup()
        managerScope.cancel()
    }

    // Internal methods for handling GATT callbacks
    private fun handleGattCallback(
        gatt: BluetoothGatt?,
        callback: GattCallbackType
    ) {
        //log(TAG, "Callback Type: ${callback::class.simpleName}")

        when (callback) {
            is GattCallbackType.ConnectionStateChanged -> handleConnectionStateChanged(
                gatt!!,
                callback.status,
                callback.newState
            )

            is GattCallbackType.ServicesDiscovered -> handleServicesDiscovered(
                gatt!!,
                callback.status
            )

            is GattCallbackType.CharacteristicChanged -> notifyManager.handleCharacteristicChanged(
                gatt!!,
                callback.characteristic,
                callback.value
            )

            is GattCallbackType.CharacteristicRead -> readManager.handleCharacteristicRead(
                gatt!!,
                callback.characteristic,
                callback.value,
                callback.status
            )

            is GattCallbackType.CharacteristicWrite -> writeManager.handleCharacteristicWrite(
                gatt!!,
                callback.characteristic,
                callback.status
            )

            is GattCallbackType.DescriptorWrite -> handleDescriptorWrite(
                gatt!!,
                callback.descriptor,
                callback.status
            )

            is GattCallbackType.ServiceChanged -> handleServiceChanged(gatt!!)

            is GattCallbackType.UpdatePhy -> {
                log(TAG, "PHY update - TX: ${callback.txPhy}, RX: ${callback.rxPhy}, Status: ${callback.status}")
                gatt?.device?.address?.let {
                    deviceManager.updateDevice(it) { device ->
                        device.withGatt(gatt)
                    }
                }
            }

            is GattCallbackType.MtuChanged -> {
                log(TAG, "MTU changed - MTU: ${callback.mtu}")
                gatt?.device?.address?.let {
                    deviceManager.updateDevice(it) { device ->
                        device.withGatt(gatt)
                    }
                }
            }

            is GattCallbackType.ReadRemoteRssi -> {
                log(TAG, "Remote RSSI read - RSSI: ${callback.rssi}, Status: ${callback.status}")
                gatt?.device?.address?.let {
                    deviceManager.updateDevice(it) { device ->
                        device.withGatt(gatt)
                    }
                    operationQueue.operationComplete(it, callback.status == BluetoothGatt.GATT_SUCCESS, callback.status)
                }
            }
        }
    }

    private fun handleConnectionStateChanged(gatt: BluetoothGatt, status: Int, newState: Int) {
        log(TAG, "Connection state changed - Status: $status, New State: $newState")
        val deviceAddress = gatt.device?.address
        if (deviceAddress == null) {
            log(TAG, "GATT device address is null")
            disconnectManager.closeGatt(gatt)
            return
        }

        val deviceInfo = deviceManager.getDevice(deviceAddress)
        if (deviceInfo == null) {
            log(TAG, "Device info not found for address: $deviceAddress")
            disconnectManager.closeGatt(gatt)
            return
        }

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> handleDeviceConnected(
                gatt,
                status,
                deviceAddress,
                deviceInfo
            )

            BluetoothProfile.STATE_DISCONNECTED -> handleDeviceDisconnected(
                gatt,
                deviceAddress,
                deviceInfo
            )
        }
    }

    private fun handleDeviceConnected(
        gatt: BluetoothGatt,
        status: Int,
        deviceAddress: String,
        device: BleDevice
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            timeoutManager.cancelTimeout(deviceAddress, TimeoutType.EARLY_CONNECTION)
            serviceDiscoveryManager.startServiceDiscovery(gatt)
        } else {
            disconnectManager.handleConnectionFailure(gatt, deviceAddress, device, status)
        }
    }

    private fun handleDeviceDisconnected(
        gatt: BluetoothGatt?,
        deviceAddress: String,
        device: BleDevice
    ) {
        disconnectManager.handleDeviceDisconnected(gatt, device, onDisconnected = {
            subscribeManager.cleanForDevice(deviceAddress)
            operationQueue.clearQueue(device.getMacAddress())
        })
    }

    private fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        serviceDiscoveryManager.handleServicesDiscovered(gatt, status, onDiscovered = { device ->
            // Use REQUESTED device types, not all device types
            @SuppressLint("MissingPermission")
            subscribeManager.subscribeToCharacteristics(device)
        })
    }

    private fun handleDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        log(
            TAG,
            "Descriptor write - Characteristic: ${descriptor.characteristic.uuid}, Descriptor: ${descriptor.uuid}, Status: $status"
        )
        operationQueue.operationComplete(
            gatt.device.address,
            status == BluetoothGatt.GATT_SUCCESS,
            status
        )

        val device = deviceManager.updateDevice(gatt.device.address) { device ->
            device.withGatt(gatt)
        }
        if (device != null) {
            log(TAG, "Delegating to characteristicSubscriber.handleDescriptorWrite()")
            val deviceAddress = device.getMacAddress()
            val characteristicUuid = descriptor.characteristic.uuid.toString()

            // Check pending operations
            val isPendingSubscription = subscribeManager.isDescriptorWriteFromSubscription(
                deviceAddress,
                characteristicUuid
            )
            val isPendingUnsubscription = unSubscribeManager.isDescriptorWriteFromUnsubscription(
                deviceAddress,
                characteristicUuid
            )


            if (isPendingSubscription) {
                subscribeManager.handleDescriptorWrite(device, descriptor, status)
            } else if (isPendingUnsubscription) {
                unSubscribeManager.handleDescriptorWrite(device, descriptor, status)
            } else {
                log(TAG, "No pending operation found")
            }
        } else {
            emitError(
                ConnectionError(
                    device = null,
                    errorCode = ConnectionError.ERROR_DEVICE_NOT_FOUND,
                    operation = Operation.DESCRIPTOR_WRITE,
                    message = "Device not found for descriptor write event"
                )
            )
        }
    }

    private fun handleServiceChanged(gatt: BluetoothGatt) {
        val deviceAddress = gatt.device.address
        log(TAG, "Service changed event received for device: $deviceAddress")

        // Re-discover services as GATT database is out of sync
        // TODO startServiceDiscovery(gatt)
    }

    private fun emitCharacteristicEvent(event: CharacteristicEvent) {
        //log(TAG, "Attempting to emit event: ${event.javaClass.simpleName}")
        //log(TAG, "Current thread: ${Thread.currentThread().name}")
        //log(TAG, "Flow has collectors: ${_characteristicEvents.subscriptionCount.value}")
        //log(TAG, "Manager scope is active: ${managerScope.isActive}")
        //log(TAG, "Manager scope is cancelled: ${managerScope.coroutineContext[Job]?.isCancelled}")
        
        managerScope.launch {
            try {
                _characteristicEvents.emit(event)
                log(TAG, "Event emitted successfully: ${event.javaClass.simpleName}")
            } catch (e: Exception) {
                log(TAG, "Failed to emit event: ${e.message}")
            }
        }
    }
}
