library rolla_band_sdk;

export 'config/sdk_config.dart';
export 'src/bluetooth_manager.dart';
export 'src/band_pairing.dart';
export 'src/health_data_sync.dart';
export 'src/backend_client.dart';
export 'src/models/bluetooth_device.dart';
export 'src/models/heart_rate_data.dart';
export 'src/models/band_timestamps.dart';

import 'dart:async';
import 'package:rolla_band_sdk/config/sdk_config.dart';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/bluetooth_manager.dart';
import 'package:rolla_band_sdk/src/band_pairing.dart';
import 'package:rolla_band_sdk/src/health_data_sync.dart';
import 'package:rolla_band_sdk/src/backend_client.dart';
import 'package:rolla_band_sdk/src/models/bluetooth_device.dart';
import 'package:rolla_band_sdk/src/models/heart_rate_data.dart';
import 'package:rolla_band_sdk/src/models/band_timestamps.dart';

class RollaBandSDK {
  static RollaBandSDK? _instance;
  
  static RollaBandSDK get instance {
    _instance ??= RollaBandSDK._();
    return _instance!;
  }
  
  RollaBandSDK._();
  
  late final BluetoothManager _bluetoothManager;
  late final BandPairing _bandPairing;
  late final HealthDataSync _healthDataSync;
  late final BackendClient _backendClient;
  RollaBandSDKConfig? _config;
  bool _initialized = false;

  Future<void> initialize(RollaBandSDKConfig config) async {
    if (_initialized) return;
    _config = config;
    _bluetoothManager = BluetoothManager();
    _backendClient = BackendClient(
      baseUrl: config.backendBaseUrl,
      accessToken: config.accessToken,
      partnerId: config.partnerId,
    );
    _bandPairing = BandPairing(
      bandApi: pigeon.BandCommandHostAPI(),
      backendClient: _backendClient,
      bluetoothApi: pigeon.RollaBluetoothHostApi(),
    );
    _healthDataSync = HealthDataSync(
      healthApi: pigeon.RollaBandHealthDataHostApi(),
    );
    _initialized = true;
  }

  Future<void> scanForDevices({List<pigeon.DeviceType> deviceTypes = const [pigeon.DeviceType.rollaBand]}) async {
    await _bluetoothManager.scanForDevices(deviceTypes: deviceTypes);
  }

  Stream<List<BluetoothDevice>> get devicesStream => _bluetoothManager.devicesStream;

  Future<void> connectToDevice(String uuid) async {
    await _bluetoothManager.connectToDevice(uuid);
  }

  Stream<(String uuid, pigeon.ConnectionState state)> get connectionStateStream =>
      _bluetoothManager.connectionStateStream;

  Future<void> pairDevice(String uuid) async {
    await _bandPairing.pairDevice(uuid);
  }

