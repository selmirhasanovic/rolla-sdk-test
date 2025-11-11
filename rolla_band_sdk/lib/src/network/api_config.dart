import 'package:meta/meta.dart';

@immutable
class ApiConfig {
  final String baseUrl;
  final String partnerId;
  final Duration connectTimeout;
  final Duration receiveTimeout;

  const ApiConfig({
    required this.baseUrl,
    required this.partnerId,
    this.connectTimeout = const Duration(seconds: 20),
    this.receiveTimeout = const Duration(seconds: 60),
  });

  factory ApiConfig.fromEnv() {
    const String envBaseUrl = String.fromEnvironment(
      'API_BASE_URL',
      defaultValue: 'https://ross.rolla.cloud',
    );
    const String envPartnerId = String.fromEnvironment(
      'PARTNER_ID',
      defaultValue: 'ross_dfb67c5b8525a2ff',
    );
    return ApiConfig(
      baseUrl: envBaseUrl,
      partnerId: envPartnerId,
    );
  }
}

