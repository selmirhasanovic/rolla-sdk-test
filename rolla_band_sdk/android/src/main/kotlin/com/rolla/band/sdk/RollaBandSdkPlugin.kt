package com.rolla.band.sdk

import android.content.Context
import app.rolla.bluetoothSdk.BleManager
import com.rolla.band.sdk.di.BleManagerFactory
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger

class RollaBandSdkPlugin : FlutterPlugin {
    private var bridge: RollaBandSdkBridge? = null
    private var bleManager: BleManager? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val context = binding.applicationContext
        val messenger = binding.binaryMessenger

        bleManager = BleManagerFactory.create(context)
        bridge = RollaBandSdkBridge(bleManager!!, messenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        bridge?.cleanUp()
        bridge = null
        bleManager = null
    }
}
