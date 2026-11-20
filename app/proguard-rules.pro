# © 2026 JB Atomic Industries — ATOMIC TV Capture
# Obfuscate all non-entrypoint implementation classes in Release builds.
# Components instantiated by the Android framework are preserved.
-keep class com.jbatomic.tvcapture.MainActivity { public <init>(); }
-keep class com.jbatomic.tvcapture.RemoteAccessibilityService { public <init>(); }
-keep class com.jbatomic.tvcapture.RecordingService { public <init>(); }
-keep class com.jbatomic.tvcapture.ApkProvider { public <init>(); }
