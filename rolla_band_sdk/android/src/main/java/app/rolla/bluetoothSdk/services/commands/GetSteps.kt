package app.rolla.bluetoothSdk.services.commands

import app.rolla.bluetoothSdk.services.commands.data.StepRecord
import app.rolla.bluetoothSdk.services.commands.data.Steps
import app.rolla.bluetoothSdk.services.commands.data.SyncTimestamp
import app.rolla.bluetoothSdk.services.data.StepsData
import app.rolla.bluetoothSdk.utils.extensions.log
import app.rolla.bluetoothSdk.utils.extensions.toBcdTime
import app.rolla.bluetoothSdk.utils.extensions.toEpochMilliSeconds
import app.rolla.bluetoothSdk.utils.extensions.toIntLE
import app.rolla.bluetoothSdk.utils.extensions.toPositionedInt
import app.rolla.bluetoothSdk.utils.extensions.writeBcdTimeToArray
import java.util.Calendar
import java.util.TimeZone

class GetSteps : Command<StepsData> {

    private var stepList: MutableList<Steps> = mutableListOf()
    private var stepTimestamp: SyncTimestamp = SyncTimestamp()
    private var nextPage = false

    companion object {
        const val TAG = "GetSteps"
        private const val NEXT_PAGE_FLAG = 0x02.toByte()
        private const val FIRST_PAGE_FLAG = 0x00.toByte()
        private const val TIMESTAMP_START_INDEX = 4
        private const val RECORD_SIZE = 25
        private const val DATA_NUMBER_OFFSET = 1
        private const val BCD_TIME_OFFSET = 3
        private const val TOTAL_STEPS_OFFSET = 9
        private const val CALORIES_OFFSET = 11
        private const val DISTANCE_OFFSET = 13
        private const val STEPS_DATA_OFFSET = 15
        private const val STEPS_PER_RECORD = 10
        private const val MILLISECONDS_PER_MINUTE = 60 * 1000
        private const val CALORIES_DIVISOR = 100f
        private const val DISTANCE_DIVISOR = 100f
        private const val PAGE_SIZE = 50 * 9
        private const val END_OF_DATA_MARKER = 0xff.toByte()
    }

    fun setStepTimestamp(lastBlockTimestamp: Long, lastEntryTimestamp: Long) {
        stepTimestamp = SyncTimestamp(lastBlockTimestamp, lastEntryTimestamp)
        stepList.clear()
    }

    fun setIsNextPage(nextPage: Boolean) {
        this.nextPage = nextPage
    }

    override fun getId(): Byte = 0x52.toByte()

    override fun bytesToWrite(): ByteArray {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = stepTimestamp.lastBlockTimestamp

        return createCommandBytes {
            this[1] = if (nextPage) NEXT_PAGE_FLAG else FIRST_PAGE_FLAG
            calendar.writeBcdTimeToArray(this, TIMESTAMP_START_INDEX)
        }
    }

    override fun read(data: ByteArray): StepsData {
        log(TAG, "Step count detailed data ${data.contentToString()}")

        val recordCount = data.size / RECORD_SIZE
        if (recordCount == 0) {
            return createStepsData(isEndOfData = true, hasMoreData = false)
        }

        for (i in 0 until recordCount) {
            val stepRecord = parseStepRecord(data, i)
            processStepRecord(data, stepRecord)

            if (shouldReturnPage(stepRecord.dataNumber)) {
                return createStepsData(isEndOfData = true, hasMoreData = stepRecord.timeInMilliseconds > stepTimestamp.lastBlockTimestamp)
            }
        }
        val endOfData = isEndOfData(data)
        return createStepsData(isEndOfData = endOfData, hasMoreData = !endOfData)
    }

    private fun parseStepRecord(data: ByteArray, recordIndex: Int): StepRecord {
        val offset = recordIndex * RECORD_SIZE
        val dataNumber = data.toIntLE(DATA_NUMBER_OFFSET + offset, 2)
        val bcdTime = data.toBcdTime(BCD_TIME_OFFSET + offset)
        val totalSteps = data.toIntLE(TOTAL_STEPS_OFFSET + offset, 2)
        val calories = data.toIntLE(CALORIES_OFFSET + offset, 2)
        val distance = data.toIntLE(DISTANCE_OFFSET + offset, 2)
        val timeInMs = bcdTime.toEpochMilliSeconds()

        return StepRecord(dataNumber, bcdTime, totalSteps, calories, distance, timeInMs, offset)
    }

    private fun processStepRecord(data: ByteArray, record: StepRecord) {
        if (record.dataNumber == 0) {
            stepTimestamp = stepTimestamp.withCurrentBlock(record.timeInMilliseconds)
        }

        val stepsArray = processMinuteSteps(data, record)
        logStepRecord(record, stepsArray)
    }

    private fun processMinuteSteps(data: ByteArray, record: StepRecord): String {
        return buildString {
            for (j in (STEPS_PER_RECORD - 1) downTo 0) {
                val steps = data[STEPS_DATA_OFFSET + j + record.offset].toPositionedInt(0)
                insert(0, "$steps ")

                val timestamp = record.timeInMilliseconds + (j * MILLISECONDS_PER_MINUTE)
                if (shouldAddStep(steps, timestamp)) {
                    addStepEntry(record, steps, timestamp)
                }
            }
        }
    }

    private fun shouldAddStep(steps: Int, timestamp: Long): Boolean {
        return steps > 0 && timestamp > stepTimestamp.lastEntryTimestamp
    }

    private fun addStepEntry(record: StepRecord, steps: Int, timestamp: Long) {
        val caloriesPerStep = if (record.totalSteps > 0) {
            record.calories * (steps.toDouble() / record.totalSteps)
        } else 0.0

        if (record.dataNumber == 0) {
            stepTimestamp = stepTimestamp.withCurrentEntry(timestamp)
        }
        stepList.add(Steps(timestamp, steps, caloriesPerStep / CALORIES_DIVISOR))
    }

    private fun logStepRecord(record: StepRecord, stepsArray: String) {
        log(TAG, "Data Number: ${record.dataNumber}, Date: ${record.bcdTime.toDateString()}, " +
                "Step: ${record.totalSteps}, Calories: ${record.calories / CALORIES_DIVISOR}, " +
                "Distance: ${record.distance / DISTANCE_DIVISOR}km, Steps array: $stepsArray")
    }

    private fun shouldReturnPage(dataNumber: Int): Boolean {
        return (dataNumber + 1) % PAGE_SIZE == 0
    }

    private fun createStepsData(isEndOfData: Boolean, hasMoreData: Boolean): StepsData {
        return StepsData(
            isEndOfData = isEndOfData,
            hasMoreData = hasMoreData,
            steps = ArrayList(stepList),
            lastBlockTimestamp = stepTimestamp.newestBlockTimestamp,
            lastEntryTimestamp = stepTimestamp.newestEntryTimestamp,
        )
    }

    private fun isEndOfData(data: ByteArray): Boolean {
        return data.lastOrNull() == END_OF_DATA_MARKER
    }

}