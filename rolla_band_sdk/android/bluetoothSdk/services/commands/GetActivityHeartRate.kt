package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.ActiveHeartRateRecord
import app.rolla.bluetoothSdk.services.commands.data.HeartRate
import app.rolla.bluetoothSdk.services.commands.data.SyncTimestamp
import app.rolla.bluetoothSdk.services.data.HeartRateData
import app.rolla.bluetoothSdk.utils.extensions.log
import app.rolla.bluetoothSdk.utils.extensions.toBcdTime
import app.rolla.bluetoothSdk.utils.extensions.toEpochMilliSeconds
import app.rolla.bluetoothSdk.utils.extensions.toIntLE
import app.rolla.bluetoothSdk.utils.extensions.toPositionedInt
import app.rolla.bluetoothSdk.utils.extensions.writeBcdTimeToArray
import java.util.Calendar
import java.util.TimeZone

class GetActivityHeartRate : Command<HeartRateData> {

    private var heartRateList: MutableList<HeartRate> = mutableListOf()
    private var activeHeartRateTimestamp: SyncTimestamp = SyncTimestamp()
    private var nextPage = false

    companion object {
        const val TAG = "GetActivityHeartRate"
        private const val NEXT_PAGE_FLAG = 0x02.toByte()
        private const val FIRST_PAGE_FLAG = 0x00.toByte()
        private const val TIMESTAMP_START_INDEX = 4
        private const val RECORD_SIZE = 24
        private const val DATA_NUMBER_OFFSET = 1
        private const val BCD_TIME_OFFSET = 3
        private const val HEART_RATES_PER_RECORD = 15
        private const val HEART_RATE_DATA_OFFSET = 9
        private const val MILLISECONDS_PER_MINUTE = 60 * 1000
        private const val PAGE_SIZE = 50 * 10
        private const val END_OF_DATA_MARKER = 0xff.toByte()
    }

    fun setHeartRateTimestamp(lastBlockTimestamp: Long, lastEntryTimestamp: Long) {
        activeHeartRateTimestamp = SyncTimestamp(lastBlockTimestamp = lastBlockTimestamp, lastEntryTimestamp = lastEntryTimestamp)
        heartRateList.clear()
    }

    fun setHeartRateList(heartRateList: ArrayList<HeartRate>) {
        this.heartRateList = heartRateList
    }

    fun setIsNextPage(nextPage: Boolean) {
        this.nextPage = nextPage
    }

    override fun getId(): Byte  = 0x54.toByte()

    override fun bytesToWrite(): ByteArray {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = activeHeartRateTimestamp.lastBlockTimestamp

        return createCommandBytes {
            this[1] = if (nextPage) NEXT_PAGE_FLAG else FIRST_PAGE_FLAG
            calendar.writeBcdTimeToArray(this, TIMESTAMP_START_INDEX)
        }
    }

    override fun read(data: ByteArray): HeartRateData {
        log(TAG, "Activity heart rate data ${data.contentToString()}")

        val recordCount = data.size / RECORD_SIZE
        if (recordCount == 0) {
            return createHeartRateData(isEndOfData = true, hasMoreData = false)
        }

        for (i in 0 until recordCount) {
            val heartRateRecord = parseHeartRateRecord(data, i)
            processHeartRateRecord(data, heartRateRecord)

            if (shouldReturnPage(heartRateRecord.dataNumber)) {
                return createHeartRateData(isEndOfData = true, hasMoreData = heartRateRecord.timeInMilliseconds > activeHeartRateTimestamp.lastBlockTimestamp)
            }
        }
        val endOfData = isEndOfData(data)
        return createHeartRateData(isEndOfData = endOfData, hasMoreData = !endOfData)
    }

    private fun parseHeartRateRecord(data: ByteArray, recordIndex: Int): ActiveHeartRateRecord {
        val offset = recordIndex * RECORD_SIZE
        val dataNumber = data.toIntLE(DATA_NUMBER_OFFSET + offset, 2)
        val bcdTime = data.toBcdTime(BCD_TIME_OFFSET + offset)
        val timeInSeconds = bcdTime.toEpochMilliSeconds()

        return ActiveHeartRateRecord(dataNumber, bcdTime, timeInSeconds, offset)
    }

    private fun processHeartRateRecord(data: ByteArray, record: ActiveHeartRateRecord) {
        if (record.dataNumber == 0) {
            activeHeartRateTimestamp = activeHeartRateTimestamp.withCurrentBlock(record.timeInMilliseconds)
        }

        val heartRateArray = processMinuteHeartRates(data, record)
        logHeartRateRecord(record, heartRateArray)
    }

    private fun processMinuteHeartRates(data: ByteArray, record: ActiveHeartRateRecord): String {
        return buildString {
            for (j in (HEART_RATES_PER_RECORD - 1) downTo 0) {
                val heartRate = data[HEART_RATE_DATA_OFFSET + j + record.offset].toPositionedInt(0)
                insert(0, "$heartRate ")

                val timestamp = record.timeInMilliseconds + (j * MILLISECONDS_PER_MINUTE)
                if (shouldAddHeartRate(heartRate, timestamp)) {
                    addHeartRateEntry(record, heartRate, timestamp)
                }
            }
        }
    }

    private fun shouldAddHeartRate(heartRate: Int, timestamp: Long): Boolean {
        return heartRate > 0 && timestamp > activeHeartRateTimestamp.lastEntryTimestamp
    }

    private fun addHeartRateEntry(record: ActiveHeartRateRecord, heartRate: Int, timestamp: Long) {
        if (record.dataNumber == 0) {
            activeHeartRateTimestamp = activeHeartRateTimestamp.withCurrentEntry(timestamp)
        }
        heartRateList.add(HeartRate(timestamp, heartRate))
    }

    private fun logHeartRateRecord(record: ActiveHeartRateRecord, heartRateArray: String) {
        log(TAG, "Data Number: ${record.dataNumber}, Date: ${record.bcdTime.toDateString()}, " +
                "Heart Rate Array: $heartRateArray")
    }

    private fun shouldReturnPage(dataNumber: Int): Boolean {
        return (dataNumber + 1) % PAGE_SIZE == 0
    }

    private fun createHeartRateData(isEndOfData: Boolean, hasMoreData: Boolean): HeartRateData{
        return HeartRateData(
            isEndOfData = isEndOfData,
            hasMoreData = hasMoreData,
            heartRates = ArrayList(heartRateList)
        )
    }

    private fun isEndOfData(data: ByteArray): Boolean {
        return data.lastOrNull() == END_OF_DATA_MARKER
    }

    fun getLastBlockTimestamp(): Long {
        return activeHeartRateTimestamp.newestBlockTimestamp
    }

    fun getLastEntryTimestamp(): Long {
        return activeHeartRateTimestamp.newestEntryTimestamp
    }
}