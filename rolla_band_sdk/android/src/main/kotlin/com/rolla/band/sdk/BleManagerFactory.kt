package com.rolla.band.sdk

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import app.rolla.bluetoothSdk.BleManager
import app.rolla.bluetoothSdk.connect.BleConnector
import app.rolla.bluetoothSdk.connect.OperationQueue
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.scan.BleScanner
import app.rolla.bluetoothSdk.scan.manager.ScanManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

object BleManagerFactory {
    fun create(context: Context): BleManager {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("BleManagerFactory", "BLE Coroutine Exception: ${throwable.message}", throwable)
        }
        val bleScopeContext: CoroutineContext = SupervisorJob() + Dispatchers.IO + exceptionHandler

        val deviceManager = DeviceManager(bleScopeContext)
        val operationQueue = OperationQueue(context)
        val bleConnector = BleConnector(context, deviceManager, operationQueue, bleScopeContext)

        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        val scanManager = bluetoothLeScanner?.let { 
            ScanManager(it, Dispatchers.IO, bleScopeContext)
        }
        val bleScanner = scanManager?.let { BleScanner(it, deviceManager, bleScopeContext) }

        return BleManager(context, bluetoothAdapter, bleScanner, bleConnector, bleScopeContext)
    }
}

