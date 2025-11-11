# Rolla Band SDK

Flutter SDK for Rolla Band connectivity and health data synchronization.

## Features

- Bluetooth device scanning and connection
- Band pairing with backend
- Health data synchronization (heart rate, steps, sleep, HRV)
- Backend API integration

## Usage

```dart
import 'package:rolla_band_sdk/rolla_band_sdk.dart';

final sdk = RollaBandSDK.instance;

await sdk.initialize(RollaBandSDKConfig(
  backendBaseUrl: 'https://api.example.com',
  accessToken: 'your_token',
));

// Scan for devices
await sdk.startScan.call(deviceTypes: [DeviceType.rollaBand]);

// Connect to device
await sdk.connectToDevice.call(deviceUuid);

// Sync data
await sdk.syncAll.call(deviceUuid, defaultFromDayStartUtc: timestamp);
```

