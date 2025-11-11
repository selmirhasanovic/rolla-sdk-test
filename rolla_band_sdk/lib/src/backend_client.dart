import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:rolla_band_sdk/src/models/heart_rate_data.dart';

class BackendClient {
  final Dio _dio;
  final String _baseUrl;

  BackendClient({required String baseUrl, String? accessToken})
      : _baseUrl = baseUrl,
        _dio = Dio(BaseOptions(
          baseUrl: baseUrl,
          headers: accessToken != null ? {'Authorization': 'Bearer $accessToken'} : {},
        ));

  Future<void> storeBand(String macAddress) async {
    await _dio.post('/api/store_user_band', data: {'mac_address': macAddress});
  }

  Future<void> sendHeartRate(List<HeartRateData> data) async {
    final heartRateData = data.map((hr) => {
      'timestamp': hr.timestamp,
      'hr': hr.heartRate,
    }).toList();
    
    await _dio.post('/health/heartrate/add', data: {
      'heart_rate_data': jsonEncode(heartRateData),
    });
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

