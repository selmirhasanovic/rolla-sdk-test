package app.rolla.bluetoothSdk.connect

import android.bluetooth.BluetoothGattCharacteristic
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.services.BleCharacteristic
import app.rolla.bluetoothSdk.services.Characteristic
import app.rolla.bluetoothSdk.services.commands.Command
import app.rolla.bluetoothSdk.utils.extensions.log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PostSubscriptionManager(
    private val operationQueue: OperationQueue,
    private val onError: (ConnectionError) -> Unit,
    private val onEvent: (CharacteristicEvent) -> Unit
) {

    companion object {
        const val TAG = "PostSubscriptionManager"
    }

    private val pendingPostCommands = ConcurrentHashMap<String, AtomicInteger>()
    private val operationList = mutableListOf<BleOperation>()

    fun addCommands(device: BleDevice, gattCharacteristic: BluetoothGattCharacteristic, characteristic: Characteristic) {
        val commands = if ( characteristic.uuid == BleCharacteristic.ROLLA_BAND_WRITE.uuid) {
            characteristic.impl.getPostSubscriptionCommands()
        } else {
            emptyList()
        }
        if (commands.isNotEmpty()) {
            for (command in commands) {
                val deviceAddress = device.getMacAddress()
                pendingPostCommands.computeIfAbsent(deviceAddress) { AtomicInteger(0) }
                    .incrementAndGet()

                val pendingCount = pendingPostCommands[deviceAddress]?.get() ?: 0
                log(TAG, "pending post command count $deviceAddress: $pendingCount")


                log(TAG, "Queueing post-subscription command for ${characteristic.uuid} - ${command.getId()}")

                val operation = BleOperation.WriteCharacteristic(
                    deviceAddress = device.getMacAddress(),
                    gatt = device.gatt!!,
                    characteristic = gattCharacteristic,
                    data = command.bytesToWrite(),
                    onComplete = { success, status ->
                        handlePostCommandComplete(device, command, success, status)
                    }
                )
                operationList.add(operation)
            }
        }
    }

    fun processPostSubscriptionCommands() {
        for (operation in operationList) {
            operationQueue.queueOperation(operation)
        }
    }

    fun cleanup() {
        pendingPostCommands.clear()
    }

    fun cleanForDevice(deviceAddress: String) {
        pendingPostCommands.remove(deviceAddress)
    }

    private fun handlePostCommandComplete(
        device: BleDevice,
        command: Command<*>,
        success: Boolean,
        status: Int?
    ) {
        val deviceAddress = device.getMacAddress()
        val commandName = command::class.simpleName

        if (success) {
            log(TAG, "✅ Post-subscription command completed: $commandName")
        } else {
            log(TAG, "❌ Post-subscription command failed: $commandName (status: $status)")
            onError(
                ConnectionError(
                    device = device,
                    errorCode = ConnectionError.ERROR_POST_SUBSCRIPTION_COMMAND_FAILED,
                    operation = Operation.WRITE_CHARACTERISTIC,
                    message = "Post-subscription command failed: $commandName"
                )
            )
        }

        // Decrement pending commands counter
        val pendingCount = pendingPostCommands[deviceAddress]?.decrementAndGet() ?: 0
        log(TAG, "Remaining post-subscription commands for $deviceAddress: $pendingCount")

        // Check if all post-subscription commands are complete
        if (pendingCount <= 0) {
            pendingPostCommands.remove(deviceAddress)
            log(TAG, "All post-subscription commands completed for $deviceAddress")
            onEvent(CharacteristicEvent.AllPostSubscriptionCommandsComplete(deviceAddress))
        }
    }

    fun hasPendingPostCommands(deviceAddress: String): Boolean {
        return (pendingPostCommands[deviceAddress]?.get() ?: 0) > 0
    }
}