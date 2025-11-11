package app.rolla.bluetoothSdk.services.data

data class SerialNumber(
    val uuid: String,
    val serialNumber: String,
    val error: String? = null
) {
    override fun toString(): String {
        return "SerialNumber(uuid=$uuid, serialNumber=$serialNumber, error=$error)"
    }
}