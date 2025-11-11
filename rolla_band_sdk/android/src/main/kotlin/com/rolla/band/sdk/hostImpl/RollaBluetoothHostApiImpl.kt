package com.rolla.band.sdk.hostImpl

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import app.rolla.bluetoothSdk.BleManager
import app.rolla.bluetoothSdk.device.DeviceConnectionState
import app.rolla.bluetoothSdk.utils.extensions.log
import com.rolla.band.sdk.generated.BluetoothState
import com.rolla.band.sdk.generated.ConnectionState
import com.rolla.band.sdk.generated.DeviceType
import com.rolla.band.sdk.generated.RollaBluetoothHostApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
class RollaBluetoothHostApiImpl(
    private val bleManager: BleManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : RollaBluetoothHostApi {

    override fun scanForDevices(
        deviceTypes: List<DeviceType>,
        scanDuration: Long,
        callback: (Result<Unit>) -> Unit
    ) {
        scope.launch {
            try {
                log("RollaBluetoothHostApiImpl", "Scanning for device types: ${deviceTypes.joinToString()}")
                val sdkDeviceTypes = deviceTypes.map { convertToSdkDeviceType(it) }.toSet()
                bleManager.setScanDuration(scanDuration)
                val result = bleManager.startScan(deviceTypes = sdkDeviceTypes)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        callback(Result.success(Unit))
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    override fun stopScanning(callback: (Result<Unit>) -> Unit) {
        scope.launch {
            try {
                log("RollaBluetoothHostApiImpl", "Stopping scan")
                val result = bleManager.stopScan()
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        callback(Result.success(Unit))
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    override fun connectToDevice(uuid: String, callback: (Result<Boolean>) -> Unit) {
        scope.launch {
            try {
                log("RollaBluetoothHostApiImpl", "Connecting to device: $uuid")
                val result = bleManager.connectToBleDevice(uuid)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        callback(Result.success(true))
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    override fun disconnectFromDevice(uuid: String, callback: (Result<Boolean>) -> Unit) {
        scope.launch {
            try {
                log("RollaBluetoothHostApiImpl", "Disconnecting from device: $uuid")
                bleManager.disconnectFromDevice(uuid)
                withContext(Dispatchers.Main) {
                    callback(Result.success(true))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    override fun disconnectAndRemoveBond(uuid: String, callback: (Result<Boolean>) -> Unit) {
        scope.launch {
            try {
                log("RollaBluetoothHostApiImpl", "Disconnecting and removing bond for: $uuid")
                bleManager.disconnectFromDevice(uuid)
                val result = bleManager.removeBond(uuid)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        callback(Result.success(true))
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    override fun checkConnectionState(
        uuid: String,
        callback: (Result<ConnectionState>) -> Unit
    ) {
        log("RollaBluetoothHostApiImpl", "Checking connection state for $uuid")
        bleManager.getConnectionState(uuid, onSuccess = { state ->
            callback(Result.success(convertToPigeonConnectionState(state)))
        }, onError = { error ->
            callback(Result.failure(error))
        })
    }

    override fun checkBluetoothState(callback: (Result<BluetoothState>) -> Unit) {
        scope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val state = when (adapter?.state) {
                    BluetoothAdapter.STATE_ON -> BluetoothState.POWERED_ON
                    BluetoothAdapter.STATE_OFF -> BluetoothState.POWERED_OFF
                    BluetoothAdapter.STATE_TURNING_ON -> BluetoothState.TURNING_ON
                    BluetoothAdapter.STATE_TURNING_OFF -> BluetoothState.TURNING_OFF
                    else -> BluetoothState.UNKNOWN
                }
                withContext(Dispatchers.Main) {
                    callback(Result.success(state))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    private fun convertToPigeonConnectionState(state: DeviceConnectionState): ConnectionState {
        return when(state) {
            DeviceConnectionState.CONNECTED -> ConnectionState.CONNECTED
            DeviceConnectionState.CONNECTING -> ConnectionState.CONNECTING
            DeviceConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
            DeviceConnectionState.DISCONNECTING -> ConnectionState.DISCONNECTED
        }
    }

    private fun convertToSdkDeviceType(pigeonType: DeviceType): app.rolla.bluetoothSdk.device.DeviceType {
        return when (pigeonType) {
            DeviceType.ROLLA_BAND -> app.rolla.bluetoothSdk.device.DeviceType.ROLLA_BAND
            DeviceType.OTHER -> app.rolla.bluetoothSdk.device.DeviceType.HEART_RATE
        }
    }

    fun cleanup() {
        scope.cancel()
    }
}

