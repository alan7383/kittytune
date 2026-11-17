# Wear OS companion ProGuard / R8 optimization and shrinking rules
# Target module: :wear

# Preserve line numbers and attributes for debugging and stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Google Play Services Wearable Data Layer API
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.wearable.**
-keepclassmembers class * implements com.google.android.gms.wearable.DataEvent { *; }
-keepclassmembers class * implements com.google.android.gms.wearable.MessageEvent { *; }
-keepclassmembers class * implements com.google.android.gms.wearable.Node { *; }
-keepclassmembers class * implements com.google.android.gms.wearable.CapabilityInfo { *; }

# Kotlinx Serialization (Shared protocol models and payloads)
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# AndroidX Wear Compose (Material 3, Foundation, Navigation)
-dontwarn androidx.wear.compose.**
-keep class androidx.wear.compose.** { *; }
-dontwarn androidx.compose.**

# Rotary Crown Input & Focus
-dontwarn androidx.compose.ui.input.rotary.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# OkHttp & WebSocket (Direct Desktop Connect & Together Relay)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Preserve R class fields for dynamic resource lookups
-keepclassmembers class **.R$* {
    public static <fields>;
}
