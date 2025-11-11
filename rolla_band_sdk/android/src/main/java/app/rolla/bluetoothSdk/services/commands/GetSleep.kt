package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.Sleep
import app.rolla.bluetoothSdk.services.commands.data.SleepPeriod
import app.rolla.bluetoothSdk.services.commands.data.SleepPhase
import app.rolla.bluetoothSdk.services.commands.data.SleepRecord
import app.rolla.bluetoothSdk.services.commands.data.SyncTimestamp
import app.rolla.bluetoothSdk.services.data.SleepData
import app.rolla.bluetoothSdk.utils.extensions.log
import app.rolla.bluetoothSdk.utils.extensions.toBcdTime
import app.rolla.bluetoothSdk.utils.extensions.toEpochMilliSeconds
import app.rolla.bluetoothSdk.utils.extensions.toIntLE
import app.rolla.bluetoothSdk.utils.extensions.toPositionedInt
import app.rolla.bluetoothSdk.utils.extensions.writeBcdTimeToArray
import java.util.Calendar
import java.util.TimeZone

class GetSleep : Command<SleepData> {

    private var sleepList: MutableList<Sleep> = mutableListOf()
    private var sleepTimestamp: SyncTimestamp = SyncTimestamp()

    private var nextPage = false

    companion object {
        const val TAG = "GetSleep"
        private const val NEXT_PAGE_FLAG = 0x02.toByte()
        private const val FIRST_PAGE_FLAG = 0x00.toByte()
        private const val TIMESTAMP_START_INDEX = 4
        private const val EXPECTED_RECORD_SIZE = 130
        private const val EXPECTED_RECORD_SIZE_WITH_END_OF_DATA = 132
        private const val UNEXPECTED_RECORD_SIZE = 34
        private const val DATA_NUMBER_OFFSET = 1
        private const val BCD_TIME_OFFSET = 3
        private const val SLEEP_LENGTH_OFFSET = 9
        private const val SLEEP_PHASE_OFFSET = 10
        private const val MILLISECONDS_PER_MINUTE = 60 * 1000
        private const val PAGE_SIZE = 50
        private const val END_OF_DATA_MARKER = 0xff.toByte()
    }

    fun setSleepTimestamp(lastBlockTimestamp: Long, lastEntryTimestamp: Long) {
        sleepTimestamp = SyncTimestamp(lastBlockTimestamp = lastBlockTimestamp, lastEntryTimestamp = lastEntryTimestamp)
        sleepList.clear()
    }

    fun setIsNextPage(nextPage: Boolean) {
        this.nextPage = nextPage
    }

    override fun getId(): Byte = 0x53.toByte()

    override fun bytesToWrite(): ByteArray {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = sleepTimestamp.lastBlockTimestamp

        return createCommandBytes {
            this[1] = if (nextPage) NEXT_PAGE_FLAG else FIRST_PAGE_FLAG
            calendar.writeBcdTimeToArray(this, TIMESTAMP_START_INDEX)
        }
    }

    override fun read(data: ByteArray): SleepData {
        log(TAG, "Sleep data ${data.contentToString()}")
        if (data.size == EXPECTED_RECORD_SIZE || (data.size == EXPECTED_RECORD_SIZE_WITH_END_OF_DATA && isEndOfData(data))) {
            val sleepRecord = parseSleepRecord(data, 0)
            processSleepRecord(data, sleepRecord)

            if (shouldReturnPage(sleepRecord.dataNumber)) {
                return createSleepData(isEndOfData = true, hasMoreData = sleepRecord.timeInMilliseconds > sleepTimestamp.lastBlockTimestamp)
            }
        } else {
            val recordCount = data.size / UNEXPECTED_RECORD_SIZE
            if (recordCount == 0) {
                return createSleepData(isEndOfData = true, hasMoreData = false)
            }
            for (i in 0 until recordCount) {
                val sleepRecord = parseSleepRecord(data, i)
                processSleepRecord(data, sleepRecord)

                if (shouldReturnPage(sleepRecord.dataNumber)) {
                    return createSleepData(isEndOfData = true, hasMoreData = sleepRecord.timeInMilliseconds > sleepTimestamp.lastBlockTimestamp)
                }
            }
        }
        val endOfData = isEndOfData(data)
        return createSleepData(isEndOfData = endOfData, hasMoreData = !endOfData)
    }

