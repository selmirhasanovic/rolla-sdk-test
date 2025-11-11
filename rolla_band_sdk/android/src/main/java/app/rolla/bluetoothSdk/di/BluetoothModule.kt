package app.rolla.bluetoothSdk.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    
    fun provideBluetoothManager(context: Context): BluetoothManager? {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    @Provides
    
    fun provideBluetoothAdapter(bluetoothManager: BluetoothManager?): BluetoothAdapter? {
        return bluetoothManager?.adapter
    }

    @Provides
    
    fun provideBluetoothLeScanner(bluetoothAdapter: BluetoothAdapter?): BluetoothLeScanner? {
        return bluetoothAdapter?.bluetoothLeScanner
    }
}