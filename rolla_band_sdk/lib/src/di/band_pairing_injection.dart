import 'package:get_it/get_it.dart';
import 'package:dio/dio.dart';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/pairing/band_pairing_repository_impl.dart';
import 'package:rolla_band_sdk/src/pairing/band_remote_data_source.dart';
import 'package:rolla_band_sdk/src/pairing/band_pairing_repository.dart';
import 'package:rolla_band_sdk/src/pairing/usecases.dart';
import 'package:rolla_band_sdk/src/bluetooth/rolla_bluetooth_plugin.dart';

void registerBandPairingFeature(GetIt getIt) {
  if (!getIt.isRegistered<pigeon.RollaBluetoothHostApi>()) {
    getIt.registerLazySingleton<pigeon.RollaBluetoothHostApi>(() => pigeon.RollaBluetoothHostApi());
  }
  getIt.registerLazySingleton<RollaBluetoothPlugin>(() => RollaBluetoothPlugin(hostApi: getIt<pigeon.RollaBluetoothHostApi>()));
  if (!getIt.isRegistered<pigeon.BandCommandHostAPI>()) {
    getIt.registerLazySingleton<pigeon.BandCommandHostAPI>(() => pigeon.BandCommandHostAPI());
  }

  getIt.registerLazySingleton<BandRemoteDataSource>(() => BandRemoteDataSourceImpl(dio: getIt<Dio>()));

  getIt.registerLazySingleton<BandPairingRepository>(() => BandPairingRepositoryImpl(
    bluetooth: getIt<RollaBluetoothPlugin>(),
    remoteDataSource: getIt(),
  ));

  getIt.registerLazySingleton(() => StartScan(getIt()));
  getIt.registerLazySingleton(() => StopScan(getIt()));
  getIt.registerLazySingleton(() => ConnectToDevice(getIt()));
  getIt.registerLazySingleton(() => DisconnectFromDevice(getIt()));
  getIt.registerLazySingleton(() => GetBluetoothState(getIt()));
  getIt.registerLazySingleton(() => GetConnectionState(getIt()));
  getIt.registerLazySingleton(() => StoreBandToBackend(getIt()));
  getIt.registerLazySingleton(() => UnpairBandOnBackend(getIt()));
  getIt.registerLazySingleton(() => GetBatteryLevel(getIt()));
  getIt.registerLazySingleton(() => GetFirmwareVersion(getIt()));
  getIt.registerLazySingleton(() => GetSerialNumber(getIt()));
  getIt.registerLazySingleton(() => DevicesStream(getIt()));
}

