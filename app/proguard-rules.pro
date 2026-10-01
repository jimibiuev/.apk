# ===== aycho R8 全量混淆规则 =====
# 不要把整个包 keep 掉，只保留反射/序列化/框架入口所需的签名

# 保留行号与源文件名（便于线上问题定位；如需更小体积可关闭）
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Kotlin 元数据（协程/反射依赖）
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Shizuku（跨进程 Binder 反射调用，必须保留）
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# 无障碍服务入口（系统按类名反射实例化）
-keep class com.aycho.app.service.HeadsUpService { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.app.Service { *; }

# Activity / Application / Receiver / Provider 由 AGP 自动 keep，此处补充显式声明
-keep class com.aycho.app.MainActivity { *; }
-keep class com.aycho.app.App { *; }

# 悬浮窗/无障碍服务入口（Manifest 中以类名硬编码，必须保留原名）
-keep class com.aycho.app.ui.HeadsUpService { *; }

# JSON 与序列化数据模型（字段名参与 json 读写）
-keepclassmembers class com.aycho.app.data.** {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.aycho.app.skills.** {
    <fields>;
    <init>(...);
}

# Compose 运行时（AGP 已内置大部分规则，此处兜底）
-dontwarn androidx.compose.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**

# 协程
-dontwarn kotlinx.coroutines.**

# 去除日志（release）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# 第三方注解与可选依赖（Tink / errorprone）
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
