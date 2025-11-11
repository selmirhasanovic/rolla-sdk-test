package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.utils.crcValue

interface Command<T> {
    fun getId(): Byte
    fun errorId(): Byte {
        return -1
    }
    fun bytesToWrite(): ByteArray
    fun read(data: ByteArray): T
    
    fun createCommandBytes(init: ByteArray.() -> Unit): ByteArray {
        return ByteArray(16).apply {
            this[0] = getId()
            init()
            crcValue()
        }
    }
}
