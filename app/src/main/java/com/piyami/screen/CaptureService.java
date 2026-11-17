package com.piyami.screen;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String ACTION_STOP = "com.piyami.screen.STOP";
    private static boolean running;
    private final Handler handler = new Handler();
    private final ExecutorService saver = Executors.newSingleThreadExecutor();
    private MediaProjection projection;
    private WindowManager windowManager;
    private TextView overlay;
    private VirtualDisplay display;
    private ImageReader reader;
    private boolean capturing;
    private final Runnable captureTimeout = () -> {
        if (capturing) {
            finishCapture();
            showToast("Ekran görüntüsü zaman aşımına uğradı");
        }
    };

    public static boolean isRunning() { return running; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection != null) return START_NOT_STICKY;
        try {
            int result = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (result != Activity.RESULT_OK || data == null) throw new IllegalStateException("Ekran izni bulunamadı");
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) throw new IllegalStateException("Üstte gösterme izni gerekli");
            startCaptureForeground();
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(result, data);
            if (projection == null) throw new IllegalStateException("Ekran izni alınamadı");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, handler);
            addOverlay();
            running = true;
            showToast("Ekran düğmesi hazır");
        } catch (Exception error) {
            showToast("Ekran servisi başlatılamadı: " + error.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startCaptureForeground() {
        String channel = "capture";
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(channel, "Ekran görüntüsü", NotificationManager.IMPORTANCE_LOW));
            builder = new Notification.Builder(this, channel);
        } else builder = new Notification.Builder(this);

        int pendingFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        Intent stopIntent = new Intent(this, CaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopAction = PendingIntent.getService(this, 0, stopIntent, pendingFlags);
        PendingIntent openAction = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), pendingFlags);
        Notification notification = builder.setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Screen")
                .setContentText("Ekran düğmesi etkin")
                .setContentIntent(openAction)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopAction)
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(1, notification);
    }

    private void addOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new TextView(this);
        overlay.setText("●");
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(23);
        overlay.setGravity(Gravity.CENTER);
        overlay.setContentDescription("Ekran görüntüsü al");
        overlay.setAlpha(0.6f);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.rgb(36, 83, 128));
        overlay.setBackground(background);
        int size = (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = size / 2;
        params.y = size * 2;
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        overlay.setOnClickListener(view -> requestCapture());
        overlay.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean dragging;

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downX;
                        float deltaY = event.getRawY() - downY;
                        if (!dragging && deltaX * deltaX + deltaY * deltaY > slop * slop) dragging = true;
                        if (dragging) {
                            params.x = startX + Math.round(deltaX);
                            params.y = startY + Math.round(deltaY);
                            windowManager.updateViewLayout(view, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return false;
                }
            }
        });
        windowManager.addView(overlay, params);
    }

    private void requestCapture() {
        if (capturing || projection == null || overlay == null) return;
        capturing = true;
        overlay.setVisibility(View.INVISIBLE);
        handler.postDelayed(this::captureFrame, 250);
        handler.postDelayed(captureTimeout, 60000);
    }

    private void captureFrame() {
        if (!capturing || projection == null) return;
        try {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(source -> readFrame(source, width, height), handler);
            display = projection.createVirtualDisplay("Screen", width, height, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
            if (display == null) throw new IllegalStateException("Ekran oluşturulamadı");
        } catch (Exception error) {
            finishCapture();
            showToast("Ekran görüntüsü alınamadı: " + error.getMessage());
        }
    }

    private void readFrame(ImageReader source, int width, int height) {
        if (!capturing) return;
        Image image = null;
        Bitmap result = null;
        boolean failed = false;
        try {
            image = source.acquireLatestImage();
            if (image == null) return;
            if (overlay != null) overlay.setVisibility(View.VISIBLE);
            Image.Plane plane = image.getPlanes()[0];
            if (plane.getPixelStride() != 4) throw new IllegalStateException("Desteklenmeyen piksel biçimi");
            int paddedWidth = width + (plane.getRowStride() - plane.getPixelStride() * width) / plane.getPixelStride();
            Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
            try {
                padded.copyPixelsFromBuffer(plane.getBuffer());
                result = Bitmap.createBitmap(padded, 0, 0, width, height);
            } finally {
                if (result != padded) padded.recycle();
            }
        } catch (Exception error) {
            failed = true;
            showToast("Ekran görüntüsü alınamadı: " + error.getMessage());
        } finally {
            if (image != null) image.close();
            if (image != null || failed) finishCapture();
        }
        if (result != null) {
            Bitmap bitmap = result;
            saver.execute(() -> {
                try {
                    saveBitmap(getApplicationContext(), bitmap);
                    showToast("Ekran görüntüsü kaydedildi");
                } catch (Exception error) { showToast("Kaydedilemedi: " + error.getMessage()); }
                finally { bitmap.recycle(); }
            });
        }
    }

    private void finishCapture() {
        handler.removeCallbacks(captureTimeout);
        capturing = false;
        if (overlay != null) overlay.setVisibility(View.VISIBLE);
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
    }

    private void saveBitmap(Context context, Bitmap bitmap) throws Exception {
        String name = "Screen_" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Medya kaydı oluşturulamadı");
            try {
                try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
                    if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IllegalStateException("PNG yazılamadı");
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                if (context.getContentResolver().update(uri, values, null, null) != 1) throw new IllegalStateException("Medya kaydı tamamlanamadı");
            } catch (Exception error) { context.getContentResolver().delete(uri, null, null); throw error; }
        } else {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Klasör oluşturulamadı");
            File file = new File(directory, name);
            try {
                try (OutputStream output = new FileOutputStream(file)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IllegalStateException("PNG yazılamadı");
                }
            } catch (Exception error) { file.delete(); throw error; }
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
        }
    }

    private void showToast(String message) {
        Context context = getApplicationContext();
        handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        finishCapture();
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (IllegalArgumentException ignored) { }
            overlay = null;
        }
        if (projection != null) { projection.stop(); projection = null; }
        saver.shutdown();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
