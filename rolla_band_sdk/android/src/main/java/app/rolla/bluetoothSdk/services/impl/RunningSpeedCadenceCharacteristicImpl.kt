package app.rolla.bluetoothSdk.services.impl

import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.services.data.RunningSpeedCadenceData
import app.rolla.bluetoothSdk.steps.RealTimeStepCalculator
import app.rolla.bluetoothSdk.utils.extensions.isBitSet
import app.rolla.bluetoothSdk.utils.extensions.log
import app.rolla.bluetoothSdk.utils.extensions.toUIntLE
import app.rolla.bluetoothSdk.utils.extensions.toUShortLE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class RunningSpeedCadenceCharacteristicImpl(
    private val stepCalculator: RealTimeStepCalculator = RealTimeStepCalculator()
) : CharacteristicImpl {
    private val _dataFlow = MutableSharedFlow<RunningSpeedCadenceData>(replay = 1)
    
    // Add typed accessor
    val rscDataFlow: SharedFlow<RunningSpeedCadenceData> = _dataFlow.asSharedFlow()

    private var userHeight : Int? = null
    private var userWeight : Float? = null
    private var userAge : Int? = null
    private var userGender : Int? = null

    // Add coroutine scope
    override val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onRead(byteArray: ByteArray, device: BleDevice) {
        processRscData(byteArray)
    }

    override fun onNotify(byteArray: ByteArray, device: BleDevice) {
        processRscData(byteArray)
    }

    private fun processRscData(byteArray: ByteArray) {
        if (byteArray.isEmpty()) {
            throw IllegalArgumentException("RSC data is empty")
        }

        val rscData = parseRunningSpeedCadence(byteArray)

        val steps = if (USE_STEP_CALCULATOR) {

            // Calculate steps for current reading
            val stepResult = stepCalculator.calculateCurrentSteps(
                cadence = rscData.instantaneousCadence,
                speed = rscData.instantaneousSpeed,
                isRunning = rscData.isRunning,
                strideLength = rscData.instantaneousStrideLength,
                userHeight = userHeight,
                userWeight = userWeight,
                userAge = userAge,
                userGender = userGender
            )

            log(
                "RunningSpeedCadenceCharacteristic",
                "Speed: ${rscData.instantaneousSpeed} km/h, " +
                        "Cadence: ${rscData.instantaneousCadence} spm, " +
                        "Stride: ${rscData.instantaneousStrideLength} cm, " +
                        "Distance: ${rscData.totalDistance} m, " +
                        "IsRunning: ${rscData.isRunning}, " +
                        "Steps: ${"%.2f".format(stepResult.incrementalSteps)} (${"%.2f".format(stepResult.incrementalUncorrectedSteps)} raw), " +
                        "Total: ${"%.1f".format(stepResult.totalCorrectedSteps)} (${"%.1f".format(stepResult.totalUncorrectedSteps)} raw), " +
                        "Correction: ${"%.2f".format(stepResult.totalCorrectionDifference)} steps (${"%.1f".format(stepResult.totalCorrectionPercentage)}%), " +
                        "Confidence: ${"%.2f".format(stepResult.confidence)}, " +
                        "Activity: ${stepResult.activityType}"
            )
            stepResult.incrementalSteps
        } else {
            rscData.instantaneousCadence / 60f
        }
        scope.launch {
            _dataFlow.emit(rscData.copy(calculatedSteps = steps))
        }
    }

    private fun parseRunningSpeedCadence(data: ByteArray): RunningSpeedCadenceData {
        if (data.size < RSC_MIN_DATA_SIZE) {
            throw IllegalArgumentException("RSC data too short: ${data.size} bytes, minimum $RSC_MIN_DATA_SIZE required")
        }

        val flags = data[0].toUByte().toInt()
        val hasStrideLength = flags.isBitSet(FLAG_STRIDE_LENGTH_PRESENT)
        val hasTotalDistance = flags.isBitSet(FLAG_TOTAL_DISTANCE_PRESENT)
        val isRunning = flags.isBitSet(FLAG_WALKING_OR_RUNNING)

        var offset = 1

        // Instantaneous Speed - uint16, resolution 1/256 m/s, convert to km/h
        val speedMs = data.toUShortLE(offset).toFloat() / SPEED_RESOLUTION
        val speedKmh = speedMs * MS_TO_KMH_CONVERSION
        offset += 2

        // Instantaneous Cadence - uint8, steps per minute
        val cadence = data[offset].toUByte().toInt()
        offset += 1

        // Optional fields
        var strideLength: Int? = null
        var totalDistance: Long? = null

        if (hasStrideLength && offset + 1 < data.size) {
            strideLength = data.toUShortLE(offset).toInt()
            offset += 2
        }

        if (hasTotalDistance && offset + 3 < data.size) {
            totalDistance = data.toUIntLE(offset).toLong()
        }

        return RunningSpeedCadenceData(
            instantaneousSpeed = speedKmh,
            instantaneousCadence = cadence,
            instantaneousStrideLength = strideLength,
            totalDistance = totalDistance,
            isRunning = isRunning
        )
    }

    fun onDeviceConnected() {
        stepCalculator.reset()
    }

    fun setUserHeight(height: Int) {
        this.userHeight = height
    }

    fun setUserWeight(weight: Float) {
        this.userWeight = weight
    }

    fun setUserAge(age: Int) {
        this.userAge = age
    }

    fun setUserGender(gender: Int) {
        this.userGender = gender
    }

    companion object {
        private const val RSC_MIN_DATA_SIZE = 4
        private const val SPEED_RESOLUTION = 256.0f
        private const val MS_TO_KMH_CONVERSION = 3.6f // m/s to km/h conversion factor

        // RSC Flags bit positions
        private const val FLAG_STRIDE_LENGTH_PRESENT = 0
        private const val FLAG_TOTAL_DISTANCE_PRESENT = 1
        private const val FLAG_WALKING_OR_RUNNING = 2

        private const val USE_STEP_CALCULATOR = false
    }
}