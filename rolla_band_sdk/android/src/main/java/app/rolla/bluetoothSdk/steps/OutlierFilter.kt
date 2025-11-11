package app.rolla.bluetoothSdk.steps

import kotlin.math.abs

/**
 * Outlier filter that uses Modified Z-Score method to detect and filter anomalous sensor readings.
 * 
 * This filter maintains a rolling window of recent values and uses statistical analysis
 * to identify outliers that deviate significantly from the median. When an outlier
 * is detected, it returns the median value instead of the anomalous reading.
 * 
 * The Modified Z-Score method is more robust than standard Z-Score because:
 * - Uses median instead of mean (less affected by outliers)
 * - Uses Median Absolute Deviation (MAD) instead of standard deviation
 * - More reliable for small sample sizes and non-normal distributions
 * 
 * Algorithm:
 * 1. Maintain rolling window of last 5 values
 * 2. Calculate median of current window
 * 3. Calculate MAD (Median Absolute Deviation)
 * 4. Compute Modified Z-Score for new value
 * 5. If |Modified Z-Score| > 3.5, return median; otherwise return original value
 * 
 * Use cases:
 * - Filtering noisy cadence readings from fitness sensors
 * - Removing speed spikes from GPS or accelerometer data
 * - Smoothing any continuous sensor data stream
 * 
 * @author Rolla Team
 * @since 1.0
 */
class OutlierFilter {
    
    /** Rolling window of recent values for statistical analysis */
    private val recentValues = mutableListOf<Double>()
    
    /** Maximum number of values to keep in history window */
    private val maxHistory = 5

    /**
     * Filters a new sensor value, returning either the original value or median if outlier detected.
     * 
     * The filtering process:
     * 1. Adds new value to rolling window
     * 2. Maintains window size by removing oldest value if needed
     * 3. Returns original value if insufficient data (< 3 readings)
     * 4. Calculates median and MAD from current window
     * 5. Computes Modified Z-Score for the new value
     * 6. Returns median if outlier detected, otherwise returns original value
     * 
     * Modified Z-Score formula:
     * ```
     * Modified Z-Score = 0.6745 * (value - median) / MAD
     * ```
     * 
     * Where:
     * - 0.6745 ≈ 0.75th quantile of standard normal distribution
     * - MAD = median of |values - median|
     * - Threshold = 3.5 (equivalent to ~3 standard deviations)
     * 
     * @param value New sensor reading to filter
     * @return Filtered value (original if normal, median if outlier)
     * 
     * @example
     * ```kotlin
     * val filter = OutlierFilter()
     * 
     * // Normal readings
     * filter.filter(120.0) // Returns 120.0
     * filter.filter(118.0) // Returns 118.0
     * filter.filter(122.0) // Returns 122.0
     * 
     * // Outlier reading
     * filter.filter(200.0) // Returns ~120.0 (median)
     * ```
     */
    fun filter(value: Double): Double {
        // Add new value to rolling window
        recentValues.add(value)
        
        // Maintain window size limit
        if (recentValues.size > maxHistory) {
            recentValues.removeAt(0)
        }

        // Need at least 3 values for reliable statistical analysis
        if (recentValues.size < 3) return value

        // Calculate median of current window
        val sorted = recentValues.sorted()
        val median = sorted[sorted.size / 2]
        
        // Calculate MAD (Median Absolute Deviation)
        val mad = sorted.map { abs(it - median) }.sorted()[sorted.size / 2]

        // Avoid division by zero if all values are identical
        if (mad == 0.0) return value

        // Calculate Modified Z-score for outlier detection
        val modifiedZScore = 0.6745 * (value - median) / mad

        // Return median if outlier detected (threshold = 3.5), otherwise return original
        return if (abs(modifiedZScore) < 3.5) value else median
    }

    /**
     * Resets the filter by clearing all historical values.
     * 
     * Use this when:
     * - Starting a new measurement session
     * - Sensor data becomes unreliable
     * - Switching between different data sources
     * - After long periods of inactivity
     * 
     * After reset, the next 2 values will pass through unfiltered
     * since statistical analysis requires at least 3 data points.
     */
    fun reset() {
        recentValues.clear()
    }
}