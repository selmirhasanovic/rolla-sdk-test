package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.ChargingStatus as ChargingStatusEnum
import app.rolla.bluetoothSdk.utils.extensions.log

class ChargingStatus : Command<ChargingStatusEnum> {

    companion object {
        const val TAG = "ChargingStatus"
        private const val CHARGING_FLAG = 0x01.toByte()
        private const val NOT_CHARGING_FLAG = 0x00.toByte()
    }

    override fun getId(): Byte = 0x20.toByte()

    override fun bytesToWrite(): ByteArray = createCommandBytes { }

    override fun read(data: ByteArray): ChargingStatusEnum {
        log(TAG, "Charging outside activity ${data.contentToString()}")
        
        val status = when (data[1]) {
            CHARGING_FLAG -> ChargingStatusEnum.CHARGING
            NOT_CHARGING_FLAG -> ChargingStatusEnum.NOT_CHARGING
            else -> ChargingStatusEnum.NOT_CHARGING // Default to not charging
        }
        
        log(TAG, "Charging status: $status")
        return status
    }
}