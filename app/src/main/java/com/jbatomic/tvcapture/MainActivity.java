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
    private int learning = 0; // 1 screenshot, 2 video
    private boolean learningCombo = false;
    private int learningFirst = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        LinearLayout brand = vertical();
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        label(brand, "▣   ATOMIC TV CAPTURE", 23, FG, true, 0);
        label(brand, "JB ATOMIC INDUSTRIES   /   VERSION " + BuildConfig.VERSION_NAME, 11, MUTED, false, 1);
        TextView state = new TextView(this);
        state.setText(RemoteAccessibilityService.isConnected() ? "●  SERVICE ACTIF" : "●  SERVICE INACTIF");
        state.setTextSize(12);
        state.setTextColor(RemoteAccessibilityService.isConnected() ? 0xff73dbad : 0xffffbd76);
        state.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        top.addView(state, new LinearLayout.LayoutParams(-2, dp(44)));
        gap(body, 10);

        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
            && getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density >= 760;
        LinearLayout upper = horizontal();
        if (!wide) upper.setOrientation(LinearLayout.VERTICAL);
        body.addView(upper);

        LinearLayout capture = panel(upper, wide, "CAPTURE D'ÉCRAN");
        label(capture, shot.label(), 14, FG, true, 6);
        label(capture, "Appui long 1,1 s  •  Combinaison 0,7 s", 11, MUTED, false, 7);
        action(capture, "Capturer l'écran (après 2 secondes)", true, () -> {
            if (!RemoteAccessibilityService.isConnected()) { toast("Active l'accessibilité avant la capture"); return; }
            moveTaskToBack(true);
            handler.postDelayed(() -> RemoteAccessibilityService.requestScreenshot(), 2000);
        });
        action(capture, "Choisir une touche / combinaison", false, () -> chooseShortcut(1));
        action(capture, "Accessibilité Android TV", false, () -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception e) { toast("Ouvre Paramètres > Accessibilité"); }
        });

        LinearLayout movie = panel(upper, wide, "ENREGISTREMENT VIDÉO");
        label(movie, RecordingService.isRecording() ? "● EN COURS" : "Prêt · MP4 H.264 / 1080p / 30 fps", 13,
            RecordingService.isRecording() ? 0xfff19a9b : MUTED, true, 6);
        label(movie, "Raccourci : " + video.label(), 12, MUTED, false, 9);
        action(movie, RecordingService.isRecording() ? "Arrêter l'enregistrement" : "Démarrer l'enregistrement", true, () -> {
            if (RecordingService.isRecording()) {
                startService(new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_STOP));
                handler.postDelayed(this::render, 550);
            } else requestRecording();
        });
        action(movie, "Choisir une touche / combinaison", false, () -> chooseShortcut(2));
        label(movie, "Autorisation Android requise à chaque session. Sans audio.", 11, MUTED, false, 0);

        LinearLayout lower = horizontal();
        if (!wide) lower.setOrientation(LinearLayout.VERTICAL);
        body.addView(lower);
        LinearLayout transfer = panel(lower, wide, "TRANSFERT & CONTRÔLE PC");
        label(transfer, web.getAddress(), 19, ACCENT, true, 5);
        label(transfer, "CODE À 4 CHIFFRES : " + web.getPin(), 15, 0xffffd38b, true, 6);
        label(transfer, web.isRunning() ? "Serveur accessible sur le réseau local" : "Serveur indisponible, vérifie les ports",
            12, web.isRunning() ? MUTED : 0xffffbb87, false, 9);
        action(transfer, "Modifier le port (auto si occupé)", false, this::portDialog);
        action(transfer, "Générer un nouveau code", false, () -> {
            new AlertDialog.Builder(this).setTitle("Renouveler le code ?")
                .setMessage("Toutes les connexions PC existantes seront déconnectées.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Renouveler", (d, x) -> { web.regeneratePin(); render(); }).show();
        });

        LinearLayout storage = panel(lower, wide, "BIBLIOTHÈQUE");
        label(storage, files.length + " fichier(s) · " + readable(CaptureStore.totalBytes(this)), 18, FG, true, 6);
        label(storage, "Images PNG et vidéos MP4 stockées sur la TV", 12, MUTED, false, 9);
        action(storage, "Parcourir / supprimer des captures", true, this::manageFiles);
        action(storage, "Actualiser", false, this::render);
        label(storage, "Téléchargement ZIP, suppression unitaire / totale aussi depuis le PC.", 11, MUTED, false, 0);

        LinearLayout updatesRow = horizontal();
        body.addView(updatesRow);
        LinearLayout updates = panel(updatesRow, false, "APPLICATION & MISES À JOUR");
        label(updates, "Version " + BuildConfig.VERSION_NAME + "   •   par JB Atomic Industries", 12, MUTED, false, 6);
        label(updates, "Dépôt : " + UpdateManager.repository(this), 11, MUTED, false, 7);
        action(updates, "Rechercher une mise à jour sur GitHub", true, () -> new UpdateManager(this).check());
        action(updates, "Configurer le dépôt de publication", false, this::githubRepositoryDialog);
        boolean auto = getSharedPreferences("tv_capture", MODE_PRIVATE).getBoolean("auto_update_check", true);
        action(updates, "Vérifier à l’ouverture (toutes les 24h) : " + (auto ? "activée" : "désactivée"), false, () -> {
            getSharedPreferences("tv_capture", MODE_PRIVATE).edit().putBoolean("auto_update_check", !auto).apply();
            render();
        });

        label(body, "© JB ATOMIC INDUSTRIES   ·   https://jbatomic.com/   ·   La capture HDMI et certaines applications protégées peuvent être bloquées.",
            11, MUTED, false, 0);
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
        new AlertDialog.Builder(this).setTitle(target == 1 ? "Raccourci capture" : "Raccourci vidéo")
            .setItems(new String[] { "Appui long sur une touche", "Deux touches simultanées", "Désactiver le raccourci" },
                (dialog, option) -> {
                    String action = target == 1 ? ShortcutConfig.SCREENSHOT : ShortcutConfig.VIDEO;
                    if (option == 2) {
                        new ShortcutConfig(-1, -1).save(this, action);
                        render(); return;
                    }
                    learning = target;
                    learningCombo = option == 1;
                    learningFirst = -1;
                    toast(learningCombo ? "Garde une touche enfoncée puis appuie sur une deuxième touche" :
                        "Appuie maintenant sur la touche à utiliser (Retour pour annuler)");
                }).show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (learning == 0) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (learningCombo && key == learningFirst) {
                learningFirst = -1;
                toast("Maintiens la première touche jusqu'à la deuxième");
            }
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return true;
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
            learning = 0; learningFirst = -1; toast("Configuration annulée"); return true;
        }
        if (ShortcutConfig.isUnsafe(key)) { toast("Touche système réservée : choisis-en une autre"); return true; }
        if (learningCombo && learningFirst == -1) {
            learningFirst = key;
            toast("Maintiens " + ShortcutConfig.key(key) + " et appuie sur l'autre touche");
            return true;
        }
        if (learningCombo && learningFirst == key) return true;
        ShortcutConfig selected = new ShortcutConfig(learningCombo ? learningFirst : key, learningCombo ? key : -1);
        String action = learning == 1 ? ShortcutConfig.SCREENSHOT : ShortcutConfig.VIDEO;
        String other = learning == 1 ? ShortcutConfig.VIDEO : ShortcutConfig.SCREENSHOT;
        if (selected.sameAs(ShortcutConfig.read(this, other))) { toast("Déjà attribué à l'autre fonction"); return true; }
        selected.save(this, action);
        learning = 0; learningFirst = -1;
        toast("Raccourci enregistré : " + selected.label());
        render();
        return true;
    }

    private void portDialog() {
        final ShareServer web = ShareServer.get(this);
        new AlertDialog.Builder(this).setTitle("Port du serveur Web")
            .setItems(new String[] { "Automatique (à partir de 8766)", "8766", "8770", "8888", "Port personnalisé…" },
                (d, which) -> {
                    if (which == 4) {
                        EditText input = new EditText(this);
                        input.setInputType(InputType.TYPE_CLASS_NUMBER);
                        input.setSingleLine(true);
                        input.setText(Integer.toString(web.getPort()));
                        new AlertDialog.Builder(this).setTitle("Port TCP (1024–65535)")
                            .setView(input).setNegativeButton("Annuler", null)
                            .setPositiveButton("Appliquer", (dialog, n) -> {
                                try { applyPort(Integer.parseInt(input.getText().toString())); }
                                catch (NumberFormatException e) { toast("Port invalide"); }
                            }).show();
                    } else {
                        applyPort(which == 0 ? 8766 : which == 1 ? 8766 : which == 2 ? 8770 : 8888);
                    }
                }).show();
    }

    private void applyPort(int port) {
        if (port < 1024 || port > 65535) { toast("Choisis un port entre 1024 et 65535"); return; }
        ShareServer web = ShareServer.get(this);
        web.changePort(port);
        toast(web.isRunning() ? "Port actif : " + web.getPort() : "Aucun port disponible");
        render();
    }

    private void githubRepositoryDialog() {
        EditText text = new EditText(this);
        text.setSingleLine(true);
        text.setText(UpdateManager.repository(this));
        text.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Dépôt GitHub des versions officielles")
            .setMessage("Format : propriétaire/dépôt. Les mises à jour utilisent les APK des Releases publiques et vérifient leur signature.")
            .setView(text).setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", (d, x) -> {
                String repo = text.getText().toString().trim();
                if (!UpdateManager.validRepository(repo)) { toast("Dépôt invalide (ex. ElecktroSkwiiZz/atomic-tv-capture)"); return; }
                getSharedPreferences("tv_capture", MODE_PRIVATE).edit().putString("github_repo", repo).apply();
                toast("Dépôt enregistré : " + repo);
                render();
            }).show();
    }

    private void manageFiles() {
        File[] files = CaptureStore.list(this);
        if (files.length == 0) { toast("Aucune capture enregistrée"); return; }
        String[] choices = new String[files.length];
        for (int i = 0; i < files.length; i++) choices[i] = files[i].getName() + "   (" + readable(files[i].length()) + ")";
        new AlertDialog.Builder(this).setTitle("Captures et vidéos")
            .setItems(choices, (d, index) -> {
                String name = files[index].getName();
                new AlertDialog.Builder(this).setTitle("Supprimer cette capture ?")
                    .setMessage(name).setNegativeButton("Conserver", null)
                    .setPositiveButton("Supprimer", (a, b) -> {
                        if (RecordingService.isRecording()) { toast("Arrête la vidéo avant de supprimer"); return; }
                        toast(CaptureStore.delete(this, name) ? "Capture supprimée" : "Suppression impossible");
                        render();
                    }).show();
            }).setNeutralButton("Tout supprimer", (d, n) -> {
                new AlertDialog.Builder(this).setTitle("Vider la bibliothèque ?")
                    .setMessage(files.length + " fichiers seront définitivement effacés de la TV. Cette action est irréversible.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Tout effacer", (a, b) -> {
                        if (RecordingService.isRecording()) { toast("Arrête la vidéo avant de supprimer"); return; }
                        toast(CaptureStore.deleteAll(this) + " fichiers supprimés"); render();
                    }).show();
            }).setNegativeButton("Fermer", null).show();
    }

    private void requestRecording() {
        if (consentInProgress || RecordingService.isRecording()) return;
        try {
            consentInProgress = true;
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        } catch (Exception e) { consentInProgress = false; toast("Ce téléviseur refuse la capture vidéo"); }
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
                toast("Enregistrement lancé. Ouvre l'application souhaitée.");
                handler.postDelayed(() -> moveTaskToBack(true), 650);
            } catch (Exception e) { toast("Impossible de démarrer la vidéo"); }
        } else toast("Autorisation de capture refusée");
        handler.postDelayed(this::render, 1100);
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
