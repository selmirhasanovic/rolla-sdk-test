import 'dart:async';

import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;

class RollaBluetoothPlugin implements pigeon.RollaBluetoothFlutterApi {
  RollaBluetoothPlugin({pigeon.RollaBluetoothHostApi? hostApi})
      : _hostApi = hostApi ?? pigeon.RollaBluetoothHostApi() {
    pigeon.RollaBluetoothFlutterApi.setUp(this);
  }

  final pigeon.RollaBluetoothHostApi _hostApi;

  final StreamController<List<pigeon.BluetoothDevice>> _devicesController =
      StreamController<List<pigeon.BluetoothDevice>>.broadcast();

  final StreamController<(String uuid, pigeon.ConnectionState state)>
      _connectionStateController =
          StreamController<(String, pigeon.ConnectionState)>.broadcast();
  final StreamController<pigeon.BluetoothState> _bluetoothStateController =
      StreamController<pigeon.BluetoothState>.broadcast();

  List<pigeon.BluetoothDevice> _availableDevices = <pigeon.BluetoothDevice>[];

  List<pigeon.BluetoothDevice> get availableDevices => _availableDevices;

  Stream<List<pigeon.BluetoothDevice>> get devicesStream => _devicesController.stream;

  Stream<(String uuid, pigeon.ConnectionState state)> get connectionStateStream =>
      _connectionStateController.stream;
  Stream<pigeon.BluetoothState> get bluetoothStateStream =>
      _bluetoothStateController.stream;

  Future<void> scanForDevices({
    required List<pigeon.DeviceType> deviceTypes,
    int scanDurationMs = 15000,
  }) async {
    await _hostApi.scanForDevices(deviceTypes: deviceTypes, scanDuration: scanDurationMs);
  }

  Future<void> stopScanning() async {
    await _hostApi.stopScanning();
  }

  Future<bool> connectToDevice(String uuid) async {
    return _hostApi.connectToDevice(uuid);
  }

  Future<bool> disconnectFromDevice(String uuid) async {
    return _hostApi.disconnectFromDevice(uuid);
  }

  Future<bool> disconnectAndRemoveBond(String uuid) async {
    return _hostApi.disconnectAndRemoveBond(uuid);
  }

  Future<pigeon.BluetoothState> checkBluetoothState() async {
    return _hostApi.checkBluetoothState();
  }

  Future<pigeon.ConnectionState> checkConnectionState(String uuid) async {
    return _hostApi.checkConnectionState(uuid);
  }

  @override
  void onDevicesFound(List<pigeon.BluetoothDevice> devices) {
    _availableDevices = devices;
    _devicesController.add(_availableDevices);
  }

  @override
  void onConnectionStateChanged(String uuid, pigeon.ConnectionState state) {
    _connectionStateController.add((uuid, state));

    final List<pigeon.BluetoothDevice> updated = _availableDevices.map((d) {
      if (d.uuid == uuid) {
        return pigeon.BluetoothDevice(
          uuid: d.uuid,
          name: d.name,
          connectionState: state,
          rssi: d.rssi,
          capabilities: d.capabilities,
          deviceType: d.deviceType,
        );
      }
      return d;
    }).toList();
    _availableDevices = updated;
    _devicesController.add(_availableDevices);
  }

  @override
  void onBluetoothStateChanged(pigeon.BluetoothState state) {
    _bluetoothStateController.add(state);
  }

  void dispose() {
    _devicesController.close();
    _connectionStateController.close();
    _bluetoothStateController.close();
  }
}

