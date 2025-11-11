package app.rolla.bluetoothSdk.scan.manager

import android.Manifest
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import app.rolla.bluetoothSdk.MethodResult
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.exceptions.InvalidParameterException
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Manager class for Bluetooth LE device scanning operations.
 */
class ScanManager constructor(
    private val bluetoothScanner: BluetoothLeScanner?,
    private val ioDispatcher: CoroutineDispatcher,
    private val coroutineContext: CoroutineContext
) {
    companion object {
        private const val TAG = "ScanManager"

        // Configuration constants
        const val MAX_SCAN_ATTEMPTS = 5
        const val MAX_SCAN_ATTEMPTS_TIME_MS = 30_000L
        const val DEFAULT_SCAN_DURATION_MS = 30_000L
        const val MAX_ALLOWED_SCAN_DURATION_MS = 5 * 60_000L // 5 minutes

        // Error messages
        private const val ERROR_ALREADY_SCANNING = "Already scanning"
        private const val ERROR_MAX_SCAN_ATTEMPTS = "Maximum scan attempts reached"
        private const val ERROR_NOT_SCANNING = "Scanning already stopped"
    }

    // Event flows
    private val _scanResults = MutableSharedFlow<BleDevice>(replay = 1)
    val scanResults: SharedFlow<BleDevice> = _scanResults

    private val _scanErrors = MutableSharedFlow<ScanError>(replay = 1)
    val scanErrors: SharedFlow<ScanError> = _scanErrors

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Internal state
    private val scanScope = CoroutineScope(coroutineContext)
    private var scanJob: Job? = null
    private var scanDuration = DEFAULT_SCAN_DURATION_MS
    private val scanAttemptTimestamps = Collections.synchronizedList(mutableListOf<Long>())
    private val isScanningFlag = AtomicBoolean(false)
    private val scanMutex = Mutex()

    // Scanning state
    sealed class ScanState {
        object Idle : ScanState()
        object Scanning : ScanState()

        object Stopped: ScanState()
        object Completed : ScanState()
        data class Failed(val errorCode: Int, val message: String?) : ScanState()
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { processResult(it) }
        }

        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { result ->
                processResult(result)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun onScanFailed(errorCode: Int) {
            val message = "Scan failed with error code: $errorCode"
            log(TAG, message)

            stopScanOperation()

            scanScope.launch {
                val errorMessage = ScanError.getErrorDescription(errorCode)
                _scanErrors.emit(ScanError(errorCode, errorMessage))
                _scanState.value = ScanState.Stopped
                _scanState.value = ScanState.Failed(errorCode, errorMessage)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun processResult(result: ScanResult) {
            if (result.scanRecord?.serviceUuids?.isNotEmpty() == true) {
                scanScope.launch {
                    _scanResults.emit(BleDevice(result))
                }
            } else {
                val deviceName = result.device.name ?: "Unknown"
                val deviceAddress = result.device.address
                log(TAG, "Device $deviceName ($deviceAddress) doesn't have exposed service UUIDs")
            }
        }
    }

    /**
     * Starts scanning for BLE devices that advertise the specified service UUIDs.
     */
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    suspend fun startScan(uuids: List<String> = emptyList()): MethodResult = scanMutex.withLock {
        log(TAG, "Starting scan with ${uuids.size} UUIDs")

        if (bluetoothScanner == null) {
            return MethodResult(false, "Bluetooth scanner not available")
        }

        if (isScanningFlag.get()) {
            return MethodResult(false, ERROR_ALREADY_SCANNING)
        }

        try {
            cleanupOutdatedScanAttempts()

            if (scanAttemptTimestamps.size >= MAX_SCAN_ATTEMPTS) {
                return MethodResult(false, ERROR_MAX_SCAN_ATTEMPTS)
            }

            // Create scan filters for the provided UUIDs
            val scanFilters = createScanFilters(uuids)

            withContext(ioDispatcher) {
                _scanState.value = ScanState.Scanning
            }

            isScanningFlag.set(true)
            startScanOperation(scanFilters)

            scanAttemptTimestamps.add(System.currentTimeMillis())

            return MethodResult(true)
        } catch (e: Exception) {
            isScanningFlag.set(false)
            log(TAG, "Error starting scan: ${e.message}")

            scanScope.launch {
                _scanErrors.emit(ScanError(
                    ScanError.ERROR_INTERNAL_ERROR,
                    "Failed to start scan: ${e.message}",
                    e
                ))
                _scanState.value = ScanState.Failed(
                    ScanError.ERROR_INTERNAL_ERROR,
                    e.message
                )
            }

            return MethodResult(false, e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Sets the duration for each scan operation.
     */
    @Throws(InvalidParameterException::class)
    fun setScanDuration(durationMs: Long) {
        if (durationMs < 0 || durationMs > MAX_ALLOWED_SCAN_DURATION_MS) {
            throw InvalidParameterException(
                "Scan duration must be between 0 and ${MAX_ALLOWED_SCAN_DURATION_MS}ms (0-5 minutes)"
            )
        }

        scanDuration = durationMs
        log(TAG, "Scan duration set to $durationMs ms")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScanOperation() {
        if (isScanningFlag.get()) {
            try {
                bluetoothScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                log(TAG, "Error stopping scan: ${e.message}")
            } finally {
                scanJob?.cancel()
                scanJob = null
                scanDuration = DEFAULT_SCAN_DURATION_MS
                isScanningFlag.set(false)
            }
        }
    }

    /**
     * Stops an ongoing scan operation.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    suspend fun stopScan(): MethodResult = scanMutex.withLock {
        if (!isScanningFlag.get()) {
            return MethodResult(false, ERROR_NOT_SCANNING)
        }

        log(TAG, "Stopping scan")

        try {
            stopScanOperation()
            _scanState.value = ScanState.Stopped
            _scanState.value = ScanState.Idle
            return MethodResult(true)
        } catch (e: Exception) {
            log(TAG, "Error in stopScan: ${e.message}")
            scanScope.launch {
                _scanErrors.emit(ScanError(
                    ScanError.ERROR_INTERNAL_ERROR,
                    "Failed to stop scan: ${e.message}",
                    e
                ))
            }
            return MethodResult(false, "Error stopping scan: ${e.message}")
        }
    }

    /**
     * Cleans up all resources used by this manager.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun destroy() {
        try {
            if (isScanningFlag.get()) {
                bluetoothScanner?.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            log(TAG, "Error during cleanup: ${e.message}")
        }

        scanJob?.cancel()
        scanJob = null
        scanAttemptTimestamps.clear()
        isScanningFlag.set(false)
        _scanState.value = ScanState.Idle
    }

    private fun cleanupOutdatedScanAttempts() {
        val currentTime = System.currentTimeMillis()
        scanAttemptTimestamps.removeAll { currentTime - it > MAX_SCAN_ATTEMPTS_TIME_MS }
    }

    private fun createScanFilters(uuids: List<String>): List<ScanFilter> {
        if (uuids.isEmpty()) {
            return emptyList()
        }

        return uuids.mapNotNull { uuidString ->
            try {
                val uuid = UUID.fromString(uuidString)
                ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build()
            } catch (e: IllegalArgumentException) {
                log(TAG, "Invalid UUID format: $uuidString")
                null
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScanOperation(scanFilters: List<ScanFilter>) {
        bluetoothScanner?.let { scanner ->
            val settings = ScanSettings.Builder().apply {
                setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setLegacy(false)
                    setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                }
                setReportDelay(0)
            }.build()

            scanner.startScan(scanFilters, settings, scanCallback)
            startScanTimeoutTimer()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScanTimeoutTimer() {
        scanJob?.cancel()

        scanJob = scanScope.launch {
            try {
                delay(scanDuration)
                log(TAG, "Scan timeout reached after $scanDuration ms")

                if (isScanningFlag.get()) {
                    stopScan()
                    _scanState.value = ScanState.Completed
                }
            } catch (e: Exception) {
                log(TAG, "Timer stopped: ${e.message}")
                if (isScanningFlag.get()) {
                    stopScanOperation()
                }
            }
        }
    }

    /**
     * Utility method to emit errors in a consistent way
     */
    private suspend fun emitError(code: Int, message: String, exception: Exception? = null) {
        _scanErrors.emit(ScanError(code, message, exception))
        _scanState.value = ScanState.Failed(code, message)
    }
}