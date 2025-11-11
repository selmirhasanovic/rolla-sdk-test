package app.rolla.bluetoothSdk.connect

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.utils.extensions.log

/**
 * Handles GATT callbacks for BLE connections
 */
class GattCallbackHandler(
    private val onCallback: (BluetoothGatt?, GattCallbackType) -> Unit
) {
    
    companion object {
        private const val TAG = "BleGattCallbackHandler"
    }

    fun createGattCallback(bleDevice: BleDevice): BluetoothGattCallback {
        val deviceAddress = bleDevice.btDevice.address
        
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        log(TAG, "Device connected: $deviceAddress")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        log(TAG, "Device disconnected: $deviceAddress")
                    }
                }
                
                gatt?.let { 
                    onCallback(it, GattCallbackType.ConnectionStateChanged(status, newState))
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log(TAG, "Services discovered for: $deviceAddress")
                } else {
                    log(TAG, "Service discovery failed for $deviceAddress with status: $status")
                }
                
                gatt?.let { 
                    onCallback(it, GattCallbackType.ServicesDiscovered(status))
                }
            }
            
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                super.onCharacteristicChanged(gatt, characteristic, value)
                log(TAG, "Characteristic changed (API 33+)")
                onCallback(gatt, GattCallbackType.CharacteristicChanged(characteristic, value))
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                // Only call super if we're on older API to avoid double calls
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                    super.onCharacteristicChanged(gatt, characteristic)
                    //log(TAG, "Characteristic changed (legacy)")
                    val value = characteristic.value ?: ByteArray(0)
                    onCallback(gatt, GattCallbackType.CharacteristicChanged(characteristic, value))
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                super.onDescriptorWrite(gatt, descriptor, status)
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log(TAG, "Descriptor write successful for ${descriptor.characteristic.uuid}")
                } else {
                    log(TAG, "Descriptor write failed for ${descriptor.characteristic.uuid} with status: $status")
                }
                
                onCallback(gatt, GattCallbackType.DescriptorWrite(descriptor, status))
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, value, status)
                onCallback(gatt, GattCallbackType.CharacteristicRead(characteristic, value, status))
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                // Only call super if we're on older API to avoid double calls
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                    super.onCharacteristicRead(gatt, characteristic, status)
                    // Use deprecated getValue() for older API levels
                    val value = characteristic.value ?: ByteArray(0)
                    onCallback(gatt, GattCallbackType.CharacteristicRead(characteristic, value, status))
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                onCallback(gatt, GattCallbackType.CharacteristicWrite(characteristic, status))
            }

            override fun onServiceChanged(gatt: BluetoothGatt) {
                super.onServiceChanged(gatt)
                log(TAG, "Service changed for device: ${gatt.device.address}")
                onCallback(gatt, GattCallbackType.ServiceChanged)
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
                super.onReadRemoteRssi(gatt, rssi, status)
                onCallback(gatt, GattCallbackType.ReadRemoteRssi(rssi, status))
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
            }

            override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
                super.onPhyUpdate(gatt, txPhy, rxPhy, status)
            }

        }
    }
}