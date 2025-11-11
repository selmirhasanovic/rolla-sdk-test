class ApiException implements Exception {
  final String reason;
  final List<String>? errors;
  final int? statusCode;

  ApiException({required this.reason, this.errors, this.statusCode});

  @override
  String toString() =>
      'ApiException(statusCode: ' +
      (statusCode?.toString() ?? '-') +
      ', reason: ' +
      reason +
      ', errors: ' +
      (errors?.join(', ') ?? '-') +
      ')';
}