  /// Syncs heart rate data from the band.
  /// Flow:
  /// 1. Ensure device is connected (required for BLE communication)
  /// 2. Get last sync timestamps from backend (if available)
  /// 3. Fetch new data from band since last timestamps (or from start of today if no timestamps)
  /// 4. Upload data to backend with updated timestamps (if backend is configured)
  /// Returns the heart rate data fetched from the band
  Future<List<HeartRateData>> syncHeartRate(String uuid) async {
    // First, ensure device is connected (required for reading data from band)
    print('[SDK] Checking if device is connected for sync...');
    final connectionState = await _bluetoothManager.checkConnectionState(uuid);
    if (connectionState != pigeon.ConnectionState.connected) {
      print('[SDK] Device not connected, attempting to connect...');
      final connected = await _bluetoothManager.connectToDevice(uuid);
      if (!connected) {
        throw Exception('Device not connected. Please ensure the band is powered on and in range.');
      }
      // Wait for connection to stabilize
      await Future.delayed(const Duration(milliseconds: 2000));
      
      // Verify connection
      final newState = await _bluetoothManager.checkConnectionState(uuid);
      if (newState != pigeon.ConnectionState.connected) {
        throw Exception('Failed to connect to device. Please try again.');
      }
      print('[SDK] Device connected successfully');
    } else {
      print('[SDK] Device already connected');
    }
    print('[SDK] Starting heart rate sync for device: $uuid');
    
    // Get default timestamp (start of current day in UTC)
    final DateTime nowLocal = DateTime.now();
    final DateTime localMidnight = DateTime(nowLocal.year, nowLocal.month, nowLocal.day);
    final DateTime dayStartUtc = localMidnight.toUtc();
    final int defaultFromMs = dayStartUtc.millisecondsSinceEpoch;
    
    print('[SDK] Default timestamp (start of day UTC): $defaultFromMs (${dayStartUtc.toIso8601String()})');

    // 1. Get last sync timestamps from backend (if available)
    print('[SDK] Fetching timestamps from backend...');
    BandTimestamps timestamps = const BandTimestamps();
    try {
      timestamps = await _backendClient.getBandTimestamps();
      print('[SDK] Received timestamps:');
      print('[SDK]   activityHrLastBlock: ${timestamps.activityHrLastBlock}');
      print('[SDK]   activityHrLastEntry: ${timestamps.activityHrLastEntry}');
      print('[SDK]   passiveHrLastTimestamp: ${timestamps.passiveHrLastTimestamp}');
    } catch (e) {
      print('[SDK] Could not fetch timestamps from backend (will use defaults): $e');
    }
    
    final int aBlock = timestamps.activityHrLastBlock ?? defaultFromMs;
    final int aEntry = timestamps.activityHrLastEntry ?? defaultFromMs;
    final int pTs = timestamps.passiveHrLastTimestamp ?? defaultFromMs;
    
    print('[SDK] Using timestamps for sync:');
    print('[SDK]   activityBlock: $aBlock (${timestamps.activityHrLastBlock == null ? "DEFAULT" : "FROM_BACKEND"})');
    print('[SDK]   activityEntry: $aEntry (${timestamps.activityHrLastEntry == null ? "DEFAULT" : "FROM_BACKEND"})');
    print('[SDK]   passiveTimestamp: $pTs (${timestamps.passiveHrLastTimestamp == null ? "DEFAULT" : "FROM_BACKEND"})');

    // 2. Fetch new data from band since last timestamps
    print('[SDK] Fetching heart rate data from band...');
    final syncResult = await _healthDataSync.syncHeartRate(
      uuid,
      activityLastSyncedBlockTimestamp: aBlock,
      activityLastSyncedEntryTimestamp: aEntry,
      passiveLastSyncedTimestamp: pTs,
    );
    
    print('[SDK] Received ${syncResult.heartRates.length} heart rate entries from band');
    print('[SDK] Updated timestamps from band:');
    print('[SDK]   activityLastBlock: ${syncResult.activityLastSyncedBlockTimestamp}');
    print('[SDK]   activityLastEntry: ${syncResult.activityLastSyncedEntryTimestamp}');
    print('[SDK]   passiveLastTimestamp: ${syncResult.passiveLastSyncedTimestamp}');

    // 3. Upload to backend if there's new data (skip if backend not configured or fails)
    if (syncResult.heartRates.isNotEmpty) {
      try {
        print('[SDK] Uploading ${syncResult.heartRates.length} entries to backend...');
        await _backendClient.sendHeartRate(
          data: syncResult.heartRates,
          activityLastBlock: syncResult.activityLastSyncedBlockTimestamp,
          activityLastEntry: syncResult.activityLastSyncedEntryTimestamp,
          passiveLastTimestamp: syncResult.passiveLastSyncedTimestamp,
        );
        print('[SDK] ✓ Upload successful');
      } catch (e) {
        print('[SDK] ⚠ Upload to backend failed (continuing with local data): $e');
      }
    } else {
      print('[SDK] No new data to upload');
    }

    return syncResult.heartRates;
  }

  Future<List<HeartRateData>> getHeartRateFromBackend() async {
    return await _backendClient.getHeartRate();
  }
}

