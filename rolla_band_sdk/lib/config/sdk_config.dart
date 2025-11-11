class RollaBandSDKConfig {
  final String backendBaseUrl;
  final String accessToken;
  final String? partnerId;

  RollaBandSDKConfig({
    required this.backendBaseUrl,
    required this.accessToken,
    this.partnerId,
  });
}

