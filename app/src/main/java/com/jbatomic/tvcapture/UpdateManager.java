package com.jbatomic.tvcapture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** Download an official GitHub Release APK and hand off to Android's installer (with user approval). */
public final class UpdateManager {
    public static final String DEFAULT_REPOSITORY = "ElecktroSkwiiZz/atomic-tv-capture";
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean busy;

    public UpdateManager(Activity activity) { this.activity = activity; }
    public static String repository(Activity activity) {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("tv_capture", Activity.MODE_PRIVATE);
        String configured = prefs.getString("github_repo", DEFAULT_REPOSITORY);
        // Migrate the original provisional repository to the public ATOMIC TV Capture repository.
        if ("ElecktroSkwiiZz/TV-Capture-AndroidTV".equals(configured)) {
            prefs.edit().remove("github_repo").apply();
            return DEFAULT_REPOSITORY;
        }
        return configured;
    }
    public static boolean validRepository(String name) {
        return name != null && name.matches("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}") && !name.contains("..");
    }

    public void check() { checkInternal(false); }

    public void checkAutomaticallyIfDue() {
        android.content.SharedPreferences pref = activity.getSharedPreferences("tv_capture", Activity.MODE_PRIVATE);
        if (!pref.getBoolean("auto_update_check", true)) return;
        long now = System.currentTimeMillis();
        if (now - pref.getLong("last_update_check", 0L) < 24L * 3600_000) return;
        pref.edit().putLong("last_update_check", now).apply();
        checkInternal(true);
    }

