import 'dart:async';

import 'package:dartz/dartz.dart';
import 'package:flutter/services.dart';
import 'package:rolla_band_sdk/src/error/failures.dart';
import 'package:rolla_band_sdk/src/error/api_exception.dart';
import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/pairing/band_pairing_repository.dart';
import 'package:rolla_band_sdk/src/pairing/band_remote_data_source.dart';
import 'package:rolla_band_sdk/src/bluetooth/rolla_bluetooth_plugin.dart';

class _CachedBattery {
  final int level;
  final DateTime timestamp;
  _CachedBattery(this.level, this.timestamp);
}

class BandPairingRepositoryImpl implements BandPairingRepository {
  final RollaBluetoothPlugin _bluetooth;
  final BandRemoteDataSource _remoteDataSource;
  final pigeon.BandCommandHostAPI _bandApi = pigeon.BandCommandHostAPI();

  final StreamController<List<pigeon.BluetoothDevice>> _devicesController = StreamController.broadcast();
  final StreamController<(String, pigeon.ConnectionState)> _connectionController = StreamController.broadcast();

  final Map<String, Future<int>> _batteryReadInFlight = <String, Future<int>>{};
  final Map<String, _CachedBattery> _batteryCache = <String, _CachedBattery>{};
  final Duration _batteryCacheTtl = const Duration(seconds: 5);

  final Map<String, Future<bool>> _connectInFlight = <String, Future<bool>>{};

  BandPairingRepositoryImpl({
    RollaBluetoothPlugin? bluetooth,
    required BandRemoteDataSource remoteDataSource,
  })  : _bluetooth = bluetooth ?? RollaBluetoothPlugin(),
        _remoteDataSource = remoteDataSource {
    _bluetooth.devicesStream.listen((devices) => _devicesController.add(devices));
    _bluetooth.connectionStateStream.listen((update) => _connectionController.add(update));
  }

  @override
  Future<Either<Failure, Unit>> startScan({required List<pigeon.DeviceType> deviceTypes, int durationMs = 10000}) async {
    try {
      await _bluetooth.scanForDevices(deviceTypes: deviceTypes, scanDurationMs: durationMs);
      return const Right(unit);
    } on PlatformException catch (e) {
      return Left(ServerFailure(e.message ?? 'Scan failed'));
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, Unit>> stopScan() async {
    try {
      await _bluetooth.stopScanning();
      return const Right(unit);
    } on PlatformException catch (e) {
      return Left(ServerFailure(e.message ?? 'Stop scan failed'));
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, bool>> connect(String deviceId) async {
    final Future<bool>? existing = _connectInFlight[deviceId];
    if (existing != null) {
      try {
        final bool ok = await existing;
        return Right(ok);
      } catch (e) {
        return Left(ServerFailure('Connect failed'));
      }
    }
    final Future<bool> future = _bluetooth.connectToDevice(deviceId);
    _connectInFlight[deviceId] = future;
    try {
      final bool ok = await future;
      return Right(ok);
    } on PlatformException catch (e) {
      final String msg = (e.message ?? 'Connect failed');
      final String lower = msg.toLowerCase();
      if (lower.contains('in use') || lower.contains('already in use') || lower.contains('busy')) {
        return const Left(ValidationFailure('Band already in use.'));
      }
      return Left(ServerFailure(msg));
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    } finally {
      _connectInFlight.remove(deviceId);
    }
  }

  @override
  Future<Either<Failure, bool>> disconnect(String deviceId) async {
    try {
      bool result;
      try {
        result = await _bluetooth.disconnectAndRemoveBond(deviceId);
      } catch (_) {
        result = await _bluetooth.disconnectFromDevice(deviceId);
      }
      return Right(result);
    } on PlatformException catch (e) {
      return Left(ServerFailure(e.message ?? 'Disconnect failed'));
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, pigeon.BluetoothState>> getBluetoothState() async {
    try {
      final result = await _bluetooth.checkBluetoothState();
      return Right(result);
    } on PlatformException catch (e) {
      return Left(ServerFailure(e.message ?? 'Bluetooth state check failed'));
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Stream<pigeon.BluetoothState> bluetoothStateStream() => _bluetooth.bluetoothStateStream;

  @override
  Future<Either<Failure, pigeon.ConnectionState>> getConnectionState(String deviceId) async {
    try {
      final result = await _bluetooth.checkConnectionState(deviceId);
      return Right(result);
    } on PlatformException catch (e) {
      return Left(ServerFailure(e.message ?? 'Connection state check failed'));
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Stream<List<pigeon.BluetoothDevice>> devicesStream() => _devicesController.stream;

  @override
  Stream<(String uuid, pigeon.ConnectionState state)> connectionStateStream() => _connectionController.stream;

  @override
  Future<Either<Failure, Unit>> storeBandToBackend(String macAddress) async {
    try {
      await _remoteDataSource.storeBand(macAddress);
      return const Right(unit);
    } on ApiException catch (e) {
      final String reason = e.reason.toLowerCase();
      if (e.statusCode == 409 || reason.contains('already in use') || reason.contains('already paired')) {
        return const Left(ConflictFailure('Device pairing conflict. User already has an active device or device is already paired.'));
      }
      return Left(ValidationFailure(e.reason));
    } catch (e) {
      return Left(ServerFailure('Store band failed'));
    }
  }

  @override
  Future<Either<Failure, Unit>> unpairBandOnBackend() async {
    try {
      await _remoteDataSource.unpairBand();
      return const Right(unit);
    } on ApiException catch (e) {
      return Left(ValidationFailure(e.reason));
    } catch (e) {
      return Left(ServerFailure('Unpair band failed'));
    }
  }

  @override
  Future<Either<Failure, int>> getBatteryLevel(String deviceId) async {
    final _CachedBattery? cached = _batteryCache[deviceId];
    if (cached != null && DateTime.now().difference(cached.timestamp) < _batteryCacheTtl) {
      return Right(cached.level);
    }

    final Future<int>? inFlight = _batteryReadInFlight[deviceId];
    if (inFlight != null) {
      try {
        final int level = await inFlight;
        return Right(level);
      } catch (e) {
        return Left(ServerFailure('Battery read failed'));
      }
    }

    final Future<int> future = _bandApi.getBatteryLevel(deviceId);
    _batteryReadInFlight[deviceId] = future;
    try {
      final int level = await future;
      _batteryCache[deviceId] = _CachedBattery(level, DateTime.now());
      return Right(level);
    } catch (e) {
      return Left(ServerFailure('Battery read failed'));
    } finally {
      _batteryReadInFlight.remove(deviceId);
    }
  }

  @override
  Future<Either<Failure, String>> getFirmwareVersion(String deviceId) async {
    try {
      final String fw = await _bandApi.getFirmwareVersion(deviceId);
      return Right(fw);
    } catch (e) {
      return Left(ServerFailure('Firmware read failed'));
    }
  }

  @override
  Future<Either<Failure, String>> getSerialNumber(String deviceId) async {
    try {
      final String sn = await _bandApi.getSerialNumber(deviceId);
      return Right(sn);
    } catch (e) {
      return Left(ServerFailure('Serial read failed'));
    }
  }
}

