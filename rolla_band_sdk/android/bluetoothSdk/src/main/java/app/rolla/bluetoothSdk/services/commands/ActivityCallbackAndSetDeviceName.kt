package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.ActivityInfo
import app.rolla.bluetoothSdk.services.commands.data.ActivityMode
import app.rolla.bluetoothSdk.services.commands.data.WristDetectionStatus
import app.rolla.bluetoothSdk.utils.extensions.log

class ActivityCallbackAndSetDeviceName : Command<ActivityInfo> {

    companion object {
        const val TAG = "ActivityCallbackAndSetDeviceName"
        private const val ACTIVITY_CONTROL_FLAG = 0x06.toByte()
        private const val WRIST_DETECTION_FLAG = 0x07.toByte()
        private const val ACTIVITY_FINISHED = 0x00.toByte()
        private const val ACTIVITY_STARTED = 0x01.toByte()
        private const val ACTIVITY_PAUSED = 0x02.toByte()
        private const val ACTIVITY_CONTINUED = 0x03.toByte()
        private const val WRIST_WEAR = 0x01.toByte()
    }

    override fun getId(): Byte = 0x16.toByte()

    override fun bytesToWrite(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun read(data: ByteArray): ActivityInfo {
        val activityInfo = ActivityInfo()
        
        when (data[1]) {
            ACTIVITY_CONTROL_FLAG -> handleActivityControl(data, activityInfo)
            WRIST_DETECTION_FLAG -> handleWristDetection(data, activityInfo)
        }
        
        return activityInfo
    }

    private fun handleActivityControl(data: ByteArray, activityInfo: ActivityInfo) {
        when (data[2]) {
            ACTIVITY_FINISHED -> {
                log(TAG, "Activity finished")
                activityInfo.activityMode = ActivityMode.STOP
            }
            ACTIVITY_STARTED -> {
                log(TAG, "Activity started")
                activityInfo.activityMode = ActivityMode.START
            }
            ACTIVITY_PAUSED -> {
                log(TAG, "Activity paused")
            }
            ACTIVITY_CONTINUED -> {
                log(TAG, "Activity continued")
            }
        }
    }

    private fun handleWristDetection(data: ByteArray, activityInfo: ActivityInfo) {
        log(TAG, "Wrist detection ${data[2]}")
        activityInfo.wristDetectionStatus = if (data[2] == WRIST_WEAR) {
            WristDetectionStatus.WEAR
        } else {
            WristDetectionStatus.NOT_WEAR
        }
    }
}