    private fun parseSleepRecord(data: ByteArray, recordIndex: Int): SleepRecord {
        val offset = recordIndex * UNEXPECTED_RECORD_SIZE
        val dataNumber = data.toIntLE(DATA_NUMBER_OFFSET + offset, 2)
        val bcdTime = data.toBcdTime(BCD_TIME_OFFSET + offset)
        val sleepLength = data[SLEEP_LENGTH_OFFSET + offset].toPositionedInt(0)
        val timeInSeconds = bcdTime.toEpochMilliSeconds()
        return SleepRecord(dataNumber, bcdTime, sleepLength, timeInSeconds, offset)
    }

    private fun processSleepRecord(data: ByteArray, record: SleepRecord) {
        if (record.dataNumber == 0) {
            sleepTimestamp = sleepTimestamp.withCurrentBlock(record.timeInMilliseconds)
        }

        val sleepPhaseArray = processSleepPhases(data, record)
        logSleepRecord(record, sleepPhaseArray)
    }

    private fun processSleepPhases(data: ByteArray, record: SleepRecord): String {
        return try {
            buildString {
                for (j in (record.sleepLength - 1) downTo 0) {
                    val sleepPhase = data[SLEEP_PHASE_OFFSET + j + record.offset].toPositionedInt(0)
                    insert(0, "$sleepPhase ")

                    val timestamp = record.timeInMilliseconds + (j * MILLISECONDS_PER_MINUTE)
                    if (shouldAddSleepPhase(sleepPhase, timestamp)) {
                        addSleepPhaseEntry(record, sleepPhase, timestamp)
                    }
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            "IndexOutOfBoundsException ${e.message} - data: ${data.contentToString()}}"
        }
    }

    private fun shouldAddSleepPhase(sleepPhase: Int, timestamp: Long): Boolean {
        return sleepPhase > 0 && timestamp > sleepTimestamp.lastEntryTimestamp
    }

    private fun addSleepPhaseEntry(record: SleepRecord, sleepPhase: Int, timestamp: Long) {
        if (record.dataNumber == 0 && sleepList.isEmpty()) {
            sleepTimestamp = sleepTimestamp.withCurrentEntry(timestamp + MILLISECONDS_PER_MINUTE)
        }
        sleepList.add(Sleep(timestamp, mapSleepPhase(sleepPhase)))
    }

    private fun logSleepRecord(record: SleepRecord, sleepPhaseArray: String) {
        log(TAG, "Data Number: ${record.dataNumber}, Date: ${record.bcdTime.toDateString()}, " +
                "Sleep Phase Array: $sleepPhaseArray")
    }

    private fun mapSleepPhase(sleepPhase: Int): SleepPhase {
        return when (sleepPhase) {
            1 -> SleepPhase.DEEP
            2 -> SleepPhase.LIGHT
            3 -> SleepPhase.REM
            else -> SleepPhase.AWAKE
        }
    }

    private fun shouldReturnPage(dataNumber: Int): Boolean {
        return (dataNumber + 1) % PAGE_SIZE == 0
    }

    private fun createSleepData(isEndOfData: Boolean, hasMoreData: Boolean): SleepData {
        return SleepData(
            isEndOfData = isEndOfData,
            hasMoreData = hasMoreData,
            sleeps = convertToSleepPeriodList(),
            lastBlockTimestamp = sleepTimestamp.newestBlockTimestamp,
            lastEntryTimestamp = sleepTimestamp.newestEntryTimestamp,
        )
    }

    private fun convertToSleepPeriodList(): ArrayList<SleepPeriod> {
        val sleepPeriodList = arrayListOf<SleepPeriod>()
        if (sleepList.isEmpty()) return sleepPeriodList
        
        var currentPhase = sleepList[0].phase
        var endTime = sleepList[0].timestamp + MILLISECONDS_PER_MINUTE
        
        for (i in 1 until sleepList.size) {
            val sleep = sleepList[i]
            
            if (sleep.phase != currentPhase) {
                // Phase changed, create period for previous phase
                val startTime = sleepList[i - 1].timestamp
                sleepPeriodList.add(SleepPeriod(startTime, endTime, currentPhase))
                
                // Start new period
                currentPhase = sleep.phase
                endTime = sleep.timestamp + MILLISECONDS_PER_MINUTE
            }
        }
        
        // Add the last (oldest) period
        val lastStartTime = sleepList.last().timestamp
        sleepPeriodList.add(SleepPeriod(lastStartTime, endTime, currentPhase))
        
        return sleepPeriodList
    }

    private fun isEndOfData(data: ByteArray): Boolean {
        return data.lastOrNull() == END_OF_DATA_MARKER && data[data.size - 2] == getId()
    }

}