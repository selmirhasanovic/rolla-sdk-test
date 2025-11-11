import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/models/heart_rate_data.dart';

class HeartRateSyncResult {
  final List<HeartRateData> heartRates;
  final int activityLastSyncedBlockTimestamp;
  final int activityLastSyncedEntryTimestamp;
  final int passiveLastSyncedTimestamp;

  HeartRateSyncResult({
    required this.heartRates,
    required this.activityLastSyncedBlockTimestamp,
    required this.activityLastSyncedEntryTimestamp,
    required this.passiveLastSyncedTimestamp,
  });
}

class HealthDataSync {
  final pigeon.RollaBandHealthDataHostApi _healthApi;

  HealthDataSync({required pigeon.RollaBandHealthDataHostApi healthApi})
      : _healthApi = healthApi;

  Future<HeartRateSyncResult> syncHeartRate(
    String uuid, {
    required int activityLastSyncedBlockTimestamp,
    required int activityLastSyncedEntryTimestamp,
    required int passiveLastSyncedTimestamp,
  }) async {
    final response = await _healthApi.getHeartRateData(
      uuid,
      activityLastSyncedBlockTimestamp,
      activityLastSyncedEntryTimestamp,
      passiveLastSyncedTimestamp,
    );

    // Band returns timestamps in milliseconds, convert to seconds for consistency with backend
    final heartRates = response.heartRates.map((hr) {
      return HeartRateData(
        timestamp: hr.timestamp ~/ 1000, // Convert ms to seconds
        heartRate: hr.hr,
      );
    }).toList();

    return HeartRateSyncResult(
      heartRates: heartRates,
      activityLastSyncedBlockTimestamp: response.activityLastSyncedBlockTimestamp,
      activityLastSyncedEntryTimestamp: response.activityLastSyncedEntryTimestamp,
      passiveLastSyncedTimestamp: response.passiveLastSyncedTimestamp,
    );
  }
}

