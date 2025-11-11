package app.rolla.bluetoothSdk.connect

import app.rolla.bluetoothSdk.device.BleDevice
import java.util.UUID

sealed class CharacteristicEvent {
    data class ServicesDiscovered(val device: BleDevice, val services: List<UUID>) : CharacteristicEvent()
    data class CharacteristicSubscribed(val device: BleDevice, val characteristicUuid: UUID) : CharacteristicEvent()
    data class CharacteristicUnsubscribed(val device: BleDevice, val characteristicUuid: UUID) : CharacteristicEvent()
    data class AllSubscriptionsComplete(val deviceAddress: String) : CharacteristicEvent()
    data class AllPostSubscriptionCommandsComplete(val deviceAddress: String) : CharacteristicEvent()

    data class DeviceFullyConnected(val deviceAddress: String) : CharacteristicEvent()
    data class AllUnsubscriptionsComplete(val deviceAddress: String) : CharacteristicEvent()
    data class CharacteristicChanged(val device: BleDevice, val characteristicUuid: UUID, val value: ByteArray) : CharacteristicEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CharacteristicChanged
            if (device != other.device) return false
            if (characteristicUuid != other.characteristicUuid) return false
            if (!value.contentEquals(other.value)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = device.hashCode()
            result = 31 * result + characteristicUuid.hashCode()
            result = 31 * result + value.contentHashCode()
            return result
        }
    }
    data class CharacteristicRead(val device: BleDevice, val characteristicUuid: UUID, val value: ByteArray) : CharacteristicEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CharacteristicRead
            if (device != other.device) return false
            if (characteristicUuid != other.characteristicUuid) return false
            if (!value.contentEquals(other.value)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = device.hashCode()
            result = 31 * result + characteristicUuid.hashCode()
            result = 31 * result + value.contentHashCode()
            return result
        }
    }
    data class CharacteristicWrite(val device: BleDevice, val characteristicUuid: UUID) : CharacteristicEvent()
    data class Error(val deviceAddress: String, val errorMessage: String) : CharacteristicEvent()
}