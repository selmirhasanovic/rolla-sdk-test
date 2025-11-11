package app.rolla.bluetoothSdk.services.data

import app.rolla.bluetoothSdk.services.commands.data.HeartRate

data class HeartRateData (
    var uuid: String = "",
    val isEndOfData: Boolean = false,
    val hasMoreData: Boolean = false,
    val heartRates: ArrayList<HeartRate> = arrayListOf(),
)