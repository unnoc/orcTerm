# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\sdk/tools/proguard/proguard-android.txt

# --- Android Core ---
-keep class com.orcterm.core.ssh.SshNative { *; }
-keep class com.orcterm.data.HostEntity
-keep class **.R$* { *; }
-keep class **.R { *; }

# --- AndroidX / Material ---
-keep class com.google.android.material.** { *; }
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn com.google.android.material.**
-dontwarn androidx.**

# --- Room ---
-keep class androidx.room.RoomDatabase { *; }
-keep class androidx.room.Room { *; }
-dontwarn androidx.room.paging.**

# --- MPAndroidChart ---
-keep class com.github.mikephil.charting.** { *; }

# --- ZXing ---
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.CoroutineExceptionHandler {
    <init>(...);
}

# --- Generic ---
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# Keep application classes that are used via reflection or XML
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep custom views
-keep public class com.orcterm.ui.widget.** { *; }
