package app.rolla.bluetoothSdk.di

import android.bluetooth.BluetoothAdapter
import android.content.Context
import app.rolla.bluetoothSdk.BleManager
import app.rolla.bluetoothSdk.connect.BleConnector
import app.rolla.bluetoothSdk.connect.OperationQueue
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.scan.BleScanner
import app.rolla.bluetoothSdk.scan.manager.ScanManager
import kotlin.coroutines.CoroutineContext

@Module
@InstallIn(SingletonComponent::class)
object BleSdkModule {

    @Provides
    
    fun provideDeviceManager(
        bleScopeContext: CoroutineContext
    ): DeviceManager = DeviceManager(bleScopeContext)

    @Provides
    
    fun provideBleOperationQueue(
        context: Context
    ): OperationQueue = OperationQueue(context)

    @Provides
    
    fun provideBleConnectManager(
        context: Context,
        deviceManager: DeviceManager,
        operationQueue: OperationQueue,
        bleScopeContext: CoroutineContext
    ): BleConnector = BleConnector(context, deviceManager, operationQueue, bleScopeContext)

    @Provides
    
    fun provideBleScanner(
        scanManager: ScanManager?,
        deviceManager: DeviceManager, // Inject DeviceManager
        bleScopeContext: CoroutineContext
    ): BleScanner? = scanManager?.let { 
        BleScanner(it, deviceManager,  bleScopeContext)
    }

    @Provides
    
    fun provideBleManager(
        context: Context,
        bluetoothAdapter: BluetoothAdapter?,
        bleScanner: BleScanner?,
        bleConnector: BleConnector,
        bleScopeContext: CoroutineContext
    ): BleManager =
        BleManager(context, bluetoothAdapter, bleScanner, bleConnector,  bleScopeContext)
}