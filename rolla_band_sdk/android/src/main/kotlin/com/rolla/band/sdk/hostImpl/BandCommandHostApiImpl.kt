package com.rolla.band.sdk.hostImpl

import app.rolla.bluetoothSdk.BleManager
import app.rolla.bluetoothSdk.services.commands.data.UserInfo
import app.rolla.bluetoothSdk.utils.extensions.log
import com.rolla.band.sdk.generated.BandBatteryFlutterApi
import com.rolla.band.sdk.generated.BandCommandHostAPI
import com.rolla.band.sdk.generated.UserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BandCommandHostApiImpl(
    private val bleManager: BleManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val batteryApi: BandBatteryFlutterApi
) : BandCommandHostAPI {

    init {
        scope.launch {
            bleManager.getSerialNumberFlow().collect { serialNumber ->
                withContext(Dispatchers.Main) {
                    log("BandCommandHostApiImpl", "Serial number: $serialNumber")
                    val callback = serialNumberCallbacks[serialNumber.uuid]
                    if (callback != null) {
                        if (serialNumber.error != null) {
                            callback(Result.failure(Throwable(serialNumber.error)))
                        } else {
                            callback(Result.success(serialNumber.serialNumber))
                        }
                        serialNumberCallbacks.remove(serialNumber.uuid)
                    } else {
                        log(
                            "BandCommandHostApiImpl",
                            "No callback found for serial number $serialNumber"
                        )
                    }
                }
            }
        }
        scope.launch {
            bleManager.getFirmwareVersionFlow().collect { firmwareRevision ->
                withContext(Dispatchers.Main) {
                    log("BandCommandHostApiImpl", "Firmware revision: $firmwareRevision")
                    val callback = firmwareCallbacks[firmwareRevision.uuid]
                    if (callback != null) {
                        if (firmwareRevision.error != null) {
                            callback(Result.failure(Throwable(firmwareRevision.error)))
                        } else {
                            callback(Result.success(firmwareRevision.firmwareVersion))
                        }
                        firmwareCallbacks.remove(firmwareRevision.uuid)
                    } else {
                        log(
                            "BandCommandHostApiImpl",
                            "No callback found for firmware revision $firmwareRevision"
                        )
                    }
                }
            }
        }
        scope.launch {
            bleManager.getRollaBandBatteryLevelFlow().collect { batteryLevel ->
                withContext(Dispatchers.Main) {
                    log("BandCommandHostApiImpl", "Battery level: $batteryLevel")
                    val callback = batteryCallbacks[batteryLevel.uuid]
                    if (callback != null) {
                        if (batteryLevel.error != null) {
                            callback(Result.failure(Throwable(batteryLevel.error)))
                        } else {
                            callback(Result.success(batteryLevel.batteryLevel.toLong()))
                        }
                        batteryCallbacks.remove(batteryLevel.uuid)
                    } else {
                        log(
                            "BandCommandHostApiImpl",
                            "No callback found for battery level $batteryLevel"
                        )
                        batteryApi.onBatteryLevelReceived(batteryLevel.batteryLevel.toLong()) {}
                    }
                }
            }
        }
        scope.launch {
            bleManager.getRollaBandUserDataFlow().collect { userData ->
                withContext(Dispatchers.Main) {
                    log("BandCommandHostApiImpl", "User data: $userData")
                    val callback = getUserDataCallbacks[userData.uuid]
                    if (callback != null) {
                        if (userData.error != null) {
                            callback(Result.failure(Throwable(userData.error)))
                        } else {
                            callback(Result.success(convertToPigeonUserData(userData)))
                        }
                        getUserDataCallbacks.remove(userData.uuid)
                    } else {
                        log("BandCommandHostApiImpl", "No callback found for user data $userData")
                    }
                }
            }
        }
        scope.launch {
            bleManager.getRollaBandSetUserDataFlow().collect { userData ->
                withContext(Dispatchers.Main) {
                    log("BandCommandHostApiImpl", "Set user data: $userData")
                    val callback = setUserCallbacks[userData.uuid]
                    if (callback != null) {
                        if (userData.error != null) {
                            callback(Result.failure(Throwable(userData.error)))
                        } else {
                            callback(Result.success(Unit))
                        }
                        setUserCallbacks.remove(userData.uuid)
                    } else {
                        log("BandCommandHostApiImpl", "No callback found for user data $userData")
                    }
                }
            }
        }
    }

    private val serialNumberCallbacks: MutableMap<String, (Result<String>) -> Unit> = mutableMapOf()
    private val firmwareCallbacks: MutableMap<String, (Result<String>) -> Unit> = mutableMapOf()
    private val batteryCallbacks: MutableMap<String, (Result<Long>) -> Unit> = mutableMapOf()

    private val getUserDataCallbacks: MutableMap<String, (Result<UserData>) -> Unit> = mutableMapOf()

    private val setUserCallbacks: MutableMap<String, (Result<Unit>) -> Unit> = mutableMapOf()

    override fun updateUserData(
        uuid: String,
        userData: UserData,
        callback: (Result<Unit>) -> Unit
    ) {
        if (setUserCallbacks.containsKey(uuid)) {
            callback.invoke(Result.failure(Throwable("User data is already being set for this device")))
            return
        }
        setUserCallbacks[uuid] = callback

        scope.launch {
            try {
                val result = bleManager.setBandUserData(uuid, convertToSdkUserInfo(userData))
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        // Don't invoke immediately, wait for flow data
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                        setUserCallbacks.remove(uuid)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                    setUserCallbacks.remove(uuid)
                }
            }
        }
    }

    override fun getUserData(uuid: String, callback: (Result<UserData>) -> Unit) {
        if (getUserDataCallbacks.containsKey(uuid)) {
            callback.invoke(Result.failure(Throwable("User data is already fetching for this device")))
            return
        }
        getUserDataCallbacks[uuid] = callback

        scope.launch {
            try {
                val result = bleManager.readBandUserData(uuid)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        // Don't invoke immediately, wait for flow data
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                        getUserDataCallbacks.remove(uuid)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                    getUserDataCallbacks.remove(uuid)
                }
            }
        }
    }

    override fun getFirmwareVersion(uuid: String, callback: (Result<String>) -> Unit) {
        if (firmwareCallbacks.containsKey(uuid)) {
            callback.invoke(Result.failure(Throwable("Firmware version is already fetching for this device")))
            return
        }
        firmwareCallbacks[uuid] = callback

        scope.launch {
            try {
                val result = bleManager.readFirmwareRevision(uuid)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        // Don't invoke immediately, wait for flow data
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                        firmwareCallbacks.remove(uuid)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                    firmwareCallbacks.remove(uuid)
                }
            }
        }
    }

    override fun getSerialNumber(uuid: String, callback: (Result<String>) -> Unit) {
        if (serialNumberCallbacks.containsKey(uuid)) {
            callback.invoke(Result.failure(Throwable("Serial number is already fetching for this device")))
            return
        }
        serialNumberCallbacks[uuid] = callback

        scope.launch {
            try {
                val result = bleManager.readSerialNumber(uuid)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        // Don't invoke immediately, wait for flow data
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                        serialNumberCallbacks.remove(uuid)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                    serialNumberCallbacks.remove(uuid)
                }
            }
        }
    }

    override fun getBatteryLevel(uuid: String, callback: (Result<Long>) -> Unit) {
        if (batteryCallbacks.containsKey(uuid)) {
            callback.invoke(Result.failure(Throwable("Battery level is already fetching for this device")))
            return
        }
        batteryCallbacks[uuid] = callback

        scope.launch {
            try {
                val result = bleManager.readBandBatteryLevel(uuid)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        // Don't invoke immediately, wait for flow data
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                        batteryCallbacks.remove(uuid)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                    batteryCallbacks.remove(uuid)
                }
            }
        }
    }

    fun convertToPigeonUserData(sdkData: app.rolla.bluetoothSdk.services.data.UserData): UserData {
        return UserData(
            age = sdkData.userInfo.age.toLong(),
            height = sdkData.userInfo.height.toDouble(),
            weight = sdkData.userInfo.weight.toDouble(),
            gender = sdkData.userInfo.gender.toLong()
        )
    }

    fun convertToSdkUserInfo(pigeonData: UserData): UserInfo {
        log(
            "BandCommandHostApiImpl",
            "Converting pigeon data to sdk data: ${pigeonData.gender} - ${pigeonData.age} - ${pigeonData.height} - ${pigeonData.weight}"
        )
        return UserInfo(
            gender = pigeonData.gender.toInt(),
            age = pigeonData.age.toInt(),
            height = pigeonData.height.toInt(),
            weight = pigeonData.weight.toFloat(),
            stride = 0,
            userDeviceId = ""
        )
    }

    fun cleanup() {
        setUserCallbacks.clear()
        getUserDataCallbacks.clear()
        serialNumberCallbacks.clear()
        firmwareCallbacks.clear()
        batteryCallbacks.clear()
        scope.cancel()
    }
}
