class RollaBandSDKConfig {
  final String backendBaseUrl;
  final String accessToken;
  final String partnerId;

  RollaBandSDKConfig({
    required this.backendBaseUrl,
    required this.accessToken,
    this.partnerId = 'ross_dfb67c5b8525a2ff', // Default Partner-ID
  });
}

