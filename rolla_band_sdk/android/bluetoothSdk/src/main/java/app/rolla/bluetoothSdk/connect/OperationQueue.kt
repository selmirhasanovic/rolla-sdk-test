package app.rolla.bluetoothSdk.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import app.rolla.bluetoothSdk.utils.extensions.log
import app.rolla.bluetoothSdk.utils.extensions.executeWithBluetoothConnect
import app.rolla.bluetoothSdk.utils.extensions.hasBluetoothConnectPermission
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import javax.inject.Singleton

@Singleton
class OperationQueue(private val context: Context) {
    companion object {
        private const val TAG = "BleOperationQueue"
        private var instanceCounter = 0
    }

    private val instanceId = ++instanceCounter
    private val operationQueues = ConcurrentHashMap<String, LinkedBlockingQueue<BleOperation>>()
    private val activeOperations = ConcurrentHashMap<String, BleOperation>() // Changed from Boolean to BleOperation

    init {
        val stackTrace = Thread.currentThread().stackTrace
        log(TAG, "BleOperationQueue instance #$instanceId created")
        log(TAG, "Created from: ${stackTrace.getOrNull(3)?.className}.${stackTrace.getOrNull(3)?.methodName}")
    }

    fun queueOperation(operation: BleOperation) {
        val threadInfo = "[Thread: ${Thread.currentThread().name}-${Thread.currentThread().id}][Instance: #$instanceId]"
        //log(TAG, "$threadInfo → queueOperation() called for ${operation.deviceAddress}, operation: ${operation::class.simpleName}")
        
        val queue = operationQueues.computeIfAbsent(operation.deviceAddress) { 
            //log(TAG, "$threadInfo   Creating new queue for device: ${operation.deviceAddress}")
            LinkedBlockingQueue() 
        }
        queue.offer(operation)
        
        //log(TAG, "$threadInfo   Added operation to operationQueues[${operation.deviceAddress}], queue size: ${queue.size}")
        //log(TAG, "$threadInfo   Current operationQueues keys: ${operationQueues.keys}")
        //log(TAG, "$threadInfo   Current activeOperations: $activeOperations")
        
        context.executeWithBluetoothConnect(
            operation = {
                @SuppressLint("MissingPermission")
                processNextOperation(operation.deviceAddress)
            },
            onPermissionDenied = {
                log(TAG, "$threadInfo   Missing BLUETOOTH_CONNECT permission, operation queued but not processed")
            }
        )
        
        //log(TAG, "$threadInfo ← queueOperation() completed for ${operation.deviceAddress}")
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun processNextOperation(deviceAddress: String) {
        //log(TAG, "→ processNextOperation() called for $deviceAddress")
        //log(TAG, "  Current activeOperations: ${activeOperations.keys}")
        //log(TAG, "  Current operationQueues keys: ${operationQueues.keys}")
        
        // Check permission first
        if (!context.hasBluetoothConnectPermission()) {
            log(TAG, "  Missing BLUETOOTH_CONNECT permission for operation")
            val queue = operationQueues[deviceAddress] ?: return
            val operation = queue.poll() ?: return
            log(TAG, "  Removed operation from operationQueues[$deviceAddress] due to permission, remaining: ${queue.size}")
            
            activeOperations.remove(deviceAddress)
            operation.onComplete(false, null)
            return
        }

        // Don't start new operation if one is already active
        if (activeOperations.containsKey(deviceAddress)) {
            log(TAG, "  Operation already active for $deviceAddress, returning")
            return
        }

        val queue = operationQueues[deviceAddress] ?: return
        val operation = queue.poll() ?: return
        
        //log(TAG, "  Polled operation from operationQueues[$deviceAddress], remaining: ${queue.size}")

        // Store the current operation (not just true/false)
        activeOperations[deviceAddress] = operation
        //log(TAG, "  Set activeOperations[$deviceAddress] = ${operation::class.simpleName}")

        val success = when (operation) {
            is BleOperation.ReadCharacteristic -> {
                log(TAG, "  Executing ReadCharacteristic for ${operation.characteristic.uuid}")
                try {
                    @SuppressLint("MissingPermission")
                    val result = operation.gatt.readCharacteristic(operation.characteristic)
                    log(TAG, "  ReadCharacteristic result: $result")
                    result
                } catch (e: SecurityException) {
                    log(TAG, "  SecurityException reading characteristic: ${e.message}")
                    false
                } catch (e: Exception) {
                    log(TAG, "  Failed to read characteristic: ${e.message}")
                    false
                }
            }
            is BleOperation.WriteCharacteristic -> {
                log(TAG, "  Executing WriteCharacteristic for ${operation.characteristic.uuid}, data size: ${operation.data.size}")
                try {
                    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        @SuppressLint("MissingPermission")
                        operation.gatt.writeCharacteristic(
                            operation.characteristic, 
                            operation.data, 
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        operation.characteristic.value = operation.data
                        @Suppress("DEPRECATION")
                        @SuppressLint("MissingPermission")
                        operation.gatt.writeCharacteristic(operation.characteristic)
                    }
                    log(TAG, "  WriteCharacteristic result: $result")
                    result
                } catch (e: SecurityException) {
                    log(TAG, "  SecurityException writing characteristic: ${e.message}")
                    false
                } catch (e: Exception) {
                    log(TAG, "  Failed to write characteristic: ${e.message}")
                    false
                }
            }
            is BleOperation.WriteDescriptor -> {
                log(TAG, "  Executing WriteDescriptor for ${operation.descriptor.uuid}, value size: ${operation.value.size}")
                try {
                    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        @SuppressLint("MissingPermission")
                        operation.gatt.writeDescriptor(
                            operation.descriptor, 
                            operation.value
                        ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        operation.descriptor.value = operation.value
                        @Suppress("DEPRECATION")
                        @SuppressLint("MissingPermission")
                        operation.gatt.writeDescriptor(operation.descriptor)
                    }
                    log(TAG, "  WriteDescriptor result: $result")
                    result
                } catch (e: SecurityException) {
                    log(TAG, "  SecurityException writing descriptor: ${e.message}")
                    false
                } catch (e: Exception) {
                    log(TAG, "  Failed to write descriptor: ${e.message}")
                    false
                }
            }
            is BleOperation.ReadRssi -> {
                log(TAG, "  Executing ReadRssi")
                try {
                    @SuppressLint("MissingPermission")
                    val result = operation.gatt.readRemoteRssi()
                    log(TAG, "  ReadRssi result: $result")
                    result
                } catch (e: SecurityException) {
                    log(TAG, "  SecurityException reading RSSI: ${e.message}")
                    false
                } catch (e: Exception) {
                    log(TAG, "  Failed to read RSSI: ${e.message}")
                    false
                }
            }
        }

        if (!success) {
            log(TAG, "  ${operation::class.simpleName} failed immediately for $deviceAddress")
            operationComplete(deviceAddress, false, null)
        }
        // Note: For successful operations, operationComplete will be called from GATT callbacks
        
        log(TAG, "← processNextOperation() completed for $deviceAddress")
    }

    fun operationComplete(deviceAddress: String, success: Boolean, status: Int?) {
        val threadInfo = "[Thread: ${Thread.currentThread().name}-${Thread.currentThread().id}][Instance: #$instanceId]"
        //log(TAG, "$threadInfo → operationComplete() called for $deviceAddress: success=$success, status=$status")
        
        // Get and remove the active operation
        val activeOperation = activeOperations.remove(deviceAddress)
        
        activeOperation?.let { operation ->
            log(TAG, "$threadInfo   Calling onComplete for ${operation::class.simpleName}: success=$success, status=$status")
            operation.onComplete(success, status)
        } ?: log(TAG, "$threadInfo   No active operation found for $deviceAddress")
        
        //log(TAG, "$threadInfo   Current activeOperations after removal: ${activeOperations.keys}")
        
        context.executeWithBluetoothConnect(
            operation = {
                log(TAG, "$threadInfo   Permission granted, calling processNextOperation")
                @SuppressLint("MissingPermission")
                processNextOperation(deviceAddress)
            },
            onPermissionDenied = {
                log(TAG, "$threadInfo   Missing BLUETOOTH_CONNECT permission, cannot process next operation")
                operationQueues[deviceAddress]?.let { queue ->
                    log(TAG, "$threadInfo   Clearing ${queue.size} operations from operationQueues[$deviceAddress] due to permission")
                    queue.forEach { operation ->
                        operation.onComplete(false, null)
                    }
                    queue.clear()
                    
                    if (queue.isEmpty()) {
                        operationQueues.remove(deviceAddress)
                        log(TAG, "$threadInfo   Removed empty operationQueues[$deviceAddress]")
                    }
                }
            }
        )
        
        log(TAG, "$threadInfo ← operationComplete() completed for $deviceAddress")
    }

    fun clearQueue(deviceAddress: String) {
        log(TAG, "→ clearQueue() called for $deviceAddress")
        log(TAG, "  Current operationQueues keys before removal: ${operationQueues.keys}")
        log(TAG, "  Current activeOperations before removal: ${activeOperations.keys}")
        
        // Clear active operation
        val activeOperation = activeOperations.remove(deviceAddress)
        activeOperation?.let { operation ->
            log(TAG, "  Calling onComplete(false) for active operation: ${operation::class.simpleName}")
            operation.onComplete(false, null)
        } ?: log(TAG, "  No active operation found for $deviceAddress")
        
        // Clear queued operations
        operationQueues.remove(deviceAddress)?.let { queue ->
            log(TAG, "  Removed operationQueues[$deviceAddress] with ${queue.size} pending operations")
            queue.forEach { operation ->
                operation.onComplete(false, null)
            }
        } ?: log(TAG, "  No queue found for $deviceAddress to remove")
        
        log(TAG, "  Current operationQueues keys after removal: ${operationQueues.keys}")
        log(TAG, "  Current activeOperations after removal: ${activeOperations.keys}")
        log(TAG, "← clearQueue() completed for $deviceAddress")
    }

    fun cleanup() {
        log(TAG, "→ cleanup() called")
        log(TAG, "  Current operationQueues keys: ${operationQueues.keys}")
        log(TAG, "  Current activeOperations: ${activeOperations.keys}")
        
        // Clear all active operations
        activeOperations.values.forEach { operation ->
            operation.onComplete(false, null)
        }
        activeOperations.clear()
        
        // Clear all queued operations
        operationQueues.values.forEach { queue ->
            queue.forEach { operation ->
                operation.onComplete(false, null)
            }
        }
        operationQueues.clear()
        
        log(TAG, "  Cleared all operationQueues and activeOperations")
        log(TAG, "← cleanup() completed")
    }
}
