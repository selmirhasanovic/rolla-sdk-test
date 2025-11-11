package app.rolla.bluetoothSdk

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.rolla.bluetoothSdk.device.DeviceType
import app.rolla.bluetoothSdk.exceptions.*
import app.rolla.bluetoothSdk.scan.BleScanner
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.exceptions.BleAdapterNullException
import app.rolla.bluetoothSdk.scan.manager.ScanError
import app.rolla.bluetoothSdk.scan.manager.ScanManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class BleManagerTest {

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var bluetoothAdapter: BluetoothAdapter

    @MockK
    private lateinit var bleScanner: BleScanner

    @MockK
    private lateinit var packageManager: PackageManager

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var bleManager: BleManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { context.packageManager } returns packageManager
        // Mock Android log calls
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        bleManager = BleManager(
            context = context,
            bluetoothAdapter = bluetoothAdapter,
            bleScanner = bleScanner,
            ioDispatcher = testDispatcher,
            coroutineContext = testScope.coroutineContext
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun isBluetoothLESupportedReturnsTrueWhenBLEIsSupported() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns true
        assertTrue(bleManager.isBluetoothLESupported())
    }

    @Test
    fun isBluetoothLESupportedReturnsFalseWhenBLEIsNotSupported() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns false
        assertFalse(bleManager.isBluetoothLESupported())
    }

    @Test
    fun isBluetoothEnabledReturnsTrueWhenBluetoothIsEnabled() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns true
        every { bluetoothAdapter.isEnabled } returns true
        assertTrue(bleManager.isBluetoothEnabled())
    }

    @Test
    fun isBluetoothEnabledReturnsFalseWhenBluetoothIsNotEnabled() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns true
        every { bluetoothAdapter.isEnabled } returns false
        assertFalse(bleManager.isBluetoothEnabled())
    }

    @Test
    fun isBluetoothEnabledThrowsWhenBLEIsNotSupported() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns false
        assertFailsWith<BleNotSupportedException> { bleManager.isBluetoothEnabled() }
    }

    @Test
    fun isBluetoothEnabledThrowsWhenAdapterIsNull() {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns true
        val bleManagerWithNullAdapter = BleManager(
            context = context,
            bluetoothAdapter = null,
            bleScanner = bleScanner,
            ioDispatcher = testDispatcher,
            coroutineContext = testScope.coroutineContext
        )

        assertFailsWith<BleAdapterNullException> { bleManagerWithNullAdapter.isBluetoothEnabled() }
    }

    @Test
    fun startScanThrowsWhenBLEIsNotSupported() = runTest {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns false

        assertFailsWith<BleNotSupportedException> {
            bleManager.startScan(setOf(DeviceType.HEART_RATE))
        }
    }

    @Test
    fun startScanThrowsWhenBluetoothIsNotEnabled() = runTest {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns true
        every { bluetoothAdapter.isEnabled } returns false

        assertFailsWith<BleNotEnabledException> {
            bleManager.startScan(setOf(DeviceType.HEART_RATE))
        }
    }

    @Test
    fun startScanThrowsWhenScannerIsNotAvailable() = runTest {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns true
        every { bluetoothAdapter.isEnabled } returns true

        val bleManagerWithNullScanner = BleManager(
            context = context,
            bluetoothAdapter = bluetoothAdapter,
            bleScanner = null,
            ioDispatcher = testDispatcher,
            coroutineContext = testScope.coroutineContext
        )

        assertFailsWith<BleScannerNotAvailableException> {
            bleManagerWithNullScanner.startScan(setOf(DeviceType.HEART_RATE))
        }
    }

    @Test
    fun startScanForwardsCallToScannerWithDeviceTypes() = runTest {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns true
        every { bluetoothAdapter.isEnabled } returns true

        val deviceTypes = setOf(DeviceType.HEART_RATE, DeviceType.ROLLA_BAND)
        val expectedResult = mockk<MethodResult>()
        coEvery { bleScanner.startScan(deviceTypes) } returns expectedResult

        val result = bleManager.startScan(deviceTypes)

        assertEquals(expectedResult, result)
        coVerify(exactly = 1) { bleScanner.startScan(deviceTypes) }
    }

    @Test
    fun stopScanForwardsCallToScanner() = runTest {
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) } returns true
        every { bluetoothAdapter.isEnabled } returns true

        val expectedResult = mockk<MethodResult>()
        coEvery { bleScanner.stopScan() } returns expectedResult

        val result = bleManager.stopScan()

        assertEquals(expectedResult, result)
        coVerify(exactly = 1) { bleScanner.stopScan() }
    }

    @Test
    fun getAllScannedDevicesReturnsDevicesFromScanner() {
        val expectedDevices = listOf<BleDevice>(mockk(), mockk())
        every { bleScanner.getAllScannedDevices() } returns expectedDevices

        val result = bleManager.getAllScannedDevices()

        assertEquals(expectedDevices, result)
        verify(exactly = 1) { bleScanner.getAllScannedDevices() }
    }

    @Test
    fun getAllScannedDevicesThrowsWhenScannerIsNotAvailable() {
        val bleManagerWithNullScanner = BleManager(
            context = context,
            bluetoothAdapter = bluetoothAdapter,
            bleScanner = null,
            ioDispatcher = testDispatcher,
            coroutineContext = testScope.coroutineContext
        )

        assertFailsWith<BleScannerNotAvailableException> {
            bleManagerWithNullScanner.getAllScannedDevices()
        }
    }

    @Test
    fun getDevicesByTypeReturnsFilteredDevicesFromScanner() {
        val deviceType = DeviceType.RUNNING_SPEED_AND_CADENCE
        val expectedDevices = listOf<BleDevice>(mockk(), mockk())
        every { bleScanner.getDevicesByType(deviceType) } returns expectedDevices

        val result = bleManager.getDevicesByType(deviceType)

        assertEquals(expectedDevices, result)
        verify(exactly = 1) { bleScanner.getDevicesByType(deviceType) }
    }

    @Test
    fun setScanDurationForwardsCallToScanner() {
        val duration = 5000L
        every { bleScanner.setScanDuration(duration) } just runs

        bleManager.setScanDuration(duration)

        verify(exactly = 1) { bleScanner.setScanDuration(duration) }
    }

    @Test
    fun setScanDurationThrowsWhenScannerIsNotAvailable() {
        val bleManagerWithNullScanner = BleManager(
            context = context,
            bluetoothAdapter = bluetoothAdapter,
            bleScanner = null,
            ioDispatcher = testDispatcher,
            coroutineContext = testScope.coroutineContext
        )

        assertFailsWith<BleScannerNotAvailableException> {
            bleManagerWithNullScanner.setScanDuration(5000L)
        }
    }

    @Test
    fun clearScannedDevicesForwardsCallToScanner() = runTest {
        coEvery { bleScanner.clearDevices() } just runs

        bleManager.clearScannedDevices()

        // Need to advance time since clearDevices launches a coroutine
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { bleScanner.clearDevices() }
    }

    @Test
    fun clearScannedDevicesThrowsWhenScannerIsNotAvailable() {
        val bleManagerWithNullScanner = BleManager(
            context = context,
            bluetoothAdapter = bluetoothAdapter,
            bleScanner = null,
            ioDispatcher = testDispatcher,
            coroutineContext = testScope.coroutineContext
        )

        assertFailsWith<BleScannerNotAvailableException> {
            bleManagerWithNullScanner.clearScannedDevices()
        }
    }

    @Test
    fun scanStateFlowProperlyDelegatesToScannerProperty() {
        val stateFlow = MutableStateFlow(ScanManager.ScanState.Idle)
        every { bleScanner.scanStateFlow } returns stateFlow

        assertEquals(stateFlow, bleManager.scanStateFlow)
    }

    @Test
    fun scanErrorFlowProperlyDelegatesToScannerProperty() {
        // Create a SharedFlow for the scan error
        val errorSharedFlow = MutableSharedFlow<ScanError>()
        every { bleScanner.scanErrorFlow } returns errorSharedFlow

        // Assert that the manager's flow is the same instance
        assertEquals(errorSharedFlow, bleManager.scanErrorFlow)
    }

    @Test
    fun discoveredDevicesFlowProperlyDelegatesToScannerProperty() {
        val devicesFlow = MutableStateFlow<List<BleDevice>>(emptyList())
        every { bleScanner.discoveredDevicesFlow } returns devicesFlow

        assertEquals(devicesFlow, bleManager.discoveredDevicesFlow)
    }

    @Test
    fun destroyCallsScannerDestroyMethod() {
        every { bleScanner.destroy() } just runs

        bleManager.destroy()

        verify(exactly = 1) { bleScanner.destroy() }
    }

    @Test
    fun destroyHandlesExceptionsGracefully() {
        every { bleScanner.destroy() } throws RuntimeException("Test error")

        // Should not throw an exception
        bleManager.destroy()

        verify(exactly = 1) { bleScanner.destroy() }
    }

    @Test
    fun flowPropertiesReturnNullWhenScannerIsNull() {
        val bleManagerWithNullScanner = BleManager(
            context = context,
            bluetoothAdapter = bluetoothAdapter,
            bleScanner = null,
            ioDispatcher = testDispatcher,
            coroutineContext = testScope.coroutineContext
        )

        assertNull(bleManagerWithNullScanner.scanStateFlow)
        assertNull(bleManagerWithNullScanner.scanErrorFlow)
        assertNull(bleManagerWithNullScanner.discoveredDevicesFlow)
    }
}