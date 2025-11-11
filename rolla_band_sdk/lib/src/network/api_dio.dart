import 'package:dio/dio.dart';
import 'package:rolla_band_sdk/src/network/api_config.dart';
import 'package:rolla_band_sdk/src/network/token_storage.dart';
import 'package:rolla_band_sdk/src/network/partner_provider.dart';

Dio createDio({
  required ApiConfig apiConfig,
  required TokenStorage tokenStorage,
  PartnerProvider? partnerProvider,
}) {
  final Dio dio = Dio(
    BaseOptions(
      baseUrl: apiConfig.baseUrl,
      connectTimeout: apiConfig.connectTimeout,
      receiveTimeout: apiConfig.receiveTimeout,
      contentType: Headers.formUrlEncodedContentType,
      headers: {'Partner-ID': partnerProvider?.partnerId ?? apiConfig.partnerId},
    ),
  );

  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        final bool skipAuth = (options.extra['skipAuth'] == true);
        if (!skipAuth) {
          final String? accessToken = await tokenStorage.getAccessToken();
          if (accessToken != null && accessToken.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $accessToken';
          }
        }
        options.headers['Partner-ID'] = partnerProvider?.partnerId ?? apiConfig.partnerId;
        return handler.next(options);
      },
      onError: (DioException error, ErrorInterceptorHandler handler) async {
        return handler.next(error);
      },
    ),
  );

  return dio;
}

