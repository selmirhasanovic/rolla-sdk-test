package com.rolla.band.sdk.hostImpl

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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

// NOTE: This is a POC implementation. For production, integrate with bluetoothSdk module
// The actual implementation should use app.rolla.bluetoothSdk.BleManager
@SuppressLint("MissingPermission")
class RollaBluetoothHostApiImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : RollaBluetoothHostApi {

    // TODO: Inject BleManager from bluetoothSdk
    // private val bleManager: BleManager

    override fun scanForDevices(
        deviceTypes: List<DeviceType>,
        scanDuration: Long,
        callback: (Result<Unit>) -> Unit
    ) {
        scope.launch {
            try {
                // TODO: Implement using bleManager.startScan()
                withContext(Dispatchers.Main) {
                    callback(Result.success(Unit))
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
                // TODO: Implement using bleManager.stopScan()
                withContext(Dispatchers.Main) {
                    callback(Result.success(Unit))
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
                // TODO: Implement using bleManager.connectToBleDevice(uuid)
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

    override fun disconnectFromDevice(uuid: String, callback: (Result<Boolean>) -> Unit) {
        scope.launch {
            try {
                // TODO: Implement using bleManager.disconnectFromDevice(uuid)
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
                // TODO: Implement using bleManager.disconnectFromDevice() + removeBond()
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

    override fun checkConnectionState(
        uuid: String,
        callback: (Result<ConnectionState>) -> Unit
    ) {
        scope.launch {
            try {
                // TODO: Implement using bleManager.getConnectionState(uuid)
                withContext(Dispatchers.Main) {
                    callback(Result.success(ConnectionState.DISCONNECTED))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
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

    fun cleanup() {
        scope.cancel()
    }
}

