package com.rolla.band.sdk.hostImpl

import android.annotation.SuppressLint
import app.rolla.bluetoothSdk.BleManager
import app.rolla.bluetoothSdk.services.commands.data.SleepPhase
import app.rolla.bluetoothSdk.services.data.HrvData
import app.rolla.bluetoothSdk.services.data.SleepData
import app.rolla.bluetoothSdk.services.data.StepsData
import app.rolla.bluetoothSdk.services.data.TotalHeartRateData
import app.rolla.bluetoothSdk.utils.extensions.log
import com.rolla.band.sdk.generated.RollaBandHRV
import com.rolla.band.sdk.generated.RollaBandHRVSyncResponse
import com.rolla.band.sdk.generated.RollaBandHealthDataHostApi
import com.rolla.band.sdk.generated.RollaBandHeartRate
import com.rolla.band.sdk.generated.RollaBandHeartRateSyncResponse
import com.rolla.band.sdk.generated.RollaBandSleepStage
import com.rolla.band.sdk.generated.RollaBandSleepStageValue
import com.rolla.band.sdk.generated.RollaBandSleepSyncResponse
import com.rolla.band.sdk.generated.RollaBandStep
import com.rolla.band.sdk.generated.RollaBandStepsSyncResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
class RollaBandHealthDataHostApiImpl(
    private val bleManager: BleManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : RollaBandHealthDataHostApi {

    private val stepsCallbacks: MutableMap<String, (Result<RollaBandStepsSyncResponse>) -> Unit> = mutableMapOf()
    private val hrvCallbacks: MutableMap<String, (Result<RollaBandHRVSyncResponse>) -> Unit> = mutableMapOf()
    private val heartRateCallbacks: MutableMap<String, (Result<RollaBandHeartRateSyncResponse>) -> Unit> = mutableMapOf()

    private val sleepCallbacks: MutableMap<String, (Result<RollaBandSleepSyncResponse>) -> Unit> = mutableMapOf()

    init {
        scope.launch {
            bleManager.getRollaBandStepsFlow().collect { stepsData ->
                withContext(Dispatchers.Main) {
                    log("RollaBandHealthDataHostApiImpl", "Steps data: $stepsData")
                    val callback = stepsCallbacks[stepsData.uuid]
                    if (callback != null) {
                        if (!stepsData.hasMoreData && stepsData.isEndOfData) {
                            callback(Result.success(convertToPigeonStepsData(stepsData)))
                            stepsCallbacks.remove(stepsData.uuid)
                        }
                    } else {
                        log("RollaBandHealthDataHostApiImpl", "No callback found for steps data $stepsData")
                    }
                }
            }
        }
        scope.launch {
            bleManager.getRollaBandHrvFlow().collect { hrvData ->
                withContext(Dispatchers.Main) {
                    log("RollaBandHealthDataHostApiImpl", "HRV data: $hrvData")
                    val callback = hrvCallbacks[hrvData.uuid]
                    if (callback != null) {
                        if (!hrvData.hasMoreData && hrvData.isEndOfData) {
                            callback(Result.success(convertToPigeonHrvData(hrvData)))
                            hrvCallbacks.remove(hrvData.uuid)
                        }
                    } else {
                        log("RollaBandHealthDataHostApiImpl", "No callback found for hrv data $hrvData")
                    }
                }
            }
        }
        scope.launch {
            bleManager.getRollaBandHeartRateFlow().collect { heartRateData ->
                withContext(Dispatchers.Main) {
                    log("RollaBandHealthDataHostApiImpl", "Heart rate data: $heartRateData")
                    val callback = heartRateCallbacks[heartRateData.uuid]
                    if (callback != null) {
                        callback(Result.success(convertToPigeonHeartRateData(heartRateData)))
                        heartRateCallbacks.remove(heartRateData.uuid)
                    } else {
                        log(
                            "RollaBandHealthDataHostApiImpl",
                            "No callback found for heart rate data $heartRateData"
                        )
                    }
                }
            }
        }
        scope.launch {
            bleManager.getRollaBandSleepFlow().collect { sleepData ->
                withContext(Dispatchers.Main) {
                    log("RollaBandHealthDataHostApiImpl", "Sleep data: $sleepData")
                    val callback = sleepCallbacks[sleepData.uuid]
                    if (callback != null) {
                        if (!sleepData.hasMoreData && sleepData.isEndOfData) {
                            callback(Result.success(convertToPigeonSleepData(sleepData)))
                            sleepCallbacks.remove(sleepData.uuid)
                        }
                    } else {
                        log("RollaBandHealthDataHostApiImpl", "No callback found for sleep data $sleepData")
                    }
                }
            }
        }
    }

    override fun getStepsData(
        uuid: String,
        lastSyncedBlockTimestamp: Long,
        lastSyncedEntryTimestamp: Long,
        callback: (Result<RollaBandStepsSyncResponse>) -> Unit
    ) {
        if (stepsCallbacks.containsKey(uuid)) {
            callback.invoke(Result.failure(Throwable("Steps data is already fetching for this device")))
            return
        }
        stepsCallbacks[uuid] = callback

        scope.launch {
            try {
                val result = bleManager.readBandSteps(uuid, lastSyncedBlockTimestamp, lastSyncedEntryTimestamp)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        // Don't invoke immediately, wait for flow data
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                        stepsCallbacks.remove(uuid)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                    stepsCallbacks.remove(uuid)
                }
            }
        }
    }

    override fun getHeartRateData(
        uuid: String,
        activityLastSyncedBlockTimestamp: Long,
        activityLastSyncedEntryTimestamp: Long,
        passiveLastSyncedTimestamp: Long,
        callback: (Result<RollaBandHeartRateSyncResponse>) -> Unit
    ) {
        if (heartRateCallbacks.containsKey(uuid)) {
            callback.invoke(Result.failure(Throwable("Heart rate data is already fetching for this device")))
            return
        }
        heartRateCallbacks[uuid] = callback

        scope.launch {
            try {
                val result = bleManager.readBandHeartRate(
                    uuid,
                    activityLastSyncedBlockTimestamp,
                    activityLastSyncedEntryTimestamp,
                    passiveLastSyncedTimestamp
                )
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        // Don't invoke immediately, wait for flow data
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                        heartRateCallbacks.remove(uuid)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                    heartRateCallbacks.remove(uuid)
                }
            }
        }
    }

    override fun getHRVData(
        uuid: String,
        lastSyncedBlockTimestamp: Long,
        callback: (Result<RollaBandHRVSyncResponse>) -> Unit
    ) {
        if (hrvCallbacks.containsKey(uuid)) {
            callback.invoke(Result.failure(Throwable("HRV data is already fetching for this device")))
            return
        }
        hrvCallbacks[uuid] = callback

        scope.launch {
            try {
                val result = bleManager.readBandHrv(uuid, lastSyncedBlockTimestamp)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        // Don't invoke immediately, wait for flow data
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                        hrvCallbacks.remove(uuid)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                    hrvCallbacks.remove(uuid)
                }
            }
        }
    }

    override fun getSleepData(
        uuid: String,
        lastSyncedBlockTimestamp: Long,
        lastSyncedEntryTimestamp: Long,
        callback: (Result<RollaBandSleepSyncResponse>) -> Unit
    ) {
        log("RollaBandHealthDataHostApiImpl", "getSleepData called uuid=$uuid block=$lastSyncedBlockTimestamp entry=$lastSyncedEntryTimestamp")
        if (sleepCallbacks.containsKey(uuid)) {
            callback.invoke(Result.failure(Throwable("Sleep data is already fetching for this device")))
            return
        }
        sleepCallbacks[uuid] = callback

        scope.launch {
            try {
                val result = bleManager.readBandSleep(uuid, lastSyncedBlockTimestamp, lastSyncedEntryTimestamp)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        // Don't invoke immediately, wait for flow data
                    } else {
                        callback(Result.failure(Throwable(result.errorMessage)))
                        sleepCallbacks.remove(uuid)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                    sleepCallbacks.remove(uuid)
                }
            }
        }
    }

    fun convertToPigeonStepsData(stepsData: StepsData): RollaBandStepsSyncResponse {
        return RollaBandStepsSyncResponse(
            steps = stepsData.steps.map {
                RollaBandStep(it.timestamp, it.steps.toLong(), it.calories)
            },
            lastSyncedBlockTimestamp = stepsData.lastBlockTimestamp,
            lastSyncedEntryTimestamp = stepsData.lastEntryTimestamp
        )
    }

    fun convertToPigeonHrvData(hrvData: HrvData): RollaBandHRVSyncResponse {
        return RollaBandHRVSyncResponse(
            hrvs = hrvData.hrvList.map {
                RollaBandHRV(it.timestamp, it.hrv.toLong())
            },
            lastSyncedBlockTimestamp = hrvData.lastBlockTimestamp
        )
    }

    fun convertToPigeonHeartRateData(heartRateData: TotalHeartRateData): RollaBandHeartRateSyncResponse {
        return RollaBandHeartRateSyncResponse(
            heartRates = heartRateData.heartRates.map {
                RollaBandHeartRate(it.timestamp, it.heartRate.toLong())
            },
            activityLastSyncedBlockTimestamp = heartRateData.activityLastBlockTimestamp,
            activityLastSyncedEntryTimestamp = heartRateData.activityLastEntryTimestamp,
            passiveLastSyncedTimestamp = heartRateData.passiveLastBlockTimestamp
        )
    }

    fun convertToPigeonSleepData(sleepData: SleepData): RollaBandSleepSyncResponse {
        return RollaBandSleepSyncResponse(
            sleepStages = sleepData.sleeps.map {
                RollaBandSleepStage(it.startTime, it.endTime, it.phase.toPigeonValue())
            },
            lastSyncedBlockTimestamp = sleepData.lastBlockTimestamp,
            lastSyncedEntryTimestamp = sleepData.lastEntryTimestamp
        )
    }

    private fun SleepPhase.toPigeonValue(): RollaBandSleepStageValue {
        return when (this) {
            SleepPhase.DEEP -> RollaBandSleepStageValue.DEEP
            SleepPhase.LIGHT -> RollaBandSleepStageValue.LIGHT
            SleepPhase.REM -> RollaBandSleepStageValue.REM
            SleepPhase.AWAKE -> RollaBandSleepStageValue.AWAKE
        }
    }

    fun cleanup() {
        stepsCallbacks.clear()
        hrvCallbacks.clear()
        heartRateCallbacks.clear()
        sleepCallbacks.clear()
        scope.cancel()
    }

}

