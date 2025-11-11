package app.rolla.bluetoothSdk.services.commands

class OtaUpdate : Command<ByteArray> {

    override fun getId(): Byte = 0x47.toByte()

    override fun bytesToWrite(): ByteArray = createCommandBytes { }

    override fun read(data: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

}