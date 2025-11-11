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
  /// 1. Ensure device is connected
  /// 2. Store band MAC address to backend (associates device with user account)
  /// 3. Optionally update user data on the band
  Future<void> pairDevice(String uuid) async {
    print('[BandPairing] Starting pairing for device: $uuid');
    
    // 1. Check if device is connected
    print('[BandPairing] Checking connection state...');
    final connectionState = await _bluetoothApi.checkConnectionState(uuid);
    print('[BandPairing] Connection state: $connectionState');
    
    if (connectionState != pigeon.ConnectionState.connected) {
      print('[BandPairing] Device not connected, attempting to connect...');
      final connected = await _bluetoothApi.connectToDevice(uuid);
      if (!connected) {
        throw Exception('Failed to connect to device. Please ensure the device is powered on and in range.');
      }
      // Wait a bit for connection to stabilize
      await Future.delayed(const Duration(milliseconds: 500));
      
      // Verify connection
      final newState = await _bluetoothApi.checkConnectionState(uuid);
      if (newState != pigeon.ConnectionState.connected) {
        throw Exception('Device connection failed. Please try again.');
      }
      print('[BandPairing] Device connected successfully');
    }
    
    // 2. Validate MAC address format
    if (!_isValidMacAddress(uuid)) {
      throw Exception('Invalid device UUID/MAC address format: $uuid');
    }
    
    // 3. Store band to backend (this associates the device with the user account)
    print('[BandPairing] Storing band to backend...');
    try {
      await _backendClient.storeBand(uuid);
      print('[BandPairing] ✓ Band stored to backend successfully');
    } catch (e) {
      final errorMsg = e.toString().toLowerCase();
      if (errorMsg.contains('in use') || errorMsg.contains('already') || errorMsg.contains('conflict')) {
        throw Exception('Band is already paired to another user account');
      }
      rethrow;
    }
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

