package app.rolla.bluetoothSdk.services.commands.data

import app.rolla.bluetoothSdk.utils.extensions.BcdTime

data class PassiveHeartRateRecord(
    val dataNumber: Int,
    val bcdTime: BcdTime,
    val heartRate: Int,
    val timeInMilliseconds: Long,
    val offset: Int
)