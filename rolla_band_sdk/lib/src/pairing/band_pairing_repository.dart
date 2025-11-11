import 'package:dartz/dartz.dart';
import 'package:rolla_band_sdk/src/error/failures.dart';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;

abstract class BandPairingRepository {
  Future<Either<Failure, Unit>> startScan({
    required List<pigeon.DeviceType> deviceTypes,
    int durationMs = 10000,
  });

  Future<Either<Failure, Unit>> stopScan();

  Future<Either<Failure, bool>> connect(String deviceId);

  Future<Either<Failure, bool>> disconnect(String deviceId);

  Future<Either<Failure, pigeon.BluetoothState>> getBluetoothState();

  Stream<pigeon.BluetoothState> bluetoothStateStream();

  Future<Either<Failure, pigeon.ConnectionState>> getConnectionState(String deviceId);

  Stream<List<pigeon.BluetoothDevice>> devicesStream();

  Stream<(String uuid, pigeon.ConnectionState state)> connectionStateStream();

  Future<Either<Failure, Unit>> storeBandToBackend(String macAddress);

  Future<Either<Failure, Unit>> unpairBandOnBackend();

  Future<Either<Failure, int>> getBatteryLevel(String deviceId);

  Future<Either<Failure, String>> getFirmwareVersion(String deviceId);

  Future<Either<Failure, String>> getSerialNumber(String deviceId);
}

