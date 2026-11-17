package com.piyami.screen;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
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
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
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
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String ACTION_STOP = "com.piyami.screen.eski.STOP";
    private static boolean running;
    private final Handler handler = new Handler();
    private final ExecutorService saver = Executors.newSingleThreadExecutor();
    private MediaProjection projection;
    private WindowManager windowManager;
    private TextView overlay;
    private VirtualDisplay display;
    private ImageReader reader;
    private Image latestFrame;
    private int captureWidth;
    private int captureHeight;
    private int captureDensity;
    private long frameSequence;
    private long lastFrameTimestamp;
    private long sequenceAtHide;
    private long timestampAtHide;
    private long hideTimeNanos;
    private boolean capturing;
    private boolean captureReady;
    private final Runnable captureDelay = () -> {
        if (!capturing) return;
        captureReady = true;
        consumeLatestFrame();
    };
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
            if (Build.VERSION.SDK_INT >= 28) throw new IllegalStateException("Bu sürüm Android 5–8.1 içindir");
            int result = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (result != Activity.RESULT_OK || data == null) throw new IllegalStateException("Ekran izni bulunamadı");
            if (Build.VERSION.SDK_INT >= 23 && !LegacyApi23.canDrawOverlays(this)) throw new IllegalStateException("Üstte gösterme izni gerekli");
            startCaptureForeground();
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(result, data);
            if (projection == null) throw new IllegalStateException("Ekran izni alınamadı");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, handler);
            addOverlay();
            createDisplay();
            running = true;
            showToast("Ekran düğmesi hazır");
        } catch (Exception error) {
            showToast("Ekran servisi başlatılamadı: " + error.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startCaptureForeground() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = LegacyApi26.notificationBuilder(this);
        } else builder = new Notification.Builder(this);

        int pendingFlags = 0;
        Intent stopIntent = new Intent(this, CaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopAction = PendingIntent.getService(this, 0, stopIntent, pendingFlags);
        PendingIntent openAction = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), pendingFlags);
        Notification notification = builder.setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Screen Eski")
                .setContentText("Ekran düğmesi etkin")
                .setContentIntent(openAction)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopAction)
                .build();
        startForeground(1, notification);
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
        int type = Build.VERSION.SDK_INT >= 26 ? LegacyApi26.overlayType() : WindowManager.LayoutParams.TYPE_PHONE;
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

    private ImageReader newReader(int width, int height) {
        ImageReader result = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        result.setOnImageAvailableListener(this::readFrame, handler);
        return result;
    }

    private void createDisplay() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        captureWidth = metrics.widthPixels;
        captureHeight = metrics.heightPixels;
        captureDensity = metrics.densityDpi;
        reader = newReader(captureWidth, captureHeight);
        display = projection.createVirtualDisplay("Screen", captureWidth, captureHeight, captureDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
        if (display == null) throw new IllegalStateException("Ekran oluşturulamadı");
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        handler.postDelayed(() -> {
            if (display == null || windowManager == null) return;
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            resizeDisplay(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi);
        }, 100);
    }

    private void resizeDisplay(int width, int height, int density) {
        if (display == null || width <= 0 || height <= 0 || density <= 0) return;
        if (width == captureWidth && height == captureHeight && density == captureDensity) return;
        ImageReader replacement = null;
        try {
            replacement = newReader(width, height);
            display.resize(width, height, density);
            display.setSurface(replacement.getSurface());
            ImageReader previous = reader;
            reader = replacement;
            replacement = null;
            captureWidth = width;
            captureHeight = height;
            captureDensity = density;
            closeLatestFrame();
            sequenceAtHide = frameSequence;
            lastFrameTimestamp = 0;
            timestampAtHide = 0;
            if (previous != null) previous.close();
        } catch (Exception error) {
            if (replacement != null) replacement.close();
            showToast("Ekran boyutu değiştirilemedi: " + error.getMessage());
            stopSelf();
        }
    }

    private void requestCapture() {
        if (capturing || projection == null || overlay == null || reader == null) return;
        readFrame(reader);
        closeLatestFrame();
        overlay.setVisibility(View.INVISIBLE);
        hideTimeNanos = System.nanoTime();
        sequenceAtHide = frameSequence;
        timestampAtHide = lastFrameTimestamp;
        capturing = true;
        captureReady = false;
        handler.postDelayed(captureDelay, 300);
        handler.postDelayed(captureTimeout, 1500);
    }

    private void readFrame(ImageReader source) {
        if (source != reader) return;
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null) return;
            frameSequence++;
            long timestamp = image.getTimestamp();
            lastFrameTimestamp = timestamp;
            boolean newer = frameSequence > sequenceAtHide &&
                    (timestamp <= 0 || timestampAtHide <= 0 || timestamp > timestampAtHide);
            long now = System.nanoTime();
            boolean sameClock = timestamp > 0 && Math.abs(now - timestamp) < 10000000000L;
            if (capturing && newer && (!sameClock || timestamp >= hideTimeNanos)) {
                closeLatestFrame();
                latestFrame = image;
                image = null;
                if (captureReady) consumeLatestFrame();
            }
        } catch (Exception error) {
            if (capturing) {
                finishCapture();
                showToast("Ekran görüntüsü alınamadı: " + error.getMessage());
            }
        } finally { if (image != null) image.close(); }
    }

    private void consumeLatestFrame() {
        if (!capturing || !captureReady || latestFrame == null) return;
        Image image = latestFrame;
        latestFrame = null;
        finishCapture();
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            Image.Plane plane = image.getPlanes()[0];
            int stride = plane.getRowStride();
            if (plane.getPixelStride() != 4 || stride % 4 != 0 || stride < width * 4 ||
                    (long) stride * height > Integer.MAX_VALUE) throw new IllegalStateException("Desteklenmeyen piksel biçimi");
            byte[] pixels = new byte[stride * height];
            ByteBuffer buffer = plane.getBuffer();
            if (buffer.remaining() < (long) (height - 1) * stride + width * 4L) {
                throw new IllegalStateException("Eksik görüntü verisi");
            }
            buffer.get(pixels, 0, Math.min(buffer.remaining(), pixels.length));
            saver.execute(() -> saveFrame(pixels, width, height, stride));
        } catch (Exception | OutOfMemoryError error) {
            showToast("Ekran görüntüsü alınamadı: " + error.getMessage());
        } finally { image.close(); }
    }

    private void saveFrame(byte[] pixels, int width, int height, int stride) {
        Bitmap padded = null;
        Bitmap bitmap = null;
        try {
            padded = Bitmap.createBitmap(stride / 4, height, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
            bitmap = Bitmap.createBitmap(padded, 0, 0, width, height);
            saveBitmap(getApplicationContext(), bitmap);
            showToast("Ekran görüntüsü kaydedildi");
        } catch (Exception | OutOfMemoryError error) {
            showToast("Kaydedilemedi: " + error.getMessage());
        } finally {
            if (bitmap != null && bitmap != padded) bitmap.recycle();
            if (padded != null) padded.recycle();
        }
    }

    private void closeLatestFrame() {
        if (latestFrame != null) { latestFrame.close(); latestFrame = null; }
    }

    private void finishCapture() {
        handler.removeCallbacks(captureDelay);
        handler.removeCallbacks(captureTimeout);
        capturing = false;
        captureReady = false;
        closeLatestFrame();
        if (overlay != null) overlay.setVisibility(View.VISIBLE);
    }

    private void saveBitmap(Context context, Bitmap bitmap) throws Exception {
        String name = "Screen_" + System.currentTimeMillis() + ".png";
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

    private void showToast(String message) {
        Context context = getApplicationContext();
        handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        finishCapture();
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
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
