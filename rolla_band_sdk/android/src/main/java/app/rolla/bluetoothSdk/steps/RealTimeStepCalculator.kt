package app.rolla.bluetoothSdk.steps

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Real-time step calculator that processes cadence and speed data from fitness devices
 * to calculate incremental step counts with confidence scoring.
 * 
 * This calculator uses advanced algorithms including:
 * - Activity type detection based on movement patterns
 * - Outlier filtering to handle sensor noise
 * - Anthropometric stride length estimation
 * - Confidence-based step validation
 * - Temporal smoothing for consistent results
 * 
 * The calculator maintains a rolling history of sensor readings to improve accuracy
 * through trend analysis and consistency checking.
 * 
 * @author Rolla Team
 * @since 1.0
 */

class RealTimeStepCalculator {
    
    /** Rolling history of sensor readings for trend analysis */
    private val readings = mutableListOf<SensorReading>()
    
    /** Detects activity type (walking, running, etc.) from movement metrics */
    private val activityDetector = ActivityTypeDetector()
    
    /** Filters outlier values to reduce sensor noise impact */
    private val outlierFilter = OutlierFilter()
    
    /** Timestamp of the last processed reading for duration calculation */
    private var lastTimestamp = 0L
    
    /** Total corrected steps accumulated across all readings */
    private var totalCorrectedSteps = 0f
    
    /** Total uncorrected steps for comparison purposes */
    private var totalUncorrectedSteps = 0f
    
    companion object {
        /** Maximum number of readings to keep in history */
        private const val MAX_READINGS_HISTORY = 10
        
        /** Minimum time between readings to be considered valid (0.5 seconds) */
        private const val MIN_DURATION_MS = 500L
        
        /** Maximum time between readings to be considered valid (10 seconds) */
        private const val MAX_DURATION_MS = 10_000L
    }
    
    /**
     * Calculate incremental steps for the current measurement period.
     * 
     * This method processes a single sensor reading and returns only the steps
     * calculated for this specific time period, not cumulative steps.
     * 
     * The calculation process:
     * 1. Validates input data quality
     * 2. Calculates time duration since last reading
     * 3. Applies smoothing and outlier filtering
     * 4. Detects current activity type
     * 5. Estimates stride length if user data provided
     * 6. Calculates base steps from cadence and duration
     * 7. Applies activity-specific corrections
     * 8. Calculates confidence score
     * 9. Returns steps only if confidence threshold met
     * 
     * @param cadence Steps per minute from sensor (0-300 valid range)
     * @param speed Movement speed in km/h (0-30 valid range)
     * @param isRunning Boolean flag indicating running mode (may be unreliable)
     * @param strideLength Optional stride length in cm (if available from device)
     * @param timestamp Reading timestamp in milliseconds (defaults to current time)
     * @param userHeight User height in cm (for stride calculation)
     * @param userWeight User weight in kg (for stride calculation)
     * @param userAge User age in years (for stride calculation)
     * @param userGender User gender (0=female, 1=male, for stride calculation)
     * 
     * @return StepCalculationResult containing:
     *         - incrementalSteps: Steps for this measurement period only
     *         - confidence: Reliability score (0.0-1.0)
     *         - activityType: Detected activity classification
     *         - averageCadence: Recent average cadence
     *         - currentSpeed: Current speed reading
     *         - smoothedCadence: Filtered cadence value
     * 
     * @throws IllegalArgumentException if cadence or speed values are invalid
     */
    fun calculateCurrentSteps(
        cadence: Int,
        speed: Float,
        isRunning: Boolean,
        strideLength: Int? = null,
        timestamp: Long = System.currentTimeMillis(),
        userHeight: Int? = null,
        userWeight: Float? = null,
        userAge: Int? = null,
        userGender: Int? = null
    ): StepCalculationResult {
        
        // Validate input data quality
        if (!isValidReading(cadence, speed)) {
            return createZeroResult()
        }
        
        // First reading - no duration available for calculation
        if (lastTimestamp == 0L) {
            lastTimestamp = timestamp
            addReading(SensorReading(cadence, speed, strideLength))
            return createZeroResult()
        }
        
        val duration = timestamp - lastTimestamp
        
        // Validate time duration between readings
        if (duration < MIN_DURATION_MS || duration > MAX_DURATION_MS) {
            return createZeroResult()
        }
        
        // Add current reading to history
        val reading = SensorReading(cadence, speed, strideLength)
        addReading(reading)

        // Apply smoothing and outlier filtering
        val smoothedCadence = getSmoothedCadence()
        val filteredCadence = outlierFilter.filter(smoothedCadence)

        // Detect activity type from movement patterns
        val activityType = activityDetector.detectActivity(
            cadence = filteredCadence,
            speed = speed,
            isRunning = isRunning
        )

        // Calculate stride length from user anthropometrics if not provided
        val effectiveStrideLength = strideLength ?: run {
            if (userHeight != null && userWeight != null && userAge != null && userGender != null) {
                StrideCalculator.calculateStride(userHeight, userWeight, userAge, userGender, activityType)
            } else null
        }

        // Update reading with calculated stride
        val finalReading = reading.copy(strideLength = effectiveStrideLength)
        
        // Calculate base steps from cadence and time duration
        val baseSteps = calculateBaseSteps(filteredCadence, duration)
        
        // Calculate uncorrected steps (just base calculation)
        val uncorrectedSteps = calculateBaseSteps(cadence.toDouble(), duration)
        
        // Apply activity-specific and stride-based corrections
        val correctedSteps = applyActivityCorrection(baseSteps, activityType, finalReading)
        
        // Calculate confidence score for this reading
        val confidence = calculateConfidence(finalReading, activityType)
        
        // Apply confidence threshold - reject low-confidence readings
        val finalSteps = if (confidence >= 0.6f) correctedSteps else 0f
        val finalUncorrectedSteps = if (confidence >= 0.6f) uncorrectedSteps else 0f
        
        // Update totals
        totalCorrectedSteps += finalSteps
        totalUncorrectedSteps += finalUncorrectedSteps
        
        // Update timestamp for next calculation
        lastTimestamp = timestamp
        
        return StepCalculationResult(
            incrementalSteps = finalSteps,
            confidence = confidence,
            activityType = activityType,
            averageCadence = getRecentAverageCadence(),
            currentSpeed = speed,
            smoothedCadence = filteredCadence,
            totalCorrectedSteps = totalCorrectedSteps,
            totalUncorrectedSteps = totalUncorrectedSteps,
            incrementalUncorrectedSteps = finalUncorrectedSteps
        )
    }
    
