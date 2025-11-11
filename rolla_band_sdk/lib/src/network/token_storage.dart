import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter/foundation.dart';

abstract class TokenStorage {
  Future<void> saveAccessToken(String token);
  Future<void> saveRefreshToken(String token);
  Future<String?> getAccessToken();
  Future<String?> getRefreshToken();
  Future<void> clear();
}

class TokenStorageImpl implements TokenStorage {
  static const String _kAccessTokenKey = 'access_token';
  static const String _kRefreshTokenKey = 'refresh_token';

  final FlutterSecureStorage secureStorage;

  const TokenStorageImpl(this.secureStorage);

  @override
  Future<void> saveAccessToken(String token) async {
    await secureStorage.write(key: _kAccessTokenKey, value: token);
  }

  @override
  Future<void> saveRefreshToken(String token) async {
    await secureStorage.write(key: _kRefreshTokenKey, value: token);
  }

  @override
  Future<String?> getAccessToken() => secureStorage.read(key: _kAccessTokenKey);

  @override
  Future<String?> getRefreshToken() => secureStorage.read(key: _kRefreshTokenKey);

  @override
  Future<void> clear() async {
    debugPrint('[TokenStorage] clear start');
    try {
      await secureStorage.delete(key: _kAccessTokenKey);
      await secureStorage.delete(key: _kRefreshTokenKey);
      debugPrint('[TokenStorage] clear done');
    } catch (e) {
      debugPrint('[TokenStorage] clear error: $e');
      rethrow;
    }
  }
}

