import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;

class BluetoothDevice {
  final String name;
  final int rssi;
  final String uuid;
  final List<pigeon.BluetoothCapabilities> capabilities;
  final pigeon.ConnectionState connectionState;
  final pigeon.DeviceType deviceType;

  BluetoothDevice({
    required this.name,
    required this.rssi,
    required this.uuid,
    required this.capabilities,
    required this.connectionState,
    required this.deviceType,
  });

  factory BluetoothDevice.fromPigeon(pigeon.BluetoothDevice device) {
    return BluetoothDevice(
      name: device.name,
      rssi: device.rssi,
      uuid: device.uuid,
      capabilities: device.capabilities,
      connectionState: device.connectionState,
      deviceType: device.deviceType,
    );
  }
}

