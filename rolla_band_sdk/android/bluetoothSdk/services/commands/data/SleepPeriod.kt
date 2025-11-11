package app.rolla.bluetoothSdk.services.commands.data

data class SleepPeriod (
    val startTime: Long, // Timestamp in utc
    val endTime: Long, // Timestamp in utc
    val phase: SleepPhase
)