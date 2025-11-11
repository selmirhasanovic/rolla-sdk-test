package app.rolla.bluetoothSdk.services.commands.data

import app.rolla.bluetoothSdk.services.commands.data.AutomaticMode

/**
 * @param workMode - 0: off
 *                   1: time period working mode
 *                   2: interval within time period.
 * @param week - "1-0-1-0-1-1-0" (Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday)
 * @param intervalTime - When working in working mode 2, how much time is tested once in minutes. Each test time is 1 minute.
 */
data class AutomaticDetection(
    val workMode : Int,
    val startHour: String,
    val startMin: String,
    val stopHour: String,
    val stopMin: String,
    val week: String,
    val intervalTime: Int, // When working in working mode 2, how much time is tested once in minutes. Each test
                 //time is 1 minute.
    val automaticMode: AutomaticMode
) {
    override fun toString(): String {
        return "AutomaticDetection(workMode=$workMode, startHour=$startHour, startMin=$startMin, stopHour=$stopHour, stopMin=$stopMin, week=$week, intervalTime=$intervalTime)"
    }
}