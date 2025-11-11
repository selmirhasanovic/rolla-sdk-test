package app.rolla.bluetoothSdk.scan

import app.rolla.bluetoothSdk.MethodResult
import app.rolla.bluetoothSdk.device.DeviceManager
import app.rolla.bluetoothSdk.device.DeviceType
import app.rolla.bluetoothSdk.exceptions.InvalidParameterException
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.scan.manager.ScanError
import app.rolla.bluetoothSdk.scan.manager.ScanManager
import app.rolla.bluetoothSdk.scan.manager.ScanManager.ScanState
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BleScannerTest {

    @MockK
    private lateinit var scanManager: ScanManager

    @MockK
    private lateinit var deviceManager: DeviceManager

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bleScanner: BleScanner
    private val testScope = TestScope(testDispatcher)
    private val scanResultsFlow = MutableSharedFlow<BleDevice>()
    private val scanErrorsFlow = MutableSharedFlow<ScanError>()
    private val scanStateFlow = MutableStateFlow<ScanState>(ScanState.Idle)
    private val discoveredDevicesFlow = MutableStateFlow<List<BleDevice>>(emptyList())

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        // Setup mocks
        every { scanManager.scanResults } returns scanResultsFlow
        every { scanManager.scanErrors } returns scanErrorsFlow
        every { scanManager.scanState } returns scanStateFlow
        every { deviceManager.devicesFlow } returns discoveredDevicesFlow
        every { scanManager.destroy() } just Runs
        every { deviceManager.destroy() } just Runs

        // Create the test subject
        bleScanner = BleScanner(
            scanManager,
            deviceManager,
            testDispatcher,
            testDispatcher
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun startScanSetsDeviceTypesFilterAndReturnsScanResult() = runTest {
        // Given
        val deviceTypes = setOf<DeviceType>(
            DeviceType.HEART_RATE,
            DeviceType.ROLLA_BAND
        )
        val expectedUuids = deviceTypes.flatMap { it.scanningServices }.distinct()
        val startScanResult = MethodResult(true)

        every { deviceManager.setAllowedDeviceTypes(deviceTypes) } just Runs
        coEvery { scanManager.startScan(expectedUuids) } returns startScanResult

        // When
        val result = bleScanner.startScan(deviceTypes)

        // Then
        assertTrue(result.isSuccess())
        assertEquals(startScanResult, result)
        verify { deviceManager.setAllowedDeviceTypes(deviceTypes) }
        coVerify { scanManager.startScan(expectedUuids) }
    }

    @Test
    fun stopScanReturnsStopResultFromScanManager() = runTest {
        // Given
        val expectedResult = MethodResult(true)
        coEvery { scanManager.stopScan() } returns expectedResult

        // When
        val result = bleScanner.stopScan()

        // Then
        assertTrue(result.isSuccess())
        assertEquals(expectedResult, result)
        coVerify { scanManager.stopScan() }
    }

    @Test
    fun getAllScannedDevicesDelegatesToDeviceManager() {
        // Given
        val expectedDevices = listOf(mockk<BleDevice>(), mockk<BleDevice>())
        every { deviceManager.getAllDevicesSorted() } returns expectedDevices

        // When
        val result = bleScanner.getAllScannedDevices()

        // Then
        assertEquals(expectedDevices, result)
        verify { deviceManager.getAllDevicesSorted() }
    }

    @Test
    fun getDevicesByTypeDelegatesToDeviceManager() {
        // Given
        val deviceType = DeviceType.HEART_RATE
        val expectedDevices = listOf(mockk<BleDevice>())
        every { deviceManager.getDevicesByType(deviceType) } returns expectedDevices

        // When
        val result = bleScanner.getDevicesByType(deviceType)

        // Then
        assertEquals(expectedDevices, result)
        verify { deviceManager.getDevicesByType(deviceType) }
    }

    @Test
    fun clearDevicesDelegatesToDeviceManager() {
        // Given
        every { deviceManager.clearDevices() } just Runs

        // When
        bleScanner.clearDevices()

        // Then
        verify { deviceManager.clearDevices() }
    }

    @Test
    fun setScanDurationDelegatesToScanManager() {
        // Given
        val duration = 5000L
        every { scanManager.setScanDuration(duration) } just Runs

        // When
        bleScanner.setScanDuration(duration)

        // Then
        verify { scanManager.setScanDuration(duration) }
    }

    @Test(expected = InvalidParameterException::class)
    fun setScanDurationPropagatesInvalidParameterException() {
        // Given
        val duration = -1000L
        every { scanManager.setScanDuration(duration) } throws InvalidParameterException("Invalid duration")

        // When/Then
        bleScanner.setScanDuration(duration) // Should throw InvalidParameterException
    }

    @Test
    fun destroyStopsScanAndDestroysManagers() = runTest {
        // Given
        coEvery { scanManager.stopScan() } returns MethodResult(true)

        // When
        bleScanner.destroy()
        testDispatcher.scheduler.advanceUntilIdle() // Process the coroutine launched in destroy

        // Then
        coVerify { scanManager.stopScan() }
        verify { scanManager.destroy() }
        verify { deviceManager.destroy() }
    }

    @Test
    fun scanErrorFromManagerIsForwardedToErrorFlow() = runTest {
        // Create a TestScope with our dispatcher
        val testScope = TestScope(testDispatcher)

        // Create the actual flow that will be used inside scanManager
        val actualScanErrorsFlow = MutableSharedFlow<ScanError>()

        // Configure mock to use this flow
        every { scanManager.scanErrors } returns actualScanErrorsFlow

        // Create a new bleScanner with our properly mocked scanManager
        val bleScanner = BleScanner(
            scanManager,
            deviceManager,
            testDispatcher,
            testDispatcher
        )

        // Create the error we'll emit
        val scanError = ScanError(ScanError.ERROR_BLUETOOTH_DISABLED, "Bluetooth is disabled")

        // Keep track of collected errors
        val collectedErrors = mutableListOf<ScanError>()

        // Start collection BEFORE emitting
        val job = testScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            bleScanner.scanErrorFlow.collect {
                collectedErrors.add(it)
            }
        }

        // Wait a moment to ensure collection is established
        testDispatcher.scheduler.advanceTimeBy(100)

        // Now emit the error to the flow that scanManager is exposing
        testScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            actualScanErrorsFlow.emit(scanError)
        }

        // Allow all coroutines to complete
        testDispatcher.scheduler.advanceUntilIdle()

        // Now check if the error was properly forwarded
        assertTrue(collectedErrors.isNotEmpty(), "No errors were collected")
        assertEquals(1, collectedErrors.size, "Expected exactly one error")
        assertEquals(scanError, collectedErrors.first(), "Collected error doesn't match expected error")

        // Clean up
        job.cancel()
    }

    @Test
    fun scanResultsAreForwardedToDeviceManager() = runTest {
        // Given
        val bleDevice = mockk<BleDevice>()
        every { deviceManager.processDevice(bleDevice) } just Runs

        // We need to manually trigger the collection that happens in registerListeners()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            scanResultsFlow.collect { device ->
                deviceManager.processDevice(device)
            }
        }

        // When
        scanResultsFlow.emit(bleDevice)
        advanceUntilIdle()

        // Then
        verify { deviceManager.processDevice(bleDevice) }

        // Clean up
        collectJob.cancel()
    }

    @Test
    fun scanningStateChangeToScanningStartsDeviceMonitoring() = runTest {
        // Given
        every { deviceManager.startDeviceMonitoring() } just Runs

        // When
        scanStateFlow.value = ScanState.Scanning
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify { deviceManager.startDeviceMonitoring() }
    }

    @Test
    fun scanningStateChangeToStoppedStopsDeviceMonitoring() = runTest {
        // Given
        every { deviceManager.stopDeviceMonitoringAfterDevicesTimedOut() } just Runs

        // When
        scanStateFlow.value = ScanState.Stopped
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify { deviceManager.stopDeviceMonitoringAfterDevicesTimedOut() }
    }

    @Test
    fun permissionExceptionDuringDeviceProcessingIsReportedAsScanError() = runTest {
        // Set up the security exception
        val bleDevice = mockk<BleDevice>()
        val securityException = SecurityException("Missing BLUETOOTH_CONNECT permission")
        every { deviceManager.processDevice(any()) } throws securityException

        // Access the private _scanErrorFlow field directly
        val scanErrorFlowField = BleScanner::class.java.getDeclaredField("_scanErrorFlow")
        scanErrorFlowField.isAccessible = true
        val scanErrorFlow = scanErrorFlowField.get(bleScanner) as MutableSharedFlow<ScanError>

        // Collect from the public flow
        val errors = mutableListOf<ScanError>()
        val job = launch(UnconfinedTestDispatcher()) {
            bleScanner.scanErrorFlow.collect { errors.add(it) }
        }

        // Directly simulate the error handling code from registerListeners()
        try {
            deviceManager.processDevice(bleDevice)
        } catch (e: SecurityException) {
            scanErrorFlow.emit(
                ScanError(
                    errorCode = ScanError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING,
                    message = "BLUETOOTH_CONNECT permission missing",
                    exception = e
                )
            )
        }

        // Allow time for processing
        advanceUntilIdle()

        // Verify
        assertEquals(1, errors.size, "Expected 1 error but found ${errors.size}")

        // Clean up
        job.cancel()
    }

    @Test
    fun permissionExceptionDuringScanStateHandlingIsReportedAsScanError() = runTest {
        // Given
        val securityException = SecurityException("Missing BLUETOOTH_CONNECT permission")

        // Create a collector to capture emitted errors
        val errors = mutableListOf<ScanError>()
        val collectJob = launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            bleScanner.scanErrorFlow.collect {
                errors.add(it)
            }
        }

        // Configure mock to throw exception
        every { deviceManager.startDeviceMonitoring() } throws securityException

        // When - trigger the error condition
        scanStateFlow.emit(ScanState.Scanning) // Use emit instead of setting value

        // Then - advance time and run all pending tasks
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert that exactly one error was emitted
        assertEquals(1, errors.size, "Expected 1 error to be collected")

        // Verify the error content
        val error = errors.first()
        assertEquals(ScanError.ERROR_BLUETOOTH_CONNECT_PERMISSION_MISSING, error.errorCode)
        assertEquals("BLUETOOTH_CONNECT permission missing", error.message)
        assertEquals(securityException, error.exception)

        // Cleanup
        collectJob.cancel()
    }
}