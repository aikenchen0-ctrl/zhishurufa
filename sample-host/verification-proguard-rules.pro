# 仅仪器测试共享的运行时边界，避免宿主先内联删除而测试 APK 仍引用原符号。
-keep class androidx.tracing.Trace { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class com.zhishurufa.sample.R$* { *; }
