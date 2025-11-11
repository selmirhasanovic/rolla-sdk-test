import 'package:dartz/dartz.dart';
import 'package:rolla_band_sdk/src/error/failures.dart';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:meta/meta.dart';
import 'package:rolla_band_sdk/src/pairing/usecases.dart' as pairing_usecases;
import 'package:rolla_band_sdk/src/sync/band_sync_repository.dart';

class SyncSteps {
  final BandSyncRepository repository;
  SyncSteps(this.repository);
  Future<Either<Failure, bool>> call(String deviceUuid, {required int defaultFromDayStartUtc}) async {
    final res = await repository.syncSteps(deviceUuid, defaultFromDayStartUtc: defaultFromDayStartUtc);
    return res;
  }
}

class SyncHeartRate {
  final BandSyncRepository repository;
  SyncHeartRate(this.repository);
  Future<Either<Failure, bool>> call(String deviceUuid, {required int defaultFromDayStartUtc}) => repository.syncHeartRate(deviceUuid, defaultFromDayStartUtc: defaultFromDayStartUtc);
}

class SyncHRV {
  final BandSyncRepository repository;
  SyncHRV(this.repository);
  Future<Either<Failure, bool>> call(String deviceUuid, {required int defaultFromDayStartUtc}) => repository.syncHRV(deviceUuid, defaultFromDayStartUtc: defaultFromDayStartUtc);
}

class SyncSleep {
  final BandSyncRepository repository;
  SyncSleep(this.repository);
  Future<Either<Failure, bool>> call(String deviceUuid, {required int defaultFromDayStartUtc}) async {
    final res = await repository.syncSleep(deviceUuid, defaultFromDayStartUtc: defaultFromDayStartUtc);
    return res;
  }
}

class SyncAll {
  final SyncSteps syncSteps;
  final SyncHeartRate syncHeartRate;
  final SyncHRV syncHRV;
  final SyncSleep syncSleep;

  SyncAll({required this.syncSteps, required this.syncHeartRate, required this.syncHRV, required this.syncSleep});

  Future<Either<Failure, bool>> call(String deviceUuid, {required int defaultFromDayStartUtc, void Function(SyncStage stage, int index, int total, bool started, bool? uploaded)? onStage}) async {
    bool any = false;
    onStage?.call(SyncStage.sleep, 0, 4, true, null);
    final sleepResult = await syncSleep(deviceUuid, defaultFromDayStartUtc: defaultFromDayStartUtc);
    onStage?.call(SyncStage.sleep, 0, 4, false, sleepResult.fold((_) => null, (r) => r));
    sleepResult.fold((_) {}, (uploaded) { any = any || uploaded; });

    onStage?.call(SyncStage.heartRate, 1, 4, true, null);
    final hrResult = await syncHeartRate(deviceUuid, defaultFromDayStartUtc: defaultFromDayStartUtc);
    onStage?.call(SyncStage.heartRate, 1, 4, false, hrResult.fold((_) => null, (r) => r));
    hrResult.fold((_) {}, (uploaded) { any = any || uploaded; });

    onStage?.call(SyncStage.hrv, 2, 4, true, null);
    final hrvResult = await syncHRV(deviceUuid, defaultFromDayStartUtc: defaultFromDayStartUtc);
    onStage?.call(SyncStage.hrv, 2, 4, false, hrvResult.fold((_) => null, (r) => r));
    hrvResult.fold((_) {}, (uploaded) { any = any || uploaded; });

    onStage?.call(SyncStage.steps, 3, 4, true, null);
    final stepsResult = await syncSteps(deviceUuid, defaultFromDayStartUtc: defaultFromDayStartUtc);
    onStage?.call(SyncStage.steps, 3, 4, false, stepsResult.fold((_) => null, (r) => r));
    stepsResult.fold((_) {}, (uploaded) { any = any || uploaded; });
    return Right(any);
  }
}

enum SyncStage { sleep, heartRate, hrv, steps }

@immutable
class EnsureDeviceReadyConfig {
  final Duration connectTimeout;
  final int liveValidationAttempts;
  final Duration liveValidationDelay;
  final Duration liveValidationCallTimeout;
  const EnsureDeviceReadyConfig({
    this.connectTimeout = const Duration(seconds: 10),
    this.liveValidationAttempts = 8,
    this.liveValidationDelay = const Duration(milliseconds: 300),
    this.liveValidationCallTimeout = const Duration(seconds: 2),
  });
}

class EnsureDeviceReadyForSync {
  final pairing_usecases.GetConnectionState getConnectionState;
  final pairing_usecases.ConnectToDevice connectToDevice;
  final pairing_usecases.GetBatteryLevel getBatteryLevel;

  EnsureDeviceReadyForSync({
    required this.getConnectionState,
    required this.connectToDevice,
    required this.getBatteryLevel,
  });

  Future<Either<Failure, Unit>> call(String deviceUuid, {EnsureDeviceReadyConfig config = const EnsureDeviceReadyConfig()}) async {
    try {
      final s0 = await getConnectionState(deviceUuid);
      final pigeon.ConnectionState? c0 = s0.fold((_) => null, (v) => v);
      if (c0 != pigeon.ConnectionState.connected) {
        await connectToDevice(deviceUuid);
        final DateTime start = DateTime.now();
        while (DateTime.now().difference(start) < config.connectTimeout) {
          await Future.delayed(const Duration(milliseconds: 300));
          final s1 = await getConnectionState(deviceUuid);
          final pigeon.ConnectionState? c1 = s1.fold((_) => null, (v) => v);
          if (c1 == pigeon.ConnectionState.connected) break;
        }
        final s2 = await getConnectionState(deviceUuid);
        final pigeon.ConnectionState? c2 = s2.fold((_) => null, (v) => v);
        if (c2 != pigeon.ConnectionState.connected) {
          return const Left(NetworkFailure('Band not connected'));
        }
      }

      for (int i = 0; i < config.liveValidationAttempts; i++) {
        try {
          final r = await getBatteryLevel(deviceUuid).timeout(config.liveValidationCallTimeout);
          final bool ok = r.fold((_) => false, (_) => true);
          if (ok) return const Right(unit);
        } catch (_) {}
        await Future.delayed(config.liveValidationDelay);
      }
      return const Left(NetworkFailure('Band not connected'));
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }
}

