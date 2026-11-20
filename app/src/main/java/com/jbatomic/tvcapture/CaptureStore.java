/* ATOMIC TV Capture — JB ATOMIC INDUSTRIES — https://jbatomic.com/
 * © 2026 JB Atomic Industries. Product branding and attribution.
 * User screenshots and recordings remain unwatermarked.
 */
package com.jbatomic.tvcapture;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CaptureStore {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private CaptureStore() {}

    public static File directory(Context ctx) {
        File f = new File(ctx.getFilesDir(), "captures");
        if (!f.exists() && !f.mkdirs()) throw new IllegalStateException("Impossible de créer le dossier captures");
        return f;
    }

    public static File newCaptureFile(Context ctx, String extension) {
        String name = "TV_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.FRANCE).format(new Date()) + "." + extension;
        return new File(directory(ctx), name);
    }

    public static File[] list(Context ctx) {
        File[] files = directory(ctx).listFiles(f -> f.isFile() && (f.getName().endsWith(".png") || f.getName().endsWith(".mp4")));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    public static File resolve(Context ctx, String name) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,120}") || name.equals("..")) return null;
        if (!(name.endsWith(".png") || name.endsWith(".mp4"))) return null;
        File file = new File(directory(ctx), name);
        try { if (!file.getCanonicalFile().getParentFile().equals(directory(ctx).getCanonicalFile())) return null; }
        catch (Exception ex) { return null; }
        return file;
    }

    public static boolean delete(Context ctx, String name) {
        File file = resolve(ctx, name);
        return file != null && file.isFile() && file.delete();
    }

    public static int deleteAll(Context ctx) {
        int count = 0;
        for (File file : list(ctx)) {
            if (file.delete()) count++;
        }
        return count;
    }

    public static long totalBytes(Context ctx) {
        long total = 0;
        for (File file : list(ctx)) total += file.length();
        return total;
    }

    public static void saveBitmap(Context ctx, Bitmap bitmap) {
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            File file = newCaptureFile(app, "png");
            boolean saved = false;
            try (FileOutputStream out = new FileOutputStream(file)) {
                saved = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            } catch (Exception ignored) { }
            finally { bitmap.recycle(); }
            if (!saved) file.delete();
            final boolean success = saved;
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(app, success ? "Capture enregistrée : " + file.getName() : "Échec de la capture", Toast.LENGTH_LONG).show());
        });
    }
}
