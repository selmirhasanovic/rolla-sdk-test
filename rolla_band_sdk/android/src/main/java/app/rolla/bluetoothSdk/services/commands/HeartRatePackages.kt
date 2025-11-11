package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.utils.extensions.toPositionedInt
import app.rolla.bluetoothSdk.utils.extensions.toIntLE
import app.rolla.bluetoothSdk.utils.extensions.log

class HeartRatePackages : Command<ByteArray> {
    companion object {
        const val TAG = "HeartRatePackages"
    }

    override fun getId(): Byte = 0x18.toByte()

    override fun bytesToWrite(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun read(data: ByteArray): ByteArray {
        val heartRate = data[1].toPositionedInt(0)
        val steps = data.toIntLE(2, 4)
        log(TAG, "Heart rate: $heartRate, steps: $steps, full data: ${data.contentToString()}")
        return data
    }
}