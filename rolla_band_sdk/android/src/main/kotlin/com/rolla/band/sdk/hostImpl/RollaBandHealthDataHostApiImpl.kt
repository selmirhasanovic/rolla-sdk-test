package com.rolla.band.sdk.hostImpl

import app.rolla.bluetoothSdk.BleManager
import app.rolla.bluetoothSdk.services.data.TotalHeartRateData
import app.rolla.bluetoothSdk.utils.extensions.log
import com.rolla.band.sdk.generated.RollaBandHealthDataHostApi
import com.rolla.band.sdk.generated.RollaBandHeartRate
import com.rolla.band.sdk.generated.RollaBandHeartRateSyncResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RollaBandHealthDataHostApiImpl(
    private val bleManager: BleManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : RollaBandHealthDataHostApi {

    private val heartRateCallbacks: MutableMap<String, (Result<RollaBandHeartRateSyncResponse>) -> Unit> = mutableMapOf()

    init {
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

    fun cleanup() {
        heartRateCallbacks.clear()
        scope.cancel()
    }
}

