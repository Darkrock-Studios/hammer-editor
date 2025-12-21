################################################################################
# GLOBAL SETTINGS (Minification Only - Stable for 2025)
################################################################################
-dontobfuscate
-dontoptimize
-ignorewarnings

# Essential for stack traces, reflection, and DI (Koin/Serialization)
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*

# Preserve the ServiceLoader directory (Essential for Ktor, Darklaf, and Coil)
-keepdirectories META-INF/services
-adaptresourcefilenames META-INF/services/**
-adaptresourcefilecontents META-INF/services/**

################################################################################
# ENTRY POINT & PROJECT
################################################################################
-keepclasseswithmembers public class com.darkrockstudios.apps.hammer.desktop.MainKt {
    public static void main(java.lang.String[]);
}
-keep class com.darkrockstudios.** { *; }

################################################################################
# DARKLAF / JTHEMEDETECTOR (Fixes Theme Provider Error)
################################################################################
# Keep the Darklaf package and all classes implementing the Theme interface
-keep class com.github.weisj.darklaf.** { *; }
-keep class * implements com.github.weisj.darklaf.theme.Theme

################################################################################
# COIL 3 (Fixes Images Not Loading)
################################################################################
-keep class coil3.** { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget
-keep class * implements coil3.util.FetcherServiceLoaderTarget
# Keep Ktor network fetcher if used by Coil
-keep class coil3.network.ktor.** { *; }

################################################################################
# KTOR & SERIALIZATION
################################################################################
-keep class io.ktor.** { *; }
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider

################################################################################
# SYSTEM & NATIVE (OSHI / JNA)
################################################################################
# Fix for the NullPointerException inside EnumMap
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class oshi.** { *; }
-keep class com.jthemedetecor.** { *; }
-keep class sun.** { *; }
-keep class com.sun.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

################################################################################
# LIBRARIES (Broad Keeps for Stability)
################################################################################
-keep class androidx.compose.** { *; }
-keep class androidx.collection.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class org.jetbrains.compose.** { *; }
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class com.arkivanov.** { *; }
-keep class org.koin.** { *; }

################################################################################
# CLEANUP & WARNING SUPPRESSION
################################################################################
-dontwarn okio.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn com.github.weisj.darklaf.**
-dontwarn oshi.**

-keep class com.arkivanov.decompose.extensions.compose.mainthread.SwingMainThreadChecker { *; }
