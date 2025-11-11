package app.rolla.bluetoothSdk.utils.extensions

// Extension function for cleaner bit checking
fun Int.isBitSet(position: Int): Boolean = (this shr position) and 1 == 1