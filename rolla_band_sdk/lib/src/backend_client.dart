import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:rolla_band_sdk/src/models/heart_rate_data.dart';
import 'package:rolla_band_sdk/src/models/band_timestamps.dart';

class BackendClient {
  final Dio _dio;
  final String _baseUrl;

  BackendClient({required String baseUrl, required String accessToken})
      : _baseUrl = baseUrl,
        _dio = Dio(BaseOptions(
          baseUrl: baseUrl,
          headers: {'Authorization': 'Bearer $accessToken'},
        ));

  Future<void> storeBand(String macAddress) async {
    await _dio.post('/api/store_user_band', data: {'mac_address': macAddress});
  }

  Future<BandTimestamps> getBandTimestamps() async {
    try {
      final Response<dynamic> response = await _dio.get<dynamic>('/api/band_timestamps');
      final dynamic data = response.data;
      if (data is Map && data['success'] == true) {
        return BandTimestamps.fromResponse(data as Map<String, dynamic>);
      }
      throw Exception('Failed to load band timestamps: ${response.statusCode}');
    } on DioError catch (e) {
      final Response<dynamic>? r = e.response;
      if (r != null && r.data is Map) {
        final Map<String, dynamic> data = r.data as Map<String, dynamic>;
        throw Exception((data['reason'] as String?) ?? 'Failed to load band timestamps');
      }
      throw Exception('Failed to load band timestamps: ${r?.statusCode}');
    }
  }

  Future<void> sendHeartRate({
    required List<HeartRateData> data,
    int? activityLastBlock,
    int? activityLastEntry,
    int? passiveLastTimestamp,
  }) async {
    if (data.isEmpty) return;
    
    final heartRateData = data.map((hr) => {
      'timestamp': hr.timestamp,
      'hr': hr.heartRate,
    }).toList();
    
    final Map<String, dynamic> requestData = {
      'heart_rate_data': jsonEncode(heartRateData),
    };
    
    if (activityLastBlock != null) requestData['activity_hr_last_block'] = activityLastBlock;
    if (activityLastEntry != null) requestData['activity_hr_last_entry'] = activityLastEntry;
    if (passiveLastTimestamp != null) requestData['passive_hr_last_timestamp'] = passiveLastTimestamp;
    
    final response = await _dio.post('/health/heartrate/add', data: requestData);
    final dynamic body = response.data;
    if (body is Map && body['success'] != true) {
      throw Exception((body['reason'] as String?) ?? 'Heart rate upload failed');
    }
  }

  Future<List<HeartRateData>> getHeartRate() async {
    final response = await _dio.post('/health/heartrate/get', data: {
      'from': '2025-01-01',
      'to': '2025-12-31',
      'type': 'daily',
    });
    final data = response.data as Map<String, dynamic>;
    final hrData = data['hr_data'] as List<dynamic>? ?? [];
    
    return hrData.map((item) {
      return HeartRateData(
        timestamp: DateTime.parse(item['period_start'] as String).millisecondsSinceEpoch ~/ 1000,
        heartRate: (item['avg'] as num).toInt(),
      );
    }).toList();
  }
}

