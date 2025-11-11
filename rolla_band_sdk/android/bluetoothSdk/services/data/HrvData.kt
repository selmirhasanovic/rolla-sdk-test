package app.rolla.bluetoothSdk.services.data

import app.rolla.bluetoothSdk.services.commands.data.Hrv

data class HrvData (
    var uuid: String = "",
    val isEndOfData: Boolean = false,
    val hasMoreData: Boolean = false,
    val hrvList: ArrayList<Hrv>,
    val lastBlockTimestamp: Long
)