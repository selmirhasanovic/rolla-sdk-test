package app.rolla.bluetoothSdk.scan

import android.Manifest
import androidx.annotation.RequiresPermission
import app.rolla.bluetoothSdk.MethodResult
import app.rolla.bluetoothSdk.device.DeviceConnectionState
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.device.DeviceType
import app.rolla.bluetoothSdk.exceptions.InvalidParameterException
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.di.BleScopeContext
import app.rolla.bluetoothSdk.scan.manager.ScanError
import app.rolla.bluetoothSdk.scan.manager.ScanManager
import app.rolla.bluetoothSdk.scan.manager.ScanManager.ScanState
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * High-level BLE scanning service that integrates ScanManager for device discovery
 * and DeviceManager for device tracking. Provides a unified interface for discovering
 * and managing Bluetooth devices.
 */
@Singleton
class BleScanner @Inject constructor(
    private val scanManager: ScanManager?,
    private val deviceManager: DeviceManager, // Use DeviceManager directly
    @BleScopeContext bleScopeContext: CoroutineContext
) {
    companion object {
        private const val TAG = "BleScanner"
    }

    // Event flows
    private val _scanErrorFlow = MutableSharedFlow<ScanError>(replay = 1)
    val scanErrorFlow: SharedFlow<ScanError> = _scanErrorFlow.asSharedFlow()

    // Internal state and dependencies
    private val scanScope = CoroutineScope(bleScopeContext)

    // Forward scan state from manager with null safety
    val scanStateFlow: StateFlow<ScanState> get() = scanManager?.scanState 
        ?: MutableStateFlow(ScanState.Idle).asStateFlow()

    // Forward discovered devices from device manager
    val discoveredDevicesFlow: StateFlow<List<BleDevice>> get() = deviceManager.devicesFlow
    val connectionStateFlow: StateFlow<Map<String, DeviceConnectionState>> get() = deviceManager.connectionStateFlow
    // Forward connection state changes from device manager
    val connectionStateChanges: SharedFlow<Pair<String, DeviceConnectionState>> get() = 
        deviceManager.connectionStateChanges

    init {
        if (scanManager != null) {
            registerListeners()
        }
    }

    /**
     * Registers flow collectors for device and scan events.
     */
    private fun registerListeners() {
        scanManager?.let { manager ->
            // Listen for scan results and forward to device manager
            manager.scanResults
                .onEach { device ->
                    // Wrap operations requiring permission in try-catch
                    try {
                        deviceManager.processScannedDevice(device)
                    } catch (e: SecurityException) {
                        _scanErrorFlow.emit(
                            ScanError(
                                errorCode = ScanError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                                message = "BLUETOOTH_CONNECT permission missing",
                                exception = e
                            )
                        )
                    }
                }.launchIn(scanScope)

            // Forward scan errors
            manager.scanErrors
                .onEach { error ->
                    _scanErrorFlow.emit(error)
                }.launchIn(scanScope)

            // Handle scan state changes
            manager.scanState
                .onEach { state ->
                    try {
                        handleScanStateChange(state)
                    } catch (e: SecurityException) {
                        _scanErrorFlow.emit(
                            ScanError(
                                errorCode = ScanError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                                message = "BLUETOOTH_CONNECT permission missing",
                                exception = e
                            )
                        )
                    }
                }.launchIn(scanScope)
        }
    }

    /**
     * Handles scan state changes to manage device monitoring.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleScanStateChange(state: ScanState) {
        when (state) {
            is ScanState.Scanning -> {
                log(TAG, "Scan started")
            }
            is ScanState.Completed -> {
                log(TAG, "Scan completed")
                // Keep monitoring active to allow devices to timeout naturally
            }
            is ScanState.Idle -> {
                log(TAG, "Scan stopped")
                // Keep monitoring active to allow devices to timeout naturally
            }
            is ScanState.Stopped -> {
                log(TAG, "Scan stopped")
            }
            is ScanState.Failed -> {
                log(TAG, "Scan failed with error code: ${state.errorCode}")
                // Keep monitoring active to allow devices to timeout naturally
            }
        }
    }

    /**
     * Starts scanning for devices of the specified types.
     *
     * @param deviceTypes Set of device types to scan for
     * @return Result of the scan start operation
     */
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    suspend fun startScan(deviceTypes: Set<DeviceType>): MethodResult {
        log(TAG, "Starting scan for device types: ${deviceTypes.joinToString { it.toString() }}")

        if (scanManager == null) {
            log(TAG, "Scanner not available")
            return MethodResult(false, "Scanner not available")
        }

        // Set device types filter in device manager
        deviceManager.setAllowedDeviceTypes(deviceTypes)

        // Get UUIDs to scan for from device types
        val scanUuids = deviceTypes.flatMap { it.scanningServices }.distinct()

        return scanManager.startScan(scanUuids)
    }

    /**
     * Stops the current scan operation.
     *
     * @return Result of the scan stop operation
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    suspend fun stopScan(): MethodResult {
        log(TAG, "Stopping scan")
        return scanManager?.stopScan() ?: MethodResult(false, "Scanner not available")
    }

    /**
     * Get all scanned devices from DeviceManager
     */
    fun getAllScannedDevices(): List<BleDevice> = deviceManager.getAllDevices()

    /**
     * Gets devices of a specific type sorted by signal strength.
     */
    fun getDevicesByType(deviceType: DeviceType): List<BleDevice> = 
        deviceManager.getDevicesByType(deviceType)

    /**
     * Clear devices
     */
    fun clearDevices() {
        deviceManager.clearDevices()
    }

    /**
     * Sets the duration for scan operations.
     *
     * @param scanDurationMs The scan duration in milliseconds
     * @throws InvalidParameterException if duration is invalid
     */
    @Throws(InvalidParameterException::class)
    fun setScanDuration(scanDurationMs: Long) {
        scanManager?.setScanDuration(scanDurationMs) 
            ?: throw InvalidParameterException("Scanner not available")
    }

    /**
     * Cleans up resources when the scanner is no longer needed.
     * Should be called when the component using this scanner is destroyed.
     */
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    fun destroy() {
        log(TAG, "Destroying BleScanner")
        scanScope.launch {
            try {
                scanManager?.stopScan()
            } catch (e: Exception) {
                log(TAG, "Error stopping scan during destruction: ${e.message}")
            }
        }
        scanManager?.destroy()
        deviceManager.destroy()
    }
}