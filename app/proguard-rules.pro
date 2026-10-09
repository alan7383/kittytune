# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Native (JNI) methods and C++ DSP processors
-keepclasseswithmembers class * {
    native <methods>;
}
-keep class com.alananasss.kittytune.data.zapret.** { *; }
-keep class com.alananasss.kittytune.ui.player.audio.** { *; }
-keep class com.alananasss.kittytune.data.AudioScannerManager { *; }

# ONNX Runtime (AI music detection / ArtifactNet)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Rive Android (SoundCloud Wrapped story engine)
-keep class app.rive.** { *; }
-dontwarn app.rive.**

# NewPipe Extractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
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
-keep class com.zionhuang.innertube.** { *; }
-keep class com.zionhuang.kugou.** { *; }

# Gson & Serialization Models
-keep class com.google.gson.** { *; }
-dontwarn sun.misc.**
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers enum * { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Kotlinx Serialization
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

# App Models, Entities & Providers
-keep class com.alananasss.kittytune.domain.** { *; }
-keep class com.alananasss.kittytune.data.model.** { *; }
-keep class com.alananasss.kittytune.data.remote.** { *; }
-keep class com.alananasss.kittytune.data.lyrics.models.** { *; }
-keep class com.alananasss.kittytune.data.yearlyplayback.** { *; }
-keep class com.alananasss.kittytune.data.musicimport.** { *; }
-keep class com.alananasss.kittytune.data.spotify.** { *; }
-keep class com.alananasss.kittytune.data.upload.** { *; }
-keep class com.alananasss.kittytune.data.vk.** { *; }
-keep class com.alananasss.kittytune.ui.**.**Models* { *; }
-keep class com.alananasss.kittytune.audio.providers.** { *; }
-keep class com.alananasss.kittytune.data.SessionManager** { *; }
-keep class com.alananasss.kittytune.data.TokenManager** { *; }
-keep class com.alananasss.kittytune.data.AuthFlowManager** { *; }
-keep class com.alananasss.kittytune.data.PkceHelper** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.alananasss.kittytune.data.local.** { *; }
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

# Lottie
-keep class com.airbnb.lottie.** { *; }

# OpenCC4J
-keep class com.github.houbb.opencc4j.** { *; }
-dontwarn com.github.houbb.opencc4j.**

# ZXing & Camera
-dontwarn com.google.zxing.**
-dontwarn androidx.camera.**

# Media3
-dontwarn androidx.media3.**

# WebKit & JavaScript Interface
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    *;
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    *;
}
-keep class androidx.webkit.** { *; }
-dontwarn androidx.webkit.**
-keep class org.chromium.support_lib_boundary.** { *; }
-dontwarn org.chromium.support_lib_boundary.**
-keep class com.alananasss.kittytune.ui.common.SafeWebView { *; }
