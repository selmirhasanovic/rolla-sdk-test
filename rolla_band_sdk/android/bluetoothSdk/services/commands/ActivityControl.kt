package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.ActivityMode
import app.rolla.bluetoothSdk.services.commands.data.ActivityType
import app.rolla.bluetoothSdk.services.commands.data.ActivityControlInfo
import app.rolla.bluetoothSdk.utils.extensions.log

class ActivityControl : Command<ActivityControlInfo> {

    companion object {
        const val TAG = "ActivityControl"
        private const val START_MODE_BYTE = 0x01.toByte()
        private const val STOP_MODE_BYTE = 0x04.toByte()
        private const val SUCCESS_RESPONSE = 0x01.toByte()
    }

    override fun getId(): Byte = 0x19.toByte()

    private var activityType = ActivityType.RUN
    private var activityMode = ActivityMode.START

    override fun bytesToWrite(): ByteArray = createCommandBytes {
        this[1] = getActivityModeBytes()
        this[2] = activityType.ordinal.toByte()
        this[4] = 0x00.toByte()
    }

    fun setActivityType(type: ActivityType) {
        activityType = type
    }

    fun setActivityMode(mode: ActivityMode) {
        activityMode = mode
    }

    private fun getActivityModeBytes(): Byte {
        return when(activityMode) {
            ActivityMode.START -> START_MODE_BYTE
            ActivityMode.STOP -> STOP_MODE_BYTE
        }
    }

    override fun read(data: ByteArray): ActivityControlInfo {
        log(TAG, "Activity control response: ${data.contentToString()}")
        
        return when (data[1]) {
            SUCCESS_RESPONSE -> {
                log(TAG, "Activity command executed successfully")
                ActivityControlInfo(success = true)
            }
            else -> {
                log(TAG, "Activity command failed")
                ActivityControlInfo(
                    success = false,
                    error = "Blood pressure test or activity is already in progress"
                )
            }
        }
    }
}