import 'package:get_it/get_it.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:rolla_band_sdk/src/network/api_config.dart';
import 'package:rolla_band_sdk/src/network/api_dio.dart';
import 'package:rolla_band_sdk/src/network/token_storage.dart';
import 'package:rolla_band_sdk/src/network/partner_provider.dart';
import 'package:rolla_band_sdk/src/sync/steps_baseline_provider.dart';
import 'package:rolla_band_sdk/src/sync/steps_baseline_provider_impl.dart';
import 'package:rolla_band_sdk/src/di/band_pairing_injection.dart';
import 'package:rolla_band_sdk/src/di/band_sync_injection.dart';

final GetIt sdkGetIt = GetIt.instance;

Future<void> initSdkDependencies({
  required ApiConfig apiConfig,
  required String accessToken,
  String? partnerId,
}) async {
  sdkGetIt.registerLazySingleton<ApiConfig>(() => apiConfig);

  sdkGetIt.registerLazySingleton<FlutterSecureStorage>(() => const FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
  ));
  sdkGetIt.registerLazySingleton<TokenStorage>(() => TokenStorageImpl(sdkGetIt()));

  final PartnerProvider partnerProvider = PartnerProvider(
    initialPartnerId: partnerId ?? apiConfig.partnerId,
  );
  sdkGetIt.registerLazySingleton<PartnerProvider>(() => partnerProvider);

  sdkGetIt.registerLazySingleton<Dio>(() => createDio(
        apiConfig: sdkGetIt(),
        tokenStorage: sdkGetIt(),
        partnerProvider: sdkGetIt(),
      ));

  await sdkGetIt<TokenStorage>().saveAccessToken(accessToken);

  sdkGetIt.registerLazySingleton<StepsBaselineProvider>(() => StepsBaselineProviderImpl());

  registerBandPairingFeature(sdkGetIt);
  registerBandSyncFeature(sdkGetIt);
}

