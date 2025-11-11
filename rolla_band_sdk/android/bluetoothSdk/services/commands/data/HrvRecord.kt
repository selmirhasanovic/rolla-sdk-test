package app.rolla.bluetoothSdk.services.commands.data

import app.rolla.bluetoothSdk.utils.extensions.BcdTime

data class HrvRecord(
    val dataNumber: Int,
    val bcdTime: BcdTime,
    val hrv: Int,
    val fatigueDegree: Int,
    val sbp: Int,
    val dbp: Int,
    val timeInMilliseconds: Long,
    val offset: Int
)