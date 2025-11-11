package app.rolla.bluetoothSdk.services.data

/**
 * Heart Rate measurement data from Bluetooth Heart Rate Service.
 * Based on Bluetooth SIG Heart Rate Service specification.
 */
data class HeartRateMeasurementData(
    /** Heart rate value in beats per minute (BPM) */
    val heartRate: Int, // BPM
    /** Sensor contact status: true = detected, false = not detected, null = not supported */
    val sensorContactDetected: Boolean? = null,
    /** Energy expended in kilojoules since last reset (optional) */
    val energyExpended: Int? = null, // kJ
    /** RR-Interval values in milliseconds (optional) */
    val rrIntervals: List<Int>? = null // ms
) {
    override fun toString(): String {
        return HeartRateMeasurementData::class.simpleName + "(heartRate=$heartRate, " +
                "sensorContactDetected=$sensorContactDetected, " +
                "energyExpended=$energyExpended, " +
                "rrIntervals=${rrIntervals?.size ?: 0} values)"
    }
}