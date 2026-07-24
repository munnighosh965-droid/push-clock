# Power Clock ProGuard/R8 rules.

# MediaPipe Tasks (native JNI bindings and protobuf-lite generated classes).
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.auto.value.**
# MediaPipe shades javapoet/autovalue which reference compile-time-only
# javax.lang.model classes never used at runtime on Android.
-dontwarn javax.lang.model.**

# ZXing (pure Java, reflection-free, but keep result parsers used indirectly).
-dontwarn com.google.zxing.**

# Keep Room entity/DAO metadata handled by KSP; no extra rules needed.

# Keep the application class referenced from the manifest.
-keep class com.powerclock.alarm.PowerClockApp { *; }
