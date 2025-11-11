package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.utils.extensions.toPositionedInt
import app.rolla.bluetoothSdk.utils.extensions.log

class BatteryLevel : Command<Int> {
    companion object {
        const val TAG = "BatteryLevel"
        private const val BATTERY_REQUEST_FLAG = 0x99.toByte()
    }

    override fun getId(): Byte = 0x13.toByte()

    override fun errorId(): Byte = 0x93.toByte()

    override fun bytesToWrite(): ByteArray = createCommandBytes {
        this[1] = BATTERY_REQUEST_FLAG
    }

    override fun read(data: ByteArray): Int {
        val batteryLevel = data[1].toPositionedInt(0)
        log(TAG, "Battery Level: $batteryLevel%")
        return batteryLevel
    }
}