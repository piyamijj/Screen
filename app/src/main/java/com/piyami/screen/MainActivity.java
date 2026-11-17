package com.piyami.screen;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int STORAGE_PERMISSION = 1;
    private static final int OVERLAY_PERMISSION = 2;
    private static final int PROJECTION_PERMISSION = 3;
    private static final int NOTIFICATION_PERMISSION = 4;
    private TextView status;
    private Button startButton;
    private boolean pendingOverlayReturn;
    private final Handler handler = new Handler();
    private final Runnable statusUpdate = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        TextView instructions = new TextView(this);
        instructions.setGravity(Gravity.CENTER);
        instructions.setTextSize(20);
        instructions.setText("Başlat'a bir kez bas, \"Şimdi başlat\"ı onayla, sonra düğmeyle istediğin kadar ekran görüntüsü al.");
        layout.addView(instructions, new LinearLayout.LayoutParams(-1, -2));

        TextView systemNotice = new TextView(this);
        systemNotice.setGravity(Gravity.CENTER);
        systemNotice.setTextSize(16);
        systemNotice.setText("Sistem onayı, servis her başlatıldığında bir kez görünür; Android gereği kaldırılamaz.");
        layout.addView(systemNotice, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(18);
        layout.addView(status, new LinearLayout.LayoutParams(-1, -2));

        startButton = new Button(this);
        startButton.setText("Başlat");
        startButton.setAllCaps(false);
        startButton.setOnClickListener(view -> requestStart());
        layout.addView(startButton, new LinearLayout.LayoutParams(-1, -2));

        Button stopButton = new Button(this);
        stopButton.setText("Durdur");
        stopButton.setAllCaps(false);
        stopButton.setOnClickListener(view -> {
            pendingOverlayReturn = false;
            stopService(new Intent(this, CaptureService.class));
            status.setText("Ekran düğmesi durduruldu");
            startButton.setEnabled(true);
        });
        layout.addView(stopButton, new LinearLayout.LayoutParams(-1, -2));

        TextView footer = new TextView(this);
        footer.setText("© Piyami Polat");
        footer.setGravity(Gravity.CENTER);
        layout.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        setContentView(layout);
        if (state != null) pendingOverlayReturn = state.getBoolean("pending_overlay_return", false);
    }

    @Override protected void onResume() {
        super.onResume();
        statusUpdate.run();
        if (pendingOverlayReturn) finishOverlayReturn();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(statusUpdate);
        super.onPause();
    }

    private void refreshStatus() {
        boolean running = CaptureService.isRunning();
        status.setText(running ? "Ekran düğmesi etkin" : "Ekran düğmesi durduruldu");
        startButton.setEnabled(!running);
    }

    private void requestStart() {
        if (CaptureService.isRunning()) { refreshStatus(); return; }
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setMessage("Düğmenin ekranda görünmesi için izin gerekli. Devam?")
                    .setPositiveButton("Evet", (dialog, which) -> openOverlaySettings())
                    .setNegativeButton("Hayır", (dialog, which) -> showError("Düğmeyi göstermek için bu izin gerekli"))
                    .setOnCancelListener(dialog -> showError("Düğmeyi göstermek için bu izin gerekli"))
                    .show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                !getPreferences(MODE_PRIVATE).getBoolean("notification_requested", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_requested", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
            return;
        }
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        try { startActivityForResult(manager.createScreenCaptureIntent(), PROJECTION_PERMISSION); }
        catch (Exception error) { showError("Ekran izni açılamadı"); }
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) { requestStart(); return; }
        try {
            pendingOverlayReturn = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION);
        } catch (Exception error) {
            pendingOverlayReturn = false;
            showError("Üstte gösterme ayarı açılamadı");
        }
    }

    private void finishOverlayReturn() {
        if (!pendingOverlayReturn) return;
        pendingOverlayReturn = false;
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) requestStart();
        else showError("Üstte gösterme izni gerekli");
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("pending_overlay_return", pendingOverlayReturn);
        super.onSaveInstanceState(state);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == STORAGE_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) requestStart();
            else showError("Depolama izni gerekli");
        } else if (request == NOTIFICATION_PERMISSION) {
            requestStart();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == OVERLAY_PERMISSION) {
            finishOverlayReturn();
        } else if (request == PROJECTION_PERMISSION) {
            if (result != RESULT_OK || data == null) { showError("Ekran görüntüsü izni verilmedi"); return; }
            Intent service = new Intent(this, CaptureService.class);
            service.putExtra(CaptureService.EXTRA_RESULT_CODE, result);
            service.putExtra(CaptureService.EXTRA_RESULT_DATA, data);
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                else startService(service);
                moveTaskToBack(true);
                finish();
            } catch (Exception error) { showError("Ekran servisi başlatılamadı"); }
        }
    }

    private void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
