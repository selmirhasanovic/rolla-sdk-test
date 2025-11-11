import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/backend_client.dart';

class BandPairing {
  final pigeon.BandCommandHostAPI _bandApi;
  final BackendClient _backendClient;
  final pigeon.RollaBluetoothHostApi _bluetoothApi;

  BandPairing({
    required pigeon.BandCommandHostAPI bandApi,
    required BackendClient backendClient,
    required pigeon.RollaBluetoothHostApi bluetoothApi,
  })  : _bandApi = bandApi,
        _backendClient = backendClient,
        _bluetoothApi = bluetoothApi;

  /// Pairs a device with the current user.
  /// Flow:
  /// 1. Validate MAC address format
  /// 2. Store band MAC address to backend (associates device with user account)
  /// The backend API call is immediate and doesn't require device connection verification.
  Future<void> pairDevice(String uuid) async {
    print('[BandPairing] Starting pairing for device: $uuid');
    
    // 1. Validate MAC address format
    if (!_isValidMacAddress(uuid)) {
      print('[BandPairing] ✗ Invalid MAC address format: $uuid');
      throw Exception('Invalid device UUID/MAC address format: $uuid');
    }
    print('[BandPairing] ✓ MAC address format valid: $uuid');
    
    // 2. Store band to backend (this associates the device with the user account)
    // This is an immediate API call - no need to verify BLE connection first
    print('[BandPairing] Calling backend API to pair band...');
    try {
      await _backendClient.storeBand(uuid)
          .timeout(const Duration(seconds: 10));
      print('[BandPairing] ✓ Band paired successfully with user account');
    } catch (e) {
      print('[BandPairing] ✗ Backend store error: $e');
      final errorMsg = e.toString().toLowerCase();
      if (errorMsg.contains('in use') || errorMsg.contains('already') || errorMsg.contains('conflict')) {
        throw Exception('Band is already paired to another user account');
      }
      if (errorMsg.contains('connection refused') || errorMsg.contains('failed host lookup')) {
        throw Exception('Cannot reach backend server. Check your network connection and backend URL.');
      }
      if (errorMsg.contains('timeout')) {
        throw Exception('Backend request timed out. Please check your network connection.');
      }
      rethrow;
    }
    
    print('[BandPairing] ✓ Pairing completed successfully');
  }

  Future<void> updateUserData(String uuid, pigeon.UserData userData) async {
    await _bandApi.updateUserData(uuid, userData);
  }

  bool _isValidMacAddress(String value) {
    // MAC address pattern: XX:XX:XX:XX:XX:XX or XX-XX-XX-XX-XX-XX
    final macRegex = RegExp(r'^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$');
    return macRegex.hasMatch(value);
  }
}

