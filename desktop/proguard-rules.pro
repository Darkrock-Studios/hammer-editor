################################################################################
# GLOBAL SETTINGS
# Library rules from META-INF/proguard and ServiceLoader providers come from the
# proguardLibraryRules task; Compose's default rules cover Kotlin, Skiko,
# coroutines, and kotlinx.serialization.
################################################################################
-ignorewarnings

# The obfuscation pass is what strips attributes outside -keepattributes, and the
# optimizer leaves LocalVariableTables it cannot load. Nothing is renamed.
-keepnames class ** { *; }
-keeppackagenames **

# -ignorewarnings leaves unresolved references, so type narrowing is unsound
# (okio's buffer() fails verification) and merged classes break reflection.
-optimizations !method/specialization/**,!field/specialization/type,!class/merging/**

-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*,NestHost,NestMembers,PermittedSubclasses,Record,MethodParameters

-keepdirectories META-INF/services
-adaptresourcefilenames META-INF/services/**
-adaptresourcefilecontents META-INF/services/**

-keepclasseswithmembers public class com.darkrockstudios.apps.hammer.desktop.MainKt {
    public static void main(java.lang.String[]);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclasseswithmembernames class * {
    native <methods>;
}

################################################################################
# JNA AND NUCLEUS
# From Nucleus' own defaults (plugin-build/.../nucleus-default-compose-desktop-rules.pro):
# JNA proxies resolve interface methods and Structure fields by name, and the
# Nucleus natives call back into these classes over JNI.
################################################################################
-keep class com.sun.jna.* { *; }
-keep class com.sun.jna.ptr.* { *; }
-keep class com.sun.jna.internal.* { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * extends com.sun.jna.NativeLong { *; }
-dontnote com.sun.jna.**

-keepclasseswithmembernames,includedescriptorclasses class dev.nucleusframework.** {
    native <methods>;
}
-keep class dev.nucleusframework.**Bridge { *; }
-keep class dev.nucleusframework.**Jni { *; }
-keep interface dev.nucleusframework.launcher.windows.ThumbBarClickListener {
    void onThumbButtonClick(int);
}
-keep class * implements dev.nucleusframework.launcher.windows.ThumbBarClickListener {
    void onThumbButtonClick(int);
}
-keep class dev.nucleusframework.window.** { *; }
-keep class dev.nucleusframework.darkmodedetector.** { *; }
-keep class dev.nucleusframework.energymanager.** { *; }
-keep class com.jetbrains.** { *; }

################################################################################
# DBUS-JAVA (Linux portals and launcher quicklist)
# Calls, signals, and structs are bound by reflection over interface methods,
# constructors, and annotated fields.
################################################################################
-keep class org.freedesktop.dbus.** { *; }
-keep interface * extends org.freedesktop.dbus.interfaces.DBusInterface { *; }
-keep class * implements org.freedesktop.dbus.interfaces.DBusInterface { *; }
-keep class * extends org.freedesktop.dbus.Struct { *; }
-keep class * extends org.freedesktop.dbus.Tuple { *; }
-keep class * extends org.freedesktop.dbus.messages.DBusSignal { *; }
-keep class * extends org.freedesktop.dbus.exceptions.DBusExecutionException { *; }

################################################################################
# LOGGING
# commons-logging (PDFBox) picks its implementation by name, so the shrinker sees
# no reference to LogFactoryImpl and PDDocument's static init dies without it.
################################################################################
-keep class org.apache.commons.logging.** { *; }
-dontwarn org.apache.commons.logging.**

# Napier's DebugAntilog reads its caller at a fixed stack depth, so inlining
# inside Napier leaves the index past the end of the trace.
-keep,includecode class io.github.aakira.napier.** { *; }

################################################################################
# WARNING SUPPRESSION
################################################################################
-dontwarn okio.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn dev.nucleusframework.**
-dontnote dev.nucleusframework.**
