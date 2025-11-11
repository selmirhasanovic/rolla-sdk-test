package app.rolla.bluetoothSdk.utils

internal fun ByteArray.crcValue() {
    require(isNotEmpty()) { "ByteArray cannot be empty for CRC calculation" }

    val crc = dropLast(1).sumOf { it.toInt() }
    this[lastIndex] = crc.toByte()
}

internal fun decimalToBcd(value: Int): Byte {
    return Integer.parseInt(value.toString(), 16).toByte()
}