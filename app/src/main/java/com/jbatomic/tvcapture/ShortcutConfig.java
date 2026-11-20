package com.jbatomic.tvcapture;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

/** The second key is optional; a chord requires both keys to be pressed simultaneously. */
public final class ShortcutConfig {
    public static final String SCREENSHOT = "shot";
    public static final String VIDEO = "video";
    public final int first;
    public final int second;

    public ShortcutConfig(int first, int second) {
        this.first = first;
        this.second = second;
    }

    public boolean enabled() { return first >= 0; }
    public boolean isChord() { return second >= 0; }

    public static ShortcutConfig read(Context ctx, String action) {
        SharedPreferences p = ctx.getSharedPreferences("tv_capture", Context.MODE_PRIVATE);
        int initial = SCREENSHOT.equals(action) ? KeyEvent.KEYCODE_MENU : -1;
        // Migrate the 1.0 settings without invalidating previous user shortcuts.
        int old = p.getInt(SCREENSHOT.equals(action) ? "screenshot_key" : "recording_key", initial);
        return new ShortcutConfig(p.getInt(action + "_primary", old), p.getInt(action + "_secondary", -1));
    }

    public void save(Context ctx, String action) {
        ctx.getSharedPreferences("tv_capture", Context.MODE_PRIVATE).edit()
            .putInt(action + "_primary", first).putInt(action + "_secondary", second).apply();
    }

    public String label() {
        if (!enabled()) return "Désactivé";
        if (!isChord()) return key(first) + " · appui long";
        return key(first) + " + " + key(second) + " · appui long";
    }

    public static String key(int code) {
        if (code < 0) return "—";
        return KeyEvent.keyCodeToString(code).replace("KEYCODE_", "").replace('_', ' ');
    }

    public boolean sameAs(ShortcutConfig other) {
        if (!enabled() || !other.enabled()) return false;
        return (first == other.first && second == other.second) ||
            (isChord() && first == other.second && second == other.first);
    }

    public static boolean isUnsafe(int code) {
        return code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_POWER ||
            code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE ||
            code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN ||
            code == KeyEvent.KEYCODE_VOLUME_MUTE || code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_CENTER ||
            code == KeyEvent.KEYCODE_ENTER;
    }
}
