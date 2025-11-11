import 'package:dio/dio.dart';
import 'package:rolla_band_sdk/src/error/api_exception.dart';

abstract class BandRemoteDataSource {
  Future<Map<String, dynamic>> storeBand(String macAddress);
  Future<void> unpairBand();
}

class BandRemoteDataSourceImpl implements BandRemoteDataSource {
  final Dio dio;

  BandRemoteDataSourceImpl({required this.dio});

  @override
  Future<Map<String, dynamic>> storeBand(String macAddress) async {
    try {
      final Response<dynamic> response = await dio.post<dynamic>(
        '/api/store_user_band',
        data: {'mac_address': macAddress},
      );
      final data = response.data as Map<String, dynamic>;
      if (data['success'] == true) {
        return data;
      }
      throw ApiException(reason: (data['reason'] as String?) ?? 'Store band failed', statusCode: response.statusCode);
    } on DioException catch (e) {
      final Response<dynamic>? r = e.response;
      if (r != null && r.data is Map) {
        final Map<String, dynamic> data = r.data as Map<String, dynamic>;
        throw ApiException(reason: (data['reason'] as String?) ?? 'Store band failed', statusCode: r.statusCode);
      }
      throw ApiException(reason: 'Store band failed', statusCode: r?.statusCode);
    }
  }

  @override
  Future<void> unpairBand() async {
    try {
      final Response<dynamic> response = await dio.post<dynamic>('/api/unpair_user_band');
      final data = response.data as Map<String, dynamic>;
      if (data['success'] != true) {
        throw ApiException(reason: (data['reason'] as String?) ?? 'Unpair band failed', statusCode: response.statusCode);
      }
    } on DioException catch (e) {
      final Response<dynamic>? r = e.response;
      if (r != null && r.data is Map) {
        final Map<String, dynamic> data = r.data as Map<String, dynamic>;
        throw ApiException(reason: (data['reason'] as String?) ?? 'Unpair band failed', statusCode: r.statusCode);
      }
      throw ApiException(reason: 'Unpair band failed', statusCode: r?.statusCode);
    }
  }
}

