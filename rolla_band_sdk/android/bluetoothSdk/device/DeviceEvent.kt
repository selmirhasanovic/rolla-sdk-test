package app.rolla.bluetoothSdk.device

sealed class DeviceEvent {
    data class RssiReadRequested(val deviceAddress: String) : DeviceEvent()
    data class DeviceUnresponsive(val deviceAddress: String) : DeviceEvent()
}