package com.piyami.screen;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Intent permissionData;
    private boolean bound;
    private boolean busy;
    private Button captureButton;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) { beginProjection(); }
        @Override public void onServiceDisconnected(ComponentName name) { fail("Ekran servisi durdu"); }
    };
    private final Runnable timeout = () -> fail("Ekran görüntüsü zaman aşımına uğradı");

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        captureButton = new Button(this);
        captureButton.setAllCaps(false);
        captureButton.setText("Ekran görüntüsü al");
        captureButton.setTextSize(22);
        captureButton.setOnClickListener(view -> requestCapture());
        layout.addView(captureButton, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = new TextView(this);
        footer.setText("© Piyami Polat");
        footer.setGravity(Gravity.CENTER);
        layout.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        setContentView(layout);
    }

    private void requestCapture() {
        if (busy) return;
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }
        busy = true;
        captureButton.setEnabled(false);
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        try { startActivityForResult(manager.createScreenCaptureIntent(), 2); }
        catch (Exception error) { fail("Ekran izni açılamadı: " + error.getMessage()); }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) requestCapture();
            else Toast.makeText(this, "Depolama izni gerekli", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 2) return;
        if (result != RESULT_OK || data == null) { fail("Ekran görüntüsü izni verilmedi"); return; }
        permissionData = data;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                Intent service = new Intent(this, CaptureService.class);
                startForegroundService(service);
                bound = bindService(service, connection, BIND_AUTO_CREATE);
                if (!bound) fail("Ekran servisine bağlanılamadı");
            } catch (Exception error) { fail("Ekran servisi başlatılamadı: " + error.getMessage()); }
        } else beginProjection();
    }

    private void beginProjection() {
        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(RESULT_OK, permissionData);
            permissionData = null;
            if (projection == null) { fail("Ekran izni alınamadı"); return; }
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { if (busy) fail("Ekran paylaşımı durduruldu"); }
            }, handler);
            moveTaskToBack(true);
            handler.postDelayed(this::capture, 700);
            handler.postDelayed(timeout, 10000);
        } catch (Exception error) { fail("Ekran başlatılamadı: " + error.getMessage()); }
    }

    private void capture() {
        if (!busy || projection == null) return;
        try {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(source -> {
                Image image = null;
                try {
                    image = source.acquireLatestImage();
                    if (image == null) return;
                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    int paddedWidth = width + (plane.getRowStride() - plane.getPixelStride() * width) / plane.getPixelStride();
                    Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
                    padded.copyPixelsFromBuffer(buffer);
                    Bitmap bitmap = Bitmap.createBitmap(padded, 0, 0, width, height);
                    try { save(bitmap); } finally { if (bitmap != padded) bitmap.recycle(); padded.recycle(); }
                    image.close();
                    image = null;
                    Toast.makeText(this, "Ekran görüntüsü kaydedildi: Pictures/Screenshots", Toast.LENGTH_LONG).show();
                    cleanup();
                } catch (Exception error) { fail("Kaydedilemedi: " + error.getMessage()); }
                finally { if (image != null) image.close(); }
            }, handler);
            display = projection.createVirtualDisplay("Screen", width, height, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
            if (display == null) fail("Ekran oluşturulamadı");
        } catch (Exception error) { fail("Ekran görüntüsü alınamadı: " + error.getMessage()); }
    }

    private void save(Bitmap bitmap) throws Exception {
        String name = "Screen_" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Medya kaydı oluşturulamadı");
            try {
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new Exception("PNG yazılamadı");
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                if (getContentResolver().update(uri, values, null, null) != 1) throw new Exception("Medya kaydı tamamlanamadı");
            } catch (Exception error) { getContentResolver().delete(uri, null, null); throw error; }
        } else {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new Exception("Klasör oluşturulamadı");
            File file = new File(directory, name);
            try {
                try (OutputStream output = new FileOutputStream(file)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new Exception("PNG yazılamadı");
                }
            } catch (Exception error) { file.delete(); throw error; }
            MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
        }
    }

    private void fail(String message) {
        if (busy) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        cleanup();
    }

    private void cleanup() {
        busy = false;
        captureButton.setEnabled(true);
        handler.removeCallbacksAndMessages(null);
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) { projection.stop(); projection = null; }
        permissionData = null;
        if (bound) { unbindService(connection); bound = false; }
        if (Build.VERSION.SDK_INT >= 29) stopService(new Intent(this, CaptureService.class));
    }

    @Override protected void onDestroy() { cleanup(); super.onDestroy(); }
}
