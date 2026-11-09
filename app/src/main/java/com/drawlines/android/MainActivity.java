package com.drawlines.android;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY = 100;
    private static final int REQUEST_CAPTURE = 101;

    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectionManager =
                (MediaProjectionManager)
                        getSystemService(MEDIA_PROJECTION_SERVICE);

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
                "Guía de trayectoria y seguimiento del taco"
        );
        description.setTextSize(16);
        description.setGravity(Gravity.CENTER);

        Button start = new Button(this);
        start.setText("ACTIVAR DRAWLINES");

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

        requestScreenCapture();
    }

    private void requestScreenCapture() {

        Intent captureIntent =
                projectionManager.createScreenCaptureIntent();

        startActivityForResult(
                captureIntent,
                REQUEST_CAPTURE
        );
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
    }

    private void startCaptureService(
            int resultCode,
            Intent data) {

        Intent service = new Intent(
                this,
                ScreenCaptureService.class
        );

        service.putExtra(
                ScreenCaptureService.EXTRA_RESULT_CODE,
                resultCode
        );

        service.putExtra(
                ScreenCaptureService.EXTRA_DATA,
                data
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == REQUEST_OVERLAY) {

            if (Settings.canDrawOverlays(this)) {
                requestScreenCapture();
            } else {
                Toast.makeText(
                        this,
                        "Permiso flotante no concedido",
                        Toast.LENGTH_LONG
                ).show();
            }

            return;
        }

        if (requestCode == REQUEST_CAPTURE) {

            startFloatingService();

            if (resultCode == RESULT_OK && data != null) {

                startCaptureService(resultCode, data);

                Toast.makeText(
                        this,
                        "Captura autorizada",
                        Toast.LENGTH_SHORT
                ).show();

            } else {

                Toast.makeText(
                        this,
                        "Captura cancelada. El lápiz seguirá disponible.",
                        Toast.LENGTH_LONG
                ).show();
            }

            finish();
        }
    }
}
