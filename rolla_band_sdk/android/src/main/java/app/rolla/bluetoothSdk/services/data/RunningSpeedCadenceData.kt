package app.rolla.bluetoothSdk.services.data

/**
 * Running Speed and Cadence measurement data from Bluetooth RSC Service.
 * Based on Bluetooth SIG Running Speed and Cadence Service specification.
 */
data class RunningSpeedCadenceData(
    /** Instantaneous speed in meters per second */
    val instantaneousSpeed: Float, // km/h
    /** Instantaneous cadence in steps per minute */
    val instantaneousCadence: Int, // steps/min
    /** Instantaneous stride length in centimeters (optional) */
    val instantaneousStrideLength: Int? = null, // cm
    /** Total distance traveled in meters (optional) */
    val totalDistance: Long? = null, // m
    /** Movement status: true = running, false = walking */
    val isRunning: Boolean,
    /** Calculated steps for this measurement period */
    val calculatedSteps: Float = 0f
) {
    override fun toString(): String {
        return "RunningSpeedCadenceData(speed=$instantaneousSpeed km/h, " +
                "cadence=$instantaneousCadence spm, steps=$calculatedSteps"
    }
}
