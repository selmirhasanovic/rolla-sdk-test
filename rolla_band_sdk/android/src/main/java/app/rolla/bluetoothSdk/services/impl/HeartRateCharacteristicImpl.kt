package app.rolla.bluetoothSdk.services.impl

import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.services.data.HeartRateMeasurementData
import app.rolla.bluetoothSdk.utils.extensions.isBitSet
import app.rolla.bluetoothSdk.utils.extensions.log
import app.rolla.bluetoothSdk.utils.extensions.toUShortLE
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class HeartRateCharacteristicImpl : CharacteristicImpl {

    private val _dataFlow = MutableSharedFlow<HeartRateMeasurementData>(replay = 1)

    val heartRateMeasurementDataFlow: SharedFlow<HeartRateMeasurementData> = _dataFlow.asSharedFlow()

    // Add coroutine scope
    override val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onRead(byteArray: ByteArray, device: BleDevice) {
        processHeartRateData(byteArray)
    }

    override fun onNotify(byteArray: ByteArray, device: BleDevice) {
        processHeartRateData(byteArray)
    }

    private fun processHeartRateData(byteArray: ByteArray) {
        if (byteArray.isEmpty()) {
            throw IllegalArgumentException("Heart rate data is empty")
        }

        val heartRateData = parseHeartRate(byteArray)
        log(
            "HeartRateCharacteristic",
            "HR: ${heartRateData.heartRate} BPM, Contact: ${heartRateData.sensorContactDetected}, " +
                    "Energy: ${heartRateData.energyExpended} kJ, RR: ${heartRateData.rrIntervals?.size ?: 0} intervals"
        )

        scope.launch {
            _dataFlow.emit(heartRateData)
        }
    }

    /**
     * Parse heart rate measurement data according to Bluetooth Heart Rate Service specification.
     *
     * @param data Raw bytes from heart rate characteristic
     * @return Complete heart rate measurement data
     */
    private fun parseHeartRate(data: ByteArray): HeartRateMeasurementData {
        if (data.isEmpty()) {
            throw IllegalArgumentException("Heart rate data cannot be empty")
        }

        val flags = data[0].toUByte().toInt()
        val is16BitFormat = flags.isBitSet(FLAG_HR_VALUE_FORMAT)
        val hasSensorContact = flags.isBitSet(FLAG_SENSOR_CONTACT_SUPPORTED)
        val sensorContactDetected = if (hasSensorContact) flags.isBitSet(FLAG_SENSOR_CONTACT_DETECTED) else null
        val hasEnergyExpended = flags.isBitSet(FLAG_ENERGY_EXPENDED_PRESENT)
        val hasRrInterval = flags.isBitSet(FLAG_RR_INTERVAL_PRESENT)

        var offset = 1

        // Heart Rate Value (8-bit or 16-bit)
        val heartRate = if (is16BitFormat) {
            if (data.size < offset + 2) {
                throw IllegalArgumentException("Insufficient data for 16-bit heart rate")
            }
            val value = data.toUShortLE(offset).toInt()
            offset += 2
            value
        } else {
            if (data.size < offset + 1) {
                throw IllegalArgumentException("Insufficient data for 8-bit heart rate")
            }
            val value = data[offset].toUByte().toInt()
            offset += 1
            value
        }

        // Energy Expended (optional, 16-bit)
        var energyExpended: Int? = null
        if (hasEnergyExpended) {
            if (data.size < offset + 2) {
                throw IllegalArgumentException("Insufficient data for energy expended")
            }
            energyExpended = data.toUShortLE(offset).toInt()
            offset += 2
        }

        // RR-Intervals (optional, multiple 16-bit values)
        var rrIntervals: List<Int>? = null
        if (hasRrInterval && offset < data.size) {
            val intervals = mutableListOf<Int>()
            while (offset + 1 < data.size) {
                val rrValue = data.toUShortLE(offset).toInt()
                intervals.add((rrValue * RR_INTERVAL_RESOLUTION) / RR_INTERVAL_DIVISOR)
                offset += 2
            }
            rrIntervals = intervals.takeIf { it.isNotEmpty() }
        }

        return HeartRateMeasurementData(
            heartRate = heartRate,
            sensorContactDetected = sensorContactDetected,
            energyExpended = energyExpended,
            rrIntervals = rrIntervals
        )
    }

    companion object {
        // Heart Rate Flags bit positions
        private const val FLAG_HR_VALUE_FORMAT = 0
        private const val FLAG_SENSOR_CONTACT_SUPPORTED = 1
        private const val FLAG_SENSOR_CONTACT_DETECTED = 2
        private const val FLAG_ENERGY_EXPENDED_PRESENT = 3
        private const val FLAG_RR_INTERVAL_PRESENT = 4

        // RR-Interval conversion constants
        private const val RR_INTERVAL_RESOLUTION = 1000
        private const val RR_INTERVAL_DIVISOR = 1024
    }

}
