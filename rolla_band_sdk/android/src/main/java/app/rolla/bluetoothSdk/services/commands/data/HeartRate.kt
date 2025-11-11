package app.rolla.bluetoothSdk.services.commands.data

data class HeartRate (
    val timestamp: Long, // Timestamp in utc
    val heartRate: Int // Heart rate in minute
)