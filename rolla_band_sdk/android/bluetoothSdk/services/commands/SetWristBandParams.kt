package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.utils.extensions.log

class SetWristBandParams(val inActivity: Boolean) : Command<ByteArray> {

    companion object {
        const val TAG = "SetWristBandParams"
        private const val WRIST_DETECTION_ENABLED = 0x81.toByte()
        private const val IN_ACTIVITY_MODE = 0x8A.toByte()
        private const val OUTSIDE_ACTIVITY_MODE = 0x8F.toByte()
    }

    override fun getId(): Byte = 0x03.toByte()

    override fun errorId(): Byte = 0x83.toByte()

    override fun bytesToWrite(): ByteArray = createCommandBytes {
        this[5] = WRIST_DETECTION_ENABLED
        this[6] = if (inActivity) IN_ACTIVITY_MODE else OUTSIDE_ACTIVITY_MODE
    }

    override fun read(data: ByteArray): ByteArray {
        log(TAG, "Successful - Set wristband basic params response: ${data.contentToString()}")
        return data
    }
}