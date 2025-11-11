import 'dart:async';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/models/bluetooth_device.dart';

class BluetoothManager implements pigeon.RollaBluetoothFlutterApi {
  final pigeon.RollaBluetoothHostApi _hostApi;
  
  final StreamController<List<BluetoothDevice>> _devicesController =
      StreamController<List<BluetoothDevice>>.broadcast();
  final StreamController<(String uuid, pigeon.ConnectionState state)> _connectionController =
      StreamController<(String, pigeon.ConnectionState)>.broadcast();

  BluetoothManager({pigeon.RollaBluetoothHostApi? hostApi})
      : _hostApi = hostApi ?? pigeon.RollaBluetoothHostApi() {
    pigeon.RollaBluetoothFlutterApi.setUp(this);
  }

  Stream<List<BluetoothDevice>> get devicesStream => _devicesController.stream;
  Stream<(String, pigeon.ConnectionState)> get connectionStateStream => _connectionController.stream;

  Future<void> scanForDevices({
    required List<pigeon.DeviceType> deviceTypes,
    int scanDurationMs = 10000,
  }) async {
    await _hostApi.scanForDevices(deviceTypes: deviceTypes, scanDuration: scanDurationMs);
  }

  Future<void> stopScanning() async {
    await _hostApi.stopScanning();
  }

  Future<bool> connectToDevice(String uuid) async {
    return await _hostApi.connectToDevice(uuid);
  }

  Future<bool> disconnectFromDevice(String uuid) async {
    return await _hostApi.disconnectFromDevice(uuid);
  }

  Future<pigeon.ConnectionState> checkConnectionState(String uuid) async {
    return await _hostApi.checkConnectionState(uuid);
  }

  @override
  void onDevicesFound(List<pigeon.BluetoothDevice> devices) {
    final mapped = devices.map((d) => BluetoothDevice.fromPigeon(d)).toList();
    _devicesController.add(mapped);
  }

  @override
  void onConnectionStateChanged(String uuid, pigeon.ConnectionState state) {
    _connectionController.add((uuid, state));
  }

  @override
  void onBluetoothStateChanged(pigeon.BluetoothState state) {
    // Handle if needed
  }

  void dispose() {
    _devicesController.close();
    _connectionController.close();
  }
}

