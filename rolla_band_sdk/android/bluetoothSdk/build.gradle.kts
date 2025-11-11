plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")  // Required for annotation processing
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "app.rolla.bluetoothSdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ENABLE_LOGS", "true")
        }
        debug {
            buildConfigField("boolean", "ENABLE_LOGS", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    api("no.nordicsemi.android:dfu:2.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.0.0")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("org.powermock:powermock-api-mockito2:2.0.9")
    testImplementation("org.powermock:powermock-module-junit4:2.0.9")
    testImplementation("io.mockk:mockk-android:1.14.5")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57.1")
    kapt("com.google.dagger:hilt-android-compiler:2.57.1")

    // For testing with Hilt
    testImplementation("com.google.dagger:hilt-android-testing:2.57.1")
    kaptTest("com.google.dagger:hilt-android-compiler:2.57.1")
    testImplementation(kotlin("test"))
}