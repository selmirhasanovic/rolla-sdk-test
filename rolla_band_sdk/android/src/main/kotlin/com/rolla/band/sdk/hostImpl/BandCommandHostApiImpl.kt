package com.rolla.band.sdk.hostImpl

import com.rolla.band.sdk.generated.BandCommandHostAPI
import com.rolla.band.sdk.generated.UserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// NOTE: This is a POC implementation. For production, integrate with bluetoothSdk module
class BandCommandHostApiImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : BandCommandHostAPI {

    // TODO: Inject BleManager from bluetoothSdk
    // private val bleManager: BleManager

    override fun updateUserData(
        uuid: String,
        userData: UserData,
        callback: (Result<Unit>) -> Unit
    ) {
        scope.launch {
            try {
                // TODO: Implement using bleManager.setBandUserData(uuid, userInfo)
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

    override fun getUserData(uuid: String, callback: (Result<UserData>) -> Unit) {
        scope.launch {
            try {
                // TODO: Implement using bleManager.readBandUserData(uuid)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(Throwable("Not implemented")))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    override fun getFirmwareVersion(uuid: String, callback: (Result<String>) -> Unit) {
        scope.launch {
            try {
                // TODO: Implement using bleManager.readFirmwareRevision(uuid)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(Throwable("Not implemented")))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    override fun getSerialNumber(uuid: String, callback: (Result<String>) -> Unit) {
        scope.launch {
            try {
                // TODO: Implement using bleManager.readSerialNumber(uuid)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(Throwable("Not implemented")))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    override fun getBatteryLevel(uuid: String, callback: (Result<Long>) -> Unit) {
        scope.launch {
            try {
                // TODO: Implement using bleManager.readBandBatteryLevel(uuid)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(Throwable("Not implemented")))
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

