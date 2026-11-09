package com.drawlines.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "capture_data";

    private static final int NOTIFICATION_ID = 92;
    private static final String CHANNEL_ID = "drawlines_capture";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private boolean captureStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Captura DrawLines",
                            NotificationManager.IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("DrawLines")
                .setContentText("Captura de pantalla activa")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        if (intent == null || captureStarted) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(
                    NOTIFICATION_ID,
                    createNotification()
            );
        }

        int resultCode = intent.getIntExtra(
                EXTRA_RESULT_CODE,
                0
        );

        Intent data;

        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(
                    EXTRA_DATA,
                    Intent.class
            );
        } else {
            data = intent.getParcelableExtra(EXTRA_DATA);
        }

        if (data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        captureStarted = true;
        startCapture(resultCode, data);

        return START_NOT_STICKY;
    }

    private void startCapture(
            int resultCode,
            Intent data) {

        try {
            MediaProjectionManager manager =
                    (MediaProjectionManager)
                            getSystemService(
                                    Context.MEDIA_PROJECTION_SERVICE
                            );

            mediaProjection =
                    manager.getMediaProjection(resultCode, data);

            if (mediaProjection == null) {
                stopSelf();
                return;
            }

            mediaProjection.registerCallback(
                    new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            releaseCapture();
                            stopSelf();
                        }
                    },
                    null
            );

            WindowManager windowManager =
                    (WindowManager)
                            getSystemService(WINDOW_SERVICE);

            DisplayMetrics metrics = new DisplayMetrics();

            windowManager.getDefaultDisplay()
                    .getRealMetrics(metrics);

            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int density = metrics.densityDpi;

            imageReader = ImageReader.newInstance(
                    width,
                    height,
                    PixelFormat.RGBA_8888,
                    2
            );

            imageReader.setOnImageAvailableListener(
                    reader -> {
                        Image image = reader.acquireLatestImage();

                        if (image != null) {
                            try {
                                // Las imágenes se reciben correctamente.
                                // La detección de la línea blanca
                                // se incorporará en la siguiente etapa.
                            } finally {
                                image.close();
                            }
                        }
                    },
                    null
            );

            virtualDisplay =
                    mediaProjection.createVirtualDisplay(
                            "DrawLinesCapture",
                            width,
                            height,
                            density,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            imageReader.getSurface(),
                            null,
                            null
                    );

        } catch (Exception e) {
            releaseCapture();
            stopSelf();
        }
    }

    private void releaseCapture() {

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    public void onDestroy() {
        releaseCapture();

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
