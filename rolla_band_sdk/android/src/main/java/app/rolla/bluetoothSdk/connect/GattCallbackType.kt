package app.rolla.bluetoothSdk.connect

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

/**
 * Sealed class for different callback types
 */
sealed class GattCallbackType {

    data class ConnectionStateChanged(val status: Int, val newState: Int) : GattCallbackType()
    data class ServicesDiscovered(val status: Int) : GattCallbackType()
    data class CharacteristicChanged(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray
    ) : GattCallbackType() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CharacteristicChanged
            if (characteristic != other.characteristic) return false
            if (!value.contentEquals(other.value)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = characteristic.hashCode()
            result = 31 * result + value.contentHashCode()
            return result
        }
    }

    data class CharacteristicRead(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
        val status: Int
    ) : GattCallbackType() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CharacteristicRead
            if (characteristic != other.characteristic) return false
            if (!value.contentEquals(other.value)) return false
            if (status != other.status) return false
            return true
        }

        override fun hashCode(): Int {
            var result = characteristic.hashCode()
            result = 31 * result + value.contentHashCode()
            result = 31 * result + status
            return result
        }
    }

    data class CharacteristicWrite(
        val characteristic: BluetoothGattCharacteristic,
        val status: Int
    ) : GattCallbackType()

    data class DescriptorWrite(
        val descriptor: BluetoothGattDescriptor,
        val status: Int
    ) : GattCallbackType()

    object ServiceChanged : GattCallbackType()

    data class ReadRemoteRssi(val rssi: Int, val status: Int) : GattCallbackType()

    data class UpdatePhy(val txPhy: Int, val rxPhy: Int, val status: Int) : GattCallbackType()

    data class MtuChanged(val mtu: Int) : GattCallbackType()
}