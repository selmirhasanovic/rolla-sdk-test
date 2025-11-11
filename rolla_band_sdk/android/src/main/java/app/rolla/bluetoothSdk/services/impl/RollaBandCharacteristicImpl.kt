package app.rolla.bluetoothSdk.services.impl

import android.annotation.SuppressLint
import android.content.Context
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.services.commands.ActivityCallbackAndSetDeviceName
import app.rolla.bluetoothSdk.services.commands.ActivityControl
import app.rolla.bluetoothSdk.services.commands.data.AutomaticMode
import app.rolla.bluetoothSdk.services.commands.BatteryLevel
import app.rolla.bluetoothSdk.services.commands.ChargingStatus
import app.rolla.bluetoothSdk.services.commands.Command
import app.rolla.bluetoothSdk.services.commands.GetActivityHeartRate
import app.rolla.bluetoothSdk.services.commands.GetAutomaticDetection
import app.rolla.bluetoothSdk.services.commands.GetCurrentTime
import app.rolla.bluetoothSdk.services.commands.GetHrv
import app.rolla.bluetoothSdk.services.commands.GetPassiveHeartRate
import app.rolla.bluetoothSdk.services.commands.GetSleep
import app.rolla.bluetoothSdk.services.commands.GetSteps
import app.rolla.bluetoothSdk.services.commands.GetUserData
import app.rolla.bluetoothSdk.services.commands.GetWristBandParams
import app.rolla.bluetoothSdk.services.commands.Handshake
import app.rolla.bluetoothSdk.services.commands.HeartRatePackages
import app.rolla.bluetoothSdk.services.commands.OtaUpdate
import app.rolla.bluetoothSdk.services.commands.SetAutomaticDetection
import app.rolla.bluetoothSdk.services.commands.SetCurrentTime
import app.rolla.bluetoothSdk.services.commands.SetUserData
import app.rolla.bluetoothSdk.services.commands.SetWristBandParams
import app.rolla.bluetoothSdk.services.commands.data.ActivityMode
import app.rolla.bluetoothSdk.services.commands.data.ActivityStatus
import app.rolla.bluetoothSdk.services.commands.data.FirmwareState
import app.rolla.bluetoothSdk.services.commands.data.HeartRate
import app.rolla.bluetoothSdk.services.commands.data.UserInfo
import app.rolla.bluetoothSdk.services.data.ActivityControlData
import app.rolla.bluetoothSdk.services.data.BatteryLevelData
import app.rolla.bluetoothSdk.services.data.HeartRateData
import app.rolla.bluetoothSdk.services.data.HrvData
import app.rolla.bluetoothSdk.services.data.SleepData
import app.rolla.bluetoothSdk.services.data.StepsData
import app.rolla.bluetoothSdk.services.data.StopData
import app.rolla.bluetoothSdk.services.data.TotalHeartRateData
import app.rolla.bluetoothSdk.services.data.UserData
import app.rolla.bluetoothSdk.utils.extensions.log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.android.dfu.DfuProgressListener
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RollaBandCharacteristicImpl : CharacteristicImpl, DfuProgressListener {

    companion object {
        const val TAG = "RollaBandImpl"
    }

    // Add coroutine scope at class level
    override val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Commands
    val handshake = Handshake()
    val getCurrentTime = GetCurrentTime()
    val setCurrentTime = SetCurrentTime()
    val getAutomaticDetectionHeartRate = GetAutomaticDetection(AutomaticMode.HEART_RATE)
    val getAutomaticDetectionHrv = GetAutomaticDetection(AutomaticMode.HRV)
    val setAutomaticDetectionHeartRate = SetAutomaticDetection(AutomaticMode.HEART_RATE)
    val setAutomaticDetectionHrv = SetAutomaticDetection(AutomaticMode.HRV)
    val getWristBandParams = GetWristBandParams()
    val setWristbandParamsInActivity = SetWristBandParams(true)
    val setWristbandParamsOutsideActivity = SetWristBandParams(false)
    val batteryLevel = BatteryLevel()
    val chargingStatus = ChargingStatus()
    val getUserData = GetUserData()
    val setUserData = SetUserData()
    val activityControl = ActivityControl()
    val activityCallbackAndSetDeviceName = ActivityCallbackAndSetDeviceName()
    val heartRatePackages = HeartRatePackages()
    val otaUpdate = OtaUpdate()
    val getSteps = GetSteps()
    val getHrv = GetHrv()
    val getPassiveHeartRate = GetPassiveHeartRate()
    val getActivityHeartRate = GetActivityHeartRate()
    val getSleep = GetSleep()


    // Flows
    private val _batteryLevelDataFlow = MutableSharedFlow<BatteryLevelData>(replay = 1)
    val batteryLevelDataFlow: SharedFlow<BatteryLevelData> = _batteryLevelDataFlow.asSharedFlow()

    private val _userDataResponseFlow = MutableSharedFlow<UserData>(replay = 1)
    val userDataFlow: SharedFlow<UserData> = _userDataResponseFlow.asSharedFlow()

    private val _setUserDataResponseFlow = MutableSharedFlow<UserData>(replay = 1)
    val setUserDataFlow: SharedFlow<UserData> = _setUserDataResponseFlow.asSharedFlow()

    private val _startActivityFlow = MutableSharedFlow<ActivityControlData>(replay = 1)
    val startActivityFlow: SharedFlow<ActivityControlData> = _startActivityFlow.asSharedFlow()

    private val _stopActivityFlow = MutableSharedFlow<ActivityControlData>(replay = 1)
    val stopActivityFlow: SharedFlow<ActivityControlData> = _stopActivityFlow.asSharedFlow()

    private val _heartRatePackagesFlow = MutableSharedFlow<StopData>(replay = 1)
    val heartRatePackagesFlow: SharedFlow<StopData> = _heartRatePackagesFlow.asSharedFlow()

    private val _firmwareUpdateFlow = MutableSharedFlow<FirmwareState>(replay = 1)
    val firmwareUpdateFlow: SharedFlow<FirmwareState> = _firmwareUpdateFlow.asSharedFlow()

    private val _firmwareUpdateProgressFlow = MutableSharedFlow<Int>(replay = 1)
    val firmwareUpdateProgressFlow: SharedFlow<Int> = _firmwareUpdateProgressFlow.asSharedFlow()

    private val _firmwareUpdateErrorFlow = MutableSharedFlow<String>(replay = 1)
    val firmwareUpdateErrorFlow: SharedFlow<String> = _firmwareUpdateErrorFlow.asSharedFlow()

    private val _stepsFlow = MutableSharedFlow<StepsData>(replay = 1)
    val stepsFlow: SharedFlow<StepsData> = _stepsFlow.asSharedFlow()

    private val _hrvFlow = MutableSharedFlow<HrvData>(replay = 1)
    val hrvFlow: SharedFlow<HrvData> = _hrvFlow.asSharedFlow()
    private val _passiveHeartRateFlow = MutableSharedFlow<HeartRateData>(replay = 1)
    val passiveHeartRateFlow: SharedFlow<HeartRateData> = _passiveHeartRateFlow.asSharedFlow()
    private val _activityHeartRateFlow = MutableSharedFlow<HeartRateData>(replay = 1)
    val activityHeartRateFlow: SharedFlow<HeartRateData> = _activityHeartRateFlow.asSharedFlow()

    private val _totalHeartRateFlow = MutableSharedFlow<TotalHeartRateData>(replay = 1)
    val totalHeartRateFlow: SharedFlow<TotalHeartRateData> = _totalHeartRateFlow.asSharedFlow()

    private val _sleepFlow = MutableSharedFlow<SleepData>(replay = 1)
    val sleepFlow: SharedFlow<SleepData> = _sleepFlow.asSharedFlow()

    private var activityStatus = ActivityStatus.FINISHED
    private var activityMode = ActivityMode.STOP
    private var stopped = false
    private var state = FirmwareState.IDLE
    private var dfuServiceController: DfuServiceController? = null
    private var context: Context? = null
    private var dfuServiceClass: Class<out DfuBaseService>? = null
    private var otaUrl = ""


    override fun onRead(byteArray: ByteArray, device: BleDevice) {
        log(TAG, "onRead")
        processData(byteArray, device)
    }

    override fun onNotify(byteArray: ByteArray, device: BleDevice) {
        log(TAG, "onNotify")
        processData(byteArray, device)
    }

    private fun processData(byteArray: ByteArray, device: BleDevice?) {
        when (byteArray[0]) {
            handshake.getId() -> handshake.read(byteArray)
            handshake.errorId() -> {
                log(TAG, "Handshake error")
            }
            getCurrentTime.getId() -> getCurrentTime.read(byteArray)
            getCurrentTime.errorId() -> {
                log(TAG, "Get current time error")
            }
            setCurrentTime.getId() -> setCurrentTime.read(byteArray)
            setCurrentTime.errorId() -> {
                log(TAG, "Set current time error")
            }
            getAutomaticDetectionHeartRate.getId() -> getAutomaticDetectionHeartRate.read(byteArray)
            getAutomaticDetectionHeartRate.errorId() -> {
                log(TAG, "Get automatic detection error")
            }
            setAutomaticDetectionHeartRate.getId() -> setAutomaticDetectionHeartRate.read(byteArray)
            setAutomaticDetectionHeartRate.errorId() -> {
                log(TAG, "Set automatic detection heart rate error")
            }
            setAutomaticDetectionHrv.getId() -> setAutomaticDetectionHrv.read(byteArray)
            setAutomaticDetectionHrv.errorId() -> {
                log(TAG, "Set automatic detection hrv error")
            }
            getWristBandParams.getId() -> getWristBandParams.read(byteArray)
            getWristBandParams.errorId() -> {
                log(TAG, "Get wrist band params error")
            }
            setWristbandParamsOutsideActivity.getId() -> setWristbandParamsOutsideActivity.read(byteArray)
            setWristbandParamsOutsideActivity.errorId() -> {
                log(TAG, "Set wrist band params outside activity error")
            }
            batteryLevel.getId() -> {
                val batteryLevel = batteryLevel.read(byteArray)
                scope.launch {
                    _batteryLevelDataFlow.emit(BatteryLevelData(device!!.getMacAddress(), batteryLevel))
                }
            }
            batteryLevel.errorId() -> {
                log(TAG, "Get battery level error")
                scope.launch {
                    _batteryLevelDataFlow.emit(BatteryLevelData(device!!.getMacAddress(), -1, "Error getting battery level"))
                }
            }
            chargingStatus.getId() -> chargingStatus.read(byteArray)
            getUserData.getId() -> {
                val userInfo = getUserData.read(byteArray)
                scope.launch {
                    _userDataResponseFlow.emit(UserData(device!!.getMacAddress(), userInfo))
                }
            }
            getUserData.errorId() -> {
                log(TAG, "Get user data error")
                scope.launch {
                    _userDataResponseFlow.emit(UserData(device!!.getMacAddress(), UserInfo(0, 0, 0, 0f, 0, ""), "Error getting user data"))
                }
            }
            setUserData.getId() -> {
                setUserData.read(byteArray)
                scope.launch {
                    _setUserDataResponseFlow.emit(UserData(device!!.getMacAddress(), UserInfo(0, 0, 0, 0f, 0, "")))
                }
            }
            setUserData.errorId() -> {
                log(TAG, "Set user data error")
                scope.launch {
                    _setUserDataResponseFlow.emit(UserData(device!!.getMacAddress(), UserInfo(0, 0, 0, 0f, 0, ""), "Error setting user data"))
                }
            }
            activityControl.getId() -> {
                val activityControlInfo = activityControl.read(byteArray)
                if (!activityControlInfo.success) {
                    if (activityStatus == ActivityStatus.STARTING) {
                        scope.launch {
                            _startActivityFlow.emit(ActivityControlData(device!!.getMacAddress(), false, activityStatus, activityControlInfo.error))
                        }
                        activityStatus = ActivityStatus.STARTED
                    }
                    if (activityStatus == ActivityStatus.FINISHING) {
                        scope.launch {
                            _stopActivityFlow.emit(ActivityControlData(device!!.getMacAddress(), false, activityStatus, activityControlInfo.error))
                        }
                        activityStatus = ActivityStatus.FINISHED
                    }
                    log(TAG, "Activity control error")
                }
            }
            activityCallbackAndSetDeviceName.getId() -> {
                val activityInfo = activityCallbackAndSetDeviceName.read(byteArray)
                if (activityInfo.activityMode == ActivityMode.START) {
                    activityStatus = ActivityStatus.STARTED
                    activityMode = ActivityMode.START
                    scope.launch {
                        _startActivityFlow.emit(ActivityControlData(device!!.getMacAddress(), true, activityStatus))
                    }
                } else if (activityInfo.activityMode == ActivityMode.STOP) {
                    activityStatus = ActivityStatus.FINISHED
                    activityMode = ActivityMode.STOP
                    scope.launch {
                        _stopActivityFlow.emit(ActivityControlData(device!!.getMacAddress(), true, activityStatus))
                    }
                }
            }
            heartRatePackages.getId() -> {
                heartRatePackages.read(byteArray)
                if (activityStatus != ActivityStatus.FINISHING) {
                    activityStatus = ActivityStatus.STARTED
                }
                if (activityMode == ActivityMode.STOP && activityStatus == ActivityStatus.STARTED && !stopped) {
                    scope.launch {
                        _heartRatePackagesFlow.emit(StopData(device!!.getMacAddress()))
                    }
                    stopped = true
                }
            }
            getSteps.getId() -> {
                val stepsData = getSteps.read(byteArray)
                stepsData.uuid = device!!.getMacAddress()
                log(TAG, "Steps data: $stepsData")
                scope.launch {
                    if (stepsData.isEndOfData) {
                        _stepsFlow.emit(stepsData)
                    }
                }
            }
            getHrv.getId() -> {
                val hrvData = getHrv.read(byteArray)
                hrvData.uuid = device!!.getMacAddress()
                log(TAG, "HRV data: $hrvData")
                scope.launch {
                    if (hrvData.isEndOfData) {
                        _hrvFlow.emit(hrvData)
                    }
                }
            }
            getPassiveHeartRate.getId() -> {
                val heartRateData = getPassiveHeartRate.read(byteArray)
                heartRateData.uuid = device!!.getMacAddress()
                log(TAG, "Heart rate data: $heartRateData")
                scope.launch {
                    if (heartRateData.isEndOfData) {
                        _passiveHeartRateFlow.emit(heartRateData)
                    }
                }
            }
            getActivityHeartRate.getId() -> {
                val heartRateData = getActivityHeartRate.read(byteArray)
                heartRateData.uuid = device!!.getMacAddress()
                log(TAG, "Activity heart rate data: $heartRateData")
                scope.launch {
                    if (heartRateData.isEndOfData) {
                        _activityHeartRateFlow.emit(heartRateData)
                    }
                }
            }
            getSleep.getId() -> {
                val sleepData = getSleep.read(byteArray)
                sleepData.uuid = device!!.getMacAddress()
                log(TAG, "Sleep data: $sleepData")
                scope.launch {
                    if (sleepData.isEndOfData) {
                        _sleepFlow.emit(sleepData)
                    }
                }
            }
            else -> {
                log(TAG, "Not handled ${byteArray[0]}")
            }
        }
    }

    fun onDeviceConnected() {
        stopped = false
        activityStatus = ActivityStatus.FINISHED
        activityMode = ActivityMode.STOP
        state = FirmwareState.IDLE
    }

    fun canStartActivity(): Boolean {
        return activityStatus == ActivityStatus.FINISHED
    }

    fun setStartingStatus() {
        activityStatus = ActivityStatus.STARTING
    }

    fun isActivityStarted(): Boolean {
        return activityStatus == ActivityStatus.STARTED
    }

    fun setFinishingStatus() {
        activityStatus = ActivityStatus.FINISHING
    }

    override fun getPostSubscriptionCommands(): List<Command<*>> {
        return listOf(
            handshake,
            getCurrentTime,
            setCurrentTime,
            getAutomaticDetectionHeartRate,
            setAutomaticDetectionHeartRate,
            getAutomaticDetectionHrv,
            setAutomaticDetectionHrv,
            getWristBandParams,
            setWristbandParamsOutsideActivity
        )
    }

    fun setFirmwareVersion(version: String) {
        getUserData.setFirmwareVersion(version)
        setUserData.setFirmwareVersion(version)
    }

    override fun onWrite(byteArray: ByteArray?, device: BleDevice) {
        super.onWrite(byteArray, device)
        if (byteArray?.get(0) == otaUpdate.getId()) {
            log(TAG, "OTA update started from onWrite")
            state = FirmwareState.STARTED
            scope.launch {
                _firmwareUpdateFlow.emit(state)
            }
        }
    }

    fun isOtaStarted(): Boolean {
        return state != FirmwareState.IDLE
    }

    @SuppressLint("MissingPermission")
    fun startInternalUpdate(bleDevice: BleDevice) {
        if (state == FirmwareState.STARTED) {
            state = FirmwareState.INTERNAL_STARTED
            scope.launch {
                _firmwareUpdateFlow.emit(state)
            }
            startUpdate(bleDevice.getMacAddress(), bleDevice.btDevice.name)
        }
    }

    fun setContext(context: Context) {
        this.context = context
    }

    fun setDfuServiceClass(serviceClass: Class<out DfuBaseService>) {
        this.dfuServiceClass = serviceClass
    }

    fun setOtaUrl(url: String) {
        this.otaUrl = url
    }

    fun startUpdate(macAddress: String, deviceName: String) {
        val ctx = context ?: throw IllegalStateException("Context not set")
        val serviceClass = dfuServiceClass ?: throw IllegalStateException("DfuService class not set")

        val starter = DfuServiceInitiator(macAddress)
            .setDeviceName(deviceName)
            .setKeepBond(true)
            .disableResume()
            .setDisableNotification(true) // Disabling notification
            .setPacketsReceiptNotificationsEnabled(false) // Disabling notification
            .setForeground(false) // Disabling notification
            .setRestoreBond(true)
            .setRebootTime(60000)
            .setNumberOfRetries(2)
        // If you want to have experimental button less DFU feature (DFU from SDK 12.x only!) supported call additionally:
        starter.setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)
        // but be aware of this: https://devzone.nordicsemi.com/question/100609/sdk-12-bootloader-erased-after-programming/
        // and other issues related to this experimental service.

        // For DFU bootloaders from SDK 15 and 16 it may be required to add a delay before sending each
        // data packet. This delay gives the DFU target more time to prepare flash memory, causing less
        // packets being dropped and more reliable transfer. Detection of packets being lost would cause
        // automatic switch to PRN = 1, making the DFU very slow (but reliable).
        starter.setPrepareDataObjectDelay(300L)

        // Init packet is required by Bootloader/DFU from SDK 7.0+ if HEX or BIN file is given above.
        // In case of a ZIP file, the init packet (a DAT file) must be included inside the ZIP file.
        //val uri = Uri.parse("file:///android_asset/J2251_V015_1_20230324.zip")
        //context.contentResolver.openInputStream(uri)
        //val uri = Uri.parse("file:///android_asset/J2251_V015_1_20230324")
        //Uri.parse("file://android_asset/J2251_V015_1_20230324.zip")
        //starter.setZip(null, "storage/emulated/0/Download/J2251_V034_1_20231010.zip")
        ///data/user/0/app.rolla.one/cache/RollaOne/RollaBand/J2251_V034_1_20231010.zip
        starter.setZip(null, otaUrl)

        DfuServiceListenerHelper.registerProgressListener(ctx, this)

        dfuServiceController = starter.start(ctx, serviceClass)
        // You may use the controller to pause, resume or abort the DFU process.
    }


    override fun onDeviceConnecting(deviceAddress: String) {
        log(TAG, "onDeviceConnecting $deviceAddress")
    }

    override fun onDeviceConnected(deviceAddress: String) {
        log(TAG, "onDeviceConnected $deviceAddress")
    }

    override fun onDfuProcessStarting(deviceAddress: String) {
        log(TAG, "onDfuProcessStarting $deviceAddress")
    }

    override fun onDfuProcessStarted(deviceAddress: String) {
        log(TAG, "onDfuProcessStarted $deviceAddress")
    }

    override fun onEnablingDfuMode(deviceAddress: String) {
        log(TAG, "onEnablingDfuMode $deviceAddress")
    }

    override fun onProgressChanged(
        deviceAddress: String,
        percent: Int,
        speed: Float,
        avgSpeed: Float,
        currentPart: Int,
        partsTotal: Int
    ) {
        log(TAG, "onProgressChanged $deviceAddress $percent $speed $avgSpeed $currentPart $partsTotal")
        scope.launch {
            _firmwareUpdateProgressFlow.emit(percent)
        }
    }

    override fun onFirmwareValidating(deviceAddress: String) {
        log(TAG, "onFirmwareValidating $deviceAddress")
    }

    override fun onDeviceDisconnecting(deviceAddress: String?) {
        log(TAG, "onDeviceDisconnecting $deviceAddress")
    }

    override fun onDeviceDisconnected(deviceAddress: String) {
        log(TAG, "onDeviceDisconnected $deviceAddress")
    }

    override fun onDfuCompleted(deviceAddress: String) {
        log(TAG, "onDfuCompleted $deviceAddress")
        state = FirmwareState.IDLE
        scope.launch {
            _firmwareUpdateProgressFlow.emit(100)
        }
    }

    override fun onDfuAborted(deviceAddress: String) {
        log(TAG, "onDfuAborted $deviceAddress")
        state = FirmwareState.IDLE
    }

    override fun onError(
        deviceAddress: String,
        error: Int,
        errorType: Int,
        message: String?
    ) {
        state = FirmwareState.IDLE
        log(TAG, "onError $deviceAddress $error $errorType $message")
        scope.launch {
            _firmwareUpdateErrorFlow.emit(message ?: "Unknown error")
        }
    }

    fun emitHeartRate(uuid: String, heartRates: ArrayList<HeartRate>) {
        heartRates.sortBy { heartRate -> heartRate.timestamp }
        scope.launch {
            _totalHeartRateFlow.emit(TotalHeartRateData(
                uuid = uuid,
                heartRates = heartRates,
                activityLastBlockTimestamp = getActivityHeartRate.getLastBlockTimestamp(),
                activityLastEntryTimestamp = getActivityHeartRate.getLastEntryTimestamp(),
                passiveLastBlockTimestamp = getPassiveHeartRate.getLastBlockTimestamp()
            ))
        }
    }

}