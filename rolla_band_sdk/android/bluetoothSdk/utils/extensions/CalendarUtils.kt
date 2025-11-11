package app.rolla.bluetoothSdk.utils.extensions

import app.rolla.bluetoothSdk.utils.decimalToBcd
import java.util.Calendar
import java.util.TimeZone

fun Calendar.writeBcdTimeToArray(array: ByteArray, startIndex: Int = 1) {
    array[startIndex] = decimalToBcd(get(Calendar.YEAR))
    array[startIndex + 1] = decimalToBcd(get(Calendar.MONTH) + 1)
    array[startIndex + 2] = decimalToBcd(get(Calendar.DAY_OF_MONTH))
    array[startIndex + 3] = decimalToBcd(get(Calendar.HOUR_OF_DAY))
    array[startIndex + 4] = decimalToBcd(get(Calendar.MINUTE))
    array[startIndex + 5] = decimalToBcd(get(Calendar.SECOND))
}

fun BcdTime.toEpochMilliSeconds(): Long {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(year, month - 1, day, hour, minute, second)
    }
    return calendar.timeInMillis
}