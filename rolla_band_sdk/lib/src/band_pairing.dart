import 'package:rolla_band_sdk/generated/pigeons.g.dart' as pigeon;
import 'package:rolla_band_sdk/src/backend_client.dart';

class BandPairing {
  final pigeon.BandCommandHostAPI _bandApi;
  final BackendClient _backendClient;

  BandPairing({
    required pigeon.BandCommandHostAPI bandApi,
    required BackendClient backendClient,
  })  : _bandApi = bandApi,
        _backendClient = backendClient;

  Future<void> pairDevice(String uuid) async {
    await _backendClient.storeBand(uuid);
  }

  Future<void> updateUserData(String uuid, pigeon.UserData userData) async {
    await _bandApi.updateUserData(uuid, userData);
  }
}

