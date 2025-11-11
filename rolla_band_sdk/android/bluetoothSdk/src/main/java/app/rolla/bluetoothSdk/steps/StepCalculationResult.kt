package app.rolla.bluetoothSdk.steps

data class StepCalculationResult(
    val incrementalSteps: Float,
    val confidence: Float,
    val activityType: ActivityType,
    val averageCadence: Double,
    val currentSpeed: Float,
    val smoothedCadence: Double,
    val totalCorrectedSteps: Float,
    val totalUncorrectedSteps: Float,
    val incrementalUncorrectedSteps: Float
) {
    /**
     * Gets the difference between corrected and uncorrected incremental steps.
     */
    val incrementalCorrectionDifference: Float
        get() = incrementalSteps - incrementalUncorrectedSteps
    
    /**
     * Gets the total correction difference.
     */
    val totalCorrectionDifference: Float
        get() = totalCorrectedSteps - totalUncorrectedSteps
    
    /**
     * Gets the correction percentage for this reading.
     */
    val incrementalCorrectionPercentage: Float
        get() = if (incrementalUncorrectedSteps > 0) {
            ((incrementalSteps - incrementalUncorrectedSteps) / incrementalUncorrectedSteps) * 100f
        } else 0f
    
    /**
     * Gets the total correction percentage.
     */
    val totalCorrectionPercentage: Float
        get() = if (totalUncorrectedSteps > 0) {
            ((totalCorrectedSteps - totalUncorrectedSteps) / totalUncorrectedSteps) * 100f
        } else 0f
}
