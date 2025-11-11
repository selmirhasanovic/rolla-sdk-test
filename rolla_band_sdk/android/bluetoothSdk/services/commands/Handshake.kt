package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.utils.extensions.log

class Handshake : Command<ByteArray> {

    companion object {
        const val TAG = "Handshake"
    }

    override fun getId(): Byte = 0x33.toByte()

    override fun errorId(): Byte = 0xc3.toByte()

    override fun bytesToWrite(): ByteArray = createCommandBytes {
        this[1] = 0x01.toByte()
    }

    override fun read(data: ByteArray): ByteArray {
        log(TAG, "Successful - Handshake response: ${data.contentToString()}")
        return data
    }
}