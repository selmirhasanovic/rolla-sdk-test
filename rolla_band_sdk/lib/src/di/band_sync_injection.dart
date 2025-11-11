import 'package:get_it/get_it.dart';
import 'package:dio/dio.dart';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/sync/band_timestamps_remote_data_source.dart';
import 'package:rolla_band_sdk/src/sync/band_upload_remote_data_source.dart';
import 'package:rolla_band_sdk/src/sync/band_sync_repository_impl.dart';
import 'package:rolla_band_sdk/src/sync/band_sync_repository.dart';
import 'package:rolla_band_sdk/src/sync/usecases.dart';
import 'package:rolla_band_sdk/src/sync/steps_baseline_provider.dart';
import 'package:rolla_band_sdk/src/sync/band_sync_report.dart';

void registerBandSyncFeature(GetIt getIt) {
  if (!getIt.isRegistered<pigeon.RollaBandHealthDataHostApi>()) {
    getIt.registerLazySingleton<pigeon.RollaBandHealthDataHostApi>(() => pigeon.RollaBandHealthDataHostApi());
  }

  getIt.registerLazySingleton<BandTimestampsRemoteDataSource>(() => BandTimestampsRemoteDataSourceImpl(dio: getIt<Dio>()));
  getIt.registerLazySingleton<BandUploadRemoteDataSource>(() => BandUploadRemoteDataSourceImpl(dio: getIt<Dio>()));

  getIt.registerLazySingleton<BandSyncReport>(() => BandSyncReport());

  getIt.registerLazySingleton<BandSyncRepository>(() => BandSyncRepositoryImpl(
        timestampsRemote: getIt(),
        uploadRemote: getIt(),
        hostApi: getIt(),
        stepsBaselineProvider: getIt<StepsBaselineProvider>(),
        syncReport: getIt<BandSyncReport>(),
      ));

  getIt.registerLazySingleton(() => SyncSteps(getIt()));
  getIt.registerLazySingleton(() => SyncHeartRate(getIt()));
  getIt.registerLazySingleton(() => SyncHRV(getIt()));
  getIt.registerLazySingleton(() => SyncSleep(getIt()));
  getIt.registerLazySingleton(() => SyncAll(syncSteps: getIt(), syncHeartRate: getIt(), syncHRV: getIt(), syncSleep: getIt()));
  getIt.registerLazySingleton(() => EnsureDeviceReadyForSync(
        getConnectionState: getIt(),
        connectToDevice: getIt(),
        getBatteryLevel: getIt(),
      ));
}

