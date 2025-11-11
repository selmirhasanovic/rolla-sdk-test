package app.rolla.bluetoothSdk.services.commands.data

import app.rolla.bluetoothSdk.utils.extensions.BcdTime

data class CurrentTime (
    val bcdTime: BcdTime,
    val gpsDate: String
) {
    val year: Int get() = bcdTime.year
    val month: Int get() = bcdTime.month
    val day: Int get() = bcdTime.day
    val hour: Int get() = bcdTime.hour
    val minute: Int get() = bcdTime.minute
    val second: Int get() = bcdTime.second

    fun getDate(): String {
        return bcdTime.toDateString()
    }
}