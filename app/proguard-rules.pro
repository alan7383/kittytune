# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Native (JNI) methods and C++ DSP processors
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.alananasss.kittytune.ui.player.audio.** { *; }
-keep class com.alananasss.kittytune.data.AudioScannerManager { *; }

# ONNX Runtime (AI music detection / ArtifactNet)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Rive Android (SoundCloud Wrapped story engine)
-keep class app.rive.** { *; }
-dontwarn app.rive.**

# NewPipe Extractor
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# Subprojects / Modules
-keep class com.my.kizzy.** { *; }
-keep class com.alananasss.lrclib.** { *; }
-keep class com.alananasss.shazamkit.** { *; }

# Serialization & Models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers enum * { *; }
-keep class com.alananasss.kittytune.domain.** { *; }
-keep class com.alananasss.kittytune.data.model.** { *; }
-keep class com.alananasss.kittytune.data.remote.** { *; }
-keep class com.alananasss.kittytune.audio.providers.** { *; }

# Kotlinx Serialization
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit & OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Brotli
-keep class org.brotli.** { *; }
-dontwarn org.brotli.**

# AboutLibraries
-keep class com.mikepenz.aboutlibraries.** { *; }
-dontwarn com.mikepenz.aboutlibraries.**

# Coil
-dontwarn coil.**

# Compose
-dontwarn androidx.compose.**
