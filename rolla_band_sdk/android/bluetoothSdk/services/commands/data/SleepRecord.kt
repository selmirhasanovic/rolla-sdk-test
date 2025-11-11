package app.rolla.bluetoothSdk.services.commands.data

import app.rolla.bluetoothSdk.utils.extensions.BcdTime

data class SleepRecord(
    val dataNumber: Int,
    val bcdTime: BcdTime,
    val sleepLength: Int,
    val timeInMilliseconds: Long,
    val offset: Int
)