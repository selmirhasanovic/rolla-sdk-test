# Keep Flutter plugin classes
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }
-keep class io.flutter.embedding.** { *; }

# Keep Pigeon generated classes
-keep class com.rolla.band.sdk.generated.** { *; }

# Keep Hilt/Dagger classes
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class **_HiltComponents { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.internal.GeneratedEntryPoint { *; }
-keep class androidx.hilt.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.EntryPoint { *; }
-keep @dagger.hilt.android.internal.managers.ComponentSupplier class * { *; }
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class dagger.hilt.internal.** { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**
-dontwarn dagger.**

# Keep bluetoothSdk classes
-keep class app.rolla.bluetoothSdk.** { *; }

# Keep SDK classes
-keep class com.rolla.band.sdk.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Keep attributes for better stacktraces
-keepattributes SourceFile,LineNumberTable

