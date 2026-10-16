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

# ===== Room =====
# Room 生成的 DAO 实现通过反射查找 *_Impl 类名，混淆重命名会按名称找不回来；
# 实体、DAO、TypeConverter 的成员被生成代码引用，一并保留避免字段改名
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.TypeConverter class * { *; }

# ===== Media3 (ExoPlayer) =====
# Media3 内部依赖枚举、注解与反射（轨道选择、媒体会话、字幕渲染），整体保留保证播放功能可靠
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ===== kotlinx-serialization =====
# 序列化通过生成的 serial 描述与 KSerializer 反射查找，需保留 @Serializable 生成的序列化器
-keepattributes *Annotation*, Signature, InnerClasses
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== 缺失类告警 =====
# 部分依赖（如 ffmpeg-kit 桌面侧残留）引用 java.awt，Android 上没有这些类，
# 按 AGP 生成的 missing_rules.txt 直接忽略告警
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window

# ===== Crashlytics =====
# 保留行号与异常类型，让崩溃报告能还原到具体源码位置
-keepattributes SourceFile, LineNumberTable
-keep public class * extends java.lang.Exception