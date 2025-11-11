package app.rolla.bluetoothSdk.services.commands.data

import app.rolla.bluetoothSdk.utils.extensions.BcdTime

data class ActiveHeartRateRecord(
    val dataNumber: Int,
    val bcdTime: BcdTime,
    val timeInMilliseconds: Long,
    val offset: Int
)