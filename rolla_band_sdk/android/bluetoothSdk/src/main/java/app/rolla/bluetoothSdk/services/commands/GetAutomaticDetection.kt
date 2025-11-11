package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.AutomaticDetection
import app.rolla.bluetoothSdk.services.commands.data.AutomaticMode
import app.rolla.bluetoothSdk.utils.extensions.toPositionedInt
import app.rolla.bluetoothSdk.utils.extensions.toIntLE
import app.rolla.bluetoothSdk.utils.extensions.log
import app.rolla.bluetoothSdk.utils.extensions.toBitString
import app.rolla.bluetoothSdk.utils.extensions.toHex

class GetAutomaticDetection(private val automaticMode: AutomaticMode) : Command<AutomaticDetection> {

    companion object {
        private const val TAG = "GetAutomaticDetection"
        private const val HEART_RATE_MODE = 0x01.toByte()
        private const val HRV_MODE = 0x04.toByte()
    }

    override fun getId(): Byte = 0x2b.toByte()

    override fun errorId(): Byte = 0xAB.toByte()

    override fun bytesToWrite(): ByteArray = createCommandBytes {
        this[1] = getAutomaticModeByte()
    }

    private fun getAutomaticModeByte(): Byte {
        // 0x01 heart rate, 0x02 spo2, 0x03 temp, 0x04 hrv
        return when(automaticMode) {
            AutomaticMode.HEART_RATE -> HEART_RATE_MODE
            AutomaticMode.HRV -> HRV_MODE
        }
    }

    private fun toAutomaticMode(byte: Byte): AutomaticMode {
        return when (byte) {
            HEART_RATE_MODE -> AutomaticMode.HEART_RATE
            HRV_MODE -> AutomaticMode.HRV
            else -> AutomaticMode.HEART_RATE
        }
    }

    override fun read(data: ByteArray): AutomaticDetection {
        val workMode = data[1].toPositionedInt(0)
        val startHour = data[2].toHex()
        val startMin = data[3].toHex()
        val stopHour = data[4].toHex()
        val stopMin = data[5].toHex()
        val week = data[6].toBitString()
        val intervalTime = data.toIntLE(7, 2)
        val mode = toAutomaticMode(data[9])
        
        log(TAG, "Get automatic detection ${mode.name} ${data.contentToString()}")
        val detection = AutomaticDetection(workMode, startHour, startMin, stopHour, stopMin, week, intervalTime, mode)
        log(TAG, detection.toString())
        return detection
    }
}