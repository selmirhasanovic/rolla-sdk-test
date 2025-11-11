package app.rolla.bluetoothSdk.services.commands.data

data class SyncTimestamp(
    val lastBlockTimestamp: Long = 0L,
    val lastEntryTimestamp: Long = 0L,
    private val currentBlockTimestamp: Long = 0L,
    private val currentEntryTimestamp: Long = 0L
) {

    fun withCurrentBlock(timestamp: Long): SyncTimestamp {
        val updatedTimestamp = maxOf(timestamp, lastBlockTimestamp)
        return copy(currentBlockTimestamp = updatedTimestamp)
    }

    fun withCurrentEntry(timestamp: Long): SyncTimestamp {
        return if (currentEntryTimestamp == 0L && timestamp >= newestBlockTimestamp) {
            copy(currentEntryTimestamp = timestamp)
        } else {
            this
        }
    }

    val newestBlockTimestamp: Long
        get() = if (currentBlockTimestamp == 0L) lastBlockTimestamp else currentBlockTimestamp

    val newestEntryTimestamp: Long
        get() {
            val entry = if (currentEntryTimestamp == 0L) lastEntryTimestamp else currentEntryTimestamp
            return maxOf(entry, newestBlockTimestamp)
        }
}