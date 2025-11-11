package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.utils.extensions.log
import app.rolla.bluetoothSdk.utils.extensions.writeBcdTimeToArray
import java.util.Calendar
import java.util.TimeZone

class SetCurrentTime : Command<ByteArray> {

    companion object {
        const val TAG = "SetCurrentTime"
    }

    override fun getId(): Byte = 0x01.toByte()

    override fun errorId(): Byte = 0x81.toByte()

    override fun bytesToWrite(): ByteArray {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return createCommandBytes {
            calendar.writeBcdTimeToArray(this)
        }
    }

    override fun read(data: ByteArray): ByteArray {
        log(TAG, "Successful - Set current time response: ${data.contentToString()}")
        return data
    }
}