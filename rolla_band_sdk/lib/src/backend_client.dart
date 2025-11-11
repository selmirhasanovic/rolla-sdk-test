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
      'heart_rate_data': heartRateData,
    });
  }

  Future<List<HeartRateData>> getHeartRate() async {
    final response = await _dio.get('/health/heartrate/get');
    final data = response.data as Map<String, dynamic>;
    final hrData = data['hr_data'] as List<dynamic>? ?? [];
    
    return hrData.map((item) {
      return HeartRateData(
        timestamp: item['timestamp'] as int,
        heartRate: item['hr'] as int,
      );
    }).toList();
  }
}

