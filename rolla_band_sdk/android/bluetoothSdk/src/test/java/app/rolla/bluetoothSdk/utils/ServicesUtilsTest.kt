package app.rolla.bluetoothSdk.utils

import android.os.ParcelUuid
import app.rolla.bluetoothSdk.services.exceptions.InvalidUUIDException
import app.rolla.bluetoothSdk.services.utils.getFullUUID
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import java.util.UUID

// You'll need to specify your actual class that contains the extension functions
@RunWith(MockitoJUnitRunner::class)
class ServicesUtilsTest {

    @Mock
    lateinit var parcelUuid: ParcelUuid


    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun testShortUuidLowerCase() {
        // Arrange
        val shortUuid = "180d"
        val expected = "0000180D-0000-1000-8000-00805F9B34FB"

        // Act
        val result = shortUuid.getFullUUID()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun testShortUuidUpperCase() {
        // Arrange
        val shortUuid = "180D"
        val expected = "0000180D-0000-1000-8000-00805F9B34FB"

        // Act
        val result = shortUuid.getFullUUID()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun testShortUuidMixedCase() {
        // Arrange
        val shortUuid = "18Fd"
        val expected = "000018FD-0000-1000-8000-00805F9B34FB"

        // Act
        val result = shortUuid.getFullUUID()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun testFullUuidGoesUppercase() {
        // Arrange
        val fullUuid = "12345678-9abc-def0-1234-56789abcdef0"
        val expected = "12345678-9ABC-DEF0-1234-56789ABCDEF0"

        // Act
        val result = fullUuid.getFullUUID()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun testFullUuidUnchanged() {
        // Arrange
        val fullUuid = "12345678-9ABC-DEF0-1234-56789ABCDEF0"

        // Act
        val result = fullUuid.getFullUUID()

        // Assert
        assertEquals(fullUuid, result)
    }

    @Test(expected = InvalidUUIDException::class)
    fun testInvalidShortUuid() {
        // Arrange
        val invalidUuid = "18-d"

        // Act
        invalidUuid.getFullUUID()

        // No assert needed as we expect an exception
    }

    @Test(expected = InvalidUUIDException::class)
    fun testInvalidShortUuidSpecialChars() {
        // Arrange
        val invalidUuid = "18#d"

        // Act
        invalidUuid.getFullUUID()

        // No assert needed as we expect an exception
    }

    @Test(expected = InvalidUUIDException::class)
    fun testFullUuidWrongFormat() {
        // Arrange
        val invalidUuid = "12345-9abc-def0-1234-56789abcdef0"

        // Act
        invalidUuid.getFullUUID()

        // No assert needed as we expect an exception
    }

    @Test(expected = InvalidUUIDException::class)
    fun testFullUuidWrongPattern() {
        // Arrange
        val invalidUuid = "123456789-abc-def0-1234-56789abcdef"

        // Act
        invalidUuid.getFullUUID()

        // No assert needed as we expect an exception
    }

    @Test(expected = InvalidUUIDException::class)
    fun testFullUuidWithSpecialChars() {
        // Arrange
        val invalidUuid = "12345678-9abc-def0-1234-56789abcde$#"

        // Act
        invalidUuid.getFullUUID()

        // No assert needed as we expect an exception
    }

    @Test(expected = InvalidUUIDException::class)
    fun testWrongLengthShortUuid() {
        // Arrange
        val invalidUuid = "123"

        // Act
        invalidUuid.getFullUUID()

        // No assert needed as we expect an exception
    }

    @Test(expected = InvalidUUIDException::class)
    fun testEmptyStringUuid() {
        // Arrange
        val invalidUuid = ""

        // Act
        invalidUuid.getFullUUID()

        // No assert needed as we expect an exception
    }

    @Test
    fun testFullUuidToUppercase() {
        // Arrange
        val uuid = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0")
        Mockito.`when`(parcelUuid.uuid).thenReturn(uuid)
        val expected = "12345678-9ABC-DEF0-1234-56789ABCDEF0"

        // Act
        val result = parcelUuid.getFullUUID()

        // Assert
        assertEquals(expected, result)
        Mockito.verify(parcelUuid).uuid
    }

    @Test
    fun testFullUuidRemainUppercase() {
        // Arrange
        val uuid = UUID.fromString("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE")
        Mockito.`when`(parcelUuid.uuid).thenReturn(uuid)
        val expected = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"

        // Act
        val result = parcelUuid.getFullUUID()

        // Assert
        assertEquals(expected, result)
        Mockito.verify(parcelUuid).uuid
    }

    @Test
    fun testFullUuidWithMixedCaseToUpperCase() {
        // Arrange
        val uuid = UUID.fromString("aAaAaAaA-bBbB-cCcC-dDdD-eEeEeEeEeEeE")
        Mockito.`when`(parcelUuid.uuid).thenReturn(uuid)
        val expected = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"

        // Act
        val result = parcelUuid.getFullUUID()

        // Assert
        assertEquals(expected, result)
        Mockito.verify(parcelUuid).uuid
    }

    @Test
    fun testBluetoothBaseUuidToUppercase() {
        // Arrange
        val uuid = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
        Mockito.`when`(parcelUuid.uuid).thenReturn(uuid)
        val expected = "00001234-0000-1000-8000-00805F9B34FB"

        // Act
        val result = parcelUuid.getFullUUID()

        // Assert
        assertEquals(expected, result)
        Mockito.verify(parcelUuid).uuid
    }

}