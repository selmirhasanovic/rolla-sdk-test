import 'package:dartz/dartz.dart';
import 'package:rolla_band_sdk/src/error/failures.dart';

abstract class BandSyncRepository {
  Future<Either<Failure, bool>> syncSteps(String deviceUuid, {required int defaultFromDayStartUtc});
  Future<Either<Failure, bool>> syncHeartRate(String deviceUuid, {required int defaultFromDayStartUtc});
  Future<Either<Failure, bool>> syncHRV(String deviceUuid, {required int defaultFromDayStartUtc});
  Future<Either<Failure, bool>> syncSleep(String deviceUuid, {required int defaultFromDayStartUtc});
}

