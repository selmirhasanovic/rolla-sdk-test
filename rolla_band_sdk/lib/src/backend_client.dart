import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:dio/dio.dart';
import 'package:rolla_band_sdk/src/models/heart_rate_data.dart';
import 'package:rolla_band_sdk/src/models/band_timestamps.dart';

class BackendClient {
  final Dio _dio;
  final String _baseUrl;

  BackendClient({
    required String baseUrl,
    required String accessToken,
    String partnerId = 'ross_dfb67c5b8525a2ff',
  })  : _baseUrl = baseUrl,
        _dio = Dio(BaseOptions(
          baseUrl: baseUrl,
          contentType: Headers.formUrlEncodedContentType,
          headers: {
            'Authorization': 'Bearer $accessToken',
            'Partner-ID': partnerId,
          },
        ));

  Future<void> storeBand(String macAddress) async {
    try {
      debugPrint('[BackendClient] POST /api/store_user_band with mac_address: $macAddress');
      debugPrint('[BackendClient] Base URL: $_baseUrl');
      debugPrint('[BackendClient] Content-Type: ${Headers.formUrlEncodedContentType}');
      debugPrint('[BackendClient] Full URL will be: $_baseUrl/api/store_user_band');
      debugPrint('[BackendClient] Request data: {mac_address: $macAddress}');
      debugPrint('[BackendClient] Headers: ${_dio.options.headers}');
      debugPrint('[BackendClient] Dio options contentType: ${_dio.options.contentType}');
      
      final Response<dynamic> response = await _dio.post<dynamic>(
        '/api/store_user_band',
        data: {'mac_address': macAddress},
        options: Options(
          contentType: Headers.formUrlEncodedContentType,
          headers: {
            'Content-Type': Headers.formUrlEncodedContentType,
          },
        ),
      );
      final dynamic data = response.data;
      debugPrint('[BackendClient] Response status: ${response.statusCode}');
      debugPrint('[BackendClient] Response data: $data');
      
      if (data is Map && data['success'] == true) {
        print('[BackendClient] ✓ Band stored successfully');
        return;
      }
      
      final reason = (data is Map ? (data['reason'] as String?) : null) ?? 'Store band failed';
      throw Exception(reason);
    } on DioException catch (e) {
      debugPrint('[BackendClient] DioException storing band: ${e.message}');
      debugPrint('[BackendClient] Error type: ${e.type}');
      debugPrint('[BackendClient] Request path: ${e.requestOptions.path}');
      debugPrint('[BackendClient] Request data: ${e.requestOptions.data}');
      debugPrint('[BackendClient] Request headers: ${e.requestOptions.headers}');
      debugPrint('[BackendClient] Full request URL: ${e.requestOptions.uri}');
      debugPrint('[BackendClient] Request method: ${e.requestOptions.method}');
      debugPrint('[BackendClient] Request content type: ${e.requestOptions.contentType}');
      final Response<dynamic>? r = e.response;
      if (r != null) {
        debugPrint('[BackendClient] Response status: ${r.statusCode}');
        debugPrint('[BackendClient] Response data: ${r.data}');
        debugPrint('[BackendClient] Response headers: ${r.headers}');
        if (r.data is Map) {
          final Map<String, dynamic> data = r.data as Map<String, dynamic>;
          final reason = (data['reason'] as String?) ?? 'Store band failed';
          debugPrint('[BackendClient] Extracted reason: $reason');
          throw Exception(reason);
        }
        // If response is not a Map, include the raw response in the error
        throw Exception('Store band failed (${r.statusCode}): ${r.data}');
      }
      throw Exception('Store band failed: ${e.message}');
    } catch (e) {
      debugPrint('[BackendClient] Unexpected error: $e');
      rethrow;
    }
  }

