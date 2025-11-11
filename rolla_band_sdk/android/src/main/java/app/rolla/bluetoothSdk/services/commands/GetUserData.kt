package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.UserInfo
import app.rolla.bluetoothSdk.utils.extensions.toPositionedInt
import app.rolla.bluetoothSdk.utils.extensions.log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GetUserData: Command<UserInfo> {

    companion object {
        private const val TAG = "GetUserData"
        private const val NEW_FIRMWARE_MIN_YEAR = 25
        private const val NEW_FIRMWARE_MIN_MONTH = 4
    }

    private var firmwareVersion = ""

    override fun getId(): Byte = 0x42.toByte()

    override fun errorId(): Byte = 0xC2.toByte()

    override fun bytesToWrite(): ByteArray = createCommandBytes { }

    fun setFirmwareVersion(version: String) {
        firmwareVersion = version
    }

    override fun read(data: ByteArray): UserInfo {
        val gender = data[1].toPositionedInt(0) // 0 female 1 male
        val age = data[2].toPositionedInt(0)
        val height = data[3].toPositionedInt(0)
        
        log(TAG, "Firmware version: $firmwareVersion, is New Firmware Version: ${isNewFirmwareVersion()}")
        
        val (weight, stride, deviceIdRange) = if (isNewFirmwareVersion()) {
            val weightBytes = ByteArray(4)
            for (i in 0 until 4) {
                weightBytes[3 - i] = data[i + 4]
            }
            val weight = ByteBuffer.wrap(weightBytes).order(ByteOrder.BIG_ENDIAN).float
            val stride = data[8].toPositionedInt(0)
            Triple(weight, stride, 9..14)
        } else {
            val weight = data[4].toPositionedInt(0).toFloat()
            val stride = data[5].toPositionedInt(0)
            Triple(weight, stride, 6..11)
        }
        
        val userDeviceID = deviceIdRange
            .map { data[it] }
            .filter { it != 0x00.toByte() }
            .map { it.toPositionedInt(0).toChar() }
            .joinToString("")
        
        val userInfo = UserInfo(gender, age, height, weight, stride, userDeviceID)
        log(TAG, "User info: $userInfo")
        return userInfo
    }

    private fun isNewFirmwareVersion(): Boolean {
        if (firmwareVersion.length <= 6) return false
        
        return try {
            val datePart = firmwareVersion.split("-").getOrNull(1) ?: return false
            if (datePart.length < 4) return false
            
            val year = datePart.substring(0, 2).toInt()
            val month = datePart.substring(2, 4).toInt()
            
            year >= NEW_FIRMWARE_MIN_YEAR && month >= NEW_FIRMWARE_MIN_MONTH
        } catch (e: Exception) {
            log(TAG, "Error parsing firmware version: $firmwareVersion")
            false
        }
    }
}