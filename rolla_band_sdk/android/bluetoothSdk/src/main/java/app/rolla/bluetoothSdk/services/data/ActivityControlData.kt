package app.rolla.bluetoothSdk.services.data

import app.rolla.bluetoothSdk.services.commands.data.ActivityStatus

data class ActivityControlData(
    val uuid: String,
    val success: Boolean,
    val activityStatus: ActivityStatus,
    val error: String? = null
) {
    override fun toString(): String {
        return "ActivityControlData(uuid=$uuid, success=$success, error=$error)"
    }
}