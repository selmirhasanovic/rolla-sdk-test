package app.rolla.bluetoothSdk.services.commands.data

data class Steps (
    val timestamp: Long, // Timestamp in utc
    val steps: Int, // Steps in minute
    val calories: Double
) {
    override fun toString(): String {
        return "Steps(timestamp=$timestamp, steps=$steps, calories=$calories)"
    }
}