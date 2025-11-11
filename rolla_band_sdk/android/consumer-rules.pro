# Consumer ProGuard rules for rolla_band_sdk
# These rules will be merged into the consuming app's ProGuard configuration

# Keep Pigeon generated classes
-keep class com.rolla.band.sdk.generated.** { *; }

# Keep SDK public API
-keep class com.rolla.band.sdk.** { *; }

