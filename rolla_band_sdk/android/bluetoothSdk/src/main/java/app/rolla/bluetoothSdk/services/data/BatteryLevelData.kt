package app.rolla.bluetoothSdk.services.data

data class BatteryLevelData (
    val uuid: String,
    val batteryLevel: Int,
    val error: String? = null
) {
    override fun toString(): String {
        return "BatteryLevel(uuid=$uuid, batteryLevel=$batteryLevel, error=$error)"
    }
}