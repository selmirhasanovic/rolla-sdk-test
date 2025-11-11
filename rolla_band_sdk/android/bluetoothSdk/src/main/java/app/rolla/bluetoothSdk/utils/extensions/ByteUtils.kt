package app.rolla.bluetoothSdk.utils.extensions

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Bounds checking utility
private fun ByteArray.checkBounds(startIndex: Int, length: Int) {
    require(startIndex >= 0) { "Start index cannot be negative: $startIndex" }
    require(startIndex + length <= size) { 
        "Not enough bytes: need $length bytes at index $startIndex, but array size is $size" 
    }
}

// Short

fun ByteArray.toShortLE(startIndex: Int): Short {
    checkBounds(startIndex, 2)
    return this.copyShortBytes(startIndex).toShortLE()
}

fun ByteArray.toUShortLE(startIndex: Int): UShort {
    checkBounds(startIndex, 2)
    return this.copyShortBytes(startIndex).toShortLE().toUShort()
}

fun ByteArray.toShortLE(): Short {
    return ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).short
}

fun ByteArray.copyShortBytes(startIndex: Int): ByteArray {
    checkBounds(startIndex, 2)
    return this.copyOfRange(startIndex, startIndex + 2)
}

// Int

fun ByteArray.toUIntLE(startIndex: Int): UInt {
    checkBounds(startIndex, 4)
    return this.copyIntBytes(startIndex).toIntLE().toUInt()
}

fun ByteArray.copyIntBytes(startIndex: Int): ByteArray {
    checkBounds(startIndex, 4)
    return this.copyOfRange(startIndex, startIndex + 4)
}

fun ByteArray.toIntLE(): Int {
    return ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).int
}

fun Byte.toHex(): String {
    return "%02X".format(this)
}

fun Byte.toPositionedInt(pos: Int): Int {
    return this.toUByte().toInt() shl (pos * Byte.SIZE_BITS)
}

fun ByteArray.toIntLE(offset: Int, count: Int): Int {
    require(count in 1..4) { "Count must be between 1 and 4" }
    require(offset + count <= size) { "Not enough bytes" }

    var result = 0
    for (i in 0 until count) {
        result += this[offset + i].toPositionedInt(i)
    }
    return result
}

fun Byte.toBitString(): String {
    return (0 until 7).joinToString("-") { i ->
        ((this.toInt() shr i) and 1).toString()
    }
}

// BCD Time parsing
data class BcdTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int
) {
    fun toDateString(): String = "$year.$month.$day $hour:$minute:$second"
}

fun ByteArray.toBcdTime(startIndex: Int): BcdTime {
    checkBounds(startIndex, 6)
    return BcdTime(
        year = ("20" + this[startIndex].toHex()).toInt(),
        month = this[startIndex + 1].toHex().toInt(),
        day = this[startIndex + 2].toHex().toInt(),
        hour = this[startIndex + 3].toHex().toInt(),
        minute = this[startIndex + 4].toHex().toInt(),
        second = this[startIndex + 5].toHex().toInt()
    )
}
