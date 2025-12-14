# Add project specific ProGuard rules here.
# Keep ML Kit classes for optimal performance
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep CameraX classes
-keep class androidx.camera.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
