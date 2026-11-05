# Consumer ProGuard / R8 rules for :wear-common
# Automatically inherited by consuming applications (:wear, :app) during minification.

# -----------------------------------------------------------------------------
# 1. Google Play Services Wearable Data Layer API
# -----------------------------------------------------------------------------
# Preserve Play Services Wearable client classes, data items, and IPC listeners
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.wearable.**

# Preserve classes and callbacks implementing Data Layer event listeners
-keepclassmembers class * implements com.google.android.gms.wearable.DataClient$OnDataChangedListener { *; }
-keepclassmembers class * implements com.google.android.gms.wearable.MessageClient$OnMessageReceivedListener { *; }
-keepclassmembers class * implements com.google.android.gms.wearable.CapabilityClient$OnCapabilityChangedListener { *; }
-keepclassmembers class * implements com.google.android.gms.wearable.DataEvent { *; }
-keepclassmembers class * implements com.google.android.gms.wearable.MessageEvent { *; }
-keepclassmembers class * implements com.google.android.gms.wearable.Node { *; }
-keepclassmembers class * implements com.google.android.gms.wearable.CapabilityInfo { *; }

# -----------------------------------------------------------------------------
# 2. Shared Protocol Models & Codecs (:wear-common)
# -----------------------------------------------------------------------------
# Preserve all data models, codecs, and constants in com.alananasss.kittytune.wear.common
-keep class com.alananasss.kittytune.wear.common.** { *; }
-keepclassmembers class com.alananasss.kittytune.wear.common.** { *; }

# -----------------------------------------------------------------------------
# 3. Kotlinx Serialization for Shared Payloads
# -----------------------------------------------------------------------------
# Suppress expected serialization notes
-dontnote kotlinx.serialization.SerializationKt

# Preserve companion objects and serializer factories for wear.common classes
-keepclassmembers class com.alananasss.kittytune.wear.common.** {
    *** Companion;
}
-keepclasseswithmembers class com.alananasss.kittytune.wear.common.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Preserve @Serializable classes and generated serializer members in wear.common
-keep @kotlinx.serialization.Serializable class com.alananasss.kittytune.wear.common.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.alananasss.kittytune.wear.common.** { *; }
-keepclassmembers class com.alananasss.kittytune.wear.common.** {
    @kotlinx.serialization.SerialName <fields>;
}
