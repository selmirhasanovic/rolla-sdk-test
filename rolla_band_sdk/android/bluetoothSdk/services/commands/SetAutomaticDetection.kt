package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.AutomaticMode
import app.rolla.bluetoothSdk.utils.extensions.log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SetAutomaticDetection(private val automaticMode: AutomaticMode) : Command<ByteArray> {

    companion object {
        private const val TAG = "SetAutomaticDetection"
        private const val WORK_MODE = 0x02.toByte()
        private const val START_HOUR = 0x23.toByte()
        private const val START_MINUTE = 0x59.toByte()
        private const val ALL_WEEK_DAYS = 0xFF.toByte()
        private const val INTERVAL_TIME_MINUTES = 10
        private const val HEART_RATE_MODE = 0x01.toByte()
        private const val HRV_MODE = 0x04.toByte()
    }
    
    override fun getId(): Byte = 0x2a.toByte()

    override fun errorId(): Byte = 0xAA.toByte()

    override fun bytesToWrite(): ByteArray = createCommandBytes {
        this[1] = WORK_MODE
        this[2] = 0x00.toByte()
        this[3] = 0x00.toByte()
        this[4] = START_HOUR
        this[5] = START_MINUTE
        this[6] = ALL_WEEK_DAYS
        writeIntervalTime(this)
        this[9] = getAutomaticModeValue()
    }

    private fun writeIntervalTime(array: ByteArray) {
        val bytes = ByteBuffer.allocate(2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(INTERVAL_TIME_MINUTES.toShort())
            .array()
        
        bytes.copyInto(array, 7)
    }

    private fun getAutomaticModeValue(): Byte {
        return when (automaticMode) {
            AutomaticMode.HEART_RATE -> HEART_RATE_MODE
            AutomaticMode.HRV -> HRV_MODE
        }
    }

    override fun read(data: ByteArray): ByteArray {
        log(TAG, "Successful - Set automatic detection ${automaticMode.name} response: ${data.contentToString()}")
        return data
    }
}