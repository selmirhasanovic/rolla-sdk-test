package app.rolla.bluetoothSdk.steps

data class SensorReading(
    val cadence: Int,
    val speed: Float,
    val strideLength: Int?
)