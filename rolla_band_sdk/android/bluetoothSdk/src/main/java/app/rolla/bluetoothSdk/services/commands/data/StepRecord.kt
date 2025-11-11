package app.rolla.bluetoothSdk.services.commands.data

import app.rolla.bluetoothSdk.utils.extensions.BcdTime

data class StepRecord(
    val dataNumber: Int,
    val bcdTime: BcdTime,
    val totalSteps: Int,
    val calories: Int,
    val distance: Int,
    val timeInMilliseconds: Long,
    val offset: Int
)