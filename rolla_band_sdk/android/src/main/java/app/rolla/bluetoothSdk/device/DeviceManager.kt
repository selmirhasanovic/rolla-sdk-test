
package app.rolla.bluetoothSdk.device

import android.Manifest
import android.bluetooth.BluetoothGatt
import androidx.annotation.RequiresPermission
import app.rolla.bluetoothSdk.MethodResult
import app.rolla.bluetoothSdk.connect.ConnectionError
import app.rolla.bluetoothSdk.services.BleService
import app.rolla.bluetoothSdk.services.CharacteristicImpls
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext


class DeviceManager constructor(
    coroutineContext: CoroutineContext
) {
    companion object {
        private const val TAG = "DeviceManager"
        private const val DEVICE_TIMEOUT_MS = 20000L  // 10 seconds
        private const val CONNECTED_DEVICE_TIMEOUT_MS = 30000L  // 10 seconds
        private const val MONITORING_INTERVAL_MS = 1000L  // 1 second

        private const val MAX_CONCURRENT_CONNECTIONS = 6
    }

    // Internal state
    private val deviceScope = CoroutineScope(coroutineContext)
    private var monitoringJob: Job? = null

    private val devices = ConcurrentHashMap<String, BleDevice>()
    private var allowedDeviceTypes = setOf<DeviceType>()

    // Public state
    private val _devicesFlow = MutableStateFlow<List<BleDevice>>(emptyList())
    val devicesFlow: StateFlow<List<BleDevice>> = _devicesFlow.asStateFlow()
    private val _connectionState = MutableStateFlow<Map<String, DeviceConnectionState>>(emptyMap())
    val connectionStateFlow: StateFlow<Map<String, DeviceConnectionState>> = _connectionState.asStateFlow()

    // Add new flow for connection state changes
    private val _connectionStateChanges = MutableSharedFlow<Pair<String, DeviceConnectionState>>(replay = 1)
    val connectionStateChanges: SharedFlow<Pair<String, DeviceConnectionState>> = _connectionStateChanges.asSharedFlow()

    // Event flow for device events
    private val _deviceEventFlow = MutableSharedFlow<DeviceEvent>(replay = 1)
    val deviceEventFlow: SharedFlow<DeviceEvent> = _deviceEventFlow.asSharedFlow()


    init {
        startDeviceMonitoring()
    }

    /**
     * Sets the allowed device types for filtering.
     */
    fun setAllowedDeviceTypes(types: Set<DeviceType>) {
        allowedDeviceTypes = types
    }

    fun getAllowedDeviceTypes(): Set<DeviceType> = allowedDeviceTypes

    /**
     * Process scanned device (from scan results)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun processScannedDevice(bleDevice: BleDevice) {
        val serviceUuids = bleDevice.serviceUuids.map { it.uuid.toString().uppercase() }
        if (serviceUuids.find { uuid -> uuid == BleService.DEVICE_FIRMWARE_UPDATE.uuid } != null) {
            CharacteristicImpls.rollaBand.startInternalUpdate(bleDevice)
            return
        }
        val detectedTypes = determineDeviceTypes(bleDevice)

        if (allowedDeviceTypes.isNotEmpty() &&
            detectedTypes.none { it in allowedDeviceTypes }) {
            log(TAG, "Device doesn't match allowed types: ${bleDevice.btDevice.address}")
            return
        }
        val address = bleDevice.getMacAddress()
        val existingDevice = devices[address]
        if (existingDevice == null) {
            devices[address] = bleDevice
        }
        updateDevice(address) { device ->
            device.withDeviceTypes(detectedTypes.toSet(), false)
                .withRssi(bleDevice.rssi)
                .withServiceUuids(bleDevice.serviceUuids)
        }
        refreshDevicesList()
    }

    /**
     * Add device for connection operations
     */
    @Synchronized
    fun addDevice(bleDevice: BleDevice, deviceTypes: Set<DeviceType>){
        val address = bleDevice.getMacAddress()
        val existingDevice = devices[address]
        if (existingDevice == null) {
            devices[address] = bleDevice
        }
        updateDevice(address) { device ->
            device.withConnectionState(DeviceConnectionState.CONNECTING)
                  .withGatt(bleDevice.gatt)
                  .withDeviceTypes(deviceTypes)
        }

        refreshDevicesList()
        log(TAG, "Added device: $address")
    }

    /**
     * Remove device completely
     */
    @Synchronized
    fun removeDevice(deviceAddress: String): BleDevice? {
        updateDevice(deviceAddress) { device ->
            device.withConnectionState(DeviceConnectionState.DISCONNECTED)
                .withGatt(null)
                .withServicesDiscovered(false)
                .withNewRequestedTypes(emptySet())
                .withNewDeviceTypes(emptySet())
        }
        val removed = devices.remove(deviceAddress)
        if (removed != null) {
            refreshDevicesList()
            log(TAG, "Removed device: $deviceAddress")
        }
        return removed
    }

    /**
     * Get device by address
     */
    @Synchronized
    fun getDevice(address: String): BleDevice? = devices[address]

    /**
     * Update device with custom logic
     */
    @Synchronized
    fun updateDevice(address: String, updater: (BleDevice) -> BleDevice): BleDevice? {
        val updated = devices[address]?.let { device ->
            val oldConnectionState = device.connectionState
            val updatedDevice = updater(device).copy(timestamp = System.currentTimeMillis())
            devices[address] = updatedDevice

            // Emit connection state change if it actually changed
            if (oldConnectionState != updatedDevice.connectionState) {
                deviceScope.launch {
                    _connectionStateChanges.emit(address to updatedDevice.connectionState)
                }
            }
            
            refreshDevicesList()
            devices[address]
        }
        return updated
    }

    fun canCreateNewConnection(deviceAddress: String): Boolean {
        val device = devices[deviceAddress]
        val isNewConnection = device == null || device.isDisconnected()

        return !isNewConnection || getActiveConnectionCount() < MAX_CONCURRENT_CONNECTIONS
    }

    /**
     * Get all devices
     */
    fun getAllDevices(): List<BleDevice> = devices.values.toList().sortedWith(
        compareBy<BleDevice> {
            when (it.connectionState) {
                DeviceConnectionState.CONNECTED -> 0
                DeviceConnectionState.CONNECTING -> 1
                DeviceConnectionState.DISCONNECTING -> 2
                DeviceConnectionState.DISCONNECTED -> 3
            }
        }.thenByDescending { it.rssi }
    )

    fun getActiveConnectionCount() : Int {
        return devices.values.toList().count { it.isConnected() || it.isConnecting() }
    }

    /**
     * Get connected devices only
     */
    fun getConnectedDevices(): List<BleDevice> = 
        devices.values.filter { it.isConnected() }

    /**
     * Get GATT for device
     */
    fun getGattForDevice(bleDevice: BleDevice): BluetoothGatt? =
        devices[bleDevice.getMacAddress()]?.takeIf { it.isConnected() }?.gatt

    /**
     * Get connection states map
     */
    fun getConnectionStates(): Map<String, DeviceConnectionState> =
        devices.mapValues { it.value.connectionState }

    /**
     * Check device type conflicts for connection
     */
    fun checkDeviceTypeConflicts(deviceTypes: Set<DeviceType>, deviceAddress: String): Set<DeviceType> {
        return deviceTypes.filter { type ->
            devices.values.any { device ->
                device.getMacAddress() != deviceAddress &&
                        device.hasDeviceType(type) &&
                        (device.isConnected() || device.isConnecting())
            }
        }.toSet()
    }

    /**
     * Get device type subscriptions
     */
    fun getDeviceTypeSubscriptions(): Map<DeviceType, String> {
        val subscriptions = mutableMapOf<DeviceType, String>()
        devices.forEach { (address, device) ->
            if (device.isConnected() || device.isConnecting()) {
                device.deviceTypes.forEach { deviceType ->
                    subscriptions[deviceType] = address
                }
            }
        }
        return subscriptions
    }

    fun updateNewDeviceTypesForConnectedDevice(newDeviceTypes: Set<DeviceType>, address: String,
                                               onSuccess: (BleDevice) -> Unit): MethodResult {
        val device = devices[address]
        if (device == null) {
            return MethodResult(false, ConnectionError.getErrorDescription(ConnectionError.ERROR_DEVICE_NOT_FOUND))
        }
        val subscribedTypes = device.getSubscribedTypes()
        val subscriptions = getDeviceTypeSubscriptions()
        val typesToUpdate = newDeviceTypes.filter { type ->
            // Type is not already assigned to this device
            !subscribedTypes.contains(type) &&
                    // Type is not subscribed by another device
                    (subscriptions[type] == null || subscriptions[type] == address)
        }.toSet()
        if (typesToUpdate.isEmpty()) {
            log(TAG, "No new device types to add for $address")
            return MethodResult(
                false,
                ConnectionError.getErrorDescription(ConnectionError.ERROR_DEVICE_TYPE_ALREADY_SUBSCRIBED)
            )
        } else {
            log(TAG, "Adding new device types to connected device: $address, types: ${typesToUpdate.joinToString { it.typeName }}")
            val bleDevice = updateDevice(address) { device ->
                device.withDeviceTypes(typesToUpdate)
                    .withUpdatedDeviceTypeStates(DeviceTypeState.SUBSCRIBING)
            }
            onSuccess(bleDevice!!)
            return MethodResult(true)
        }
    }

    fun updateNewDeviceTypesForConnectingDevice(newDeviceTypes: Set<DeviceType>, address: String): MethodResult {
        val device = devices[address]
        if (device == null) {
            return MethodResult(false, ConnectionError.getErrorDescription(ConnectionError.ERROR_DEVICE_NOT_FOUND))
        }
        val subscribedTypes = device.deviceTypes.toSet()
        val subscriptions = getDeviceTypeSubscriptions()
        val typesToUpdate = newDeviceTypes.filter { type ->
            // Type is not already assigned to this device
            !subscribedTypes.contains(type) &&
                    // Type is not subscribed by another device
                    (subscriptions[type] == null || subscriptions[type] == address)
        }.toSet()
        if (typesToUpdate.isNotEmpty()) {
            log(TAG, "Adding new device types to connecting device: $address, types: $typesToUpdate")
            updateDevice(address) { device ->
                device.withDeviceTypes(typesToUpdate)
            }
            return MethodResult(true)
        } else {
            return MethodResult(false, ConnectionError.getErrorDescription(ConnectionError.ERROR_ALREADY_CONNECTING))
        }
    }

    /**
     * Determines device types based on advertised services.
     */
    private fun determineDeviceTypes(bleDevice: BleDevice): List<DeviceType> {
        val serviceUuids = bleDevice.serviceUuids.map { it.uuid.toString().uppercase() }

        // Create a list to hold detected device types
        val detectedTypes = mutableListOf<DeviceType>()

        // Add StandardDeviceType enum values that match
        for (deviceType in DeviceType.entries) {
            if (serviceUuids.containsAll(deviceType.detectingTypeServices)) {
                detectedTypes.add(deviceType)
            }
        }

        return detectedTypes
    }


    /**
     * Starts monitoring devices for timeout.
     */
    fun startDeviceMonitoring() {
        stopDeviceMonitoring()
        log(TAG, "Starting device monitoring - clear devices")
        clearDevices()
        monitoringJob = deviceScope.launch {
            while (true) {
                pruneOldDevices()
                delay(MONITORING_INTERVAL_MS)
            }
        }
        log(TAG, "Device monitoring started")
    }

    /**
     * Stops monitoring devices.
     */
    private fun stopDeviceMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        log(TAG, "Device monitoring stopped")
    }

    /**
     * Removes devices that haven't been seen recently and triggers RSSI reads for connected devices.
     */
    private fun pruneOldDevices() {
        val currentTime = System.currentTimeMillis()
        var changed = false

        devices.entries.removeIf { (_, device) ->
            val timeSinceLastUpdate = currentTime - device.timestamp
            
            // Debug logging
            if (device.getMacAddress() == "EF:7B:AF:22:6F:F3") {
                log(TAG, "Debug - Device ${device.getMacAddress()}: connectionState=${device.connectionState}, isConnected=${device.isConnected()}, timeSinceLastUpdate=${timeSinceLastUpdate}ms")
            }
            
            if (device.isConnected()) {
                // For connected devices, trigger RSSI reads at intervals
                when {
                    timeSinceLastUpdate >= 10000L && timeSinceLastUpdate < 11000L -> {
                        // Around 10 seconds - first RSSI read attempt
                        emitRssiReadRequest(device.getMacAddress())
                    }
                    timeSinceLastUpdate >= 20000L && timeSinceLastUpdate < 21000L -> {
                        // Around 20 seconds - second RSSI read attempt
                        emitRssiReadRequest(device.getMacAddress())
                    }
                    timeSinceLastUpdate > CONNECTED_DEVICE_TIMEOUT_MS -> {
                        // Device is unresponsive, mark for removal
                        log(TAG, "Connected device became unresponsive: ${device.btDevice.address}")
                        emitDeviceUnresponsive(device.getMacAddress())
                        changed = true
                        return@removeIf true
                    }
                }
                false // Don't remove connected devices unless unresponsive
            } else {
                // For disconnected devices, use normal timeout
                val shouldRemove = timeSinceLastUpdate > DEVICE_TIMEOUT_MS
                if (shouldRemove) {
                    try {
                        log(TAG, "Scanned device timed out: ${device.btDevice.address} (state: ${device.connectionState})")
                    } catch (e: SecurityException) {
                        log(TAG, "Security exception when logging device timeout")
                    }
                    changed = true
                }
                shouldRemove
            }
        }

        if (changed) {
            refreshDevicesList()
        }
    }
    
    /**
     * Handle unresponsive connected device
     */
    fun handleUnresponsiveDevice(device: BleDevice) {
        // Update device state to disconnected
        updateDevice(device.getMacAddress()) { device ->
            device.withConnectionState(DeviceConnectionState.DISCONNECTED)
                .withGatt(null)
                .withServicesDiscovered(false)
                .withUpdatedDeviceTypeStates(DeviceTypeState.UNSUBSCRIBED)
                .withNewDeviceTypes(emptySet())
                .withNewRequestedTypes(emptySet())
                .withDeviceTypes(emptySet())
        }
        
        log(TAG, "Marked unresponsive device as disconnected: ${device.getMacAddress()}")
    }

    private fun refreshConnectionStateFlow() {
        _connectionState.value = getConnectionStates()
    }

    /**
     * Updates the public devices flow with current devices.
     */
    private fun refreshDevicesList() {
        // Sort the devices by signal strength
        val deviceList = devices.values.toMutableList()
        deviceList.sortByDescending { it.rssi }

        // Use value = instead of update { } to ensure complete replacement
        _devicesFlow.value = deviceList

        // Also log the update for debugging
        //log(TAG, "Device list updated : ${devicesFlow.value.toTypedArray().contentToString()}}")
        refreshConnectionStateFlow()
    }

    /**
     * Gets all discovered devices sorted by signal strength.
     */
    fun getAllDevicesSorted(): List<BleDevice> {
        return devices.values.toList().sortedByDescending { it.rssi }
    }

    /**
     * Gets devices of a specific type sorted by signal strength.
     */
    fun getDevicesByType(deviceType: DeviceType): List<BleDevice> {
        return devices.values
            .filter { it.isDeviceType(deviceType) }
            .sortedByDescending { it.rssi }
    }

    /**
     * Clears all discovered devices.
     */
    fun clearDevices() {
        devices.clear()
        _devicesFlow.update { emptyList() }
    }

    /**
     * Check if device is connected (alias for isDeviceConnected)
     */
    fun isConnected(macAddress: String): Boolean =
        devices[macAddress]?.isConnected() == true

    fun getConnectionState(macAddress: String): DeviceConnectionState? {
        val device = devices[macAddress]
        return device?.connectionState
    }

    /**
     * Get connected device for specific type (alias for getConnectedDeviceForType)
     */
    fun getDeviceForType(deviceType: DeviceType): BleDevice? =
        devices.values.find { it.isConnected() && it.hasDeviceType(deviceType) }

    /**
     * Clear all devices
     */
    fun clear() {
        devices.clear()
        deviceScope.cancel()
        refreshDevicesList()
        log(TAG, "Cleared all devices")
    }

    /**
     * Cleans up resources when the manager is no longer needed.
     */
    fun destroy() {
        stopDeviceMonitoring()
        clearDevices()
    }

    private fun emitRssiReadRequest(deviceAddress: String) {
        deviceScope.launch {
            _deviceEventFlow.emit(DeviceEvent.RssiReadRequested(deviceAddress))
        }
    }

    private fun emitDeviceUnresponsive(deviceAddress: String) {
        deviceScope.launch {
            _deviceEventFlow.emit(DeviceEvent.DeviceUnresponsive(deviceAddress))
        }
    }
}