    /**
     * Calculates base step count from cadence and time duration.
     * 
     * Uses the formula: steps = cadence (steps/min) × duration (minutes)
     * 
     * @param cadence Filtered cadence in steps per minute
     * @param durationMs Time duration in milliseconds
     * @return Base step count for the time period (precise float value)
     */
    private fun calculateBaseSteps(cadence: Double, durationMs: Long): Float {
        val durationMinutes = durationMs / (60 * 1000.0)
        return (cadence * durationMinutes).toFloat()
    }
    
    /**
     * Applies activity-specific and stride-based corrections to base step count.
     * 
     * Different activities have different step counting characteristics:
     * - Stationary: 0% (no steps)
     * - Walking slow: 92% (conservative counting)
     * - Walking normal: 96% (slight under-counting)
     * - Walking fast: 98% (nearly accurate)
     * - Jogging: 100% (baseline)
     * - Running: 102% (slight over-counting for intensity)
     * - Sprinting: 105% (higher intensity bonus)
     * 
     * Stride length corrections:
     * - Short stride (<60cm): 95% (more steps for same distance)
     * - Normal stride (60-120cm): 100% (no adjustment)
     * - Long stride (>120cm): 105% (fewer steps for same distance)
     * 
     * @param baseSteps Uncorrected step count
     * @param activityType Detected activity classification
     * @param reading Sensor reading with stride information
     * @return Corrected step count (precise float value)
     */
    private fun applyActivityCorrection(
        baseSteps: Float,
        activityType: ActivityType,
        reading: SensorReading
    ): Float {
        val correctionFactor = when (activityType) {
            ActivityType.STATIONARY -> 0.0f
            ActivityType.WALKING_SLOW -> 0.92f
            ActivityType.WALKING_NORMAL -> 0.96f
            ActivityType.WALKING_FAST -> 0.98f
            ActivityType.JOGGING -> 1.0f
            ActivityType.RUNNING -> 1.02f
            ActivityType.SPRINTING -> 1.05f
        }
        
        // Stride length correction if available
        val strideLengthCorrection = reading.strideLength?.let { stride ->
            when {
                stride < 60 -> 0.95f
                stride > 120 -> 1.05f
                else -> 1.0f
            }
        } ?: 1.0f
        
        return baseSteps * correctionFactor * strideLengthCorrection
    }
    
