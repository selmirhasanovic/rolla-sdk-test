package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.UserInfo
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlin.math.roundToInt
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SetUserData : Command<ByteArray> {

    companion object {
        const val TAG = "SetUserData"
        private const val STEP_LENGTH_MULTIPLIER = 0.41
        private const val NEW_FIRMWARE_MIN_YEAR = 25
        private const val NEW_FIRMWARE_MIN_MONTH = 4
    }

    private var userInfo: UserInfo = UserInfo(0, 0, 0, 0f, 0, "")
    private var firmwareVersion = ""

    override fun getId(): Byte = 0x02.toByte()

    override fun errorId(): Byte = 0x82.toByte()

    fun setUserInfo(userInfo: UserInfo) {
        this.userInfo = userInfo
    }

    fun setFirmwareVersion(version: String) {
        firmwareVersion = version
    }

    override fun bytesToWrite(): ByteArray = createCommandBytes {
        this[1] = userInfo.gender.toByte()
        this[2] = userInfo.age.toByte()
        this[3] = userInfo.height.toByte()
        val weightEndIndex = writeWeight(this)
        this[weightEndIndex] = calculateStepLength(userInfo.height)
    }

    private fun writeWeight(array: ByteArray): Int {
        return if (isNewFirmwareVersion()) {
            writeFloatWeight(array)
        } else {
            writeByteWeight(array)
        }
    }

    private fun writeFloatWeight(array: ByteArray): Int {
        val weightBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(userInfo.weight)
            .array()
        
        weightBytes.copyInto(array, 4)
        return 8
    }

    private fun writeByteWeight(array: ByteArray): Int {
        array[4] = userInfo.weight.toInt().toByte()
        return 5
    }

    private fun calculateStepLength(height: Int): Byte {
        return (height * STEP_LENGTH_MULTIPLIER).roundToInt().toByte()
    }

    override fun read(data: ByteArray): ByteArray {
        log(TAG, "Successful - Set user data response: ${data.contentToString()}")
        return data
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