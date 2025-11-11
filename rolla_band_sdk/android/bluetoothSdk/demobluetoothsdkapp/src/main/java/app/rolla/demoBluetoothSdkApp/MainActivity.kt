package app.rolla.demoBluetoothSdkApp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import app.rolla.bluetoothSdk.BleManager
import app.rolla.bluetoothSdk.connect.ConnectionError
import app.rolla.bluetoothSdk.device.BleDevice
import app.rolla.bluetoothSdk.device.DeviceType
import app.rolla.bluetoothSdk.device.DeviceTypeState
import app.rolla.bluetoothSdk.scan.manager.ScanManager
import app.rolla.bluetoothSdk.services.data.HeartRateMeasurementData
import app.rolla.bluetoothSdk.services.data.RunningSpeedCadenceData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "BleManagerDemo"
        const val DEVICE_VALIDITY_DURATION = 20000L // 20 seconds
    }

    @Inject
    lateinit var bleManager: BleManager

    // Define permissions based on SDK version
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
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Some permissions not granted. BLE functionality may be limited.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set scan duration to 30 seconds
        try {
            bleManager.setScanDuration(30000)
            Log.d(TAG, "Scan duration set to 30 seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set scan duration", e)
        }

        // Request required permissions
        requestPermissionLauncher.launch(bluetoothPermissions)

        // Set up the UI
        setContent {
            BleManagerDemoTheme {
                BleManagerScreen()
            }
        }

        // Setup flow collectors
        setupFlowCollectors()
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    fun BleManagerScreen() {
        var scannedDevices by remember { mutableStateOf<List<BleDevice>>(emptyList()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isScanning by remember { mutableStateOf(false) }
        var scanDuration by remember { mutableStateOf("30") }
        var selectedDeviceTypes by remember { mutableStateOf(setOf(
            DeviceType.ROLLA_BAND,
            DeviceType.HEART_RATE,
            DeviceType.RUNNING_SPEED_AND_CADENCE
        )) }
        var scanTimeRemaining by remember { mutableIntStateOf(0) }
        var connectionErrors by remember { mutableStateOf<List<ConnectionError>>(emptyList()) }
        var characteristicEvents by remember { mutableStateOf<List<String>>(emptyList()) }
        var measurementData by remember { mutableStateOf<Map<String, Map<String, Any>>>(emptyMap()) }

        // Collect all devices (scanned + connected) using unified flow
        LaunchedEffect(Unit) {
            bleManager.getAllDevicesFlow()?.collect { devices ->
                scannedDevices = devices
                Log.d("BleManagerScreen", "Received ${devices.size} total devices")
                
                // Debug: Log each device's subscribed types
                devices.forEach { device ->
                    if (device.isConnected()) {
                        val deviceName = getSafeDeviceName(device)
                        Log.d("BleManagerScreen", "Connected device: $deviceName - subscribed: ${device.getSubscribedTypes().joinToString { it.typeName }}")
                    }
                }
            }
        }

        // Collect connection errors
        LaunchedEffect(Unit) {
            bleManager.connectionErrors.collect { error ->
                connectionErrors = connectionErrors.takeLast(9) + error
            }
        }

        // Collect connection events
        LaunchedEffect(Unit) {
            bleManager.characteristicEvents.collect { event ->
                val eventString = "${System.currentTimeMillis()}: $event"
                characteristicEvents = characteristicEvents.takeLast(19) + eventString
            }
        }

        // Collect measurement data with device address
        LaunchedEffect(Unit) {
            bleManager.getHeartRateFlow().collect { heartRateData ->
                // Find which device sent this data by checking connected devices with HR subscription
                val hrDevice = scannedDevices.find { device ->
                    device.isConnected() && device.getSubscribedTypes().any { it == DeviceType.HEART_RATE }
                }
                
                hrDevice?.let { device ->
                    val deviceAddress = device.getMacAddress()
                    val currentDeviceData = measurementData[deviceAddress] ?: emptyMap()
                    measurementData = measurementData + (deviceAddress to (currentDeviceData + ("heart_rate" to heartRateData)))
                }
            }
        }

        LaunchedEffect(Unit) {
            bleManager.getRunningSpeedCadenceFlow().collect { rscData ->
                // Find which device sent this data by checking connected devices with RSC subscription
                val rscDevice = scannedDevices.find { device ->
                    device.isConnected() && device.getSubscribedTypes().any { it == DeviceType.RUNNING_SPEED_AND_CADENCE }
                }
                
                rscDevice?.let { device ->
                    val deviceAddress = device.getMacAddress()
                    val currentDeviceData = measurementData[deviceAddress] ?: emptyMap()
                    measurementData = measurementData + (deviceAddress to (currentDeviceData + ("rsc" to rscData)))
                }
            }
        }

        // Collect scan state and handle timer
        LaunchedEffect(Unit) {
            bleManager.scanStateFlow?.collect { state ->
                isScanning = state == ScanManager.ScanState.Scanning
                scanTimeRemaining = if (state == ScanManager.ScanState.Scanning) {
                    scanDuration.toIntOrNull()?.times(1000) ?: 30000
                } else {
                    0
                }
            }
        }

        // Countdown timer
        LaunchedEffect(isScanning) {
            if (isScanning && scanTimeRemaining > 0) {
                while (scanTimeRemaining > 0 && isScanning) {
                    delay(1000)
                    scanTimeRemaining -= 1000
                }
            }
        }

        // Timer to refresh device ages every second
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                // Trigger recomposition by updating a state
                scannedDevices = scannedDevices.toList() // Force refresh
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // App bar
                CenterAlignedTopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("BLE SDK Demo")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // Error message display
                    errorMessage?.let { error ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDE7E7))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = Color.Red,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = error,
                                    color = Color.Red,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { errorMessage = null }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }

                    // Scan controls card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Scan",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "Scan Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Scan Duration Input - compact
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Duration",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "Duration (s):",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                OutlinedTextField(
                                    value = scanDuration,
                                    onValueChange = { scanDuration = it },
                                    modifier = Modifier.width(100.dp),
                                    enabled = !isScanning,
                                    singleLine = true
                                )
                            }

                            // Device Type Selection - horizontal layout
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = "Device Types",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "Device Types:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            val availableDeviceTypes = listOf(
                                DeviceType.ROLLA_BAND,
                                DeviceType.HEART_RATE,
                                DeviceType.RUNNING_SPEED_AND_CADENCE
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableDeviceTypes.forEach { deviceType ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = selectedDeviceTypes.contains(deviceType),
                                            onCheckedChange = { checked ->
                                                selectedDeviceTypes = if (checked) {
                                                    selectedDeviceTypes + deviceType
                                                } else {
                                                    selectedDeviceTypes - deviceType
                                                }
                                            },
                                            enabled = !isScanning,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = when (deviceType) {
                                                DeviceType.ROLLA_BAND -> "🏃 Rolla Band"
                                                DeviceType.HEART_RATE -> "❤️ HR"
                                                DeviceType.RUNNING_SPEED_AND_CADENCE -> "🚴 RSC"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Scan button and status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    bleManager.scanStateFlow?.collectAsState()?.value?.let { scanState ->
                                        Text(
                                            text = scanState.toDisplayString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isScanning && scanTimeRemaining > 0) {
                                        Text(
                                            text = "⏱️ Time remaining: ${scanTimeRemaining / 1000}s",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Blue,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                if (isScanning) {
                                    Button(
                                        onClick = {
                                            lifecycleScope.launch {
                                                try {
                                                    if (hasRequiredPermissions()) {
                                                        bleManager.stopScan()
                                                    } else {
                                                        errorMessage = "Missing Bluetooth permissions"
                                                        requestPermissionLauncher.launch(bluetoothPermissions)
                                                    }
                                                } catch (e: SecurityException) {
                                                    errorMessage = "Permission denied: ${e.message}"
                                                    Log.e(TAG, "Security exception when stopping scan", e)
                                                } catch (e: Exception) {
                                                    errorMessage = "Error stopping scan: ${e.message}"
                                                    Log.e(TAG, "Error stopping scan", e)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop",
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text("Stop Scan")
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            lifecycleScope.launch {
                                                try {
                                                    if (hasRequiredPermissions()) {
                                                        val durationMs = (scanDuration.toIntOrNull() ?: 30) * 1000L
                                                        bleManager.setScanDuration(durationMs)
                                                        
                                                        if (selectedDeviceTypes.isNotEmpty()) {
                                                            bleManager.startScan(selectedDeviceTypes)
                                                        } else {
                                                            errorMessage = "Please select at least one device type"
                                                        }
                                                    } else {
                                                        errorMessage = "Missing Bluetooth permissions"
                                                        requestPermissionLauncher.launch(bluetoothPermissions)
                                                    }
                                                } catch (e: SecurityException) {
                                                    errorMessage = "Permission denied: ${e.message}"
                                                    Log.e(TAG, "Security exception when starting scan", e)
                                                } catch (e: Exception) {
                                                    errorMessage = "Error starting scan: ${e.message}"
                                                    Log.e(TAG, "Error starting scan", e)
                                                }
                                            }
                                        },
                                        enabled = selectedDeviceTypes.isNotEmpty()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Start",
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text("Start Scan")
                                    }
                                }
                            }
                        }
                    }

                    // Devices section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeviceHub,
                            contentDescription = "Devices",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Devices (${scannedDevices.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        
                        val connectedCount = scannedDevices.count { it.isConnected() }
                        if (connectedCount > 0) {
                            Button(
                                onClick = {
                                    lifecycleScope.launch {
                                        try {
                                            if (hasRequiredPermissions()) {
                                                bleManager.disconnectAll()
                                            } else {
                                                errorMessage = "Missing Bluetooth permissions"
                                            }
                                        } catch (e: SecurityException) {
                                            errorMessage = "Permission denied: ${e.message}"
                                            Log.e(TAG, "Security exception when disconnecting all devices", e)
                                        } catch (e: Exception) {
                                            errorMessage = "Error disconnecting: ${e.message}"
                                            Log.e(TAG, "Error when disconnecting all devices", e)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LinkOff,
                                    contentDescription = "Disconnect All",
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(16.dp)
                                )
                                Text("Disconnect All", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // Device list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, Color.LightGray, shape = RoundedCornerShape(4.dp))
                    ) {
                        items(
                            items = scannedDevices,
                            key = { it.btDevice.address }
                        ) { device ->
                            if (device.isConnected()) {
                                ConnectedDeviceItemExpanded(
                                    device = device,
                                    measurementData = measurementData,
                                    onDisconnect = {
                                        lifecycleScope.launch {
                                            try {
                                                if (hasRequiredPermissions()) {
                                                    bleManager.disconnectFromDevice(device)
                                                } else {
                                                    errorMessage = "Missing Bluetooth permissions"
                                                }
                                            } catch (e: SecurityException) {
                                                errorMessage = "Permission denied: ${e.message}"
                                                Log.e(TAG, "Security exception when disconnecting device", e)
                                            } catch (e: Exception) {
                                                errorMessage = "Error disconnecting: ${e.message}"
                                                Log.e(TAG, "Error disconnecting device", e)
                                            }
                                        }
                                    },
                                    onSubscribeMore = { device, unsubscribedTypes ->
                                        lifecycleScope.launch {
                                            try {
                                                if (hasRequiredPermissions()) {
                                                    bleManager.connectToBleDevice(device, unsubscribedTypes)
                                                } else {
                                                    errorMessage = "Missing Bluetooth permissions"
                                                }
                                            } catch (e: SecurityException) {
                                                errorMessage = "Permission denied: ${e.message}"
                                                Log.e(TAG, "Security exception when subscribing to more types", e)
                                            } catch (e: Exception) {
                                                errorMessage = "Error subscribing: ${e.message}"
                                                Log.e(TAG, "Error subscribing to more types", e)
                                            }
                                        }
                                    },
                                    onUnsubscribe = { device, deviceTypes ->
                                        lifecycleScope.launch {
                                            try {
                                                if (hasRequiredPermissions()) {
                                                    bleManager.disconnectFromDevice(device, deviceTypes)
                                                } else {
                                                    errorMessage = "Missing Bluetooth permissions"
                                                }
                                            } catch (e: SecurityException) {
                                                errorMessage = "Permission denied: ${e.message}"
                                                Log.e(TAG, "Security exception when unsubscribing", e)
                                            } catch (e: Exception) {
                                                errorMessage = "Error unsubscribing: ${e.message}"
                                                Log.e(TAG, "Error unsubscribing from types", e)
                                            }
                                        }
                                    }
                                )
                            } else {
                                DeviceListItem(
                                    device = device,
                                    allDevices = scannedDevices, // Pass the scannedDevices list
                                    onConnect = { bleDevice, selectedDeviceTypes ->
                                        lifecycleScope.launch {
                                            try {
                                                Log.d(TAG, "=== CONNECTION ATTEMPT ===")
                                                Log.d(TAG, "Device: ${getSafeDeviceName(bleDevice)}")
                                                Log.d(TAG, "Selected types from UI: ${selectedDeviceTypes.joinToString { it.typeName }}")
                                                
                                                // Check what types are already in use
                                                val typesInUse = scannedDevices.filter { it.isConnected() }
                                                    .flatMap { it.getSubscribedTypes() }
                                                Log.d(TAG, "Types already in use: ${typesInUse.joinToString { it.typeName }}")
                                                
                                                // Filter out conflicting types before connecting
                                                val availableTypes = selectedDeviceTypes.filter { type ->
                                                    !typesInUse.contains(type)
                                                }.toSet()
                                                
                                                Log.d(TAG, "Final types to connect: ${availableTypes.joinToString { it.typeName }}")
                                                
                                                if (availableTypes.isEmpty()) {
                                                    errorMessage = "No available device types to connect"
                                                    Log.e(TAG, "All selected types are already in use")
                                                    return@launch
                                                }
                                                
                                                if (hasRequiredPermissions()) {
                                                    bleManager.connectToBleDevice(bleDevice, availableTypes)
                                                } else {
                                                    errorMessage = "Missing Bluetooth permissions"
                                                    requestPermissionLauncher.launch(bluetoothPermissions)
                                                }
                                            } catch (e: SecurityException) {
                                                errorMessage = "Permission denied: ${e.message}"
                                                Log.e(TAG, "Security exception when connecting to device", e)
                                            } catch (e: Exception) {
                                                errorMessage = "Error connecting: ${e.message}"
                                                Log.e(TAG, "Error when connecting to device", e)
                                            }
                                        }
                                    },
                                    onDisconnect = { bleDevice ->
                                        lifecycleScope.launch {
                                            try {
                                                if (hasRequiredPermissions()) {
                                                    bleManager.disconnectFromDevice(bleDevice)
                                                } else {
                                                    errorMessage = "Missing Bluetooth permissions"
                                                    requestPermissionLauncher.launch(bluetoothPermissions)
                                                }
                                            } catch (e: SecurityException) {
                                                errorMessage = "Permission denied: ${e.message}"
                                                Log.e(TAG, "Security exception when disconnecting device", e)
                                            } catch (e: Exception) {
                                                errorMessage = "Error disconnecting: ${e.message}"
                                                Log.e(TAG, "Error disconnecting device", e)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        if (scannedDevices.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BluetoothSearching,
                                            contentDescription = "No devices",
                                            modifier = Modifier
                                                .size(48.dp)
                                                .padding(bottom = 8.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (isScanning) "🔍 Scanning for devices..." else "📱 No devices found",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun DeviceListItem(
        device: BleDevice,
        allDevices: List<BleDevice>, // Add this parameter
        onConnect: (BleDevice, Set<DeviceType>) -> Unit,
        onDisconnect: (BleDevice) -> Unit
    ) {
        val currentTime = System.currentTimeMillis()
        val deviceAge = currentTime - device.timestamp
        val isValid = deviceAge < DEVICE_VALIDITY_DURATION || device.isConnected()
        val remainingTimeSeconds = ((DEVICE_VALIDITY_DURATION - deviceAge) / 1000).toInt().coerceAtLeast(0)
        
        var showDeviceTypeDialog by remember { mutableStateOf(false) }
        var selectedDeviceTypes by remember { mutableStateOf(device.deviceTypes.toSet()) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    device.isConnected() -> Color(0xFFE8F5E8)
                    device.isConnecting() -> Color(0xFFFFF3E0)
                    device.isDisconnecting() -> Color(0xFFFFEBEE)
                    isValid -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                val deviceName = getSafeDeviceName(device)
                val deviceAddress = getSafeDeviceAddress(device)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = deviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Connection status indicator
                            val (statusText, statusColor) = when {
                                device.isConnected() -> "Connected" to Color.Green
                                device.isConnecting() -> "Connecting" to Color(0xFFFF9800)
                                device.isDisconnecting() -> "Disconnecting" to Color.Red
                                else -> "" to Color.Transparent
                            }
                            
                            if (statusText.isNotEmpty()) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = deviceAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // RSSI with visual interpretation
                        if (device.isDisconnected()) {
                            val (rssiIcon, rssiColor) = when {
                                device.rssi >= -50 -> "📶" to Color.Green
                                device.rssi >= -70 -> "📶" to Color(0xFFFF9800) // Orange
                                device.rssi >= -85 -> "📶" to Color.Red
                                else -> "📶" to Color.Gray
                            }
                            Text(
                                text = "$rssiIcon RSSI: ${device.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = rssiColor,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "Supported types: ${device.deviceTypes.joinToString { it.typeName }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Show validity for all scanned devices (connected or not)
                        if (device.isDisconnected()) {
                            val currentTime by remember { derivedStateOf { System.currentTimeMillis() } }
                            val deviceAge = currentTime - device.timestamp
                            val remainingTimeSeconds = ((DEVICE_VALIDITY_DURATION - deviceAge) / 1000).toInt().coerceAtLeast(0)
                            val isValid = deviceAge < DEVICE_VALIDITY_DURATION || device.isConnected()
                            
                            Text(
                                text = if (device.isConnected()) 
                                    "✅ Connected (valid for: ${remainingTimeSeconds}s)" 
                                else if (isValid)
                                    "⏰ Valid for: ${remainingTimeSeconds}s"
                                else
                                    "❌ Expired",
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    device.isConnected() -> Color(0xFF2E7D32)
                                    isValid -> Color.Green
                                    else -> Color.Red
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    when {
                        device.isConnected() -> {
                            Button(
                                onClick = { onDisconnect(device) },
                                modifier = Modifier.padding(start = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("Disconnect", color = Color.White)
                            }
                        }
                        device.isConnecting() || device.isDisconnecting() -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(start = 8.dp)
                            )
                        }
                        else -> {
                            Button(
                                onClick = { showDeviceTypeDialog = true },
                                enabled = isValid,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
        }

        // Device Type Selection Dialog
        if (showDeviceTypeDialog) {
            // Filter out device types that are already connected on other devices
            val availableDeviceTypes = device.deviceTypes.filter { deviceType ->
                // Check if any other connected device is using this device type
                val isTypeInUseByOtherDevice = allDevices.any { otherDevice ->
                    otherDevice.getMacAddress() != device.getMacAddress() && 
                    otherDevice.isConnected() && 
                    otherDevice.getSubscribedTypes().contains(deviceType)
                }
                !isTypeInUseByOtherDevice
            }

            AlertDialog(
                onDismissRequest = { showDeviceTypeDialog = false },
                title = { Text("Select Device Types") },
                text = {
                    Column {
                        if (availableDeviceTypes.isEmpty()) {
                            Text(
                                text = "No available device types. All supported types are already in use by other devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Red
                            )
                        } else {
                            Text(
                                text = "Choose which device types to connect for:",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            availableDeviceTypes.forEach { deviceType ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedDeviceTypes.contains(deviceType),
                                        onCheckedChange = { checked ->
                                            selectedDeviceTypes = if (checked) {
                                                selectedDeviceTypes + deviceType
                                            } else {
                                                selectedDeviceTypes - deviceType
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = deviceType.typeName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (selectedDeviceTypes.isNotEmpty()) {
                                onConnect(device, selectedDeviceTypes)
                                showDeviceTypeDialog = false
                            }
                        },
                        enabled = selectedDeviceTypes.isNotEmpty() && availableDeviceTypes.isNotEmpty()
                    ) {
                        Text("Connect")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeviceTypeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    private fun getSafeDeviceName(device: BleDevice): String {
        return if (hasRequiredPermissions()) {
            try {
                device.btDevice.name ?: "Unknown Device"
            } catch (e: SecurityException) {
                "Permission Denied"
            }
        } else {
            "Permission Required"
        }
    }

    private fun getSafeDeviceAddress(device: BleDevice): String {
        return if (hasRequiredPermissions()) {
            try {
                device.btDevice.address
            } catch (e: SecurityException) {
                "Permission Denied"
            }
        } else {
            "Permission Required"
        }
    }

    @Composable
    fun BleManagerDemoTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = lightColorScheme(),
            content = content
        )
    }

    private fun setupFlowCollectors() {
        // Collect BLE errors for logging
        lifecycleScope.launch {
            try {
                bleManager.bleErrorFlow.collect { error ->
                    Log.e(TAG, "BLE Error: ${error.message}")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when collecting BLE errors", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting BLE errors", e)
            }
        }

        // Collect connection errors
        lifecycleScope.launch {
            try {
                bleManager.connectionErrors.collect { error ->
                    Log.e(TAG, "Connection Error: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting connection errors", e)
            }
        }
    }

    private fun ScanManager.ScanState.toDisplayString(): String {
        return when (this) {
            ScanManager.ScanState.Idle -> "Ready to scan"
            ScanManager.ScanState.Scanning -> "Scanning..."
            ScanManager.ScanState.Completed -> "Scan completed"
            ScanManager.ScanState.Stopped -> "Scan stopped"
            is ScanManager.ScanState.Failed -> "Scan failed: ${this.message}"
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

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
}

@Composable
fun ConnectedDeviceItemExpanded(
    device: BleDevice,
    measurementData: Map<String, Map<String, Any>>,
    onDisconnect: () -> Unit,
    onSubscribeMore: (BleDevice, Set<DeviceType>) -> Unit,
    onUnsubscribe: (BleDevice, Set<DeviceType>) -> Unit
) {
    var showSubscribeMoreDialog by remember { mutableStateOf(false) }
    var selectedAdditionalTypes by remember { mutableStateOf(setOf<DeviceType>()) }
    var showUnsubscribeDialog by remember { mutableStateOf(false) }
    var selectedUnsubscribeTypes by remember { mutableStateOf(setOf<DeviceType>()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                device.isConnected() -> Color(0xFFE8F5E8)
                device.isDisconnecting() -> Color(0xFFFFEBEE)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Device info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = getDeviceName(device),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = getDeviceAddress(device),
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    // Connection and subscription status
                    Column {
                        // Connection status
                        val connectionStatus = when {
                            device.isConnected() -> "Connected" to Color.Green
                            device.isConnecting() -> "Connecting" to Color(0xFFFF9800)
                            device.isDisconnecting() -> "Disconnecting" to Color.Red
                            else -> null
                        }

                        connectionStatus?.let { (text, color) ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Subscription status during connection
                        if (device.isConnecting() || device.isConnected()) {
                            val subscribingTypes = device.deviceTypes.filter { it.typeState == DeviceTypeState.SUBSCRIBING }
                            val subscribedTypes = device.deviceTypes.filter { it.typeState == DeviceTypeState.SUBSCRIBED }

                            if (subscribingTypes.isNotEmpty()) {
                                Text(
                                    text = " Subscribing: ${subscribingTypes.joinToString { it.typeName }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (subscribedTypes.isNotEmpty()) {
                                Text(
                                    text = "✅ Subscribed: ${subscribedTypes.joinToString { it.typeName }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Unsubscription status during disconnection
                        if (device.isDisconnecting()) {
                            val unsubscribingTypes = device.deviceTypes.filter { it.typeState == DeviceTypeState.UNSUBSCRIBING }
                            if (unsubscribingTypes.isNotEmpty()) {
                                Text(
                                    text = " Unsubscribing: ${unsubscribingTypes.joinToString { it.typeName }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFF5722),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Disconnect button
                if (device.isDisconnecting()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Red
                    )
                } else {
                    IconButton(onClick = onDisconnect) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Disconnect All",
                            tint = Color.Red
                        )
                    }
                }
            }

            // Action buttons row - disable based on states
            if (device.isConnected()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Subscribe More button - only show if there are unsubscribed types
                    val availableForSubscription = device.deviceTypes.filter {
                        it.typeState == DeviceTypeState.UNSUBSCRIBED
                    }
                    if (availableForSubscription.isNotEmpty()) {
                        Button(
                            onClick = {
                                selectedAdditionalTypes = setOf()
                                showSubscribeMoreDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                            modifier = Modifier.weight(1f),
                            enabled = !device.deviceTypes.any {
                                it.typeState == DeviceTypeState.SUBSCRIBING
                            }
                        ) {
                            Text("Subscribe More", color = Color.White)
                        }
                    }

                    // Unsubscribe button - only show if there are subscribed types
                    val availableForUnsubscription = device.deviceTypes.filter {
                        it.typeState == DeviceTypeState.SUBSCRIBED
                    }
                    if (availableForUnsubscription.isNotEmpty()) {
                        Button(
                            onClick = {
                                selectedUnsubscribeTypes = setOf()
                                showUnsubscribeDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                            modifier = Modifier.weight(1f),
                            enabled = !device.deviceTypes.any {
                                it.typeState == DeviceTypeState.UNSUBSCRIBING
                            }
                        ) {
                            Text("Unsubscribe", color = Color.White)
                        }
                    }
                }
            }

            // Measurements section
            if (device.isConnected()) {

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Live Measurements",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))
                val isHeartRateSubscribed =
                    device.getSubscribedTypes().any { it == DeviceType.HEART_RATE }
                if (isHeartRateSubscribed) {

                    // Show heart rate data
                    measurementData[device.getMacAddress()]?.get("heart_rate")?.let { heartRateData ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "❤️ Heart Rate",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                when (heartRateData) {
                                    is HeartRateMeasurementData -> {
                                        Text(
                                            text = "${heartRateData.heartRate} BPM",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1976D2)
                                        )
                                        heartRateData.sensorContactDetected?.let { contact ->
                                            Text(
                                                text = "Contact: ${if (contact) "✅ Detected" else "❌ Not detected"}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        heartRateData.energyExpended?.let { energy ->
                                            Text(
                                                text = "Energy: $energy kJ",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        heartRateData.rrIntervals?.let { intervals ->
                                            Text(
                                                text = "RR Intervals: ${intervals.size} values",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    else -> {
                                        Text(
                                            text = heartRateData.toString(),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val isRscSubscribed = device.getSubscribedTypes().any { it == DeviceType.RUNNING_SPEED_AND_CADENCE }
                if (isRscSubscribed) {
                    // Show RSC data
                    measurementData[device.getMacAddress()]?.get("rsc")?.let { rscData ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "🚴 Running Speed & Cadence",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                when (rscData) {
                                    is RunningSpeedCadenceData -> {
                                        Text(
                                            text = "Speed: ${
                                                String.format(
                                                    "%.2f",
                                                    rscData.instantaneousSpeed
                                                )
                                            } m/s",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = "Cadence: ${rscData.instantaneousCadence} spm",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        rscData.instantaneousStrideLength?.let { strideLength ->
                                            Text(
                                                text = "Stride: $strideLength cm",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                        if (rscData.isRunning) {
                                            Text(
                                                text = "🏃 Running",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF2E7D32)
                                            )
                                        } else {
                                            Text(
                                                text = "🚶 Walking",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }

                                    else -> {
                                        Text(
                                            text = rscData.toString(),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Subscribe More Dialog - only show unsubscribed types
    if (showSubscribeMoreDialog) {
        val unsubscribedTypes = device.deviceTypes.filter {
            it.typeState == DeviceTypeState.UNSUBSCRIBED
        }

        AlertDialog(
            onDismissRequest = { showSubscribeMoreDialog = false },
            title = { Text("Subscribe to Additional Types") },
            text = {
                Column {
                    Text(
                        text = "Choose additional device types to subscribe to:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    unsubscribedTypes.forEach { deviceType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedAdditionalTypes.contains(deviceType),
                                onCheckedChange = { checked ->
                                    selectedAdditionalTypes = if (checked) {
                                        selectedAdditionalTypes + deviceType
                                    } else {
                                        selectedAdditionalTypes - deviceType
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = deviceType.typeName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedAdditionalTypes.isNotEmpty()) {
                            onSubscribeMore(device, selectedAdditionalTypes)
                            showSubscribeMoreDialog = false
                        }
                    },
                    enabled = selectedAdditionalTypes.isNotEmpty()
                ) {
                    Text("Subscribe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubscribeMoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unsubscribe Dialog - only show subscribed types
    if (showUnsubscribeDialog) {
        val subscribedTypes = device.deviceTypes.filter {
            it.typeState == DeviceTypeState.SUBSCRIBED
        }

        AlertDialog(
            onDismissRequest = { showUnsubscribeDialog = false },
            title = { Text("Unsubscribe from Types") },
            text = {
                Column {
                    Text("Select device types to unsubscribe from:")
                    Spacer(modifier = Modifier.height(8.dp))

                    subscribedTypes.forEach { deviceType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedUnsubscribeTypes.contains(deviceType),
                                onCheckedChange = { checked ->
                                    selectedUnsubscribeTypes = if (checked) {
                                        selectedUnsubscribeTypes + deviceType
                                    } else {
                                        selectedUnsubscribeTypes - deviceType
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = deviceType.typeName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedUnsubscribeTypes.isNotEmpty()) {
                            onUnsubscribe(device, selectedUnsubscribeTypes)
                            showUnsubscribeDialog = false
                        }
                    },
                    enabled = selectedUnsubscribeTypes.isNotEmpty()
                ) {
                    Text("Unsubscribe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsubscribeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Helper functions for safe device access
private fun getDeviceName(device: BleDevice): String {
    return try {
        device.btDevice.name ?: "Unknown Device"
    } catch (e: SecurityException) {
        "Permission Denied"
    }
}

private fun getDeviceAddress(device: BleDevice): String {
    return try {
        device.btDevice.address
    } catch (e: SecurityException) {
        "Permission Denied"
    }
}
