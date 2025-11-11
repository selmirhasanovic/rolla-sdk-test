// ignore_for_file: one_member_abstracts

/// Pigeon configuration file for Rolla Bluetooth communication
///
/// This file defines the interface between Flutter and native platforms (Android/iOS)
/// for Bluetooth functionality. Pigeon generates type-safe platform channels from
/// these definitions.
///
/// Generated files:
/// - Dart: lib/generated/pigeons.g.dart
/// - Android: android/src/main/kotlin/com/rolla/band/sdk/generated/Messages.g.kt
///
/// To regenerate platform code after changes, run:
/// flutter packages pub run pigeon --input pigeon/pigeons.dart
library pigeons;
import 'package:pigeon/pigeon.dart';

/// Pigeon configuration for code generation across platforms
@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/generated/pigeons.g.dart',
    dartOptions: DartOptions(),
    kotlinOut: 'android/src/main/kotlin/com/rolla/band/sdk/generated/Messages.g.kt',
    kotlinOptions: KotlinOptions(package: 'com.rolla.band.sdk.generated'),
    dartPackageName: 'rolla_band_sdk',
  ),
)
/// Represents a discovered or connected Bluetooth device
///
/// Contains all necessary information about a Bluetooth device including
/// its capabilities, connection state, and device-specific metadata.
class BluetoothDevice {
  /// Creates a new BluetoothDevice instance
  ///
  /// All parameters are required to ensure complete device information
  /// is available for UI display and connection management.
  BluetoothDevice({
    required this.name,
    required this.rssi,
    required this.uuid,
    required this.capabilities,
    required this.connectionState,
    required this.deviceType,
  });

  /// Human-readable device name (e.g., "Rolla Band Pro")
  final String name;

  /// Received Signal Strength Indicator in dBm
  /// Typical range: -100 (weak) to 0 (strong)
  final int rssi;

  /// Unique device identifier used for connection operations
  /// Format varies by platform (UUID on iOS, MAC address on Android)
  final String uuid;

  /// List of Bluetooth capabilities supported by this device
  /// Used to determine what data can be received from the device
  final List<BluetoothCapabilities> capabilities;

  /// Current connection state of the device
  final ConnectionState connectionState;

  /// Type classification of the device
  final DeviceType deviceType;
}

/// Bluetooth Low Energy service capabilities supported by devices
///
/// These correspond to standard BLE service UUIDs:
/// - RSC: Running Speed and Cadence
/// - CSC: Cycling Speed and Cadence
/// - HR: Heart Rate
enum BluetoothCapabilities {
  /// Running Speed and Cadence service
  rsc,

  /// Cycling Speed and Cadence service
  csc,

  /// Heart Rate service
  hr,
}

/// Bluetooth state response enum
/// state on
enum BluetoothState {
  poweredOn,

  /// state off
  poweredOff,

  /// bluetooth resetting - iOS only
  resetting,

  /// unauthorized for use - iOS only
  unauthorized,

  /// bluetooth is turning off - Android only
  turningOff,

  /// bluetooth is turning on - Android only
  turningOn,

  /// other cases
  unknown,
}

/// Possible connection states for a Bluetooth device
enum ConnectionState {
  /// Device is successfully connected and ready for data transfer
  connected,

  /// Device is not connected
  disconnected,

  /// Connection attempt is in progress
  connecting,
}

/// Device type classification for filtering and UI purposes
enum DeviceType {
  /// Official Rolla Band device
  rollaBand,

  /// Other compatible Bluetooth device
  other,
}

/// User data for Rolla Band
class UserData {
  UserData({
    required this.age,
    required this.height,
    required this.weight,
    required this.gender,
  });

  final int age;
  final double height;
  final double weight;
  // 1 for male, 0 for female
  final int gender;
}

/// Activity types for Rolla Band
enum BandActivityType {
  run,
  cycling,
  badminton,
  football,
  tennis,
  yoga,
  meditation,
  dance,
  basketball,
  walk,
  workout,
  cricket,
  hiking,
  aerobics,
  pingPong,
  ropeJump,
  sitUps,
  volleyball,
}

class GpsData {
  GpsData({
    required this.latitude,
    required this.longitude,
    required this.altitude,
    required this.timestamp,
  });

  final double latitude;
  final double longitude;
  final double altitude;
  final int timestamp;
}

/// API for Flutter to call native platform Bluetooth operations
///
/// This interface defines methods that Flutter can call to interact with
/// the native Bluetooth stack. All methods are asynchronous to prevent
/// blocking the UI thread.
@HostApi()
abstract interface class RollaBluetoothHostApi {
  /// Starts scanning for Bluetooth devices
  ///
  /// [deviceTypes] - Types of devices to scan for (filters results)
  /// [scanDuration] - How long to scan in milliseconds (default: 10 seconds)
  ///
  /// Results are delivered via [RollaBluetoothFlutterApi.onDevicesFound]
  @async
  void scanForDevices({
    required List<DeviceType> deviceTypes,
    int scanDuration = 10000,
  });

