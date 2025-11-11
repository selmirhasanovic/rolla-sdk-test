import 'package:dartz/dartz.dart';
import 'package:rolla_band_sdk/src/error/failures.dart';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/pairing/band_pairing_repository.dart';

class StartScan {
  final BandPairingRepository repository;
  StartScan(this.repository);

  Future<Either<Failure, Unit>> call({
    required List<pigeon.DeviceType> deviceTypes,
    int durationMs = 10000,
  }) => repository.startScan(deviceTypes: deviceTypes, durationMs: durationMs);
}

class StopScan {
  final BandPairingRepository repository;
  StopScan(this.repository);
  Future<Either<Failure, Unit>> call() => repository.stopScan();
}

class ConnectToDevice {
  final BandPairingRepository repository;
  ConnectToDevice(this.repository);
  Future<Either<Failure, bool>> call(String id) => repository.connect(id);
}

class DisconnectFromDevice {
  final BandPairingRepository repository;
  DisconnectFromDevice(this.repository);
  Future<Either<Failure, bool>> call(String id) => repository.disconnect(id);
}

class GetBluetoothState {
  final BandPairingRepository repository;
  GetBluetoothState(this.repository);
  Future<Either<Failure, pigeon.BluetoothState>> call() => repository.getBluetoothState();
}

class GetConnectionState {
  final BandPairingRepository repository;
  GetConnectionState(this.repository);
  Future<Either<Failure, pigeon.ConnectionState>> call(String id) => repository.getConnectionState(id);
}

class StoreBandToBackend {
  final BandPairingRepository repository;
  StoreBandToBackend(this.repository);
  Future<Either<Failure, Unit>> call(String macAddress) => repository.storeBandToBackend(macAddress);
}

class UnpairBandOnBackend {
  final BandPairingRepository repository;
  UnpairBandOnBackend(this.repository);
  Future<Either<Failure, Unit>> call() => repository.unpairBandOnBackend();
}

class GetBatteryLevel {
  final BandPairingRepository repository;
  GetBatteryLevel(this.repository);
  Future<Either<Failure, int>> call(String deviceId) => repository.getBatteryLevel(deviceId);
}

class GetFirmwareVersion {
  final BandPairingRepository repository;
  GetFirmwareVersion(this.repository);
  Future<Either<Failure, String>> call(String deviceId) => repository.getFirmwareVersion(deviceId);
}

class GetSerialNumber {
  final BandPairingRepository repository;
  GetSerialNumber(this.repository);
  Future<Either<Failure, String>> call(String deviceId) => repository.getSerialNumber(deviceId);
}

class DevicesStream {
  final BandPairingRepository repository;
  DevicesStream(this.repository);
  Stream<List<pigeon.BluetoothDevice>> call() => repository.devicesStream();
}

