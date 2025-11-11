package app.rolla.bluetoothSdk.services.data

import app.rolla.bluetoothSdk.services.commands.data.Steps

data class StepsData (
    var uuid: String = "",
    val isEndOfData: Boolean = false,
    val hasMoreData: Boolean = false,
    val steps: ArrayList<Steps>,
    val lastBlockTimestamp: Long,
    val lastEntryTimestamp: Long
)