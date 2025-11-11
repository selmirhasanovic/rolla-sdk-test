package app.rolla.bluetoothSdk.steps

/**
 * Detects activity type based on cadence, speed, and running status.
 * 
 * Uses empirically derived thresholds based on typical human movement patterns
 * and sports science research to classify activities into different intensity levels.
 * 
 * The classification uses OR logic for thresholds, meaning if either speed OR cadence
 * indicates a higher activity level, it will classify accordingly to handle edge cases
 * where one metric might be inaccurate due to sensor limitations or individual variation.
 */
class ActivityTypeDetector {

    /**
     * Detects the current activity type based on movement metrics.
     * 
     * @param cadence Steps per minute (spm). Typical ranges:
     *                - Walking: 80-120 spm
     *                - Fast walking: 120-140 spm  
     *                - Jogging/Running: 150-190 spm
     *                - Sprinting: 180+ spm
     * @param speed Speed in km/h. Typical ranges:
     *              - Walking: 3-6 km/h
     *              - Fast walking: 5-7 km/h
     *              - Jogging: 8-12 km/h
     *              - Running: 12-16 km/h
     *              - Sprinting: 15+ km/h
     * @param isRunning Boolean flag indicating if the user is in running mode
     *                  (typically from device sensors or user input)
     * @return ActivityType classification
     */
    fun detectActivity(cadence: Double, speed: Float, isRunning: Boolean): ActivityType {
        return when {
            // Very low movement indicates stationary state
            cadence < 10 || speed < 0.5f -> ActivityType.STATIONARY
            
            // If running flag is explicitly set, use running classification (PRIORITY)
            isRunning -> detectRunningType(cadence, speed)
            
            // High cadence/speed indicates running even without flag
            cadence > 150 || speed > 8f -> detectRunningType(cadence, speed)
            
            // Otherwise classify as walking activity
            else -> detectWalkingType(cadence, speed)
        }
    }

    /**
     * Classifies running activities into intensity levels.
     * 
     * @param cadence Steps per minute
     * @param speed Speed in km/h
     * @return Running activity type (JOGGING, RUNNING, or SPRINTING)
     */
    private fun detectRunningType(cadence: Double, speed: Float): ActivityType {
        return when {
            // High-intensity running/sprinting
            speed > 15f || cadence > 200 -> ActivityType.SPRINTING
            // Moderate to fast running
            speed > 10f || cadence > 170 -> ActivityType.RUNNING
            // Light jogging (default for running mode)
            else -> ActivityType.JOGGING
        }
    }

    /**
     * Classifies walking activities into pace levels.
     * 
     * @param cadence Steps per minute
     * @param speed Speed in km/h
     * @return Walking activity type (WALKING_SLOW, WALKING_NORMAL, or WALKING_FAST)
     */
    private fun detectWalkingType(cadence: Double, speed: Float): ActivityType {
        return when {
            // Power walking/brisk walking
            speed > 6f || cadence > 140 -> ActivityType.WALKING_FAST
            // Normal walking pace
            speed > 3f || cadence > 100 -> ActivityType.WALKING_NORMAL
            // Slow walking/strolling
            else -> ActivityType.WALKING_SLOW
        }
    }
}