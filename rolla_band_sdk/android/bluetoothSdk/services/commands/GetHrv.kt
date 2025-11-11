package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.Hrv
import app.rolla.bluetoothSdk.services.commands.data.HrvRecord
import app.rolla.bluetoothSdk.services.commands.data.SyncTimestamp
import app.rolla.bluetoothSdk.services.data.HrvData
import app.rolla.bluetoothSdk.utils.extensions.log
import app.rolla.bluetoothSdk.utils.extensions.toBcdTime
import app.rolla.bluetoothSdk.utils.extensions.toEpochMilliSeconds
import app.rolla.bluetoothSdk.utils.extensions.toIntLE
import app.rolla.bluetoothSdk.utils.extensions.toPositionedInt
import app.rolla.bluetoothSdk.utils.extensions.writeBcdTimeToArray
import java.util.Calendar
import java.util.TimeZone

class GetHrv : Command<HrvData> {

    private var hrvList: MutableList<Hrv> = mutableListOf()
    private var hrvTimestamp: SyncTimestamp = SyncTimestamp()
    private var nextPage = false

    companion object {
        const val TAG = "GetHrv"
        private const val NEXT_PAGE_FLAG = 0x02.toByte()
        private const val FIRST_PAGE_FLAG = 0x00.toByte()
        private const val TIMESTAMP_START_INDEX = 4
        private const val RECORD_SIZE = 15
        private const val DATA_NUMBER_OFFSET = 1
        private const val BCD_TIME_OFFSET = 3
        private const val HRV_OFFSET = 9
        private const val FATIGUE_DEGREE_OFFSET = 12
        private const val SBP_OFFSET = 13
        private const val DBP_OFFSET = 14
        private const val PAGE_SIZE = 50 * 16
        private const val END_OF_DATA_MARKER = 0xff.toByte()
    }

    fun setHrvTimestamp(lastBlockTimestamp: Long) {
        hrvTimestamp = SyncTimestamp(lastBlockTimestamp = lastBlockTimestamp)
        hrvList.clear()
    }

    fun setIsNextPage(nextPage: Boolean) {
        this.nextPage = nextPage
    }

    override fun getId(): Byte = 0x56.toByte()

    override fun bytesToWrite(): ByteArray {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = hrvTimestamp.lastBlockTimestamp

        return createCommandBytes {
            this[1] = if (nextPage) NEXT_PAGE_FLAG else FIRST_PAGE_FLAG
            calendar.writeBcdTimeToArray(this, TIMESTAMP_START_INDEX)
        }
    }

    override fun read(data: ByteArray): HrvData {
        log(TAG, "HRV data ${data.contentToString()}")

        val recordCount = data.size / RECORD_SIZE
        if (recordCount == 0) {
            return createHrvData(isEndOfData = true, hasMoreData = false)
        }

        for (i in 0 until recordCount) {
            val hrvRecord = parseHrvRecord(data, i)
            processHrvRecord(hrvRecord)

            if (shouldReturnPage(hrvRecord.dataNumber)) {
                return createHrvData(isEndOfData = true, hasMoreData = hrvRecord.timeInMilliseconds > hrvTimestamp.lastBlockTimestamp)
            }
        }
        val endOfData = isEndOfData(data)
        return createHrvData(isEndOfData = endOfData, hasMoreData = !endOfData)
    }

    private fun parseHrvRecord(data: ByteArray, recordIndex: Int): HrvRecord {
        val offset = recordIndex * RECORD_SIZE
        val dataNumber = data.toIntLE(DATA_NUMBER_OFFSET + offset, 2)
        val bcdTime = data.toBcdTime(BCD_TIME_OFFSET + offset)
        val hrv = data[HRV_OFFSET + offset].toPositionedInt(0)
        val fatigueDegree = data[FATIGUE_DEGREE_OFFSET + offset].toPositionedInt(0)
        val sbp = data[SBP_OFFSET + offset].toPositionedInt(0)
        val dbp = data[DBP_OFFSET + offset].toPositionedInt(0)
        val timeInMs = bcdTime.toEpochMilliSeconds()

        return HrvRecord(dataNumber, bcdTime, hrv, fatigueDegree, sbp, dbp, timeInMs, offset)
    }

    private fun processHrvRecord(record: HrvRecord) {
        if (record.dataNumber == 0) {
            hrvTimestamp = hrvTimestamp.withCurrentBlock(record.timeInMilliseconds)
        }

        if (shouldAddHrv(record.hrv, record.timeInMilliseconds)) {
            hrvList.add(Hrv(record.timeInMilliseconds, record.hrv))
        }
        logHrvRecord(record)
    }

    private fun shouldAddHrv(hrv: Int, timestamp: Long): Boolean {
        return hrv > 0 && timestamp > hrvTimestamp.lastBlockTimestamp
    }

    private fun logHrvRecord(record: HrvRecord) {
        log(TAG, "Data Number: ${record.dataNumber}, Date: ${record.bcdTime.toDateString()}, " +
                "HRV: ${record.hrv}, Fatigue Degree: ${record.fatigueDegree}, " +
                "SBP: ${record.sbp}, DBP: ${record.dbp}")
    }

    private fun shouldReturnPage(dataNumber: Int): Boolean {
        return (dataNumber + 1) % PAGE_SIZE == 0
    }

    private fun createHrvData(isEndOfData: Boolean, hasMoreData: Boolean): HrvData {
        return HrvData(
            isEndOfData = isEndOfData,
            hasMoreData = hasMoreData,
            hrvList = ArrayList(hrvList),
            lastBlockTimestamp = hrvTimestamp.newestBlockTimestamp
        )
    }

    private fun isEndOfData(data: ByteArray): Boolean {
        return data.lastOrNull() == END_OF_DATA_MARKER
    }

}