package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.utils.extensions.log

class GetWristBandParams : Command<ByteArray> {

    companion object {
        const val TAG = "GetWristBandParams"
        private const val HR_FLAG_ENABLED = 0x81.toByte()
        private const val HR_FLAG_DISABLED = 0x80.toByte()
    }

    override fun getId(): Byte = 0x04.toByte()

    override fun errorId(): Byte = 0x84.toByte()

    override fun bytesToWrite(): ByteArray = createCommandBytes { }

    override fun read(data: ByteArray): ByteArray {
        log(TAG, "Get wristband basic params ${data.contentToString()}")
        
        val automaticHrFlag = data[5] // 0x81: Enable. 0x80: Disable
        val startStepCount = data[6] // Ranges from 10 to 40
        
        log(TAG, "Automatic HR flag: $automaticHrFlag, start step count: $startStepCount")
        return data
    }
}