  Future<BandTimestamps> getBandTimestamps() async {
    try {
      print('[BackendClient] GET /api/band_timestamps');
      final Response<dynamic> response = await _dio.get<dynamic>('/api/band_timestamps');
      final dynamic data = response.data;
      print('[BackendClient] Response status: ${response.statusCode}');
      print('[BackendClient] Response data: $data');
      
      if (data == null) {
        print('[BackendClient] Response data is null, returning empty timestamps (initial sync)');
        return const BandTimestamps();
      }
      
      if (data is Map) {
        if (data['success'] == true) {
          final timestamps = BandTimestamps.fromResponse(data as Map<String, dynamic>);
          print('[BackendClient] Parsed timestamps: activityHrLastBlock=${timestamps.activityHrLastBlock}, activityHrLastEntry=${timestamps.activityHrLastEntry}, passiveHrLastTimestamp=${timestamps.passiveHrLastTimestamp}');
          return timestamps;
        } else {
          // If success is false but we got a response, might be initial sync
          final reason = data['reason'] as String?;
          print('[BackendClient] Response success=false, reason: $reason');
          if (reason != null && !reason.toLowerCase().contains('denied')) {
            // Not an access denied error, might be no timestamps yet
            print('[BackendClient] Returning empty timestamps (initial sync scenario)');
            return const BandTimestamps();
          }
          throw Exception(reason ?? 'Failed to load band timestamps');
        }
      }
      
      // If response is not a map, return empty timestamps for initial sync
      print('[BackendClient] Response is not a map, returning empty timestamps (initial sync)');
      return const BandTimestamps();
    } on DioException catch (e) {
      print('[BackendClient] DioException getting timestamps: ${e.message}');
      print('[BackendClient] Error type: ${e.type}');
      final Response<dynamic>? r = e.response;
      
      // If it's a 404 or similar, might mean no timestamps exist yet (initial sync)
      if (r != null) {
        print('[BackendClient] Response status: ${r.statusCode}');
        print('[BackendClient] Response data: ${r.data}');
        
        if (r.statusCode == 404 || (r.data is Map && (r.data as Map)['success'] == false)) {
          print('[BackendClient] No timestamps found (404 or success=false), returning empty timestamps (initial sync)');
          return const BandTimestamps();
        }
        
        if (r.data is Map) {
          final Map<String, dynamic> data = r.data as Map<String, dynamic>;
          final reason = data['reason'] as String?;
          // Only throw if it's an actual error, not just missing timestamps
          if (reason != null && reason.toLowerCase().contains('denied')) {
            throw Exception(reason);
          }
          // Otherwise, treat as initial sync
          print('[BackendClient] Returning empty timestamps (initial sync scenario)');
          return const BandTimestamps();
        }
      }
      
      // For network errors or other issues, still try to continue with empty timestamps
      // This allows the sync to proceed with default timestamps
      print('[BackendClient] Network/connection error, returning empty timestamps to allow sync with defaults');
      return const BandTimestamps();
    } catch (e) {
      print('[BackendClient] Unexpected error: $e');
      // Return empty timestamps to allow sync to proceed with defaults
      print('[BackendClient] Returning empty timestamps (initial sync)');
      return const BandTimestamps();
    }
  }

  Future<void> sendHeartRate({
    required List<HeartRateData> data,
    int? activityLastBlock,
    int? activityLastEntry,
    int? passiveLastTimestamp,
  }) async {
    if (data.isEmpty) return;
    
    // HeartRateData.timestamp is in seconds, backend expects seconds
    final heartRateData = data.map((hr) => {
      'timestamp': hr.timestamp,
      'hr': hr.heartRate,
    }).toList();
    
    final Map<String, dynamic> requestData = {
      'heart_rate_data': jsonEncode(heartRateData),
    };
    
    // Band returns timestamps in milliseconds, backend expects seconds
    if (activityLastBlock != null) requestData['activity_hr_last_block'] = activityLastBlock ~/ 1000;
    if (activityLastEntry != null) requestData['activity_hr_last_entry'] = activityLastEntry ~/ 1000;
    if (passiveLastTimestamp != null) requestData['passive_hr_last_timestamp'] = passiveLastTimestamp ~/ 1000;
    
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

