package app.rolla.bluetoothSdk.steps


/**
 * Calculates stride length based on user anthropometric data and activity type.
 * Uses multiple validated formulas from biomechanics research.
 */
object StrideCalculator {
    
    /**
     * Calculate stride length based on user characteristics and activity.
     * 
     * @param height Height in cm
     * @param weight Weight in kg
     * @param age Age in years
     * @param gender 0 = female, 1 = male
     * @param activityType Current activity type
     * @return Estimated stride length in cm
     */
    fun calculateStride(
        height: Int,
        weight: Float,
        age: Int,
        gender: Int,
        activityType: ActivityType = ActivityType.WALKING_NORMAL
    ): Int {
        val baseStride = calculateBaseStride(height, gender)
        val weightCorrection = calculateWeightCorrection(height, weight)
        val ageCorrection = calculateAgeCorrection(age)
        val activityCorrection = calculateActivityCorrection(activityType)
        
        return (baseStride * weightCorrection * ageCorrection * activityCorrection).toInt()
    }
    
    /**
     * Base stride calculation using height and gender.
     * Based on research: Stride ≈ 0.413 × height (males), 0.415 × height (females)
     */
    private fun calculateBaseStride(height: Int, gender: Int): Double {
        val multiplier = if (gender == 1) 0.413 else 0.415 // Females slightly higher ratio
        return height * multiplier
    }
    
    /**
     * Weight correction factor based on BMI.
     * Heavier individuals tend to have slightly shorter relative stride.
     */
    private fun calculateWeightCorrection(height: Int, weight: Float): Double {
        val heightM = height / 100.0
        val bmi = weight / (heightM * heightM)
        
        return when {
            bmi < 18.5 -> 1.02  // Underweight - slightly longer stride
            bmi < 25.0 -> 1.0   // Normal weight
            bmi < 30.0 -> 0.98  // Overweight - slightly shorter stride
            else -> 0.95        // Obese - shorter stride
        }
    }
    
    /**
     * Age correction factor.
     * Stride length decreases with age due to reduced mobility.
     */
    private fun calculateAgeCorrection(age: Int): Double {
        return when {
            age < 20 -> 0.95    // Young adults - still developing
            age < 30 -> 1.0     // Peak stride length
            age < 50 -> 0.98    // Slight decrease
            age < 65 -> 0.95    // Moderate decrease
            else -> 0.90        // Significant decrease for elderly
        }
    }
    
    /**
     * Activity-based stride adjustment.
     * Running typically has longer stride than walking.
     */
    private fun calculateActivityCorrection(activityType: ActivityType): Double {
        return when (activityType) {
            ActivityType.STATIONARY -> 0.0
            ActivityType.WALKING_SLOW -> 0.92
            ActivityType.WALKING_NORMAL -> 1.0
            ActivityType.WALKING_FAST -> 1.08
            ActivityType.JOGGING -> 1.15
            ActivityType.RUNNING -> 1.25
            ActivityType.SPRINTING -> 1.35
        }
    }
}