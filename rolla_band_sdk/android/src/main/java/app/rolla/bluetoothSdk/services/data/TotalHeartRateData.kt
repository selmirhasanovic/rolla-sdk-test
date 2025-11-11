package app.rolla.bluetoothSdk.services.data

import app.rolla.bluetoothSdk.services.commands.data.HeartRate

data class TotalHeartRateData (
    var uuid: String = "",
    val heartRates: ArrayList<HeartRate> = arrayListOf(),
    val activityLastBlockTimestamp: Long = 0L,
    val activityLastEntryTimestamp: Long = 0L,
    val passiveLastBlockTimestamp: Long = 0L
)