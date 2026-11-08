package com.drawlines.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("DRAWLINES");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);

        TextView description = new TextView(this);
        description.setText(
                "Selecciona un punto y dibuja tu línea"
        );
        description.setTextSize(16);
        description.setGravity(Gravity.CENTER);

        Button start = new Button(this);
        start.setText("ACTIVAR LÁPIZ FLOTANTE");
        start.setOnClickListener(v -> activateOverlay());

        layout.addView(title);
        layout.addView(description);
        layout.addView(start);

        setContentView(layout);
    }

    private void activateOverlay() {

        if (!Settings.canDrawOverlays(this)) {

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );

            startActivityForResult(intent, REQUEST_OVERLAY);
            return;
        }

        startFloatingService();
    }

    private void startFloatingService() {

        Intent service = new Intent(
                this,
                FloatingLineService.class
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }

        finish();
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY
                && Settings.canDrawOverlays(this)) {
            startFloatingService();
        }
    }
}
