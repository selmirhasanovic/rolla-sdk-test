# BleManager SDK Integration Guide

## Overview
This documentation provides a guide on integrating and using the `BleManager` SDK for Bluetooth Low Energy (BLE) device scanning and communication in Android applications.

## Features
- BLE device scanning with filtering by device types
- Device discovery and connection management
- Support for standard device types (ROLLA_BAND, HEART_RATE, RUNNING_SPEED_AND_CADENCE)
- Reactive data flows for monitoring scan states and discovered devices
- Comprehensive error handling
- Real-time measurement data collection (Heart Rate, Running Speed & Cadence)

## Requirements
- Android SDK 24 (Android 7.0) or higher
- Compatible with Android 12+ permission model
- Bluetooth Low Energy supported hardware
- Gradle dependency setup for the BleManager SDK

## Installation

1. Add the BleManager SDK dependency to your app-level `build.gradle`:

```groovy
dependencies {
    implementation(":bluetoothSdk")
    // Other dependencies
}
```

2. If using Hilt for dependency injection, ensure proper setup:

```groovy
dependencies {
    implementation 'com.google.dagger:hilt-android:x.y.z'
    kapt 'com.google.dagger:hilt-android-compiler:x.y.z'
}
```

## Required Permissions

Add the following permissions to your `AndroidManifest.xml`:

```xml
<!-- For Android 12+ (API level 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" 
    android:usesPermissionFlags="neverForLocation"
    tools:targetApi="s" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- For Android 11 and below -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Optional: Declare that your app is available to devices that don't support BLE -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
```

## Basic Usage

### 1. Inject BleManager Instance (using Hilt):

```kotlin
@Inject
lateinit var bleManager: BleManager
```

### 2. Request Required Permissions:

```kotlin
private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
} else {
    arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
}

// Permission request launcher
private val requestPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Start BLE operations
        }
    }

// Request permissions
requestPermissionLauncher.launch(bluetoothPermissions)
```

### 3. Check Bluetooth Support and Status:

```kotlin
// Check if BLE is supported
if (!bleManager.isBluetoothLESupported()) {
    // Handle device not supporting BLE
    return
}

// Check if Bluetooth is enabled
if (!bleManager.isBluetoothEnabled()) {
    // Handle Bluetooth not enabled
    return
}
```

### 4. Start Scanning for Devices:

```kotlin
// Set scan duration (in milliseconds)
bleManager.setScanDuration(30000) // 30 seconds

// Define device types to scan for
val deviceTypes = setOf(
    BleDeviceType.ROLLA_BAND,
    BleDeviceType.HEART_RATE,
    BleDeviceType.RUNNING_SPEED_AND_CADENCE
)

// Start scanning
val scanStarted = bleManager.startScan(deviceTypes)
```

### 5. Access Discovered Devices:

```kotlin
// Get all scanned devices
val allDevices = bleManager.getAllScannedDevices()

// Get devices of specific type
val rollaBandDevices = bleManager.getDevicesByType(BleDeviceType.ROLLA_BAND)
```

### 6. Stop Scanning:

```kotlin
bleManager.stopScan()
```

### 7. Connect to Discovered Devices:

```kotlin
// Stop scanning before connecting (recommended)
val stopResult = bleManager.stopScan()
if (!stopResult.success) {
    Log.w(TAG, "Failed to stop scan: ${stopResult.errorMessage}")
}

// Connect to a device with specific device types
val deviceTypes = setOf(BleDeviceType.ROLLA_BAND)
bleManager.connectToBleDevice(discoveredDevice, deviceTypes)

// Monitor connection state
lifecycleScope.launch {
    bleManager.connectionState.collect { connectionMap ->
        // connectionMap contains device addresses and their connection status
        connectionMap.forEach { (address, isConnected) ->
            Log.d(TAG, "Device $address is ${if (isConnected) "connected" else "disconnected"}")
        }
    }
}

// Monitor connection errors
lifecycleScope.launch {
    bleManager.connectionErrors.collect { error ->
        Log.e(TAG, "Connection error: ${error.message}")
    }
}
```

### 8. Manage Connected Devices:

```kotlin
// Get all connected devices
val connectedDevices = bleManager.getConnectedDevices()

// Check if specific device is connected
val isConnected = bleManager.isDeviceConnected(device)

// Get connected device for specific type
val rollaBandDevice = bleManager.getConnectedDeviceForType(BleDeviceType.ROLLA_BAND)

// Disconnect specific device
bleManager.disconnectFromDevice(device)

// Disconnect all devices
bleManager.disconnectAll()
```

### 9. Monitor Events Using Flow Collectors:

