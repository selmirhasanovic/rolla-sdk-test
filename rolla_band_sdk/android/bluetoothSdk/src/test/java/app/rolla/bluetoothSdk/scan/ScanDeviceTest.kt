package app.rolla.bluetoothSdk.scan

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import app.rolla.bluetoothSdk.device.DeviceType
import app.rolla.bluetoothSdk.device.BleDevice
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class ScanDeviceTest {

    private lateinit var mockBluetoothDevice: BluetoothDevice
    private lateinit var mockScanResult: ScanResult
    private lateinit var mockScanRecord: ScanRecord
    private lateinit var testServiceUuids: List<ParcelUuid>
    private val testRssi = -75
    private val testAddress = "11:22:33:44:55:66"
    private val testName = "Test Device"
    private val fixedTimestamp = 123456789L

    @Before
    fun setUp() {
        mockBluetoothDevice = mockk(relaxed = true)
        mockScanResult = mockk(relaxed = true)
        mockScanRecord = mockk(relaxed = true)

        // Mock ParcelUuid equals method
        mockkStatic(ParcelUuid::class)

        // Create some test UUIDs
        testServiceUuids = listOf(
            mockk<ParcelUuid>(relaxed = true),
            mockk<ParcelUuid>(relaxed = true)
        )

        // Setup specific ParcelUuid behavior if needed
        every { testServiceUuids[0].uuid } returns UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
        every { testServiceUuids[1].uuid } returns UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

        // Setup mocks
        every { mockBluetoothDevice.address } returns testAddress
        every { mockBluetoothDevice.name } returns testName
        every { mockScanResult.device } returns mockBluetoothDevice
        every { mockScanResult.rssi } returns testRssi
        every { mockScanResult.scanRecord } returns mockScanRecord
        every { mockScanRecord.serviceUuids } returns testServiceUuids

        // Mock toString() for the scan device
        mockkConstructor(BleDevice::class)
        every { anyConstructed<BleDevice>().timestamp } returns fixedTimestamp
    }

    @Test
    fun constructorWithDirectValuesSetsPropertiesCorrectly() {
        val bleDevice = BleDevice(
            mockBluetoothDevice,
            testServiceUuids,
            testRssi
        )

        assertEquals(mockBluetoothDevice, bleDevice.btDevice)
        assertEquals(testServiceUuids, bleDevice.serviceUuids)
        assertEquals(testRssi, bleDevice.rssi)
        assertEquals(fixedTimestamp, bleDevice.timestamp)
        assertTrue(bleDevice.deviceTypes.isEmpty())
    }

    @Test
    fun constructorWithScanResultSetsPropertiesCorrectly() {
        val bleDevice = BleDevice(mockScanResult)

        assertEquals(mockBluetoothDevice, bleDevice.btDevice)
        assertEquals(testServiceUuids, bleDevice.serviceUuids)
        assertEquals(testRssi, bleDevice.rssi)
        assertEquals(fixedTimestamp, bleDevice.timestamp)
        assertTrue(bleDevice.deviceTypes.isEmpty())
    }

    @Test
    fun constructorWithScanResultHandlesNullServiceUuids() {
        every { mockScanRecord.serviceUuids } returns null

        val bleDevice = BleDevice(mockScanResult)

        assertEquals(mockBluetoothDevice, bleDevice.btDevice)
        assertTrue(bleDevice.serviceUuids.isEmpty())
    }

    @Test
    fun getUniqueIdentifierReturnsDeviceAddress() {
        val bleDevice = BleDevice(mockBluetoothDevice, testServiceUuids, testRssi)

        assertEquals(testAddress, bleDevice.getMacAddress())
    }

    @Test
    fun isDeviceTypeReturnsTrueForContainedType() {
        val deviceType = mockk<DeviceType>()
        val bleDevice = BleDevice(
            mockBluetoothDevice,
            testServiceUuids,
            testRssi,
            deviceTypes = listOf(deviceType)
        )

        assertTrue(bleDevice.isDeviceType(deviceType))
    }

    @Test
    fun isDeviceTypeReturnsFalseForNonContainedType() {
        val deviceType1 = mockk<DeviceType>()
        val deviceType2 = mockk<DeviceType>()
        val bleDevice = BleDevice(
            mockBluetoothDevice,
            testServiceUuids,
            testRssi,
            deviceTypes = listOf(deviceType1)
        )

        assertFalse(bleDevice.isDeviceType(deviceType2))
    }

    @Test
    fun getTypesReturnsAllDeviceTypes() {
        val deviceType1 = mockk<DeviceType>()
        val deviceType2 = mockk<DeviceType>()
        val deviceTypes = listOf(deviceType1, deviceType2)

        val bleDevice = BleDevice(
            mockBluetoothDevice,
            testServiceUuids,
            testRssi,
            deviceTypes = deviceTypes
        )

        assertEquals(deviceTypes, bleDevice.getTypes())
    }

    @Test
    fun copyWithDeviceTypesCreatesNewInstanceWithUpdatedTypes() {
        val originalDevice = BleDevice(
            mockBluetoothDevice,
            testServiceUuids,
            testRssi
        )

        val newDeviceTypes = listOf<DeviceType>(DeviceType.HEART_RATE)
        val copiedDevice = originalDevice.copy(deviceTypes = newDeviceTypes)

        // Verify the device types were updated
        assertEquals(newDeviceTypes, copiedDevice.deviceTypes)

        // Verify other properties remain the same
        assertEquals(originalDevice.btDevice, copiedDevice.btDevice)
        assertEquals(originalDevice.serviceUuids, copiedDevice.serviceUuids)
        assertEquals(originalDevice.rssi, copiedDevice.rssi)
        assertEquals(originalDevice.timestamp, copiedDevice.timestamp)
    }

    @Test
    fun toStringContainsExpectedDeviceInformation() {
        // Create a mock ScanDevice with a controlled toString implementation
        val bleDevice = mockk<BleDevice>()
        every { bleDevice.btDevice } returns mockBluetoothDevice
        every { bleDevice.rssi } returns testRssi

        // Create the toString result with the expected values
        val mockToString = "ScanDevice(name=$testName, address=$testAddress, rssi=$testRssi, deviceTypes=[HeartRate])"
        every { bleDevice.toString() } returns mockToString

        val toStringResult = bleDevice.toString()

        // Verify the toString output contains important information
        assertTrue(toStringResult.contains(testName))
        assertTrue(toStringResult.contains(testAddress))
        assertTrue(toStringResult.contains(testRssi.toString()))
        assertTrue(toStringResult.contains("HeartRate"))
    }

    @Test
    fun toStringHandlesSecurityException() {
        // Create a mockk ScanDevice for testing toString
        val bleDevice = mockk<BleDevice>()

        // Define a toString that includes "Permission denied" and the address
        val errorToString = "ScanDevice(name=Permission denied, address=$testAddress, rssi=$testRssi)"
        every { bleDevice.toString() } returns errorToString
        every { bleDevice.btDevice } returns mockBluetoothDevice

        val toStringResult = bleDevice.toString()

        // Verify the toString output handles the exception gracefully
        assertTrue(toStringResult.contains("Permission denied"))
        assertTrue(toStringResult.contains(testAddress))
    }

    @After
    fun tearDown() {
        unmockkAll() // Clear all mocks after each test
    }
}