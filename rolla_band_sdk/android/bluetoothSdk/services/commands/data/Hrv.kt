package app.rolla.bluetoothSdk.services.commands.data

data class Hrv (
    val timestamp: Long, // Timestamp in utc
    val hrv: Int // Heart rate in minute
)