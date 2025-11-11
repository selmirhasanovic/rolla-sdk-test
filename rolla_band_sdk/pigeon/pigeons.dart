// ignore_for_file: one_member_abstracts

/// Pigeon configuration file for Rolla Band SDK
///
/// This file defines the interface between Flutter and native Android platform
/// for Bluetooth and health data functionality.
///
/// Generated files:
/// - Dart: lib/generated/pigeons.g.dart
/// - Android: android/src/main/kotlin/com/rolla/band/sdk/generated/Messages.g.kt
///
/// To regenerate platform code after changes, run:
/// flutter packages pub run pigeon --input pigeon/pigeons.dart
library pigeons;
import 'package:pigeon/pigeon.dart';

/// Pigeon configuration for code generation
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
class BluetoothDevice {
  BluetoothDevice({
    required this.name,
    required this.rssi,
    required this.uuid,
    required this.capabilities,
    required this.connectionState,
    required this.deviceType,
  });

  final String name;
  final int rssi;
  final String uuid;
  final List<BluetoothCapabilities> capabilities;
  final ConnectionState connectionState;
  final DeviceType deviceType;
}

enum BluetoothCapabilities {
  rsc,
  csc,
  hr,
}

enum BluetoothState {
  poweredOn,
  poweredOff,
  turningOff,
  turningOn,
  unknown,
}

enum ConnectionState {
  connected,
  disconnected,
  connecting,
}

enum DeviceType {
  rollaBand,
  other,
}

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
  final int gender;
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

/// API for Flutter to call native platform Bluetooth operations
@HostApi()
abstract interface class RollaBluetoothHostApi {
  @async
  void scanForDevices({
    required List<DeviceType> deviceTypes,
    int scanDuration = 10000,
  });

  @async
  void stopScanning();

  @async
  bool connectToDevice(String uuid);

  @async
  bool disconnectFromDevice(String uuid);

  @async
  bool disconnectAndRemoveBond(String uuid);

  @async
  ConnectionState checkConnectionState(String uuid);

  @async
  BluetoothState checkBluetoothState();
}

/// API for native platforms to send data back to Flutter
@FlutterApi()
abstract interface class RollaBluetoothFlutterApi {
  void onDevicesFound(List<BluetoothDevice> devices);
  void onConnectionStateChanged(String uuid, ConnectionState state);
  void onBluetoothStateChanged(BluetoothState state);
}

/// API for Flutter to call native platform band operations
@HostApi()
abstract interface class BandCommandHostAPI {
  @async
  void updateUserData(String uuid, UserData userData);

  @async
  UserData getUserData(String uuid);

  @async
  String getFirmwareVersion(String uuid);

  @async
  String getSerialNumber(String uuid);

  @async
  int getBatteryLevel(String uuid);
}

/// API for Flutter to call native platform health data operations
@HostApi()
abstract interface class RollaBandHealthDataHostApi {
  @async
  RollaBandHeartRateSyncResponse getHeartRateData(
    String uuid,
    int activityLastSyncedBlockTimestamp,
    int activityLastSyncedEntryTimestamp,
    int passiveLastSyncedTimestamp,
  );
}