  /// Stops any active Bluetooth scanning operation
  ///
  /// Should be called when the user navigates away from device discovery
  /// or when sufficient devices have been found.
  @async
  void stopScanning();

  /// Attempts to connect to a specific Bluetooth device
  ///
  /// [uuid] - Unique identifier of the device to connect to
  ///
  /// Returns true if connection was initiated successfully.
  /// Connection state changes are delivered via
  /// [RollaBluetoothFlutterApi.onConnectionStateChanged]
  @async
  bool connectToDevice(String uuid);

  /// Disconnects from a specific Bluetooth device
  ///
  /// [uuid] - Unique identifier of the device to disconnect from
  ///
  /// Returns true if disconnection was initiated successfully.
  /// Connection state changes are delivered via
  /// [RollaBluetoothFlutterApi.onConnectionStateChanged]
  @async
  bool disconnectFromDevice(String uuid);

  /// Disconnects and removes system bond (Android only; no-op on iOS)
  ///
  /// [uuid] - MAC address on Android; ignored on iOS
  /// Returns true if the device was disconnected and bond removed (or no bond existed).
  @async
  bool disconnectAndRemoveBond(String uuid);

  /// Checks the connection state of a specific Bluetooth device
  ///
  /// [uuid] - Unique identifier of the device to check
  ///
  /// Returns the current connection state of the device as [ConnectionState].
  @async
  ConnectionState checkConnectionState(String uuid);

  /// Checks the current Bluetooth state
  ///
  /// Returns the current Bluetooth state as [BluetoothState].
  @async
  BluetoothState checkBluetoothState();
}

/// API for native platforms to send data back to Flutter
///
/// This interface defines callbacks that the native platform can invoke
/// to notify Flutter of Bluetooth events and data updates.
@FlutterApi()
abstract interface class RollaBluetoothFlutterApi {
  /// Called when new devices are discovered during scanning
  ///
  /// [devices] - List of discovered devices with their current information
  ///
  /// This method may be called multiple times during a scan as new devices
  /// are discovered or existing devices update their information.
  void onDevicesFound(List<BluetoothDevice> devices);

  /// Called when a device's connection state changes
  ///
  /// [uuid] - Unique identifier of the device
  /// [state] - New connection state
  ///
  /// This is called for both successful and failed connection attempts,
  /// as well as unexpected disconnections.
  void onConnectionStateChanged(String uuid, ConnectionState state);

  /// Called when the Bluetooth adapter state changes
  ///
  /// [state] - New Bluetooth state
  ///
  /// This is called when Bluetooth is turned on/off, becomes unauthorized,
  /// or encounters other state changes that affect device connectivity.
  void onBluetoothStateChanged(BluetoothState state);
}

/// API for native platforms to send data back to Flutter
@FlutterApi()
abstract interface class RollaBandActivityApi {
  /// Called when heart rate data is received from the Rolla Band
  ///
  /// [heartRate] - Heart rate in beats per minute (BPM)
  void onHeartRateReceived(int heartRate);

  /// Called when running speed and cadence data is received from the Rolla Band
  ///
  /// [speed] - Speed in meters per second (m/s)
  /// [cadence] - Cadence in steps per minute (spm)
  /// [steps] - Steps calculated for this measurement period (can be fractional)
  void onRunningSpeedAndCadenceReceived(double speed, int cadence, double steps);
}

/// API for Flutter to call native platform Bluetooth operations
@HostApi()
abstract interface class RollaBandWorkoutHostApi {
  @async
  void startWorkout(String uuid, BandActivityType type);
  @async
  void stopWorkout(String uuid, BandActivityType type);
}

/// API for Flutter to call native platform Location operations
@HostApi()
abstract interface class LocationHostApi {
  @async
  void requestAlwaysLocationPermission();
  @async
  void startLocationTracking(BandActivityType type);
  @async
  void stopLocationTracking();
}

/// API for native platforms to send data back to Flutter
@FlutterApi()
abstract interface class RollaBandGpsApi {
  /// Called when GPS data is received from native host platform
  ///
  /// [data] - [GpsData]
  ///
  void onGpsDataReceived(GpsData data);
}

/// API for Flutter to call native platform band operations
@HostApi()
abstract interface class BandCommandHostAPI {
  /// Update user data on the band
  ///
  /// [userData] - [UserData]
  @async
  void updateUserData(String uuid, UserData userData);

  /// Get user data from the band
  ///
  /// Returns [UserData]
  @async
  UserData getUserData(String uuid);

  @async
  String getFirmwareVersion(String uuid);

  @async
  String getSerialNumber(String uuid);

  @async
  int getBatteryLevel(String uuid);
}

/// API for native platforms to send band battery data to Flutter
@FlutterApi()
abstract interface class BandBatteryFlutterApi {
  /// Called when battery level is received from the Rolla Band
  ///
  /// [level] - Battery level in percentage (0-100)
  void onBatteryLevelReceived(int level);
}

