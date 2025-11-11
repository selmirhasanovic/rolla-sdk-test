package app.rolla.bluetoothSdk.utils.extensions

import android.util.Log
import app.rolla.bluetoothSdk.utils.GlobalConfig

fun log(tag: String, message: String){
    if (GlobalConfig.ENABLE_LOGS) {
        Log.e(tag, message)
    }
}