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

# ============================================================
# Retrofit + Gson + OkHttp keep rules (R8 / minifyEnabled)
# ============================================================

# Keep generic signatures and annotations so Gson reflection + Retrofit work.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# --- Retrofit ---
# Keep Retrofit service interfaces (methods annotated with @retrofit2.http.*).
-keepclasseswithmembers,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}
# Keep all Retrofit interfaces and their type information.
-keep,allowobfuscation interface retrofit2.**
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**
# Retrofit does reflection on method signatures and parameter types.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Kotlin Continuation for suspend functions used by Retrofit.
-keep class kotlin.coroutines.Continuation

# --- Gson ---
# Keep fields annotated with @SerializedName so JSON (de)serialization works.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep class com.google.gson.** { *; }
-keep class com.google.gson.annotations.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn com.google.gson.**

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ============================================================
# App data model classes used with Gson — keep names + members
# so R8 does not strip/rename the @SerializedName DTOs.
# ============================================================
-keep class app.sakinalauncher.data.muslim.** { *; }