@FlutterApi()
abstract interface class FirmwareProgressAPI {
  /// Called when firmware update progress is received from the Rolla Band
  ///
  /// [progress] - Firmware update progress in percentage (0-100)
  void onFirmwareProgress(int progress);

  /// Called when firmware update fails
  void onFirmwareUpdateError();

  void onFirmwareUpdateCompleted();
}

@HostApi()
abstract interface class FirmwareHostAPI {
  /// Start firmware update
  ///
  /// [url] - file path to use the firmware from
  @async
  void startFirmwareUpdate(String url, String uuid);
}

/// Charging state of the Rolla Band
enum BandChargingState { charging, notCharging }

/// API for native platforms to send band charging state to Flutter
@FlutterApi()
abstract interface class BandChargingStateFlutterApi {
  /// Called when charging state changes on the Rolla Band
  ///
  /// [state] - Current charging state
  void onChargingStateReceived(BandChargingState state);
}

/// Represents a single step measurement with timestamp and calories
class RollaBandStep {
  RollaBandStep({
    required this.timestamp,
    required this.steps,
    required this.calories,
  });

  /// UTC timestamp when the steps were recorded
  final int timestamp;

  /// Number of steps taken in this measurement period
  final int steps;

  /// Calories burned during this measurement period
  final double calories;
}

/// Response containing steps data and sync timestamps
class RollaBandStepsSyncResponse {
  RollaBandStepsSyncResponse({
    required this.steps,
    required this.lastSyncedBlockTimestamp,
    required this.lastSyncedEntryTimestamp,
  });

  /// Array of step measurements
  final List<RollaBandStep> steps;

  /// Timestamp of the last synced block for future sync operations
  final int lastSyncedBlockTimestamp;

  /// Timestamp of the last synced entry for future sync operations
  final int lastSyncedEntryTimestamp;
}

class RollaBandHeartRate {
  RollaBandHeartRate({required this.timestamp, required this.hr});

  final int timestamp;
  final int hr;
}

class RollaBandHeartRateSyncResponse {
  RollaBandHeartRateSyncResponse({
    required this.heartRates,
    required this.activityLastSyncedBlockTimestamp,
    required this.activityLastSyncedEntryTimestamp,
    required this.passiveLastSyncedTimestamp,
  });

  final List<RollaBandHeartRate> heartRates;

  final int activityLastSyncedBlockTimestamp;
  final int activityLastSyncedEntryTimestamp;
  final int passiveLastSyncedTimestamp;
}

class RollaBandHRV {
  RollaBandHRV({required this.timestamp, required this.hrv});

  final int timestamp;
  final int hrv;
}

class RollaBandHRVSyncResponse {
  RollaBandHRVSyncResponse({
    required this.hrvs,
    required this.lastSyncedBlockTimestamp,
  });

  final List<RollaBandHRV> hrvs;

  final int lastSyncedBlockTimestamp;
}

enum RollaBandSleepStageValue { deep, light, rem, awake }

class RollaBandSleepStage {
  RollaBandSleepStage({
    required this.startTime,
    required this.endTime,
    required this.value,
  });

  final int startTime;
  final int endTime;
  final RollaBandSleepStageValue value;
}

class RollaBandSleepSyncResponse {
  RollaBandSleepSyncResponse({
    required this.sleepStages,
    required this.lastSyncedBlockTimestamp,
    required this.lastSyncedEntryTimestamp,
  });

  final List<RollaBandSleepStage> sleepStages;

  final int lastSyncedBlockTimestamp;
  final int lastSyncedEntryTimestamp;
}

/// API for Flutter to call native platform health/steps operations
@HostApi()
abstract interface class RollaBandHealthDataHostApi {
  /// Fetches steps data from the connected Rolla Band
  ///
  /// [uuid] - Unique identifier of the device to fetch data from
  /// [lastSyncedBlockTimestamp] - Cursor of the last synced block (0 for full sync)
  /// [lastSyncedEntryTimestamp] - Cursor of the last synced entry (0 for full sync)
  ///
  /// Returns [RollaBandStepsSyncResponse] with steps and updated cursors
  @async
  RollaBandStepsSyncResponse getStepsData(
    String uuid,
    int lastSyncedBlockTimestamp,
    int lastSyncedEntryTimestamp,
  );

  @async
  RollaBandHeartRateSyncResponse getHeartRateData(
    String uuid,
    int activityLastSyncedBlockTimestamp,
    int activityLastSyncedEntryTimestamp,
    int passiveLastSyncedTimestamp,
  );

  @async
  RollaBandHRVSyncResponse getHRVData(
    String uuid,
    int lastSyncedBlockTimestamp,
  );

  @async
  RollaBandSleepSyncResponse getSleepData(
    String uuid,
    int lastSyncedBlockTimestamp,
    int lastSyncedEntryTimestamp,
  );
}

