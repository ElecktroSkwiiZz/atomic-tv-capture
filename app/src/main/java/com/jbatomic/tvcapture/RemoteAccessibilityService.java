/* ATOMIC TV Capture — JB ATOMIC INDUSTRIES — https://jbatomic.com/
 * © 2026 JB Atomic Industries. Product branding and attribution.
 * User screenshots and recordings remain unwatermarked.
 */
package com.jbatomic.tvcapture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class RemoteAccessibilityService extends AccessibilityService {
    private static final long SINGLE_HOLD_MS = 1100;
    private static final long COMBO_HOLD_MS = 700;
    private static volatile RemoteAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Integer> pressed = new HashSet<>();
    private Runnable shotTask, videoTask;
    private boolean shotFired, videoFired;

    public static boolean isConnected() { return instance != null; }
    public static boolean requestScreenshot() {
        RemoteAccessibilityService current = instance;
        if (current == null) return false;
        current.handler.post(current::takeCapture);
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        cancelShot(); cancelVideo(); pressed.clear();
        super.onDestroy();
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            pressed.add(code);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            pressed.remove(code);
        } else return false;

        ShortcutConfig shot = ShortcutConfig.read(this, ShortcutConfig.SCREENSHOT);
        ShortcutConfig video = ShortcutConfig.read(this, ShortcutConfig.VIDEO);
        if (!matches(shot)) { cancelShot(); shotFired = false; }
        else if (shotTask == null && !shotFired) {
            shotTask = () -> {
                shotTask = null;
                if (matches(ShortcutConfig.read(this, ShortcutConfig.SCREENSHOT))) {
                    shotFired = true; takeCapture();
                }
            };
            handler.postDelayed(shotTask, shot.isChord() ? COMBO_HOLD_MS : SINGLE_HOLD_MS);
        }
        if (!matches(video)) { cancelVideo(); videoFired = false; }
        else if (videoTask == null && !videoFired) {
            videoTask = () -> {
                videoTask = null;
                if (matches(ShortcutConfig.read(this, ShortcutConfig.VIDEO))) {
                    videoFired = true; toggleRecording();
                }
            };
            handler.postDelayed(videoTask, video.isChord() ? COMBO_HOLD_MS : SINGLE_HOLD_MS);
        }
        // Never swallow original remote key events; a short press remains a normal press.
        return false;
    }

    private boolean matches(ShortcutConfig c) {
        return c.enabled() && pressed.contains(c.first) && (!c.isChord() || pressed.contains(c.second));
    }

    private void cancelShot() {
        if (shotTask != null) handler.removeCallbacks(shotTask);
        shotTask = null;
    }

    private void cancelVideo() {
        if (videoTask != null) handler.removeCallbacks(videoTask);
        videoTask = null;
    }

    private void takeCapture() {
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    HardwareBuffer buffer = result.getHardwareBuffer();
                    if (buffer == null) { toast(t("Capture impossible : écran protégé","Screenshot blocked: protected screen")); return; }
                    try {
                        ColorSpace color = result.getColorSpace();
                        if (color == null) color = ColorSpace.get(ColorSpace.Named.SRGB);
                        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, color);
                        if (hardware == null) { toast(t("Capture indisponible","Screenshot unavailable")); return; }
                        Bitmap bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false);
                        hardware.recycle();
                        if (bitmap != null) CaptureStore.saveBitmap(RemoteAccessibilityService.this, bitmap);
                        else toast(t("Impossible d'enregistrer l'image","Unable to save screenshot"));
                    } catch (Exception e) { toast(t("Échec de capture : ","Screenshot failed: ") + e.getClass().getSimpleName()); }
                    finally { buffer.close(); }
                }
                @Override public void onFailure(int errorCode) { toast(t("Échec de capture (","Screenshot failed (") + errorCode + ")"); }
            });
        } catch (Exception e) { toast(t("Capture refusée par Android","Android blocked screenshot")); }
    }

    private void toggleRecording() {
        if (RecordingService.isRecording()) {
            startService(new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_STOP));
        } else {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction(MainActivity.ACTION_REQUEST_RECORD);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { startActivity(intent); }
            catch (Exception e) { toast(t("Ouvrez ATOMIC TV Capture pour autoriser la vidéo","Open ATOMIC TV Capture to allow screen recording")); }
        }
    }

    private boolean english() {
        return "en".equals(getSharedPreferences("tv_capture", MODE_PRIVATE).getString("language",
            java.util.Locale.getDefault().getLanguage().equals("en") ? "en" : "fr"));
    }
    private String t(String fr,String en) { return english() ? en : fr; }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
