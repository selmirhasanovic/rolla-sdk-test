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
    
    // 1. Validate MAC address format first
    if (!_isValidMacAddress(uuid)) {
      print('[BandPairing] ✗ Invalid MAC address format: $uuid');
      throw Exception('Invalid device UUID/MAC address format: $uuid');
    }
    print('[BandPairing] ✓ MAC address format valid');
    
    // 2. Check if device is connected (with timeout)
    print('[BandPairing] Checking connection state...');
    ConnectionState connectionState;
    try {
      connectionState = await _bluetoothApi.checkConnectionState(uuid)
          .timeout(const Duration(seconds: 5));
      print('[BandPairing] Connection state: $connectionState');
    } catch (e) {
      print('[BandPairing] ✗ Error checking connection state: $e');
      throw Exception('Failed to check device connection state: $e');
    }
    
    if (connectionState != pigeon.ConnectionState.connected) {
      print('[BandPairing] Device not connected (state: $connectionState), attempting to connect...');
      try {
        final connected = await _bluetoothApi.connectToDevice(uuid)
            .timeout(const Duration(seconds: 10));
        print('[BandPairing] Connect call returned: $connected');
        
        if (!connected) {
          throw Exception('Failed to connect to device. Please ensure the device is powered on and in range.');
        }
        
        // Wait for connection to stabilize and verify
        print('[BandPairing] Waiting for connection to stabilize...');
        for (int i = 0; i < 10; i++) {
          await Future.delayed(const Duration(milliseconds: 500));
          try {
            final newState = await _bluetoothApi.checkConnectionState(uuid)
                .timeout(const Duration(seconds: 2));
            print('[BandPairing] Connection check $i: $newState');
            if (newState == pigeon.ConnectionState.connected) {
              print('[BandPairing] ✓ Device connected successfully');
              break;
            }
            if (i == 9) {
              throw Exception('Device connection timeout. Please try again.');
            }
          } catch (e) {
            print('[BandPairing] Connection check error: $e');
            if (i == 9) {
              throw Exception('Failed to verify device connection: $e');
            }
          }
        }
      } catch (e) {
        print('[BandPairing] ✗ Connection error: $e');
        rethrow;
      }
    } else {
      print('[BandPairing] ✓ Device already connected');
    }
    
    // 3. Store band to backend (this associates the device with the user account)
    print('[BandPairing] Storing band to backend...');
    try {
      await _backendClient.storeBand(uuid)
          .timeout(const Duration(seconds: 10));
      print('[BandPairing] ✓ Band stored to backend successfully');
    } catch (e) {
      print('[BandPairing] ✗ Backend store error: $e');
      final errorMsg = e.toString().toLowerCase();
      if (errorMsg.contains('in use') || errorMsg.contains('already') || errorMsg.contains('conflict')) {
        throw Exception('Band is already paired to another user account');
      }
      if (errorMsg.contains('connection refused') || errorMsg.contains('failed host lookup')) {
        throw Exception('Cannot reach backend server. Check your network connection and backend URL.');
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

