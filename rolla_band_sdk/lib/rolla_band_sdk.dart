library rolla_band_sdk;

export 'config/sdk_config.dart';
export 'src/bluetooth_manager.dart';
export 'src/band_pairing.dart';
export 'src/health_data_sync.dart';
export 'src/backend_client.dart';
export 'src/models/bluetooth_device.dart';
export 'src/models/heart_rate_data.dart';

import 'dart:async';
import 'package:rolla_band_sdk/config/sdk_config.dart';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/bluetooth_manager.dart';
import 'package:rolla_band_sdk/src/band_pairing.dart';
import 'package:rolla_band_sdk/src/health_data_sync.dart';
import 'package:rolla_band_sdk/src/backend_client.dart';
import 'package:rolla_band_sdk/src/models/bluetooth_device.dart';
import 'package:rolla_band_sdk/src/models/heart_rate_data.dart';

class RollaBandSDK {
  late final BluetoothManager _bluetoothManager;
  late final BandPairing _bandPairing;
  late final HealthDataSync _healthDataSync;
  late final BackendClient _backendClient;
  RollaBandSDKConfig? _config;

  Future<void> initialize(RollaBandSDKConfig config) async {
    _config = config;
    _bluetoothManager = BluetoothManager();
    _backendClient = BackendClient(
      baseUrl: config.backendBaseUrl,
      accessToken: config.accessToken,
    );
    _bandPairing = BandPairing(
      bandApi: pigeon.BandCommandHostAPI(),
      backendClient: _backendClient,
    );
    _healthDataSync = HealthDataSync(
      healthApi: pigeon.RollaBandHealthDataHostApi(),
    );
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

  Future<List<HeartRateData>> syncHeartRate(String uuid) async {
    return await _healthDataSync.syncHeartRate(uuid);
  }

  Future<void> sendHeartRateToBackend(List<HeartRateData> data) async {
    await _backendClient.sendHeartRate(data);
  }

  Future<List<HeartRateData>> getHeartRateFromBackend() async {
    return await _backendClient.getHeartRate();
  }
}

