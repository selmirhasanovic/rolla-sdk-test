package app.rolla.bluetoothSdk.services.impl

import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.services.CharacteristicImpls
import app.rolla.bluetoothSdk.services.data.FirmwareRevisionData
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FirmwareRevisionCharacteristicImpl : CharacteristicImpl {

    private val _dataFlow = MutableSharedFlow<FirmwareRevisionData>(replay = 1)

    val firmwareRevisionDataFlow: SharedFlow<FirmwareRevisionData> = _dataFlow.asSharedFlow()

    // Add coroutine scope
    override val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onRead(byteArray: ByteArray, device: BleDevice) {
        processFirmwareRevisionData(byteArray, device)
    }

    private fun processFirmwareRevisionData(byteArray: ByteArray, device: BleDevice) {
        if (byteArray.isEmpty()) {
            throw IllegalArgumentException("Firmware revision data is empty")
        }

        try {
            val firmwareRevision = String(byteArray, Charsets.UTF_8).trim()
            log(
                "FirmwareRevisionCharacteristic",
                "Firmware Revision: $firmwareRevision"
            )
            CharacteristicImpls.rollaBand.setFirmwareVersion(firmwareRevision)
            scope.launch {
                _dataFlow.emit(FirmwareRevisionData(device.getMacAddress(), firmwareRevision))
            }
        } catch (e: Exception) {
            scope.launch {
                _dataFlow.emit(FirmwareRevisionData(device.getMacAddress(), "", "Invalid firmware revision data encoding"))
            }
        }
    }

}