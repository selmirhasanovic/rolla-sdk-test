package app.rolla.bluetoothSdk.services.data

data class FirmwareRevisionData(
    val uuid: String,
    val firmwareVersion: String,
    val error: String? = null
) {
    override fun toString(): String {
        return "FirmwareRevision(uuid=$uuid, firmwareVersion=$firmwareVersion, error=$error)"
    }
}