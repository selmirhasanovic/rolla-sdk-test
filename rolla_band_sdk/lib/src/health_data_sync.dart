import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/models/heart_rate_data.dart';

class HealthDataSync {
  final pigeon.RollaBandHealthDataHostApi _healthApi;

  HealthDataSync({required pigeon.RollaBandHealthDataHostApi healthApi})
      : _healthApi = healthApi;

  Future<List<HeartRateData>> syncHeartRate(
    String uuid, {
    int activityLastSyncedBlockTimestamp = 0,
    int activityLastSyncedEntryTimestamp = 0,
    int passiveLastSyncedTimestamp = 0,
  }) async {
    final response = await _healthApi.getHeartRateData(
      uuid,
      activityLastSyncedBlockTimestamp,
      activityLastSyncedEntryTimestamp,
      passiveLastSyncedTimestamp,
    );

    return response.heartRates.map((hr) {
      return HeartRateData(
        timestamp: hr.timestamp,
        heartRate: hr.hr,
      );
    }).toList();
  }
}

