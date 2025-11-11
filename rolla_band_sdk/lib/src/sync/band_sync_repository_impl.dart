import 'package:dartz/dartz.dart';
import 'package:flutter/services.dart';
import 'package:meta/meta.dart';
import 'package:rolla_band_sdk/src/error/failures.dart';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/sync/band_timestamps_remote_data_source.dart';
import 'package:rolla_band_sdk/src/sync/band_upload_remote_data_source.dart';
import 'package:rolla_band_sdk/src/sync/band_sync_repository.dart';
import 'package:rolla_band_sdk/src/sync/steps_baseline_provider.dart';
import 'package:rolla_band_sdk/src/sync/band_sync_report.dart';

@immutable
class BandSyncRepositoryImpl implements BandSyncRepository {
  final BandTimestampsRemoteDataSource timestampsRemote;
  final BandUploadRemoteDataSource uploadRemote;
  final pigeon.RollaBandHealthDataHostApi hostApi;
  final StepsBaselineProvider stepsBaselineProvider;
  final BandSyncReport syncReport;

  const BandSyncRepositoryImpl({
    required this.timestampsRemote,
    required this.uploadRemote,
    required this.hostApi,
    required this.stepsBaselineProvider,
    required this.syncReport,
  });

  Future<T> _retryPigeon<T>(Future<T> Function() operation) async {
    const int maxAttempts = 5;
    Duration delay = const Duration(milliseconds: 150);
    PlatformException? lastChannelError;
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        return await operation();
      } on PlatformException catch (e) {
        if (e.code == 'channel-error') {
          lastChannelError = e;
          await Future.delayed(delay);
          delay *= 2;
          continue;
        }
        rethrow;
      }
    }
    try {
      return await operation();
    } on PlatformException catch (e) {
      if (e.code == 'channel-error' && lastChannelError != null) {
        throw lastChannelError;
      }
      rethrow;
    }
  }

  String _sleepStageToString(pigeon.RollaBandSleepStageValue v) {
    switch (v) {
      case pigeon.RollaBandSleepStageValue.awake:
        return 'awake';
      case pigeon.RollaBandSleepStageValue.light:
        return 'light';
      case pigeon.RollaBandSleepStageValue.deep:
        return 'deep';
      case pigeon.RollaBandSleepStageValue.rem:
        return 'rem';
    }
  }

  @override
  Future<Either<Failure, bool>> syncSteps(String deviceUuid, {required int defaultFromDayStartUtc}) async {
    try {
      final int defaultFromMs = defaultFromDayStartUtc * 1000;
      final ts = await timestampsRemote.getBandTimestamps();
      final int fromBlock = ts.stepsLastBlock ?? defaultFromMs;
      final int fromEntry = ts.stepsLastEntry ?? defaultFromMs;
      pigeon.RollaBandStepsSyncResponse native = await hostApi.getStepsData(deviceUuid, fromBlock, fromEntry);
      final List<pigeon.RollaBandStep> sortedSteps = List<pigeon.RollaBandStep>.from(native.steps)
        ..sort((a, b) => a.timestamp.compareTo(b.timestamp));
      if (sortedSteps.isEmpty) return const Right(false);

      final List<StepsUploadItem> rawDeltaItems = <StepsUploadItem>[
        for (final pigeon.RollaBandStep s in sortedSteps)
          StepsUploadItem(
            timestamp: s.timestamp,
            stepsDelta: s.steps,
            caloriesDelta: s.calories.round(),
          ),
      ];

      await uploadRemote.uploadSteps(
        items: rawDeltaItems,
        stepsLastBlock: native.lastSyncedBlockTimestamp,
        stepsLastEntry: native.lastSyncedEntryTimestamp,
      );
      syncReport.addUtcTimestamps(rawDeltaItems.map((e) => e.timestamp));
      return const Right(true);
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, bool>> syncHeartRate(String deviceUuid, {required int defaultFromDayStartUtc}) async {
    try {
      final int defaultFromMs = defaultFromDayStartUtc * 1000;
      final ts = await timestampsRemote.getBandTimestamps();
      final int aBlock = ts.activityHrLastBlock ?? defaultFromMs;
      final int aEntry = ts.activityHrLastEntry ?? defaultFromMs;
      final int pTs = ts.passiveHrLastTimestamp ?? defaultFromMs;
      pigeon.RollaBandHeartRateSyncResponse native = await hostApi.getHeartRateData(deviceUuid, aBlock, aEntry, pTs);
      if (native.heartRates.isEmpty) return const Right(false);
      await uploadRemote.uploadHeartRate(
        items: native.heartRates.map((h) => HeartRateUploadItem(timestamp: h.timestamp, hr: h.hr)).toList(),
        activityLastBlock: native.activityLastSyncedBlockTimestamp,
        activityLastEntry: native.activityLastSyncedEntryTimestamp,
        passiveLastTimestamp: native.passiveLastSyncedTimestamp,
      );
      syncReport.addUtcTimestamps(native.heartRates.map((h) => h.timestamp));
      return const Right(true);
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, bool>> syncHRV(String deviceUuid, {required int defaultFromDayStartUtc}) async {
    try {
      final int defaultFromMs = defaultFromDayStartUtc * 1000;
      final ts = await timestampsRemote.getBandTimestamps();
      final int fromTs = ts.hrvLastTimestamp ?? defaultFromMs;
      pigeon.RollaBandHRVSyncResponse native = await hostApi.getHRVData(deviceUuid, fromTs);
      if (native.hrvs.isEmpty) return const Right(false);
      await uploadRemote.uploadHRV(
        items: native.hrvs.map((h) => HRVUploadItem(timestamp: h.timestamp, hrv: h.hrv)).toList(),
        lastTimestamp: native.lastSyncedBlockTimestamp,
      );
      syncReport.addUtcTimestamps(native.hrvs.map((h) => h.timestamp));
      return const Right(true);
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, bool>> syncSleep(String deviceUuid, {required int defaultFromDayStartUtc}) async {
    try {
      final int defaultFromMs = defaultFromDayStartUtc * 1000;
      final ts = await timestampsRemote.getBandTimestamps();
      final int fromBlock = ts.sleepLastBlock ?? defaultFromMs;
      final int fromEntry = ts.sleepLastEntry ?? defaultFromMs;
      pigeon.RollaBandSleepSyncResponse native = await _retryPigeon(() => hostApi.getSleepData(deviceUuid, fromBlock, fromEntry));
      if (native.sleepStages.isEmpty) return const Right(false);
      await uploadRemote.uploadSleep(
        items: native.sleepStages.map((s) => SleepUploadItem(startTime: s.startTime, endTime: s.endTime, stage: _sleepStageToString(s.value))).toList(),
        lastBlock: native.lastSyncedBlockTimestamp,
        lastEntry: native.lastSyncedEntryTimestamp,
      );
      for (final s in native.sleepStages) {
        syncReport.addUtcRange(s.startTime, s.endTime);
      }
      return const Right(true);
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }
}

