library rolla_band_sdk;

export 'config/sdk_config.dart';
export 'src/pairing/band_pairing_repository.dart';
export 'src/sync/band_sync_repository.dart';
export 'src/pairing/usecases.dart';
export 'src/sync/usecases.dart';
export 'src/error/failures.dart';
export 'generated/pigeons.g.dart';

import 'package:rolla_band_sdk/config/sdk_config.dart';
import 'package:rolla_band_sdk/src/network/api_config.dart';
import 'package:rolla_band_sdk/src/di/sdk_injector.dart';
import 'package:rolla_band_sdk/src/pairing/band_pairing_repository.dart';
import 'package:rolla_band_sdk/src/sync/band_sync_repository.dart';
import 'package:rolla_band_sdk/src/pairing/usecases.dart';
import 'package:rolla_band_sdk/src/sync/usecases.dart';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;

class RollaBandSDK {
  static RollaBandSDK? _instance;

  static RollaBandSDK get instance {
    _instance ??= RollaBandSDK._();
    return _instance!;
  }

  RollaBandSDK._();

  bool _initialized = false;

  Future<void> initialize(RollaBandSDKConfig config) async {
    if (_initialized) return;

    final apiConfig = ApiConfig(
      baseUrl: config.backendBaseUrl,
      partnerId: config.partnerId ?? 'ross_dfb67c5b8525a2ff',
    );

    await initSdkDependencies(
      apiConfig: apiConfig,
      accessToken: config.accessToken,
      partnerId: config.partnerId,
    );

    _initialized = true;
  }

  BandPairingRepository get pairingRepository => sdkGetIt<BandPairingRepository>();
  BandSyncRepository get syncRepository => sdkGetIt<BandSyncRepository>();

  StartScan get startScan => sdkGetIt<StartScan>();
  StopScan get stopScan => sdkGetIt<StopScan>();
  ConnectToDevice get connectToDevice => sdkGetIt<ConnectToDevice>();
  DisconnectFromDevice get disconnectFromDevice => sdkGetIt<DisconnectFromDevice>();
  GetBluetoothState get getBluetoothState => sdkGetIt<GetBluetoothState>();
  GetConnectionState get getConnectionState => sdkGetIt<GetConnectionState>();
  StoreBandToBackend get storeBandToBackend => sdkGetIt<StoreBandToBackend>();
  UnpairBandOnBackend get unpairBandOnBackend => sdkGetIt<UnpairBandOnBackend>();
  GetBatteryLevel get getBatteryLevel => sdkGetIt<GetBatteryLevel>();
  GetFirmwareVersion get getFirmwareVersion => sdkGetIt<GetFirmwareVersion>();
  GetSerialNumber get getSerialNumber => sdkGetIt<GetSerialNumber>();

  SyncSteps get syncSteps => sdkGetIt<SyncSteps>();
  SyncHeartRate get syncHeartRate => sdkGetIt<SyncHeartRate>();
  SyncHRV get syncHRV => sdkGetIt<SyncHRV>();
  SyncSleep get syncSleep => sdkGetIt<SyncSleep>();
  SyncAll get syncAll => sdkGetIt<SyncAll>();
  EnsureDeviceReadyForSync get ensureDeviceReadyForSync => sdkGetIt<EnsureDeviceReadyForSync>();

  Stream<List<pigeon.BluetoothDevice>> devicesStream() => pairingRepository.devicesStream();
  Stream<(String uuid, pigeon.ConnectionState state)> connectionStateStream() => pairingRepository.connectionStateStream();
  Stream<pigeon.BluetoothState> bluetoothStateStream() => pairingRepository.bluetoothStateStream();
}

