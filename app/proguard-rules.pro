# --- ffmpeg-kit -------------------------------------------------------------
# JNI bridge + async session/statistics callbacks (native side inke naam se dhoondta hai)
-keep class com.arthenica.** { *; }

# --- Media3 / ExoPlayer (bundled AAR apna aata hai, par transformer callbacks) ---
-keep class androidx.media3.transformer.** { *; }

# --- readable stack traces (adb logcat ke liye; koi crash reporting SDK nahi) ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# NOTE: pehle yahan `-keep class com.nova.converter.** { *; }` tha jo poore app code
# ko shrinking/obfuscation se bacha deta tha (fayda zero, R8 ka kaam zero).
# Manifest ke components (MainActivity, NovaService) AGP ke auto-generated keep
# rules se khud protect hote hain — alag se kuch nahi chahiye.