    /**
     * Calculates confidence score for the current reading based on data quality.
     * 
     * Confidence factors:
     * - Realistic cadence values (30-250 spm): Higher confidence
     * - Realistic speed values (0.5-25 km/h): Higher confidence
     * - Speed/activity consistency: Higher confidence
     * - Recent reading consistency: Bonus confidence
     * 
     * Confidence penalties:
     * - Very low cadence (<30): 70% penalty
     * - Very high cadence (>250): 60% penalty
     * - Speed/activity mismatch: 50% penalty
     * - Unrealistic speed (>25 km/h): 40% penalty
     * 
     * Consistency bonuses (based on standard deviation of recent cadences):
     * - Very consistent (σ<5): +15%
     * - Consistent (σ<10): +10%
     * - Somewhat consistent (σ<20): +5%
     * 
     * @param reading Current sensor reading
     * @param activityType Detected activity type
     * @return Confidence score between 0.0 and 1.0
     */
    private fun calculateConfidence(reading: SensorReading, activityType: ActivityType): Float {
        var confidence = 1.0f
        
        // Reduce confidence for edge cases
        when {
            reading.cadence < 30 -> confidence *= 0.3f
            reading.cadence > 250 -> confidence *= 0.4f
            reading.speed < 0.5f && activityType != ActivityType.STATIONARY -> confidence *= 0.5f
            reading.speed > 25f -> confidence *= 0.6f
        }
        
        // Consistency bonus based on recent readings
        if (readings.size >= 3) {
            val recentCadences = readings.takeLast(3).map { it.cadence.toDouble() }
            val variance = recentCadences.map { (it - recentCadences.average()).pow(2) }.average()
            val stdDev = sqrt(variance)
            
            when {
                stdDev < 5 -> confidence += 0.15f
                stdDev < 10 -> confidence += 0.1f
                stdDev < 20 -> confidence += 0.05f
            }
        }
        
        return confidence.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Calculates smoothed cadence using weighted average of recent readings.
     * 
     * Applies temporal smoothing to reduce sensor noise:
     * - 5+ readings: Weighted average (more weight on recent values)
     * - 3-4 readings: Simple average
     * - <3 readings: Use latest reading only
     * 
     * @return Smoothed cadence value
     */
    private fun getSmoothedCadence(): Double {
        val recentReadings = readings.takeLast(5)
        return when {
            recentReadings.size >= 5 -> {
                // Weighted average - more weight on recent readings
                val weights = listOf(0.1, 0.15, 0.2, 0.25, 0.3)
                recentReadings.zip(weights) { reading, weight ->
                    reading.cadence * weight
                }.sum()
            }
            recentReadings.size >= 3 -> {
                recentReadings.map { it.cadence }.average()
            }
            else -> readings.last().cadence.toDouble()
        }
    }
    
    /**
     * Calculates recent average cadence for reporting purposes.
     * 
     * @return Average cadence of last 5 readings
     */
    private fun getRecentAverageCadence(): Double {
        return readings.takeLast(5).map { it.cadence }.average()
    }
    
    /**
     * Validates sensor reading values are within acceptable ranges.
     * 
     * @param cadence Steps per minute (must be 0-300)
     * @param speed Speed in km/h (must be 0-30)
     * @return true if reading is valid, false otherwise
     */
    private fun isValidReading(cadence: Int, speed: Float): Boolean {
        return cadence in 0..300 && speed >= 0 && speed <= 30
    }
    
    /**
     * Adds a reading to the history buffer, maintaining size limit.
     * 
     * @param reading Sensor reading to add
     */
    private fun addReading(reading: SensorReading) {
        readings.add(reading)
        if (readings.size > MAX_READINGS_HISTORY) {
            readings.removeAt(0)
        }
    }
    
    /**
     * Creates a zero result for invalid or first readings.
     * 
     * @return StepCalculationResult with zero steps and low confidence
     */
    private fun createZeroResult(): StepCalculationResult {
        return StepCalculationResult(
            incrementalSteps = 0f,
            confidence = 0.0f,
            activityType = ActivityType.STATIONARY,
            averageCadence = 0.0,
            currentSpeed = 0.0f,
            smoothedCadence = 0.0,
            totalCorrectedSteps = totalCorrectedSteps,
            totalUncorrectedSteps = totalUncorrectedSteps,
            incrementalUncorrectedSteps = 0f
        )
    }
    
    /**
     * Resets the calculator state, clearing all history and totals.
     * 
     * Use this when starting a new activity session or when
     * sensor data becomes unreliable.
     */
    fun reset() {
        readings.clear()
        lastTimestamp = 0L
        outlierFilter.reset()
        totalCorrectedSteps = 0f
        totalUncorrectedSteps = 0f
    }
}