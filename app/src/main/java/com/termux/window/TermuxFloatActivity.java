package com.termux.window;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/**
 * Simple activity which immediately launches {@link TermuxFloatService} and exits.
 */
public class TermuxFloatActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();

        // Set log level for the app
        TermuxFloatApplication.setLogConfig(this, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(this, TermuxFloatPermissionActivity.class));
            finish();
            return;
        }

        Intent serviceIntent = new Intent(this, TermuxFloatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        finish();
    }
}
