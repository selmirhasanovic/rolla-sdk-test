package app.rolla.bluetoothSdk.services.data

import app.rolla.bluetoothSdk.services.commands.data.SleepPeriod

data class SleepData (
    var uuid: String = "",
    val isEndOfData: Boolean = false,
    val hasMoreData: Boolean = false,
    val sleeps: ArrayList<SleepPeriod>,
    val lastBlockTimestamp: Long,
    val lastEntryTimestamp: Long
)