    private void checkInternal(boolean silent) {
        if (busy) { if (!silent) toast("Une opération de mise à jour est déjà en cours"); return; }
        String repo = repository(activity);
        if (!validRepository(repo)) { toast("Indique d'abord un dépôt GitHub valide"); return; }
        busy = true;
        if (!silent) toast("Recherche d'une version sur GitHub…");
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "ATOMIC-TV-Capture/" + BuildConfig.VERSION_NAME);
                int status = connection.getResponseCode();
                if (status == 404) throw new Exception("Aucune version publique n'a encore été publiée sur " + repo);
                if (status != 200) throw new Exception("GitHub répond HTTP " + status);
                String json;
                try (InputStream in = connection.getInputStream()) { json = new String(readLimit(in, 512_000), StandardCharsets.UTF_8); }
                JSONObject release = new JSONObject(json);
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) throw new Exception("Aucune version stable publiée");
                String tag = release.optString("tag_name", "");
                JSONArray assets = release.optJSONArray("assets");
                String assetUrl = null;
                String digest = "";
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "").toLowerCase(Locale.ROOT);
                    if (name.endsWith(".apk")) {
                        assetUrl = asset.optString("browser_download_url");
                        digest = asset.optString("digest", "");
                        break;
                    }
                }
                if (assetUrl == null) throw new Exception("Aucune APK disponible dans cette publication GitHub");
                if (!assetUrl.startsWith("https://github.com/" + repo + "/releases/download/")) {
                    throw new Exception("Adresse APK inattendue : publication refusée");
                }
                if (compareVersion(tag, BuildConfig.VERSION_NAME) <= 0) {
                    if (!silent) onMain(() -> new AlertDialog.Builder(activity).setTitle("Application à jour")
                        .setMessage("Version installée : " + BuildConfig.VERSION_NAME + "\nDernière version GitHub : " + tag)
                        .setPositiveButton("Fermer", null).show());
                    return;
                }
                final String download = assetUrl;
                final String sha = digest;
                onMain(() -> new AlertDialog.Builder(activity).setTitle("Mise à jour disponible")
                    .setMessage("Version actuelle : " + BuildConfig.VERSION_NAME + "\nNouvelle version : " + tag +
                        "\n\nL'APK sera vérifiée avant l'installation. Android vous demandera de confirmer la mise à jour.")
                    .setNegativeButton("Plus tard", null)
                    .setPositiveButton("Télécharger", (dialog, which) -> download(download, sha)).show());
            } catch (Exception error) { if (!silent) onMain(() -> new AlertDialog.Builder(activity)
                .setTitle("Vérification impossible").setMessage(error.getMessage())
                .setPositiveButton("Fermer", null).show()); }
            finally { busy = false; }
        }, "tv-capture-update-check").start();
    }

    private void download(String url, String digest) {
        if (busy) { toast("Téléchargement déjà en cours"); return; }
        busy = true; toast("Téléchargement de l'APK depuis GitHub…");
        new Thread(() -> {
            try {
                File tmp = new File(activity.getCacheDir(), "ATOMIC-TV-Capture-update.part");
                File finalApk = new File(activity.getCacheDir(), "ATOMIC-TV-Capture-update.apk");
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "ATOMIC-TV-Capture/" + BuildConfig.VERSION_NAME);
                if (conn.getResponseCode() != 200) throw new Exception("Téléchargement HTTP " + conn.getResponseCode());
                long size = conn.getContentLengthLong();
                if (size > 200_000_000) throw new Exception("APK trop volumineuse");
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                long received = 0;
                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) != -1) {
                        received += n;
                        if (received > 200_000_000) throw new Exception("APK trop volumineuse");
                        sha.update(buf, 0, n); out.write(buf, 0, n);
                    }
                }
                if (received < 1000) throw new Exception("APK vide ou incomplète");
                if (digest.startsWith("sha256:")) {
                    String actual = hex(sha.digest());
                    if (!actual.equalsIgnoreCase(digest.substring(7))) throw new Exception("Empreinte SHA-256 incorrecte");
                }
                if (!verifySameSigningCertificate(tmp)) throw new Exception(
                    "Signature différente de l'application installée. Une mise à jour directe est interdite par Android. " +
                    "Conservez la même clé de signature pour toutes les versions.");
                PackageInfo info = activity.getPackageManager().getPackageArchiveInfo(tmp.getAbsolutePath(), 0);
                if (info == null || info.getLongVersionCode() <= BuildConfig.VERSION_CODE) throw new Exception("L'APK n'est pas une version plus récente");
                if (finalApk.exists() && !finalApk.delete()) throw new Exception("Ancien fichier APK verrouillé");
                if (!tmp.renameTo(finalApk)) throw new Exception("Impossible de préparer l'APK");
                onMain(() -> install(finalApk));
            } catch (Exception error) { onMain(() -> new AlertDialog.Builder(activity)
                .setTitle("Mise à jour non installée").setMessage(error.getMessage())
                .setPositiveButton("Fermer", null).show()); }
            finally { busy = false; }
        }, "tv-capture-apk-download").start();
    }

    private boolean verifySameSigningCertificate(File candidate) throws Exception {
        PackageManager pm = activity.getPackageManager();
        PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo downloaded = pm.getPackageArchiveInfo(candidate.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (downloaded == null || !activity.getPackageName().equals(downloaded.packageName)) return false;
        if (installed.signingInfo == null || downloaded.signingInfo == null) return false;
        Signature[] left = installed.signingInfo.getApkContentsSigners();
        Signature[] right = downloaded.signingInfo.getApkContentsSigners();
        if (left.length != right.length || left.length == 0) return false;
        for (Signature signer : left) {
            boolean found = false;
            for (Signature other : right) if (Arrays.equals(signer.toByteArray(), other.toByteArray())) found = true;
            if (!found) return false;
        }
        return true;
    }

    public void resumePendingInstallation() {
        android.content.SharedPreferences pref = activity.getSharedPreferences("tv_capture", Activity.MODE_PRIVATE);
        if (!pref.getBoolean("pending_update_install", false) || !activity.getPackageManager().canRequestPackageInstalls()) return;
        pref.edit().putBoolean("pending_update_install", false).apply();
        File file = new File(activity.getCacheDir(), "ATOMIC-TV-Capture-update.apk");
        if (file.isFile()) new AlertDialog.Builder(activity)
            .setTitle("Mise à jour prête").setMessage("Voulez-vous installer l'APK signée déjà téléchargée ?")
            .setNegativeButton("Plus tard", null)
            .setPositiveButton("Installer", (dialog, n) -> install(file)).show();
    }

    private void install(File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            activity.getSharedPreferences("tv_capture", Activity.MODE_PRIVATE).edit()
                .putBoolean("pending_update_install", true).apply();
            new AlertDialog.Builder(activity).setTitle("Autorisation Android requise")
                .setMessage("Autorisez ATOMIC TV Capture à installer des APK, puis relancez la vérification de mise à jour.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Paramètres", (d, x) -> {
                    Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                    try { activity.startActivity(i); } catch (Exception e) { toast("Ouvrez les paramètres de sécurité Android"); }
                }).show();
            return;
        }
        Uri uri = Uri.parse("content://" + activity.getPackageName() + ".updates/apk");
        Intent intent = new Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("ATOMIC TV Capture APK", uri));
        try { activity.startActivity(intent); }
        catch (Exception e) { toast("Impossible d'ouvrir l'installeur Android : " + e.getClass().getSimpleName()); }
    }

    private static byte[] readLimit(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = input.read(buf)) != -1) {
            if (out.size() + n > limit) throw new Exception("Réponse GitHub trop volumineuse");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
    private static String hex(byte[] data) {
        StringBuilder str = new StringBuilder(data.length * 2);
        for (byte b : data) str.append(String.format(Locale.US, "%02x", b & 255));
        return str.toString();
    }
    private static int compareVersion(String tag, String current) {
        String[] a = tag.replaceFirst("^[vV]", "").split("\\.");
        String[] b = current.replaceFirst("^[vV]", "").split("\\.");
        for (int i = 0; i < 3; i++) {
            int x = part(a, i), y = part(b, i);
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }
    private static int part(String[] arr, int i) {
        if (i >= arr.length) return 0;
        try { return Integer.parseInt(arr[i].replaceAll("[^0-9].*$", "")); }
        catch (Exception ignored) { return 0; }
    }
    private void onMain(Runnable callback) { main.post(callback); }
    private void toast(String message) { onMain(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show()); }
}
