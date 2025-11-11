package app.rolla.bluetoothSdk.connect

import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class TimeoutManager(
    bleScopeContext: CoroutineContext,
    private val deviceManager: DeviceManager
) {

    companion object {
        const val TAG = "Timeout manager"

        private const val CONNECTION_TIMEOUT_MS = 30_000L // 30 seconds
        private const val EARLY_CONNECTION_TIMEOUT_MS = 10_000L // 10 seconds

        private const val DISCONNECTION_TIMEOUT_MS = 15_000L // 15 seconds
    }

    private val timeoutScope = CoroutineScope(bleScopeContext)

    // Timeout job tracking
    private val connectionTimeoutJobs = ConcurrentHashMap<String, Job>()

    private val earlyConnectionTimeoutJobs = ConcurrentHashMap<String, Job>()

    private val disconnectionTimeoutJobs = ConcurrentHashMap<String, Job>()

    /**
     * Start connection timeout monitoring
     */
    fun startConnectionTimeout(deviceAddress: String, onConnectionTimeout: (BleDevice) -> Unit) {
        // Cancel existing timeout if any
        connectionTimeoutJobs[deviceAddress]?.cancel()

        val timeoutJob = timeoutScope.launch {
            try {
                delay(CONNECTION_TIMEOUT_MS)
                val device = deviceManager.getDevice(deviceAddress)
                if (device?.isConnecting() == true) {
                    log(TAG, "Connection timeout for device: $deviceAddress")
                    onConnectionTimeout(device)
                }
            } catch (e: Exception) {
                log(TAG, "Connection timeout job error for $deviceAddress: ${e.message}")
            } finally {
                connectionTimeoutJobs.remove(deviceAddress)
            }
        }

        connectionTimeoutJobs[deviceAddress] = timeoutJob
        log(TAG, "Started connection timeout for device: $deviceAddress")
    }

    /**
     * Start connection timeout monitoring
     */
    fun startEarlyConnectionTimeout(deviceAddress: String, onConnectionTimeout: (BleDevice) -> Unit) {
        // Cancel existing timeout if any
        earlyConnectionTimeoutJobs[deviceAddress]?.cancel()

        val timeoutJob = timeoutScope.launch {
            try {
                delay(EARLY_CONNECTION_TIMEOUT_MS)
                val device = deviceManager.getDevice(deviceAddress)
                if (device?.isConnecting() == true) {
                    log(TAG, "Early connection timeout for device: $deviceAddress")
                    onConnectionTimeout(device)
                }
            } catch (e: Exception) {
                log(TAG, "Early connection timeout job error for $deviceAddress: ${e.message}")
            } finally {
                earlyConnectionTimeoutJobs.remove(deviceAddress)
            }
        }

        earlyConnectionTimeoutJobs[deviceAddress] = timeoutJob
        log(TAG, "Started early connection timeout for device: $deviceAddress")
    }

    /**
     * Start disconnection timeout
     */
    fun startDisconnectionTimeout(deviceAddress: String, onDisconnectionTimeout: (String) -> Unit) {
        disconnectionTimeoutJobs[deviceAddress]?.cancel()

        val timeoutJob = timeoutScope.launch {
            try {
                delay(DISCONNECTION_TIMEOUT_MS)
                val device = deviceManager.getDevice(deviceAddress)
                if (device?.isDisconnecting() == true) {
                    log(
                        TAG,
                        "Disconnection timeout for device: $deviceAddress"
                    )
                    onDisconnectionTimeout(deviceAddress)
                }
            } catch (e: Exception) {
                log(TAG, "Disconnection timeout job error for $deviceAddress: ${e.message}")
            } finally {
                disconnectionTimeoutJobs.remove(deviceAddress)
            }
        }

        disconnectionTimeoutJobs[deviceAddress] = timeoutJob
        log(TAG, "Started disconnection timeout for device: $deviceAddress")
    }

    fun cancelTimeout(deviceAddress: String, type: TimeoutType) {
        when (type) {
            TimeoutType.CONNECTION -> {
                connectionTimeoutJobs[deviceAddress]?.cancel()
                connectionTimeoutJobs.remove(deviceAddress)
            }
            TimeoutType.EARLY_CONNECTION -> {
                earlyConnectionTimeoutJobs[deviceAddress]?.cancel()
                earlyConnectionTimeoutJobs.remove(deviceAddress)
            }
            TimeoutType.DISCONNECTION -> {
                disconnectionTimeoutJobs[deviceAddress]?.cancel()
                disconnectionTimeoutJobs.remove(deviceAddress)
            }
        }
        log(TAG, "Cancelled ${type.name} timeout for device: $deviceAddress")
    }

    fun clear() {
        // Cancel all timeout jobs
        connectionTimeoutJobs.values.forEach { it.cancel() }
        connectionTimeoutJobs.clear()

        earlyConnectionTimeoutJobs.values.forEach { it.cancel() }
        earlyConnectionTimeoutJobs.clear()

        disconnectionTimeoutJobs.values.forEach { it.cancel() }
        disconnectionTimeoutJobs.clear()

        timeoutScope.cancel()
    }
}