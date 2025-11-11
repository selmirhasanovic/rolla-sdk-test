package app.rolla.bluetoothSdk.device

import app.rolla.bluetoothSdk.exceptions.BleException

class DeviceNotFoundException : BleException("Device not found") {
}