```kotlin
// Monitor scan state changes
bleManager.scanStateFlow?.let { flow ->
    lifecycleScope.launch {
        flow.collect { state ->
            when (state) {
                ScanManager.ScanState.Idle -> { /* Handle idle state */ }
                ScanManager.ScanState.Scanning -> { /* Handle scanning state */ } 
                ScanManager.ScanState.Completed -> { /* Handle completed state */ }
                ScanManager.ScanState.Stopped -> { /* Handle stopped state */ }
                is ScanManager.ScanState.Failed -> { /* Handle failed state with error */ }
            }
        }
    }
}

// Monitor all devices (scanned + connected) using unified flow
lifecycleScope.launch {
    bleManager.getAllDevicesFlow().collect { devices ->
        // Process all devices (both scanned and connected)
        devices.forEach { device ->
            Log.d(TAG, "Device: ${device.getFullName()}, Connected: ${device.isConnected()}")
        }
    }
}

// Monitor discovered devices only
bleManager.discoveredDevicesFlow?.let { flow ->
    lifecycleScope.launch {
        flow.collectLatest { devices ->
            // Process discovered devices
        }
    }
}

// Monitor scan errors
bleManager.scanErrorFlow?.let { flow ->
    lifecycleScope.launch {
        flow.collect { error ->
            // Handle scan error
        }
    }
}

// Monitor BLE errors
lifecycleScope.launch {
    bleManager.bleErrorFlow.collect { error ->
        // Handle BLE error
    }
}

// Monitor connection events
lifecycleScope.launch {
    bleManager.connectionEvents.collect { event ->
        when (event) {
            is ConnectionEvent.Connecting -> {
                Log.d(TAG, "Connecting to ${event.device.getFullName()}")
            }
            is ConnectionEvent.Connected -> {
                Log.d(TAG, "Connected to ${event.device.getFullName()}")
            }
            is ConnectionEvent.ServicesDiscovered -> {
                Log.d(TAG, "Services discovered for ${event.device.getFullName()}")
            }
            is ConnectionEvent.Disconnecting -> {
                Log.d(TAG, "Disconnecting from ${event.device.getFullName()}")
            }
            is ConnectionEvent.Disconnected -> {
                Log.d(TAG, "Disconnected from ${event.device.getFullName()}")
            }
        }
    }
}
```

### 10. Collect Measurement Data:

```kotlin
// Monitor Heart Rate data
bleManager.getHeartRateFlow()?.let { flow ->
    lifecycleScope.launch {
        flow.collect { heartRateData ->
            Log.d(TAG, "Heart Rate: ${heartRateData.heartRate} BPM")
            heartRateData.sensorContactDetected?.let { contact ->
                Log.d(TAG, "Sensor Contact: $contact")
            }
            heartRateData.energyExpended?.let { energy ->
                Log.d(TAG, "Energy Expended: $energy kJ")
            }
        }
    }
}

// Monitor Running Speed and Cadence data
bleManager.getRunningSpeedCadenceFlow()?.let { flow ->
    lifecycleScope.launch {
        flow.collect { rscData ->
            Log.d(TAG, "Speed: ${rscData.speed} m/s, Cadence: ${rscData.cadence} spm")
        }
    }
}
```

### 11. Clear Scanned Devices:

```kotlin
// Clear all discovered devices from memory
bleManager.clearScannedDevices()
```

### 12. Cleanup Resources:

```kotlin
override fun onDestroy() {
    lifecycleScope.launch {
        if (hasRequiredPermissions()) {
            try {
                bleManager.destroy()
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when destroying BleManager", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying BleManager", e)
            }
        }
    }
    super.onDestroy()
}
```

## Error Handling

Always wrap BLE operations in try-catch blocks to handle potential exceptions:

```kotlin
try {
    // BLE operations
} catch (e: SecurityException) {
    // Handle permission-related exceptions
} catch (e: Exception) {
    // Handle other exceptions
}
```

## Device Types

The SDK supports the following device types:

- `BleDeviceType.ROLLA_BAND` - Rolla Band devices
- `BleDeviceType.HEART_RATE` - Heart rate monitors
- `BleDeviceType.RUNNING_SPEED_AND_CADENCE` - Running speed and cadence sensors

## Best Practices

1. **Always check permissions** before performing BLE operations
2. **Handle device variations** by checking feature support at runtime
3. **Stop scanning before connecting** to improve connection reliability and reduce battery usage
4. **Clean up resources** by calling `bleManager.destroy()` when done
5. **Handle scan timeouts** by setting appropriate scan durations
6. **Provide user feedback** during scanning operations
7. **Implement retry mechanisms** for failed operations
8. **Monitor battery impact** as continuous scanning can drain battery
9. **Handle connection errors** gracefully using the error flow
10. **Use unified device flow** (`getAllDevicesFlow()`) for complete device state management

## Sample Implementation

See the included `MainActivity.kt` for a complete implementation example that demonstrates proper usage of the BleManager SDK, including permission handling, scan operations, flow collection, and UI integration with Jetpack Compose.

## Troubleshooting

- **Permission Issues**: Ensure all required permissions are requested and granted
- **Scan Not Starting**: Check if Bluetooth is enabled and proper permissions are granted
- **No Devices Found**: Verify device is in range and advertising
- **App Crashes**: Ensure all BLE operations are guarded with permission checks and try-catch blocks
- **UI Not Updating**: Use the unified `getAllDevicesFlow()` for real-time device state updates
- **Connection Issues**: Monitor `connectionEvents` flow for detailed connection state information
