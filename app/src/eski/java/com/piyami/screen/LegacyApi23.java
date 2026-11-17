package com.piyami.screen;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

final class LegacyApi23 {
    static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    static Intent overlaySettings(Context context) {
        return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
    }
}
