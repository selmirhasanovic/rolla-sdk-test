package com.rolla.band.sdk

import android.util.Log
import com.rolla.band.sdk.generated.RollaBluetoothFlutterApi
import com.rolla.band.sdk.hostImpl.BandCommandHostApiImpl
import com.rolla.band.sdk.hostImpl.RollaBandHealthDataHostApiImpl
import com.rolla.band.sdk.hostImpl.RollaBluetoothHostApiImpl
import com.rolla.band.sdk.generated.RollaBluetoothHostApi
import com.rolla.band.sdk.generated.BandCommandHostAPI
import com.rolla.band.sdk.generated.RollaBandHealthDataHostApi
import io.flutter.plugin.common.BinaryMessenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class RollaBandSdkBridge(
    binaryMessenger: BinaryMessenger
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val flutterApi = RollaBluetoothFlutterApi(binaryMessenger)

    private val bluetoothHostApiImpl = RollaBluetoothHostApiImpl(scope)
    private val commandHostApiImpl = BandCommandHostApiImpl(scope)
    private val healthDataHostApiImpl = RollaBandHealthDataHostApiImpl(scope)

    init {
        RollaBluetoothHostApi.setUp(binaryMessenger, bluetoothHostApiImpl)
        BandCommandHostAPI.setUp(binaryMessenger, commandHostApiImpl)
        RollaBandHealthDataHostApi.setUp(binaryMessenger, healthDataHostApiImpl)
        Log.d("RollaBandSdkBridge", "HostApi implementations registered")
    }

    fun cleanup() {
        bluetoothHostApiImpl.cleanup()
        commandHostApiImpl.cleanup()
        healthDataHostApiImpl.cleanup()
        scope.cancel()
    }
}

