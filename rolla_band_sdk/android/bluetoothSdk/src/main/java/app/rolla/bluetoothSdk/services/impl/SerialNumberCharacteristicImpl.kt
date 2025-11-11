package app.rolla.bluetoothSdk.services.impl

import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.services.data.SerialNumberData
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SerialNumberCharacteristicImpl() : CharacteristicImpl {

    private val _dataFlow = MutableSharedFlow<SerialNumberData>(replay = 1)
    val serialNumberDataFlow: SharedFlow<SerialNumberData> = _dataFlow.asSharedFlow()

    // Add coroutine scope
    override val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onRead(byteArray: ByteArray, device: BleDevice) {
        processSerialNumberData(byteArray, device)
    }

    private fun processSerialNumberData(byteArray: ByteArray, device: BleDevice) {
        if (byteArray.isEmpty()) {
            throw IllegalArgumentException("Serial number data is empty")
        }

        try {
            val serialNumber = String(byteArray, Charsets.UTF_8).trim()
            log(
                "SerialNumberCharacteristic",
                "Serial Number: $serialNumber"
            )
            scope.launch {
                _dataFlow.emit(SerialNumberData(device.getMacAddress(), serialNumber))
            }
        } catch (e: Exception) {
            scope.launch {
                _dataFlow.emit(SerialNumberData(device.getMacAddress(), "", "Invalid serial number data encoding"))
            }
            throw IllegalArgumentException("Invalid serial number data encoding")
        }
    }

}
