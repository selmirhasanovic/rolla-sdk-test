package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.CurrentTime
import app.rolla.bluetoothSdk.utils.extensions.log
import app.rolla.bluetoothSdk.utils.extensions.toHex
import app.rolla.bluetoothSdk.utils.extensions.toBcdTime

class GetCurrentTime : Command<CurrentTime> {

    companion object {
        private const val TAG = "GetCurrentTime"
    }

    override fun getId(): Byte = 0x41.toByte()

    override fun errorId(): Byte = 0xC1.toByte()

    override fun bytesToWrite(): ByteArray = createCommandBytes { }

    override fun read(data: ByteArray): CurrentTime {
        val bcdTime = data.toBcdTime(1)
        val gpsDate = "${data[9].toHex()}.${data[10].toHex()}.${data[11].toHex()}"
        val currentTime = CurrentTime(bcdTime, gpsDate)
        
        log(TAG, "Date: ${currentTime.getDate()}, GPS date: $gpsDate")
        log(TAG, "Full data: ${data.contentToString()}")
        return currentTime
    }
}