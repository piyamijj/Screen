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
    private static final int OVERLAY_PERMISSION = 2;
    private static final int PROJECTION_PERMISSION = 3;
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
        if (Build.VERSION.SDK_INT >= 28) {
            showError("Bu sürüm Android 5–8.1 içindir; yeni cihazlarda Screen kullanın");
            return;
        }
        if (checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setMessage("Depolama iznini uygulama ayarlarından açın.")
                    .setPositiveButton("Ayarlar", (dialog, which) -> {
                        Intent settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        try { startActivity(settings); } catch (Exception error) { showError("Uygulama ayarları açılamadı"); }
                    })
                    .setNegativeButton("İptal", null)
                    .show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !LegacyApi23.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setMessage("Düğmenin ekranda görünmesi için izin gerekli. Devam?")
                    .setPositiveButton("Evet", (dialog, which) -> openOverlaySettings())
                    .setNegativeButton("Hayır", (dialog, which) -> showError("Düğmeyi göstermek için bu izin gerekli"))
                    .setOnCancelListener(dialog -> showError("Düğmeyi göstermek için bu izin gerekli"))
                    .show();
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
            Intent intent = LegacyApi23.overlaySettings(this);
            startActivityForResult(intent, OVERLAY_PERMISSION);
        } catch (Exception error) {
            pendingOverlayReturn = false;
            showError("Üstte gösterme ayarı açılamadı");
        }
    }

    private void finishOverlayReturn() {
        if (!pendingOverlayReturn) return;
        pendingOverlayReturn = false;
        if (Build.VERSION.SDK_INT < 23 || LegacyApi23.canDrawOverlays(this)) requestStart();
        else showError("Üstte gösterme izni gerekli");
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("pending_overlay_return", pendingOverlayReturn);
        super.onSaveInstanceState(state);
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
                if (Build.VERSION.SDK_INT >= 26) LegacyApi26.startForegroundService(this, service);
                else startService(service);
                moveTaskToBack(true);
                finish();
            } catch (Exception error) { showError("Ekran servisi başlatılamadı"); }
        }
    }

    private void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
