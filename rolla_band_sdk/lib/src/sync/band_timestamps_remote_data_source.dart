import 'package:dio/dio.dart';
import 'package:meta/meta.dart';
import 'package:rolla_band_sdk/src/error/api_exception.dart';

@immutable
class BandTimestampsDto {
  final int? activityHrLastBlock;
  final int? activityHrLastEntry;
  final int? passiveHrLastTimestamp;
  final int? stepsLastBlock;
  final int? stepsLastEntry;
  final int? activeKcalLastTimestamp;
  final int? sleepLastBlock;
  final int? sleepLastEntry;
  final int? hrvLastTimestamp;

  const BandTimestampsDto({
    required this.activityHrLastBlock,
    required this.activityHrLastEntry,
    required this.passiveHrLastTimestamp,
    required this.stepsLastBlock,
    required this.stepsLastEntry,
    required this.activeKcalLastTimestamp,
    required this.sleepLastBlock,
    required this.sleepLastEntry,
    required this.hrvLastTimestamp,
  });

  factory BandTimestampsDto.fromResponse(Map<String, dynamic> response) {
    final Map<String, dynamic>? ts = response['timestamps'] as Map<String, dynamic>?;
    if (ts == null) {
      return const BandTimestampsDto(
        activityHrLastBlock: null,
        activityHrLastEntry: null,
        passiveHrLastTimestamp: null,
        stepsLastBlock: null,
        stepsLastEntry: null,
        activeKcalLastTimestamp: null,
        sleepLastBlock: null,
        sleepLastEntry: null,
        hrvLastTimestamp: null,
      );
    }
    int? _toInt(dynamic v) {
      if (v == null) return null;
      if (v is int) return v;
      if (v is num) return v.toInt();
      if (v is String) return int.tryParse(v);
      return null;
    }

    return BandTimestampsDto(
      activityHrLastBlock: _toInt(ts['activity_hr_last_block']),
      activityHrLastEntry: _toInt(ts['activity_hr_last_entry']),
      passiveHrLastTimestamp: _toInt(ts['passive_hr_last_timestamp']),
      stepsLastBlock: _toInt(ts['steps_last_block']),
      stepsLastEntry: _toInt(ts['steps_last_entry']),
      activeKcalLastTimestamp: _toInt(ts['active_kcal_last_timestamp']),
      sleepLastBlock: _toInt(ts['sleep_last_block']),
      sleepLastEntry: _toInt(ts['sleep_last_entry']),
      hrvLastTimestamp: _toInt(ts['hrv_last_timestamp']),
    );
  }
}

abstract class BandTimestampsRemoteDataSource {
  Future<BandTimestampsDto> getBandTimestamps();
}

class BandTimestampsRemoteDataSourceImpl implements BandTimestampsRemoteDataSource {
  final Dio dio;

  BandTimestampsRemoteDataSourceImpl({required this.dio});

  @override
  Future<BandTimestampsDto> getBandTimestamps() async {
    try {
      final Response<dynamic> response = await dio.get<dynamic>('/api/band_timestamps');
      final dynamic data = response.data;
      if (data is Map && data['success'] == true) {
        final dto = BandTimestampsDto.fromResponse(data as Map<String, dynamic>);
        return dto;
      }
      throw ApiException(reason: 'Failed to load band timestamps', statusCode: response.statusCode);
    } on DioException catch (e) {
      final Response<dynamic>? r = e.response;
      if (r != null && r.data is Map) {
        final Map<String, dynamic> data = r.data as Map<String, dynamic>;
        throw ApiException(reason: (data['reason'] as String?) ?? 'Failed to load band timestamps', statusCode: r.statusCode);
      }
      throw ApiException(reason: 'Failed to load band timestamps', statusCode: r?.statusCode);
    }
  }
}

