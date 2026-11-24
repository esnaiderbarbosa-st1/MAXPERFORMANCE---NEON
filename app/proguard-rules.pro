# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- MAXPERFORMANCE STABILITY RULES ---

# Preserve all model classes in com.example.data.model
-keep class com.example.data.model.** { *; }

# Preserve constructor properties marked with @com.squareup.moshi.Json and Moshi structures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep @com.squareup.moshi.Json class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Preserve subclasses of WorkoutFlowState and the sealed interface itself
-keep interface com.example.ui.viewmodel.WorkoutFlowState { *; }
-keep class com.example.ui.viewmodel.WorkoutFlowState$* { *; }
-keep class com.example.ui.viewmodel.WorkoutFlowState** { *; }

# Preserve Coroutines and Lifecycle classes
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

