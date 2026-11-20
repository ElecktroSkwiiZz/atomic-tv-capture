/* ATOMIC TV Capture — JB ATOMIC INDUSTRIES — https://jbatomic.com/
 * © 2026 JB Atomic Industries. Product branding and attribution.
 * User screenshots and recordings remain unwatermarked.
 */
package com.jbatomic.tvcapture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

/** Compact D-pad oriented Android TV interface (no touch, webview or external UI libraries). */
public class MainActivity extends Activity {
    public static final String ACTION_REQUEST_RECORD = "com.jbatomic.tvcapture.REQUEST_RECORD";
    private static final int REQUEST_CAPTURE = 1001;
    private static final int BG = 0xff090f1d, CARD = 0xff162238, FG = 0xfff0f7ff;
    private static final int MUTED = 0xffa0b5d0, ACCENT = 0xff62d5ef, LINE = 0xff2e4762;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout body;
    private boolean consentInProgress = false;
    private boolean firstResume = true;
    private boolean english;
    private String t(String fr, String en) { return english ? en : fr; }
    private int learning = 0; // 1 screenshot, 2 video
    private boolean learningCombo = false;
    private int learningFirst = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        english = "en".equals(getSharedPreferences("tv_capture", MODE_PRIVATE)
            .getString("language", java.util.Locale.getDefault().getLanguage().equals("en") ? "en" : "fr"));
        render();
        handler.postDelayed(() -> new UpdateManager(this).checkAutomaticallyIfDue(), 1500);
        if (ACTION_REQUEST_RECORD.equals(getIntent().getAction())) handler.postDelayed(this::requestRecording, 250);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_REQUEST_RECORD.equals(intent.getAction())) handler.postDelayed(this::requestRecording, 250);
    }

    @Override protected void onResume() {
        super.onResume();
        if (firstResume) { firstResume = false; return; }
        handler.postDelayed(() -> {
            if (!isFinishing() && learning == 0) {
                render();
                new UpdateManager(this).resumePendingInstallation();
            }
        }, 500);
    }

    private void render() {
        if (learning != 0) return;
        ShareServer web = ShareServer.get(this);
        ShortcutConfig shot = ShortcutConfig.read(this, ShortcutConfig.SCREENSHOT);
        ShortcutConfig video = ShortcutConfig.read(this, ShortcutConfig.VIDEO);
        File[] files = CaptureStore.list(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(14), dp(22), dp(18));
        scroll.addView(body);
        setContentView(scroll);

        LinearLayout top = horizontal();
        body.addView(top);
        LinearLayout brand = horizontal();
        brand.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView corporateLogo = new ImageView(this);
        corporateLogo.setImageResource(R.drawable.jb_atomic_company);
        corporateLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        corporateLogo.setContentDescription("Logo JB Atomic Industries");
        brand.addView(corporateLogo, new LinearLayout.LayoutParams(dp(164), dp(55)));
        LinearLayout brandText = vertical();
        brandText.setPadding(dp(12), 0, 0, 0);
        brand.addView(brandText, new LinearLayout.LayoutParams(-2, -2));
        label(brandText, "ATOMIC TV CAPTURE", 19, FG, true, 0);
        label(brandText, "VERSION " + BuildConfig.VERSION_NAME + "  /  JB ATOMIC INDUSTRIES", 10, ACCENT, false, 0);
        TextView state = new TextView(this);
        state.setText(RemoteAccessibilityService.isConnected() ? t("●  SERVICE ACTIF", "●  SERVICE ACTIVE") : t("●  SERVICE INACTIF", "●  SERVICE INACTIVE"));
        state.setTextSize(12);
        state.setTextColor(RemoteAccessibilityService.isConnected() ? 0xff73dbad : 0xffffbd76);
        state.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        top.addView(state, new LinearLayout.LayoutParams(-2, dp(44)));
        Button switchLanguage = new Button(this);
        switchLanguage.setText(english ? "FR" : "EN");
        switchLanguage.setTextSize(13);
        switchLanguage.setAllCaps(false);
        switchLanguage.setTextColor(FG);
        switchLanguage.setBackground(round(0xff1f4961, 9));
        switchLanguage.setOnClickListener(v -> {
            english = !english;
            getSharedPreferences("tv_capture", MODE_PRIVATE).edit().putString("language", english ? "en" : "fr").apply();
            render();
        });
        LinearLayout.LayoutParams languageLp = new LinearLayout.LayoutParams(dp(65), dp(44));
        languageLp.leftMargin = dp(8);
        top.addView(switchLanguage, languageLp);
        gap(body, 10);

        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
            && getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density >= 760;
        LinearLayout upper = horizontal();
        if (!wide) upper.setOrientation(LinearLayout.VERTICAL);
        body.addView(upper);

        LinearLayout capture = panel(upper, wide, t("CAPTURE D'ÉCRAN", "SCREENSHOT"));
        label(capture, shortcutLabel(shot), 14, FG, true, 6);
        label(capture, t("Appui long 1,1 s  •  Combinaison 0,7 s", "Long press 1.1 s  •  Combination 0.7 s"), 11, MUTED, false, 7);
        action(capture, t("Capturer l'écran (après 2 secondes)", "Take screenshot (after 2 seconds)"), true, () -> {
            if (!RemoteAccessibilityService.isConnected()) { toast(t("Active l'accessibilité avant la capture", "Enable accessibility before taking a screenshot")); return; }
            moveTaskToBack(true);
            handler.postDelayed(() -> RemoteAccessibilityService.requestScreenshot(), 2000);
        });
        action(capture, t("Choisir une touche / combinaison", "Choose button / combination"), false, () -> chooseShortcut(1));
        action(capture, t("Accessibilité Android TV", "Android TV accessibility"), false, () -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception e) { toast(t("Ouvre Paramètres > Accessibilité", "Open Settings > Accessibility")); }
        });

        LinearLayout movie = panel(upper, wide, t("ENREGISTREMENT VIDÉO", "VIDEO RECORDING"));
        label(movie, RecordingService.isRecording() ? t("● EN COURS", "● RECORDING") : t("Prêt · MP4 H.264 / 1080p / 30 fps", "Ready · MP4 H.264 / 1080p / 30 fps"), 13,
            RecordingService.isRecording() ? 0xfff19a9b : MUTED, true, 6);
        label(movie, t("Raccourci : ", "Shortcut: ") + shortcutLabel(video), 12, MUTED, false, 9);
        action(movie, RecordingService.isRecording() ? t("Arrêter l'enregistrement", "Stop recording") : t("Démarrer l'enregistrement", "Start recording"), true, () -> {
            if (RecordingService.isRecording()) {
                startService(new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_STOP));
                handler.postDelayed(this::render, 550);
            } else requestRecording();
        });
        action(movie, t("Choisir une touche / combinaison", "Choose button / combination"), false, () -> chooseShortcut(2));
        label(movie, t("Autorisation Android requise à chaque session. Sans audio.", "Android permission required for each recording. No audio."), 11, MUTED, false, 0);

        LinearLayout lower = horizontal();
        if (!wide) lower.setOrientation(LinearLayout.VERTICAL);
        body.addView(lower);
        LinearLayout transfer = panel(lower, wide, t("TRANSFERT & CONTRÔLE PC", "PC TRANSFER & CONTROL"));
        label(transfer, web.getAddress(), 19, ACCENT, true, 5);
        label(transfer, t("CODE À 4 CHIFFRES : ", "4-DIGIT PIN: ") + web.getPin(), 15, 0xffffd38b, true, 6);
        label(transfer, web.isRunning() ? t("Serveur accessible sur le réseau local", "Local network server available") : t("Serveur indisponible, vérifie les ports", "Server unavailable; check network ports"),
            12, web.isRunning() ? MUTED : 0xffffbb87, false, 9);
        action(transfer, t("Modifier le port (auto si occupé)", "Change port (automatic fallback)"), false, this::portDialog);
        action(transfer, t("Générer un nouveau code", "Generate new PIN"), false, () -> {
            new AlertDialog.Builder(this).setTitle(t("Renouveler le code ?", "Generate a new PIN?"))
                .setMessage(t("Toutes les connexions PC existantes seront déconnectées.", "All existing PC sessions will be disconnected."))
                .setNegativeButton(t("Annuler", "Cancel"), null)
                .setPositiveButton(t("Renouveler", "Regenerate"), (d, x) -> { web.regeneratePin(); render(); }).show();
        });

        LinearLayout storage = panel(lower, wide, t("BIBLIOTHÈQUE", "LIBRARY"));
        label(storage, files.length + t(" fichier(s) · ", " file(s) · ") + readable(CaptureStore.totalBytes(this)), 18, FG, true, 6);
        label(storage, t("Images PNG et vidéos MP4 stockées sur la TV", "PNG screenshots and MP4 videos saved on the TV"), 12, MUTED, false, 9);
        action(storage, t("Parcourir / supprimer des captures", "Browse / delete captures"), true, this::manageFiles);
        action(storage, t("Actualiser", "Refresh"), false, this::render);
        label(storage, t("Téléchargement ZIP, suppression unitaire / totale aussi depuis le PC.", "Download ZIP files or delete individual / all items from the PC."), 11, MUTED, false, 0);

        LinearLayout updatesRow = horizontal();
        body.addView(updatesRow);
        LinearLayout updates = panel(updatesRow, false, t("APPLICATION & MISES À JOUR", "APP & UPDATES"));
        label(updates, "Version " + BuildConfig.VERSION_NAME + "   •   par JB Atomic Industries", 12, MUTED, false, 6);
        label(updates, t("Dépôt : ", "Repository: ") + UpdateManager.repository(this), 11, MUTED, false, 7);
        action(updates, t("Rechercher une mise à jour sur GitHub", "Check GitHub for updates"), true, () -> new UpdateManager(this).check());
        action(updates, t("Configurer le dépôt de publication", "Configure the release repository"), false, this::githubRepositoryDialog);
        boolean auto = getSharedPreferences("tv_capture", MODE_PRIVATE).getBoolean("auto_update_check", true);
        action(updates, t("Vérifier à l’ouverture (toutes les 24h) : ", "Check on launch (every 24h): ") + (auto ? t("activée", "enabled") : t("désactivée", "disabled")), false, () -> {
            getSharedPreferences("tv_capture", MODE_PRIVATE).edit().putBoolean("auto_update_check", !auto).apply();
            render();
        });

        // Corporate signature: visible in the TV UI, never burned into users' screenshots or recordings.
        label(body, "© 2026 JB ATOMIC INDUSTRIES · https://jbatomic.com/ · ATOMIC TV Capture v" + BuildConfig.VERSION_NAME +
            t(" · HDMI et contenus protégés : capture parfois impossible.", " · HDMI and DRM-protected content may not be capturable."), 11, MUTED, false, 0);
    }

    private LinearLayout panel(LinearLayout parent, boolean wide, String title) {
        LinearLayout c = vertical();
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable b = round(CARD, 11);
        b.setStroke(dp(1), LINE);
        c.setBackground(b);
        LinearLayout.LayoutParams lp = wide ? new LinearLayout.LayoutParams(0, -2, 1f)
            : new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, dp(wide ? 10 : 0), dp(9));
        parent.addView(c, lp);
        label(c, title, 12, ACCENT, true, 8);
        return c;
    }

    private void chooseShortcut(int target) {
        new AlertDialog.Builder(this).setTitle(target == 1 ? t("Raccourci capture", "Screenshot shortcut") : t("Raccourci vidéo", "Recording shortcut"))
            .setItems(new String[] { t("Appui long sur une touche", "Long-press one button"), t("Deux touches simultanées", "Press two buttons together"), t("Désactiver le raccourci", "Disable shortcut") },
                (dialog, option) -> {
                    String action = target == 1 ? ShortcutConfig.SCREENSHOT : ShortcutConfig.VIDEO;
                    if (option == 2) {
                        new ShortcutConfig(-1, -1).save(this, action);
                        render(); return;
                    }
                    learning = target;
                    learningCombo = option == 1;
                    learningFirst = -1;
                    toast(learningCombo ? t("Garde une touche enfoncée puis appuie sur une deuxième touche", "Hold one button and then press another") :
                        t("Appuie maintenant sur la touche à utiliser (Retour pour annuler)", "Press the desired button (Back to cancel)"));
                }).show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (learning == 0) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (learningCombo && key == learningFirst) {
                learningFirst = -1;
                toast(t("Maintiens la première touche jusqu'à la deuxième", "Hold the first button until you press the second"));
            }
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return true;
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
            learning = 0; learningFirst = -1; toast(t("Configuration annulée", "Configuration cancelled")); return true;
        }
        if (ShortcutConfig.isUnsafe(key)) { toast(t("Touche système réservée : choisis-en une autre", "System-reserved button: choose another")); return true; }
        if (learningCombo && learningFirst == -1) {
            learningFirst = key;
            toast(t("Maintiens ", "Hold ") + ShortcutConfig.key(key) + t(" et appuie sur l'autre touche", " and press the second button"));
            return true;
        }
        if (learningCombo && learningFirst == key) return true;
        ShortcutConfig selected = new ShortcutConfig(learningCombo ? learningFirst : key, learningCombo ? key : -1);
        String action = learning == 1 ? ShortcutConfig.SCREENSHOT : ShortcutConfig.VIDEO;
        String other = learning == 1 ? ShortcutConfig.VIDEO : ShortcutConfig.SCREENSHOT;
        if (selected.sameAs(ShortcutConfig.read(this, other))) { toast(t("Déjà attribué à l'autre fonction", "Already assigned to the other action")); return true; }
        selected.save(this, action);
        learning = 0; learningFirst = -1;
        toast(t("Raccourci enregistré : ", "Shortcut saved: ") + shortcutLabel(selected));
        render();
        return true;
    }

    private void portDialog() {
        final ShareServer web = ShareServer.get(this);
        new AlertDialog.Builder(this).setTitle(t("Port du serveur Web", "Web server port"))
            .setItems(new String[] { t("Automatique (à partir de 8766)", "Automatic (starting at 8766)"), "8766", "8770", "8888", t("Port personnalisé…", "Custom port…") },
                (d, which) -> {
                    if (which == 4) {
                        EditText input = new EditText(this);
                        input.setInputType(InputType.TYPE_CLASS_NUMBER);
                        input.setSingleLine(true);
                        input.setText(Integer.toString(web.getPort()));
                        new AlertDialog.Builder(this).setTitle(t("Port TCP (1024–65535)", "TCP port (1024–65535)"))
                            .setView(input).setNegativeButton(t("Annuler", "Cancel"), null)
                            .setPositiveButton(t("Appliquer", "Apply"), (dialog, n) -> {
                                try { applyPort(Integer.parseInt(input.getText().toString())); }
                                catch (NumberFormatException e) { toast(t("Port invalide", "Invalid port")); }
                            }).show();
                    } else {
                        applyPort(which == 0 ? 8766 : which == 1 ? 8766 : which == 2 ? 8770 : 8888);
                    }
                }).show();
    }

    private void applyPort(int port) {
        if (port < 1024 || port > 65535) { toast(t("Choisis un port entre 1024 et 65535", "Choose a port between 1024 and 65535")); return; }
        ShareServer web = ShareServer.get(this);
        web.changePort(port);
        toast(web.isRunning() ? t("Port actif : ", "Active port: ") + web.getPort() : t("Aucun port disponible", "No port available"));
        render();
    }

    private void githubRepositoryDialog() {
        EditText text = new EditText(this);
        text.setSingleLine(true);
        text.setText(UpdateManager.repository(this));
        text.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(t("Dépôt GitHub des versions officielles", "Official releases GitHub repository"))
            .setMessage(t("Format : propriétaire/dépôt. Les mises à jour utilisent les APK des Releases publiques et vérifient leur signature.", "Format: owner/repository. Updates use public GitHub Releases and verify the APK signature."))
            .setView(text).setNegativeButton(t("Annuler", "Cancel"), null)
            .setPositiveButton(t("Enregistrer", "Save"), (d, x) -> {
                String repo = text.getText().toString().trim();
                if (!UpdateManager.validRepository(repo)) { toast(t("Dépôt invalide (ex. ElecktroSkwiiZz/atomic-tv-capture)", "Invalid repository (e.g. ElecktroSkwiiZz/atomic-tv-capture)")); return; }
                getSharedPreferences("tv_capture", MODE_PRIVATE).edit().putString("github_repo", repo).apply();
                toast(t("Dépôt enregistré : ", "Repository saved: ") + repo);
                render();
            }).show();
    }

    private void manageFiles() {
        File[] files = CaptureStore.list(this);
        if (files.length == 0) { toast(t("Aucune capture enregistrée", "No captures saved")); return; }
        String[] choices = new String[files.length];
        for (int i = 0; i < files.length; i++) choices[i] = files[i].getName() + "   (" + readable(files[i].length()) + ")";
        new AlertDialog.Builder(this).setTitle(t("Captures et vidéos", "Screenshots and videos"))
            .setItems(choices, (d, index) -> {
                String name = files[index].getName();
                new AlertDialog.Builder(this).setTitle(t("Supprimer cette capture ?", "Delete this capture?"))
                    .setMessage(name).setNegativeButton(t("Conserver", "Keep"), null)
                    .setPositiveButton(t("Supprimer", "Delete"), (a, b) -> {
                        if (RecordingService.isRecording()) { toast(t("Arrête la vidéo avant de supprimer", "Stop recording before deleting")); return; }
                        toast(CaptureStore.delete(this, name) ? t("Capture supprimée", "Capture deleted") : t("Suppression impossible", "Could not delete capture"));
                        render();
                    }).show();
            }).setNeutralButton(t("Tout supprimer", "Delete all"), (d, n) -> {
                new AlertDialog.Builder(this).setTitle(t("Vider la bibliothèque ?", "Clear the library?"))
                    .setMessage(files.length + t(" fichiers seront définitivement effacés de la TV. Cette action est irréversible.", " files will be permanently deleted from the TV. This cannot be undone."))
                    .setNegativeButton(t("Annuler", "Cancel"), null)
                    .setPositiveButton(t("Tout effacer", "Delete everything"), (a, b) -> {
                        if (RecordingService.isRecording()) { toast(t("Arrête la vidéo avant de supprimer", "Stop recording before deleting")); return; }
                        toast(CaptureStore.deleteAll(this) + t(" fichiers supprimés", " files deleted")); render();
                    }).show();
            }).setNegativeButton(t("Fermer", "Close"), null).show();
    }

    private void requestRecording() {
        if (consentInProgress || RecordingService.isRecording()) return;
        try {
            consentInProgress = true;
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        } catch (Exception e) { consentInProgress = false; toast(t("Ce téléviseur refuse la capture vidéo", "This TV does not allow screen recording")); }
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQUEST_CAPTURE) return;
        consentInProgress = false;
        if (result == RESULT_OK && data != null) {
            Intent intent = new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_START);
            intent.putExtra(RecordingService.EXTRA_RESULT_CODE, result);
            intent.putExtra(RecordingService.EXTRA_RESULT_DATA, data);
            try {
                startForegroundService(intent);
                toast(t("Enregistrement lancé. Ouvre l'application souhaitée.", "Recording started. Open the app you want to record."));
                handler.postDelayed(() -> moveTaskToBack(true), 650);
            } catch (Exception e) { toast(t("Impossible de démarrer la vidéo", "Could not start video recording")); }
        } else toast(t("Autorisation de capture refusée", "Screen capture permission denied"));
        handler.postDelayed(this::render, 1100);
    }

    private String shortcutLabel(ShortcutConfig shortcut) {
        if (!english) return shortcut.label();
        if (!shortcut.enabled()) return "Disabled";
        if (!shortcut.isChord()) return ShortcutConfig.key(shortcut.first) + " · long press";
        return ShortcutConfig.key(shortcut.first) + " + " + ShortcutConfig.key(shortcut.second) + " · long press";
    }

    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private void gap(LinearLayout p, int dp) { View v = new View(this); p.addView(v, new LinearLayout.LayoutParams(1, dp(dp))); }
    private void label(LinearLayout p, String s, int sp, int color, boolean bold, int bottom) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(bottom); p.addView(t, lp);
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void action(LinearLayout p, String text, boolean primary, Runnable callback) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(FG);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(40)); b.setPadding(dp(12), dp(4), dp(12), dp(4));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] { android.R.attr.state_focused }, round(0xff168cb3, 9));
        bg.addState(new int[] { android.R.attr.state_pressed }, round(0xff15769b, 9));
        bg.addState(new int[] {}, round(primary ? 0xff13556f : 0xff26364e, 9));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
        lp.bottomMargin = dp(6);
        p.addView(b, lp); b.setOnClickListener(v -> callback.run());
    }
    private static String readable(long size) {
        if (size < 1024) return size + " o";
        if (size < 1048576) return String.format(java.util.Locale.FRANCE, "%.0f Ko", size / 1024f);
        if (size < 1073741824) return String.format(java.util.Locale.FRANCE, "%.1f Mo", size / 1048576f);
        return String.format(java.util.Locale.FRANCE, "%.2f Go", size / 1073741824f);
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
}
