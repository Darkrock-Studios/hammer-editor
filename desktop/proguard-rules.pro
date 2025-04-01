-dontobfuscate

# Ignore all warnings
-ignorewarnings

# Basic application entry point
-keepclasseswithmembers public class com.darkrockstudios.apps.hammer.desktop.MainKt {
    public static void main(java.lang.String[]);
}

# Don't warn about missing classes from these packages
-dontwarn javax.annotation.**
-dontwarn org.lwjgl.**
-dontwarn java.awt.**
-dontwarn sun.misc.**
-dontwarn com.sun.**
-dontwarn java.lang.invoke.**
-dontwarn java.nio.**
-dontwarn org.jetbrains.**
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn com.google.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.**
-dontwarn org.intellij.**
-dontwarn org.jspecify.**
-dontwarn org.jdesktop.swingx.**
-dontwarn org.conscrypt.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.apache.batik.**
-dontwarn android.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.apache.avalon.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.log.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**

# Ignore warnings about library classes depending on program classes
-dontwarn javax.imageio.metadata.**
-dontwarn java.util.prefs.**

# Keep all classes from these packages
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class org.jetbrains.** { *; }
-keep class org.lwjgl.** { *; }
-keep class javax.annotation.** { *; }
-keep class java.** { *; }
-keep class javax.** { *; }
-keep class sun.** { *; }
-keep class com.sun.** { *; }
-keep class com.darkrockstudios.** { *; }
-keep class com.github.weisj.darklaf.** { *; }
-keep class java.awt.** { *; }
-keep class org.jdesktop.swingx.** { *; }
-keep class org.apache.batik.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.apache.log4j.** { *; }
-keep class org.apache.log.** { *; }
-keep class javax.xml.** { *; }
-keep class org.w3c.dom.** { *; }
-keep class org.xml.sax.** { *; }
-keep class com.google.** { *; }
-keep class org.intellij.** { *; }
-keep class org.conscrypt.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class android.** { *; }
-keep class org.checkerframework.** { *; }
-keep class org.codehaus.** { *; }
-keep class org.jspecify.** { *; }
-keep class org.openjsse.** { *; }
-keep class org.apache.avalon.** { *; }

# Keep specific classes that are referenced but might be missing
-keep class org.jdesktop.swingx.JXTaskPaneContainer { *; }
-keep class org.jdesktop.swingx.JXTaskPane { *; }
-keep class org.apache.log.Hierarchy { *; }
-keep class org.apache.log.Logger { *; }

# Keep specific fields and methods referenced in DarkTaskPaneUI and DarkTaskPaneContainerUI
-keepclassmembers class com.github.weisj.darklaf.ui.taskpane.DarkTaskPaneContainerUI {
    org.jdesktop.swingx.JXTaskPaneContainer taskPane;
}
-keepclassmembers class com.github.weisj.darklaf.ui.taskpane.DarkTaskPaneUI {
    org.jdesktop.swingx.JXTaskPane group;
}
-keepclassmembers class com.github.weisj.darklaf.ui.taskpane.DarkTaskPaneUI$DarkPaneBorder {
    java.awt.Color specialTitleBackground;
    java.awt.Color titleBackgroundGradientStart;
    java.awt.Color borderColor;
    javax.swing.JLabel label;
    java.awt.Color getPaintColor(org.jdesktop.swingx.JXTaskPane);
}

# Keep all interfaces
-keep interface * { *; }

# Keep class hierarchies and preserve metadata
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
-keepattributes Exceptions

# Keep all classes that might be used via reflection
-keepclassmembers class * {
    public <init>(...);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-assumenosideeffects public class androidx.compose.runtime.ComposerKt {
    void sourceInformation(androidx.compose.runtime.Composer,java.lang.String);
    void sourceInformationMarkerStart(androidx.compose.runtime.Composer,int,java.lang.String);
    void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
}

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,
                Exceptions,InnerClasses,Signature,Deprecated,
                SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# Keep class hierarchies and interfaces intact
-keepattributes InnerClasses,Signature,*Annotation*

# Keep all classes that might be used via reflection
-keepclassmembers class * {
    ** MODULE$;
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all referenced classes
-keep class * implements java.io.Serializable
-keep class * implements java.lang.Comparable
-keep class * extends java.lang.Enum

# Keep all classes referenced by native code
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep all classes with their hierarchy
-keep class * extends java.lang.Object { *; }

# Keep all classes that might be used via reflection
-keepclassmembers class * {
    public <init>(void);
}

# Serializer for classes with named companion objects are retrieved using `getDeclaredClasses`.
# If you have any, uncomment and replace classes with those containing named companion objects.
#-keepattributes InnerClasses # Needed for `getDeclaredClasses`.
#-if @kotlinx.serialization.Serializable class
#com.example.myapplication.HasNamedCompanion, # <-- List serializable classes with named companions.
#com.example.myapplication.HasNamedCompanion2
#{
#    static **$* *;
#}
#-keepnames class <1>$$serializer { # -keepnames suffices; class is kept when serializer() is kept.
#    static <1>$$serializer INSTANCE;
#}

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*

# Logging
-keep class io.github.aakira.napier.** { *; }
-dontwarn org.slf4j.**
-assumenosideeffects class org.slf4j.Logger {
    public void trace(...);
    public void debug(...);
}
