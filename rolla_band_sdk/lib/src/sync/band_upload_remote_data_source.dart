import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:meta/meta.dart';
import 'package:rolla_band_sdk/src/error/api_exception.dart';

@immutable
class StepsUploadItem {
  final int timestamp;
  final int stepsDelta;
  final int caloriesDelta;
  const StepsUploadItem({required this.timestamp, required this.stepsDelta, required this.caloriesDelta});

  Map<String, dynamic> toJson() => {
    'timestamp': timestamp,
    'steps_delta': stepsDelta,
    'calories_delta': caloriesDelta,
  };
}

@immutable
class HeartRateUploadItem {
  final int timestamp;
  final int hr;
  const HeartRateUploadItem({required this.timestamp, required this.hr});

  Map<String, dynamic> toJson() => {
    'timestamp': timestamp,
    'hr': hr,
  };
}

@immutable
class HRVUploadItem {
  final int timestamp;
  final int hrv;
  const HRVUploadItem({required this.timestamp, required this.hrv});

  Map<String, dynamic> toJson() => {
    'timestamp': timestamp,
    'hrv': hrv,
  };
}

@immutable
class SleepUploadItem {
  final int startTime;
  final int endTime;
  final String stage;
  const SleepUploadItem({required this.startTime, required this.endTime, required this.stage});

  Map<String, dynamic> toJson() => {
    'start_time': startTime,
    'end_time': endTime,
    'stage': stage,
  };
}

abstract class BandUploadRemoteDataSource {
  Future<void> uploadSteps({required List<StepsUploadItem> items, int? stepsLastBlock, int? stepsLastEntry});
  Future<void> uploadHeartRate({required List<HeartRateUploadItem> items, int? activityLastBlock, int? activityLastEntry, int? passiveLastTimestamp});
  Future<void> uploadHRV({required List<HRVUploadItem> items, int? lastTimestamp});
  Future<void> uploadSleep({required List<SleepUploadItem> items, int? lastBlock, int? lastEntry});
}

class BandUploadRemoteDataSourceImpl implements BandUploadRemoteDataSource {
  final Dio dio;

  BandUploadRemoteDataSourceImpl({required this.dio});

  @override
  Future<void> uploadSteps({required List<StepsUploadItem> items, int? stepsLastBlock, int? stepsLastEntry}) async {
    if (items.isEmpty) return;
    try {
      final Map<String, dynamic> data = {
        'steps': jsonEncode(items.map((e) => e.toJson()).toList()),
      };
      if (stepsLastBlock != null) data['steps_last_block'] = stepsLastBlock;
      if (stepsLastEntry != null) data['steps_last_entry'] = stepsLastEntry;
      final Response<dynamic> response = await dio.post<dynamic>('/health/steps/add', data: data);
      final dynamic body = response.data;
      if (body is Map && body['success'] == true) {
        return;
      }
      throw ApiException(reason: (body is Map ? (body['reason'] as String?) : null) ?? 'Steps upload failed', statusCode: response.statusCode);
    } on DioException catch (e) {
      final Response<dynamic>? r = e.response;
      if (r != null && r.data is Map) {
        final Map<String, dynamic> data = r.data as Map<String, dynamic>;
        throw ApiException(reason: (data['reason'] as String?) ?? 'Steps upload failed', statusCode: r.statusCode);
      }
      throw ApiException(reason: 'Steps upload failed', statusCode: r?.statusCode);
    }
  }

  @override
  Future<void> uploadHeartRate({required List<HeartRateUploadItem> items, int? activityLastBlock, int? activityLastEntry, int? passiveLastTimestamp}) async {
    if (items.isEmpty) return;
    try {
      final Map<String, dynamic> data = {
        'heart_rate_data': jsonEncode(items.map((e) => e.toJson()).toList()),
      };
      if (activityLastBlock != null) data['activity_hr_last_block'] = activityLastBlock;
      if (activityLastEntry != null) data['activity_hr_last_entry'] = activityLastEntry;
      if (passiveLastTimestamp != null) data['passive_hr_last_timestamp'] = passiveLastTimestamp;
      final Response<dynamic> response = await dio.post<dynamic>('/health/heartrate/add', data: data);
      final dynamic body = response.data;
      if (body is Map && body['success'] == true) {
        return;
      }
      throw ApiException(reason: (body is Map ? (body['reason'] as String?) : null) ?? 'Heart rate upload failed', statusCode: response.statusCode);
    } on DioException catch (e) {
      final Response<dynamic>? r = e.response;
      if (r != null && r.data is Map) {
        final Map<String, dynamic> data = r.data as Map<String, dynamic>;
        throw ApiException(reason: (data['reason'] as String?) ?? 'Heart rate upload failed', statusCode: r.statusCode);
      }
      throw ApiException(reason: 'Heart rate upload failed', statusCode: r?.statusCode);
    }
  }

  @override
  Future<void> uploadHRV({required List<HRVUploadItem> items, int? lastTimestamp}) async {
    if (items.isEmpty) return;
    try {
      final Map<String, dynamic> data = {
        'hrv_data': jsonEncode(items.map((e) => e.toJson()).toList()),
      };
      if (lastTimestamp != null) data['hrv_last_timestamp'] = lastTimestamp;
      final Response<dynamic> response = await dio.post<dynamic>('/health/hrv/add', data: data);
      final dynamic body = response.data;
      if (body is Map && body['success'] == true) {
        return;
      }
      throw ApiException(reason: (body is Map ? (body['reason'] as String?) : null) ?? 'HRV upload failed', statusCode: response.statusCode);
    } on DioException catch (e) {
      final Response<dynamic>? r = e.response;
      if (r != null && r.data is Map) {
        final Map<String, dynamic> data = r.data as Map<String, dynamic>;
        throw ApiException(reason: (data['reason'] as String?) ?? 'HRV upload failed', statusCode: r.statusCode);
      }
      throw ApiException(reason: 'HRV upload failed', statusCode: r?.statusCode);
    }
  }

  @override
  Future<void> uploadSleep({required List<SleepUploadItem> items, int? lastBlock, int? lastEntry}) async {
    if (items.isEmpty) return;
    try {
      final Map<String, dynamic> data = {
        'sleep_data': jsonEncode(items.map((e) => e.toJson()).toList()),
      };
      if (lastBlock != null) data['sleep_last_block'] = lastBlock;
      if (lastEntry != null) data['sleep_last_entry'] = lastEntry;
      final Response<dynamic> response = await dio.post<dynamic>('/health/sleep/add', data: data);
      final dynamic body = response.data;
      if (body is Map && body['success'] == true) {
        return;
      }
      throw ApiException(reason: (body is Map ? (body['reason'] as String?) : null) ?? 'Sleep upload failed', statusCode: response.statusCode);
    } on DioException catch (e) {
      final Response<dynamic>? r = e.response;
      if (r != null && r.data is Map) {
        final Map<String, dynamic> data = r.data as Map<String, dynamic>;
        throw ApiException(reason: (data['reason'] as String?) ?? 'Sleep upload failed', statusCode: r.statusCode);
      }
      throw ApiException(reason: 'Sleep upload failed', statusCode: r?.statusCode);
    }
  }
}

