# © 2026 JB Atomic Industries — ATOMIC TV Console
-keep class com.jbatomic.tvconsole.MainActivity { public <init>(); }
# Keep referenced public API of upstream ADB transport.
-keep class com.tananaev.adblib.** { *; }
