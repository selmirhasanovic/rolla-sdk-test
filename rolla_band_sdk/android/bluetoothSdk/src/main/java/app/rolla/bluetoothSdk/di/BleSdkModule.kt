package app.rolla.bluetoothSdk.di

import android.bluetooth.BluetoothAdapter
import android.content.Context
import app.rolla.bluetoothSdk.BleManager
import app.rolla.bluetoothSdk.connect.BleConnector
import app.rolla.bluetoothSdk.connect.OperationQueue
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.scan.BleScanner
import app.rolla.bluetoothSdk.scan.manager.ScanManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Module
@InstallIn(SingletonComponent::class)
object BleSdkModule {

    @Provides
    @Singleton
    fun provideDeviceManager(
        @BleScopeContext bleScopeContext: CoroutineContext
    ): DeviceManager = DeviceManager(bleScopeContext)

    @Provides
    @Singleton
    fun provideBleOperationQueue(
        @ApplicationContext context: Context
    ): OperationQueue = OperationQueue(context)

    @Provides
    @Singleton
    fun provideBleConnectManager(
        @ApplicationContext context: Context,
        deviceManager: DeviceManager,
        operationQueue: OperationQueue,
        @BleScopeContext bleScopeContext: CoroutineContext
    ): BleConnector = BleConnector(context, deviceManager, operationQueue, bleScopeContext)

    @Provides
    @Singleton
    fun provideBleScanner(
        scanManager: ScanManager?,
        deviceManager: DeviceManager, // Inject DeviceManager
        @BleScopeContext bleScopeContext: CoroutineContext
    ): BleScanner? = scanManager?.let { 
        BleScanner(it, deviceManager,  bleScopeContext)
    }

    @Provides
    @Singleton
    fun provideBleManager(
        @ApplicationContext context: Context,
        bluetoothAdapter: BluetoothAdapter?,
        bleScanner: BleScanner?,
        bleConnector: BleConnector,
        @BleScopeContext bleScopeContext: CoroutineContext
    ): BleManager =
        BleManager(context, bluetoothAdapter, bleScanner, bleConnector,  bleScopeContext)
}