package app.rolla.bluetoothSdk

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import app.rolla.bluetoothSdk.connect.BleConnector
import app.rolla.bluetoothSdk.connect.CharacteristicEvent
import app.rolla.bluetoothSdk.connect.ConnectionError
import app.rolla.bluetoothSdk.device.DeviceConnectionState
import app.rolla.bluetoothSdk.device.DeviceType
import app.rolla.bluetoothSdk.exceptions.BleAdapterNullException
import app.rolla.bluetoothSdk.exceptions.BleException
import app.rolla.bluetoothSdk.exceptions.BleNotEnabledException
import app.rolla.bluetoothSdk.exceptions.BleNotSupportedException
import app.rolla.bluetoothSdk.exceptions.BleScannerNotAvailableException
import app.rolla.bluetoothSdk.exceptions.BleDestructionException
import app.rolla.bluetoothSdk.exceptions.InvalidParameterException
import app.rolla.bluetoothSdk.scan.BleScanner
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.di.BleScopeContext
import app.rolla.bluetoothSdk.scan.manager.ScanError
import app.rolla.bluetoothSdk.scan.manager.ScanManager.ScanState
import app.rolla.bluetoothSdk.services.BleCharacteristic
import app.rolla.bluetoothSdk.services.CharacteristicImpls
import app.rolla.bluetoothSdk.services.commands.Command
import app.rolla.bluetoothSdk.services.commands.data.ActivityMode
import app.rolla.bluetoothSdk.services.commands.data.ActivityType
import app.rolla.bluetoothSdk.services.commands.data.FirmwareState
import app.rolla.bluetoothSdk.services.commands.data.HeartRate
import app.rolla.bluetoothSdk.services.commands.data.UserInfo
import app.rolla.bluetoothSdk.services.data.ActivityControlData
import app.rolla.bluetoothSdk.services.data.BatteryLevelData
import app.rolla.bluetoothSdk.services.data.FirmwareRevisionData
import app.rolla.bluetoothSdk.services.data.HeartRateMeasurementData
import app.rolla.bluetoothSdk.services.data.HrvData
import app.rolla.bluetoothSdk.services.data.RunningSpeedCadenceData
import app.rolla.bluetoothSdk.services.data.SerialNumberData
import app.rolla.bluetoothSdk.services.data.SleepData
import app.rolla.bluetoothSdk.services.data.StepsData
import app.rolla.bluetoothSdk.services.data.TotalHeartRateData
import app.rolla.bluetoothSdk.services.data.UserData
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.dfu.DfuBaseService
import kotlin.collections.sortedWith
import kotlin.coroutines.CoroutineContext

/**
 * Main entry point for the Bluetooth SDK, providing high-level access to Bluetooth functionality.
 * Manages BLE scanning and device discovery through a unified interface.
 */
