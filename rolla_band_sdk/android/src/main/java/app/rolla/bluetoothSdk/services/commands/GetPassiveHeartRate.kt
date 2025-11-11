package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.HeartRate
import app.rolla.bluetoothSdk.services.commands.data.PassiveHeartRateRecord
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

class GetPassiveHeartRate : Command<HeartRateData> {

    private var heartRateList: MutableList<HeartRate> = mutableListOf()
    private var passiveHeartRateTimestamp: SyncTimestamp = SyncTimestamp()
    private var nextPage = false

    companion object {
        const val TAG = "GetPassiveHeartRate"
        private const val NEXT_PAGE_FLAG = 0x02.toByte()
        private const val FIRST_PAGE_FLAG = 0x00.toByte()
        private const val TIMESTAMP_START_INDEX = 4
        private const val RECORD_SIZE = 10
        private const val DATA_NUMBER_OFFSET = 1
        private const val BCD_TIME_OFFSET = 3
        private const val HEART_RATE_OFFSET = 9
        private const val PAGE_SIZE = 50 * 24
        private const val END_OF_DATA_MARKER = 0xff.toByte()
    }

    fun setHeartRateTimestamp(lastBlockTimestamp: Long) {
        passiveHeartRateTimestamp = SyncTimestamp(lastBlockTimestamp = lastBlockTimestamp)
        heartRateList.clear()
    }

    fun setIsNextPage(nextPage: Boolean) {
        this.nextPage = nextPage
    }

    override fun getId(): Byte = 0x55.toByte()

    override fun bytesToWrite(): ByteArray {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = passiveHeartRateTimestamp.lastBlockTimestamp

        return createCommandBytes {
            this[1] = if (nextPage) NEXT_PAGE_FLAG else FIRST_PAGE_FLAG
            calendar.writeBcdTimeToArray(this, TIMESTAMP_START_INDEX)
        }
    }

    override fun read(data: ByteArray): HeartRateData {
        log(TAG, "Passive heart rate data ${data.contentToString()}")

        val recordCount = data.size / RECORD_SIZE
        if (recordCount == 0) {
            return createHeartRateData(isEndOfData = true, hasMoreData = false)
        }

        for (i in 0 until recordCount) {
            val heartRateRecord = parseHeartRateRecord(data, i)
            processHeartRateRecord(heartRateRecord)

            if (shouldReturnPage(heartRateRecord.dataNumber)) {
                return createHeartRateData(isEndOfData = true, hasMoreData = heartRateRecord.timeInMilliseconds > passiveHeartRateTimestamp.lastBlockTimestamp)
            }
        }

        val endOfData = isEndOfData(data)
        return createHeartRateData(isEndOfData = endOfData, hasMoreData = !endOfData)
    }

    private fun parseHeartRateRecord(data: ByteArray, recordIndex: Int): PassiveHeartRateRecord {
        val offset = recordIndex * RECORD_SIZE
        val dataNumber = data.toIntLE(DATA_NUMBER_OFFSET + offset, 2)
        val bcdTime = data.toBcdTime(BCD_TIME_OFFSET + offset)
        val heartRate = data[HEART_RATE_OFFSET + offset].toPositionedInt(0)
        val timeInSeconds = bcdTime.toEpochMilliSeconds()

        return PassiveHeartRateRecord(dataNumber, bcdTime, heartRate, timeInSeconds, offset)
    }

    private fun processHeartRateRecord(record: PassiveHeartRateRecord) {
        if (record.dataNumber == 0) {
            passiveHeartRateTimestamp = passiveHeartRateTimestamp.withCurrentBlock(record.timeInMilliseconds)
        }

        if (shouldAddHeartRate(record.heartRate, record.timeInMilliseconds)) {
            heartRateList.add(HeartRate(record.timeInMilliseconds, record.heartRate))
        }
        logHeartRateRecord(record)
    }

    private fun shouldAddHeartRate(heartRate: Int, timestamp: Long): Boolean {
        return heartRate > 0 && timestamp > passiveHeartRateTimestamp.lastBlockTimestamp
    }

    private fun logHeartRateRecord(record: PassiveHeartRateRecord) {
        log(TAG, "Data Number: ${record.dataNumber}, Date: ${record.bcdTime.toDateString()}, " +
                "Heart Rate: ${record.heartRate}")
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
        return passiveHeartRateTimestamp.newestBlockTimestamp
    }
}