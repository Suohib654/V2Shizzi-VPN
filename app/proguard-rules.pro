# Preserve APIs accessed reflectively or from registered JNI methods.
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class hev.htproxy.** { *; }
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.**

# Keep runtime annotations needed by libraries; omit source paths and line-number metadata in release.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Keep obfuscated output compact and avoid source-file information in diagnostics.
-dontusemixedcaseclassnames