@SuppressLint("MissingPermission")
class BleManager constructor(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val bleScanner: BleScanner?,
    private val bleConnector: BleConnector,
    bleScopeContext: CoroutineContext
) {
    companion object {
        private const val TAG = "BleManager"
    }

    // Event flows
    private val _bleErrorFlow = MutableSharedFlow<BleException>(replay = 1)
    val bleErrorFlow: SharedFlow<BleException> = _bleErrorFlow.asSharedFlow()

    private val managerScope = CoroutineScope(bleScopeContext)

    // Expose scanner state and error flows if scanner is available
    val scanStateFlow: StateFlow<ScanState>? get() = bleScanner?.scanStateFlow
    val scanErrorFlow: Flow<ScanError>? get() = bleScanner?.scanErrorFlow
    val discoveredDevicesFlow: StateFlow<List<BleDevice>>? get() = bleScanner?.discoveredDevicesFlow

    // Exposed flows
    val connectionState: StateFlow<Map<String, DeviceConnectionState>>? get() = bleScanner?.connectionStateFlow
    val characteristicEvents: SharedFlow<CharacteristicEvent> = bleConnector.characteristicEvents
    val connectionErrors: SharedFlow<ConnectionError> = bleConnector.errorFlow

    // Expose connection state changes flow
    val connectionStateChanges: SharedFlow<Pair<String, DeviceConnectionState>>? get() = 
        bleScanner?.connectionStateChanges

    private val _bleStateFlow = MutableSharedFlow<Int>(replay = 1)
    val bleStateFlow: SharedFlow<Int> = _bleStateFlow.asSharedFlow()

    private val stateChangeReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent?.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                managerScope.launch {
                    _bleStateFlow.emit(state)
                }
                if (state == BluetoothAdapter.STATE_ON) {

                } else if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                    // Proactively disconnect before radio fully powers down
                    bleConnector.disconnectAll()
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    // Fallback cleanup when already off
                    bleConnector.disconnectAll()
                }
            }
        }
    }

    init {
        managerScope.launch {
            CharacteristicImpls.rollaBand.heartRatePackagesFlow.collect { stopData ->
                withContext(Dispatchers.Main) {
                    stopBandActivity(stopData.uuid, ActivityType.RUN)
                }
            }
        }
        managerScope.launch {
            CharacteristicImpls.rollaBand.firmwareUpdateFlow.collect { otaState ->
                withContext(Dispatchers.Main) {
                    log(TAG, "OTA state: $otaState")
                    if (otaState == FirmwareState.STARTED) {
                        startScan(setOf(DeviceType.ROLLA_BAND))
                    } else if (otaState == FirmwareState.INTERNAL_STARTED) {
                        stopScan()
                    }
                }
            }
        }
        managerScope.launch {
            connectionStateChanges?.collect { event ->
                if (event.second == DeviceConnectionState.CONNECTED) {
                    val bleDevice = bleConnector.getDeviceByUuid(event.first)
                    if (bleDevice?.isDeviceType(DeviceType.ROLLA_BAND) == true) {
                        CharacteristicImpls.rollaBand.onDeviceConnected()
                        CharacteristicImpls.runningSpeedCadence.onDeviceConnected()
                    } else if (bleDevice?.isDeviceType(DeviceType.RUNNING_SPEED_AND_CADENCE) == true) {
                        CharacteristicImpls.runningSpeedCadence.onDeviceConnected()
                    }

                }
            }
        }
        managerScope.launch {
            CharacteristicImpls.rollaBand.stepsFlow.collect { stepsData ->
                log(TAG, "Steps data: $stepsData")
                if (stepsData.hasMoreData && stepsData.isEndOfData) {
                    log(TAG, "Reading next page of steps data")
                    readBandStepsNextPage(stepsData.uuid)
                }
            }
        }
        managerScope.launch {
            CharacteristicImpls.rollaBand.hrvFlow.collect { hrvData ->
                log(TAG, "HRV data: $hrvData")
                if (hrvData.hasMoreData && hrvData.isEndOfData) {
                    log(TAG, "Reading next page of hrv data")
                    readBandHrvNextPage(hrvData.uuid)
                }
            }
        }
        managerScope.launch {
            CharacteristicImpls.rollaBand.passiveHeartRateFlow.collect { heartRateData ->
                log(TAG, "Passive heart rate data: $heartRateData")
                if (heartRateData.hasMoreData && heartRateData.isEndOfData) {
                    log(TAG, "Reading next page of passive heart rate data")
                    readBandPassiveHeartRateNextPage(heartRateData.uuid)
                } else {
                    readBandActivityHeartRate(heartRateData.uuid, heartRateData.heartRates)
                }
            }
        }
        managerScope.launch {
            CharacteristicImpls.rollaBand.activityHeartRateFlow.collect { heartRateData ->
                log(TAG, "Activity heart rate data: $heartRateData")
                if (heartRateData.hasMoreData && heartRateData.isEndOfData) {
                    log(TAG, "Reading next page of activity heart rate data")
                    readBandActivityHeartRateNextPage(heartRateData.uuid)
                } else {
                    CharacteristicImpls.rollaBand.emitHeartRate(heartRateData.uuid, heartRateData.heartRates)
                }
            }
        }
        managerScope.launch {
            CharacteristicImpls.rollaBand.sleepFlow.collect { sleepData ->
                log(TAG, "Sleep data: $sleepData")
                if (sleepData.hasMoreData && sleepData.isEndOfData) {
                    log(TAG, "Reading next page of sleep data")
                    readBandSleepNextPage(sleepData.uuid)
                }
            }
        }
        ContextCompat.registerReceiver(context, stateChangeReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Starts scanning for devices of the specified types.
     *
     * @param deviceTypes Set of device types to scan for
     * @return Result of the scan start operation
     * @throws BleNotSupportedException if Bluetooth LE protocol is not supported on device
     * @throws BleNotEnabledException if Bluetooth is not enabled on device
     * @throws BleAdapterNullException if Bluetooth adapter is null
     * @throws BleScannerNotAvailableException if BLE scanner is not available
     */
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    @Throws(
        BleNotSupportedException::class,
        BleNotEnabledException::class,
        BleAdapterNullException::class,
        BleScannerNotAvailableException::class
    )
    suspend fun startScan(deviceTypes: Set<DeviceType>): MethodResult {
        checkBluetoothAvailable()
        log(TAG, "Starting scan for device types: ${deviceTypes.joinToString()}")

        return bleScanner?.startScan(deviceTypes) ?:
        throw BleScannerNotAvailableException()
    }

    /**
     * Stops the current scan operation.
     *
     * @return Result of the scan stop operation
     * @throws BleNotSupportedException if Bluetooth LE protocol is not supported on device
     * @throws BleNotEnabledException if Bluetooth is not enabled on device
     * @throws BleAdapterNullException if Bluetooth adapter is null
     * @throws BleScannerNotAvailableException if BLE scanner is not available
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    @Throws(
        BleNotSupportedException::class,
        BleNotEnabledException::class,
        BleAdapterNullException::class,
        BleScannerNotAvailableException::class
    )
    suspend fun stopScan(): MethodResult {
        checkBluetoothAvailable()
        log(TAG, "Stopping scan")

        return bleScanner?.stopScan() ?:
        throw BleScannerNotAvailableException()
    }

    /**
     * Gets all discovered devices, sorted by signal strength.
     *
     * @return List of all discovered devices
     * @throws BleScannerNotAvailableException if BLE scanner is not available
     */
    @Throws(BleScannerNotAvailableException::class)
    fun getAllScannedDevices(): List<BleDevice> {
        return bleScanner?.getAllScannedDevices() ?:
        throw BleScannerNotAvailableException()
    }

    /**
     * Gets devices of a specific type, sorted by signal strength.
     *
     * @param deviceType The device type to filter by
     * @return List of devices matching the specified type
     * @throws BleScannerNotAvailableException if BLE scanner is not available
     */
    @Throws(BleScannerNotAvailableException::class)
    fun getDevicesByType(deviceType: DeviceType): List<BleDevice> {
        return bleScanner?.getDevicesByType(deviceType) ?:
        throw BleScannerNotAvailableException()
    }

    /**
     * Sets the duration for scan operations.
     *
     * @param scanDurationMs The scan duration in milliseconds
     * @throws InvalidParameterException if duration is invalid
     * @throws BleScannerNotAvailableException if BLE scanner is not available
     */
    @Throws(InvalidParameterException::class, BleScannerNotAvailableException::class)
    fun setScanDuration(scanDurationMs: Long) {
        log(TAG, "Setting scan duration to $scanDurationMs ms")

        bleScanner?.setScanDuration(scanDurationMs) ?:
        throw BleScannerNotAvailableException()
    }

    /**
     * Clears all discovered devices.
     *
     * @throws BleScannerNotAvailableException if BLE scanner is not available
     */
    @Throws(BleScannerNotAvailableException::class)
    fun clearScannedDevices() {
        log(TAG, "Clearing all discovered devices")

        if (bleScanner == null) {
            throw BleScannerNotAvailableException()
        }

        // Access the device manager through the bleScanner reference
        managerScope.launch {
            try {
                // Call the clearDevices method through BleScanner
                bleScanner.clearDevices()
            } catch (e: Exception) {
                log(TAG, "Error clearing devices: ${e.message}")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Throws(
        BleNotSupportedException::class,
        BleNotEnabledException::class,
        BleAdapterNullException::class
    )
    fun connectToBleDevice(macAddress: String): MethodResult {
        checkBluetoothAvailable()
        log(TAG, "Start connecting to device with mac address: $macAddress, subscribe for all device types")
        var bleDevice = bleConnector.getDeviceByUuid(macAddress)
        if (bleDevice == null) {
            val remoteDevice = getRemoteDevice(macAddress)
            log("BleManager", "Device: ${remoteDevice?.address}")
            if (remoteDevice != null) {
                bleDevice = BleDevice(
                    btDevice = remoteDevice,
                    serviceUuids = emptyList(),
                    rssi = 0,
                    deviceTypes = setOf(
                        DeviceType.ROLLA_BAND,
                        DeviceType.HEART_RATE,
                        DeviceType.RUNNING_SPEED_AND_CADENCE
                    )
                )
            } else {
                return MethodResult(
                    false,
                    ConnectionError.getErrorDescription(ConnectionError.ERROR_DEVICE_NOT_FOUND)
                )
            }
        }
        return bleConnector.connectToDevice(bleDevice, bleDevice.deviceTypes)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Throws(
        BleNotSupportedException::class,
        BleNotEnabledException::class,
        BleAdapterNullException::class
    )
    fun connectToBleDevice(bleDevice: BleDevice, deviceTypesForSubscribe: Set<DeviceType>): MethodResult {
        checkBluetoothAvailable()
        log(TAG, "Start connecting to: $bleDevice. Subscribe for device types $deviceTypesForSubscribe")

        return bleConnector.connectToDevice(bleDevice, deviceTypesForSubscribe)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Throws(
        BleNotSupportedException::class,
        BleNotEnabledException::class,
        BleAdapterNullException::class
    )
    fun disconnectFromDevice(macAddress: String) {
        checkBluetoothAvailable()
        log(TAG, "Start disconnecting all device types from: $macAddress")

        bleConnector.disconnectFromDevice(macAddress)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Throws(
        BleNotSupportedException::class,
        BleNotEnabledException::class,
        BleAdapterNullException::class
    )
    fun disconnectFromDevice(bleDevice: BleDevice) {
        checkBluetoothAvailable()
        log(TAG, "Start disconnecting all device types from: ${bleDevice.btDevice.address}")

        bleConnector.disconnectFromDevice(bleDevice)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Throws(
        BleNotSupportedException::class,
        BleNotEnabledException::class,
        BleAdapterNullException::class
    )
    fun disconnectFromDevice(bleDevice: BleDevice, deviceTypesForUnsubscribe: Set<DeviceType>) {
        checkBluetoothAvailable()

        bleConnector.disconnectFromDevice(bleDevice, deviceTypesForUnsubscribe)
    }

    /**
     * Get all connected devices
     */
    fun getConnectedDevices(): List<BleDevice> {
        return bleConnector.getConnectedDevices()
    }

    /**
     * Check if device is connected
     */
    fun isDeviceConnected(macAddress: String): Boolean {
        return bleConnector.isDeviceConnected(macAddress)
    }

    fun getConnectionState(macAddress: String, onSuccess: (DeviceConnectionState) -> Unit, onError: (Exception) -> Unit) {
        val state = bleConnector.getConnectionState(macAddress)
        if (state == null) {
            onError(Exception("Device not found"))
        } else {
            onSuccess(state)
        }
    }

    /**
     * Get connected device for specific type
     */
    fun getConnectedDeviceForType(deviceType: DeviceType): BleDevice? {
        return bleConnector.getConnectedDeviceForType(deviceType)
    }

    fun getRemoteDevice(macAddress: String): BluetoothDevice? {
        checkBluetoothAvailable()
        return bluetoothAdapter?.getRemoteDevice(macAddress)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun removeBond(macAddress: String): MethodResult {
        checkBluetoothAvailable()
        val device = getRemoteDevice(macAddress)
            ?: return MethodResult(false, "Device not found")
        return try {
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                return MethodResult(true)
            }
            val removeBondMethod = device.javaClass.getMethod("removeBond")
            val invokeResult = removeBondMethod.invoke(device) as? Boolean ?: false
            MethodResult(invokeResult)
        } catch (e: Exception) {
            MethodResult(false, e.message ?: "Failed to remove bond")
        }
    }

    /**
     * Disconnect all devices
     */
    fun disconnectAll() {
        bleConnector.disconnectAll()
    }


    /**
     * Read from a characteristic
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readCharacteristic(bleDevice: BleDevice, characteristic: BleCharacteristic): MethodResult {
        return bleConnector.readCharacteristic(bleDevice, characteristic)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readRollaBandCommand(bleDevice: BleDevice, characteristic: BleCharacteristic, command: Command<*>): MethodResult {
        return bleConnector.readRollaBandCommand(bleDevice, characteristic, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readSerialNumber(uuid: String): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        return readCharacteristic(bleDevice, BleCharacteristic.SERIAL_NUMBER)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readFirmwareRevision(uuid: String): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        return readCharacteristic(bleDevice, BleCharacteristic.FIRMWARE_REVISION)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readBandBatteryLevel(uuid: String): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val command = CharacteristicImpls.rollaBand.batteryLevel
        return readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readBandUserData(uuid: String): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val command = CharacteristicImpls.rollaBand.getUserData
        return readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE,
           command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun setBandUserData(uuid: String, userInfo: UserInfo): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val command = CharacteristicImpls.rollaBand.setUserData
        command.setUserInfo(userInfo)
        CharacteristicImpls.runningSpeedCadence.setUserHeight(userInfo.height)
        CharacteristicImpls.runningSpeedCadence.setUserWeight(userInfo.weight)
        CharacteristicImpls.runningSpeedCadence.setUserAge(userInfo.age)
        CharacteristicImpls.runningSpeedCadence.setUserGender(userInfo.gender)
        return readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE,
            command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startBandActivity(uuid: String, activityType: ActivityType): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val command = CharacteristicImpls.rollaBand.activityControl
        command.setActivityType(activityType)
        command.setActivityMode(ActivityMode.START)
        val canStart = CharacteristicImpls.rollaBand.canStartActivity()
        if (!canStart) {
            return MethodResult(false, "Activity already in progress or starting")
        }
        val readCommand = readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
        if (readCommand.successfullyStarted) {
            CharacteristicImpls.rollaBand.setStartingStatus()
        }
        return readCommand
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stopBandActivity(uuid: String, activityType: ActivityType): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val command = CharacteristicImpls.rollaBand.activityControl
        command.setActivityType(activityType)
        command.setActivityMode(ActivityMode.STOP)
        val isStarted = CharacteristicImpls.rollaBand.isActivityStarted()
        if (isStarted) {
            val readCommand = readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
            if (readCommand.successfullyStarted) {
                CharacteristicImpls.rollaBand.setFinishingStatus()
            }
            return readCommand
        } else {
            return MethodResult(false, "Activity not started")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startBandOtaUpdate(
        url: String, 
        uuid: String, 
        dfuServiceClass: Class<out DfuBaseService>
    ): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        if (CharacteristicImpls.rollaBand.isOtaStarted()) {
            return MethodResult(false, "OTA update already in progress")
        }
        val command = CharacteristicImpls.rollaBand.otaUpdate
        CharacteristicImpls.rollaBand.setOtaUrl(url)
        CharacteristicImpls.rollaBand.setContext(context)
        CharacteristicImpls.rollaBand.setDfuServiceClass(dfuServiceClass)
        log(TAG, "Starting OTA update for $uuid")
        return readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun setBandWristbandParams(uuid: String, inActivity: Boolean): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val command = if (inActivity) CharacteristicImpls.rollaBand.setWristbandParamsInActivity else CharacteristicImpls.rollaBand.setWristbandParamsOutsideActivity
        return readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readBandSteps(uuid: String, blockTimestamp: Long, entryTimestamp: Long): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val command = CharacteristicImpls.rollaBand.getSteps
        command.setStepTimestamp(blockTimestamp, entryTimestamp)
        command.setIsNextPage(false)
        return readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun readBandStepsNextPage(uuid: String) {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return
        val command = CharacteristicImpls.rollaBand.getSteps
        command.setIsNextPage(true)
        readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readBandHrv(uuid: String, blockTimestamp: Long): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val command = CharacteristicImpls.rollaBand.getHrv
        command.setHrvTimestamp(blockTimestamp)
        command.setIsNextPage(false)
        return readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun readBandHrvNextPage(uuid: String) {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return
        val command = CharacteristicImpls.rollaBand.getHrv
        command.setIsNextPage(true)
        readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readBandSleep(uuid: String, blockTimestamp: Long, entryTimestamp: Long): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val command = CharacteristicImpls.rollaBand.getSleep
        command.setSleepTimestamp(blockTimestamp, entryTimestamp)
        command.setIsNextPage(false)
        return readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun readBandSleepNextPage(uuid: String) {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return
        val command = CharacteristicImpls.rollaBand.getSleep
        command.setIsNextPage(true)
        readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readBandHeartRate(uuid: String, activityBlockTimestamp: Long, activityEntryTimestamp: Long, passiveBlockTimestamp: Long): MethodResult {
        val commandActive = CharacteristicImpls.rollaBand.getActivityHeartRate
        val commandPassive = CharacteristicImpls.rollaBand.getPassiveHeartRate
        commandPassive.setHeartRateTimestamp(passiveBlockTimestamp)
        commandActive.setHeartRateTimestamp(activityBlockTimestamp, activityEntryTimestamp)
        return readBandPassiveHeartRate(uuid)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readBandPassiveHeartRate(uuid: String): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val commandPassive = CharacteristicImpls.rollaBand.getPassiveHeartRate
        commandPassive.setIsNextPage(false)
        return readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, commandPassive)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun readBandPassiveHeartRateNextPage(uuid: String) {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return
        val command = CharacteristicImpls.rollaBand.getPassiveHeartRate
        command.setIsNextPage(true)
        readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun readBandActivityHeartRate(uuid: String, heartRates: ArrayList<HeartRate>): MethodResult {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return MethodResult(false, "No connected device")
        val command = CharacteristicImpls.rollaBand.getActivityHeartRate
        command.setIsNextPage(false)
        command.setHeartRateList(heartRates)
        return readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun readBandActivityHeartRateNextPage(uuid: String) {
        val bleDevice = bleConnector.getDeviceByUuid(uuid) ?: return
        val command = CharacteristicImpls.rollaBand.getActivityHeartRate
        command.setIsNextPage(true)
        readRollaBandCommand(bleDevice, BleCharacteristic.ROLLA_BAND_WRITE, command)
    }

    /**
     * Checks if bluetooth is enabled
     *
     * @return true if it is enabled or false if not
     * @throws BleNotSupportedException if Bluetooth LE protocol is not supported on device
     * @throws BleAdapterNullException if Bluetooth adapter is null
     */
    @Throws(BleNotSupportedException::class, BleAdapterNullException::class)
    fun isBluetoothEnabled(): Boolean {
        if (!isBluetoothLESupported()) {
            throw BleNotSupportedException()
        }
        return bluetoothAdapter?.isEnabled ?: throw BleAdapterNullException()
    }

    fun getBluetoothState(onSuccess: (Int) -> Unit, onError: (Exception) -> Unit) {
        if (!isBluetoothLESupported()) {
            onError(BleNotSupportedException())
            return
        }
        if (bluetoothAdapter == null) {
            onError(BleAdapterNullException())
            return
        }
        onSuccess(bluetoothAdapter.state)
    }

    /**
     * Checks if bluetooth is supported on device
     *
     * @return true if it is supported or false if not
     */
    fun isBluetoothLESupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * Checks if Bluetooth is available, enabled and supported
     * @throws BleNotSupportedException if Bluetooth LE protocol is not supported on device
     * @throws BleNotEnabledException if Bluetooth is not enabled on device
     * @throws BleAdapterNullException if Bluetooth adapter is null
     */
    @Throws(
        BleNotSupportedException::class,
        BleNotEnabledException::class,
        BleAdapterNullException::class
    )
    private fun checkBluetoothAvailable() {
        if (!isBluetoothLESupported()) {
            val error = BleNotSupportedException()
            managerScope.launch {
                _bleErrorFlow.emit(error)
            }
            throw error
        }

        if (bluetoothAdapter == null) {
            val error = BleAdapterNullException()
            managerScope.launch {
                _bleErrorFlow.emit(error)
            }
            throw error
        }

        if (!bluetoothAdapter.isEnabled) {
            val error = BleNotEnabledException()
            managerScope.launch {
                _bleErrorFlow.emit(error)
            }
            throw error
        }
    }

    /**
     * Cleans up resources when the manager is no longer needed.
     * Should be called when the application is shutting down or the manager is no longer needed.
     */
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    fun destroy() {
        log(TAG, "Destroying BleManager")
        
        try {
            // Stop scanning first
            bleScanner?.destroy()
            
            // Then clean up connections
            bleConnector.destroy()
            
            // Clean up all characteristic implementations
            BleCharacteristic.entries.forEach { characteristic ->
                characteristic.impl.cleanup()
            }

            context.unregisterReceiver(stateChangeReceiver)
            
            log(TAG, "BleManager destroyed successfully")
        } catch (e: Exception) {
            log(TAG, "Error during BleManager destruction: ${e.message}")
            // Emit error through error flow
            managerScope.launch {
                _bleErrorFlow.emit(BleDestructionException("Destruction error: ${e.message}"))
            }
        }
    }

    fun getSerialNumberFlow(): SharedFlow<SerialNumberData> {
        return CharacteristicImpls.serialNumber.serialNumberDataFlow
    }

    fun getFirmwareVersionFlow(): SharedFlow<FirmwareRevisionData> {
        return CharacteristicImpls.firmwareRevision.firmwareRevisionDataFlow
    }

    fun getHeartRateFlow(): SharedFlow<HeartRateMeasurementData> {
        return CharacteristicImpls.heartRate.heartRateMeasurementDataFlow
    }

    fun getRunningSpeedCadenceFlow(): SharedFlow<RunningSpeedCadenceData> {
        return CharacteristicImpls.runningSpeedCadence.rscDataFlow
    }

    fun getRollaBandBatteryLevelFlow(): SharedFlow<BatteryLevelData> {
        return CharacteristicImpls.rollaBand.batteryLevelDataFlow
    }

    fun getRollaBandUserDataFlow(): SharedFlow<UserData> {
        return CharacteristicImpls.rollaBand.userDataFlow
    }

    fun getRollaBandSetUserDataFlow(): SharedFlow<UserData> {
        return CharacteristicImpls.rollaBand.setUserDataFlow
    }

    fun getRollaBandStartActivityFlow(): SharedFlow<ActivityControlData> {
        return CharacteristicImpls.rollaBand.startActivityFlow
    }

    fun getRollaBandStopActivityFlow(): SharedFlow<ActivityControlData> {
        return CharacteristicImpls.rollaBand.stopActivityFlow
    }

    fun getRollaBandFirmwareUpdateProgressFlow(): SharedFlow<Int> {
        return CharacteristicImpls.rollaBand.firmwareUpdateProgressFlow
    }

    fun getRollaBandFirmwareUpdateErrorFlow(): SharedFlow<String> {
        return CharacteristicImpls.rollaBand.firmwareUpdateErrorFlow
    }

    fun getRollaBandStepsFlow(): SharedFlow<StepsData> {
        return CharacteristicImpls.rollaBand.stepsFlow
    }

    fun getRollaBandHrvFlow(): SharedFlow<HrvData> {
        return CharacteristicImpls.rollaBand.hrvFlow
    }

    fun getRollaBandHeartRateFlow(): SharedFlow<TotalHeartRateData> {
        return CharacteristicImpls.rollaBand.totalHeartRateFlow
    }

    fun getRollaBandSleepFlow(): SharedFlow<SleepData> {
        return CharacteristicImpls.rollaBand.sleepFlow
    }

    /**
     * Flow of all devices (scanned + connected)
     */
    fun getAllDevicesFlow(): Flow<List<BleDevice>>? {
        return discoveredDevicesFlow?.map { devices ->
            devices.sortedWith(
                compareBy<BleDevice> {
                    when (it.connectionState) {
                        DeviceConnectionState.CONNECTED -> 0
                        DeviceConnectionState.CONNECTING -> 1
                        DeviceConnectionState.DISCONNECTING -> 2
                        DeviceConnectionState.DISCONNECTED -> 3
                    }
                }.thenByDescending { it.rssi }
            )
        }
    }
}
