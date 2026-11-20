/* ATOMIC TV Capture — JB ATOMIC INDUSTRIES — https://jbatomic.com/
 * © 2026 JB Atomic Industries. Product branding and attribution.
 * User screenshots and recordings remain unwatermarked.
 */
package com.jbatomic.tvcapture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;

public class RecordingService extends Service {
    public static final String ACTION_START = "com.jbatomic.tvcapture.START_RECORD";
    public static final String ACTION_STOP = "com.jbatomic.tvcapture.STOP_RECORD";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL = "tv_capture_recording";
    private static final int NOTIFICATION_ID = 101;

    private static volatile boolean recording = false;
    public static boolean isRecording() { return recording; }

    private MediaProjection projection;
    private MediaRecorder recorder;
    private VirtualDisplay virtualDisplay;
    private File file;
    private boolean stopping = false;
    private long startedAt = 0L;
    private MediaProjection.Callback projectionCallback;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopRecording();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || recording || projection != null) return START_NOT_STICKY;

        createChannel();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, makeNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, makeNotification());
            }
            Intent resultData;
            if (Build.VERSION.SDK_INT >= 33) resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
            else resultData = (Intent) intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (resultData == null) throw new IllegalStateException("Autorisation absente");
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mgr.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException("Capture refusée");
            projectionCallback = new MediaProjection.Callback() {
                @Override public void onStop() { new Handler(Looper.getMainLooper()).post(() -> { stopRecording(); stopSelf(); }); }
            };
            projection.registerCallback(projectionCallback, new Handler(Looper.getMainLooper()));

            DisplayMetrics metrics = new DisplayMetrics();
            ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            double scale = Math.min(1.0, Math.min(1920.0 / width, 1080.0 / height));
            width = Math.max(2, (((int) Math.round(width * scale)) / 2) * 2);
            height = Math.max(2, (((int) Math.round(height * scale)) / 2) * 2);
            int bitrate = width >= 1600 ? 8000000 : 4500000;
            file = CaptureStore.newCaptureFile(this, "mp4");

            recorder = new MediaRecorder();
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoEncodingBitRate(bitrate);
            recorder.setVideoFrameRate(30);
            recorder.setVideoSize(width, height);
            recorder.setOutputFile(file.getAbsolutePath());
            recorder.prepare();
            virtualDisplay = projection.createVirtualDisplay("ATOMIC-TV-Capture", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder.getSurface(), null, null);
            if (virtualDisplay == null) throw new IllegalStateException("Affichage virtuel indisponible");
            recorder.start();
            startedAt = System.currentTimeMillis();
            recording = true;
            Toast.makeText(this, "Enregistrement vidéo démarré (sans audio)", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'enregistrer : " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            stopRecording();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification makeNotification() {
        Intent stop = new Intent(this, RecordingService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setContentTitle("ATOMIC TV Capture — enregistrement")
            .setContentText("La vidéo est enregistrée. Appuyez sur Arrêter pour terminer.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "Arrêter", pi).build())
            .build();
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Enregistrement d'écran",
            NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    private void stopRecording() {
        if (stopping) return;
        stopping = true;
        boolean wasRecording = recording;
        recording = false;
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) {}
        virtualDisplay = null;
        boolean valid = wasRecording && (System.currentTimeMillis() - startedAt >= 1000);
        try { if (recorder != null && wasRecording) recorder.stop(); }
        catch (RuntimeException e) { valid = false; }
        try { if (recorder != null) recorder.reset(); } catch (Exception ignored) {}
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) {}
        recorder = null;
        if (projection != null) {
            try { projection.unregisterCallback(projectionCallback); } catch (Exception ignored) {}
            try { projection.stop(); } catch (Exception ignored) {}
            projection = null;
        }
        if (file != null && !valid) file.delete();
        if (wasRecording) Toast.makeText(this,
            valid ? "Vidéo enregistrée : " + file.getName() : "Vidéo trop courte ou interrompue", Toast.LENGTH_LONG).show();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Override public void onDestroy() { stopRecording(); super.onDestroy(); }
}
