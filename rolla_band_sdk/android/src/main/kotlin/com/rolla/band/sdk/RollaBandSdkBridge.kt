package com.rolla.band.sdk

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.util.Log
import app.rolla.bluetoothSdk.BleManager
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceConnectionState
import app.rolla.bluetoothSdk.utils.extensions.log
import com.rolla.band.sdk.generated.BandBatteryFlutterApi
import com.rolla.band.sdk.generated.BandCommandHostAPI
import com.rolla.band.sdk.generated.BluetoothCapabilities
import com.rolla.band.sdk.generated.BluetoothDevice
import com.rolla.band.sdk.generated.BluetoothState
import com.rolla.band.sdk.generated.ConnectionState
import com.rolla.band.sdk.generated.DeviceType
import com.rolla.band.sdk.generated.RollaBandHealthDataHostApi
import com.rolla.band.sdk.generated.RollaBluetoothFlutterApi
import com.rolla.band.sdk.generated.RollaBluetoothHostApi
import com.rolla.band.sdk.hostImpl.BandCommandHostApiImpl
import com.rolla.band.sdk.hostImpl.RollaBandHealthDataHostApiImpl
import com.rolla.band.sdk.hostImpl.RollaBluetoothHostApiImpl
import io.flutter.plugin.common.BinaryMessenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RollaBandSdkBridge(
    private val bleManager: BleManager,
    binaryMessenger: BinaryMessenger
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flutterApi: RollaBluetoothFlutterApi = RollaBluetoothFlutterApi(binaryMessenger)
    private var batteryApi: BandBatteryFlutterApi = BandBatteryFlutterApi(binaryMessenger)

    private var bluetoothHostApiImpl: RollaBluetoothHostApiImpl =
        RollaBluetoothHostApiImpl(bleManager, scope)

    private var commandHostApiImpl: BandCommandHostApiImpl =
        BandCommandHostApiImpl(bleManager, scope, batteryApi)

    private var healthDataHostApiImpl: RollaBandHealthDataHostApiImpl = RollaBandHealthDataHostApiImpl(bleManager, scope)

    init {
        RollaBluetoothHostApi.setUp(binaryMessenger, bluetoothHostApiImpl)
        BandCommandHostAPI.setUp(binaryMessenger, commandHostApiImpl)
        RollaBandHealthDataHostApi.setUp(binaryMessenger, healthDataHostApiImpl)
        log("RollaBandSdkBridge", "Pigeon APIs set up")

        scope.launch {
            bleManager.getAllDevicesFlow()?.collect { devices ->
                val pigeonDevices = devices.map { convertToPigeonDevice(it) }
                withContext(Dispatchers.Main) {
                    flutterApi.onDevicesFound(pigeonDevices) { }
                }
            }
        }

        scope.launch {
            bleManager.connectionStateChanges?.collect { event ->
                withContext(Dispatchers.Main) {
                    when (event.second) {
                        DeviceConnectionState.CONNECTED -> {
                            flutterApi.onConnectionStateChanged(
                                event.first,
                                ConnectionState.CONNECTED
                            ) { }
                        }
                        DeviceConnectionState.DISCONNECTED -> {
                            flutterApi.onConnectionStateChanged(
                                event.first,
                                ConnectionState.DISCONNECTED
                            ) { }
                        }
                        DeviceConnectionState.CONNECTING -> {
                            flutterApi.onConnectionStateChanged(
                                event.first,
                                ConnectionState.CONNECTING
                            ) { }
                        }
                        DeviceConnectionState.DISCONNECTING -> {
                            // No-op
                        }
                    }
                }
            }
        }

        scope.launch {
            bleManager.bleStateFlow.collect { state ->
                withContext(Dispatchers.Main) {
                    val mapped = convertToPigeonBluetoothState(state)
                    flutterApi.onBluetoothStateChanged(mapped) { }
                    log("RollaBandSdkBridge", "BLE state changed to $state → $mapped")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun convertToPigeonDevice(sdkDevice: BleDevice): BluetoothDevice {
        return BluetoothDevice(
            name = sdkDevice.btDevice.name ?: "Unknown",
            rssi = sdkDevice.rssi.toLong(),
            uuid = sdkDevice.getMacAddress(),
            capabilities = sdkDevice.getSubscribedTypes().map { convertToPigeonCapability(it) },
            connectionState = if (sdkDevice.isConnected()) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED,
            deviceType = convertToPigeonDeviceType(sdkDevice.deviceTypes.firstOrNull())
        )
    }

    private fun convertToPigeonCapability(sdkType: app.rolla.bluetoothSdk.device.DeviceType): BluetoothCapabilities {
        return when (sdkType) {
            app.rolla.bluetoothSdk.device.DeviceType.RUNNING_SPEED_AND_CADENCE -> BluetoothCapabilities.RSC
            app.rolla.bluetoothSdk.device.DeviceType.HEART_RATE -> BluetoothCapabilities.HR
            else -> BluetoothCapabilities.HR
        }
    }

    private fun convertToPigeonDeviceType(sdkType: app.rolla.bluetoothSdk.device.DeviceType?): DeviceType {
        return when (sdkType) {
            app.rolla.bluetoothSdk.device.DeviceType.ROLLA_BAND -> DeviceType.ROLLA_BAND
            else -> DeviceType.OTHER
        }
    }

    private fun convertToPigeonBluetoothState(state: Int): BluetoothState {
        return when (state) {
            BluetoothAdapter.STATE_ON -> BluetoothState.POWERED_ON
            BluetoothAdapter.STATE_OFF -> BluetoothState.POWERED_OFF
            BluetoothAdapter.STATE_TURNING_ON -> BluetoothState.TURNING_ON
            BluetoothAdapter.STATE_TURNING_OFF -> BluetoothState.TURNING_OFF
            else -> BluetoothState.UNKNOWN
        }
    }

    fun cleanUp() {
        scope.cancel()
        bluetoothHostApiImpl.cleanup()
        commandHostApiImpl.cleanup()
        healthDataHostApiImpl.cleanup()
    }
}

