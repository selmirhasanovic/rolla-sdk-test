package app.rolla.bluetoothSdk.connect.result

/**
 * Result class for scan start operations.
 *
 * @property successfullyStarted Whether the scan was successfully started
 * @property errorMessage Optional error message if the scan failed to start
 */
data class ConnectResult(
    val successfullyStarted: Boolean,
    val errorMessage: String? = null
) {
    /**
     * Returns true if the scan started successfully
     */
    fun isSuccess(): Boolean = successfullyStarted
}