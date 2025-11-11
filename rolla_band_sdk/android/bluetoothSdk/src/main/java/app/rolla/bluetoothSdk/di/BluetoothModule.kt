package app.rolla.bluetoothSdk.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    @Singleton
    fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager? {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    @Provides
    @Singleton
    fun provideBluetoothAdapter(bluetoothManager: BluetoothManager?): BluetoothAdapter? {
        return bluetoothManager?.adapter
    }

    @Provides
    @Singleton
    fun provideBluetoothLeScanner(bluetoothAdapter: BluetoothAdapter?): BluetoothLeScanner? {
        return bluetoothAdapter?.bluetoothLeScanner
    }
}