package com.rolla.band.sdk.hostImpl

import com.rolla.band.sdk.generated.RollaBandHealthDataHostApi
import com.rolla.band.sdk.generated.RollaBandHeartRate
import com.rolla.band.sdk.generated.RollaBandHeartRateSyncResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// NOTE: This is a POC implementation. For production, integrate with bluetoothSdk module
class RollaBandHealthDataHostApiImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : RollaBandHealthDataHostApi {

    // TODO: Inject BleManager from bluetoothSdk
    // private val bleManager: BleManager

    override fun getHeartRateData(
        uuid: String,
        activityLastSyncedBlockTimestamp: Long,
        activityLastSyncedEntryTimestamp: Long,
        passiveLastSyncedTimestamp: Long,
        callback: (Result<RollaBandHeartRateSyncResponse>) -> Unit
    ) {
        scope.launch {
            try {
                // TODO: Implement using bleManager.readBandHeartRate(uuid, timestamps)
                // Listen to bleManager.getRollaBandHeartRateFlow() for response
                val response = RollaBandHeartRateSyncResponse(
                    heartRates = emptyList(),
                    activityLastSyncedBlockTimestamp = activityLastSyncedBlockTimestamp,
                    activityLastSyncedEntryTimestamp = activityLastSyncedEntryTimestamp,
                    passiveLastSyncedTimestamp = passiveLastSyncedTimestamp,
                )
                withContext(Dispatchers.Main) {
                    callback(Result.success(response))
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

