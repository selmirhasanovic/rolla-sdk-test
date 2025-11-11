package app.rolla.bluetoothSdk.connect

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

sealed class BleOperation() {
    abstract val deviceAddress: String
    abstract val onComplete: (Boolean, Int?) -> Unit

    data class ReadCharacteristic(
        override val deviceAddress: String,
        val gatt: BluetoothGatt,
        val characteristic: BluetoothGattCharacteristic,
        override val onComplete: (Boolean, Int?) -> Unit
    ) : BleOperation()

    data class WriteCharacteristic(
        override val deviceAddress: String,
        val gatt: BluetoothGatt,
        val characteristic: BluetoothGattCharacteristic,
        val data: ByteArray,
        override val onComplete: (Boolean, Int?) -> Unit
    ) : BleOperation() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as WriteCharacteristic

            if (deviceAddress != other.deviceAddress) return false
            if (gatt != other.gatt) return false
            if (characteristic != other.characteristic) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = deviceAddress.hashCode()
            result = 31 * result + gatt.hashCode()
            result = 31 * result + characteristic.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    data class WriteDescriptor(
        override val deviceAddress: String,
        val gatt: BluetoothGatt,
        val descriptor: BluetoothGattDescriptor,
        val value: ByteArray = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
        override val onComplete: (Boolean, Int?) -> Unit
    ) : BleOperation()

    data class ReadRssi(
        override val deviceAddress: String,
        val gatt: BluetoothGatt,
        override val onComplete: (Boolean, Int?) -> Unit
    ) : BleOperation()
}