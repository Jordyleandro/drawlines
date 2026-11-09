package com.drawlines.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "capture_data";

    public static final String ACTION_DIRECTION =
            "com.drawlines.android.DIRECTION_DETECTED";

    public static final String EXTRA_ANGLE = "detected_angle";

    private static final int NOTIFICATION_ID = 92;
    private static final String CHANNEL_ID = "drawlines_capture";

    private static final String PREFS_NAME = "drawlines_origin";
    private static final String PREF_X = "origin_x";
    private static final String PREF_Y = "origin_y";
    private static final String PREF_SELECTED = "origin_selected";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private HandlerThread captureThread;
    private Handler captureHandler;

    private boolean captureStarted = false;

    private long lastDetectionTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        captureThread = new HandlerThread("DrawLinesCapture");
        captureThread.start();

        captureHandler = new Handler(
                captureThread.getLooper()
        );
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
                .setContentText("Seguimiento de dirección activo")
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

                            releaseDisplay();

                            stopSelf();
                        }

                    },
                    captureHandler
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
                    this::analyzeImage,
                    captureHandler
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
                            captureHandler
                    );

        } catch (Exception e) {

            releaseDisplay();
            stopSelf();
        }
    }

    private void analyzeImage(ImageReader reader) {

        Image image = null;

        try {

            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            long now = System.currentTimeMillis();

            // Limitar el análisis para no saturar el teléfono.
            if (now - lastDetectionTime < 100) {
                return;
            }

            lastDetectionTime = now;

            SharedPreferences preferences =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );

            boolean selected = preferences.getBoolean(
                    PREF_SELECTED,
                    false
            );

            if (!selected) {
                return;
            }

            float originX = preferences.getFloat(
                    PREF_X,
                    -1f
            );

            float originY = preferences.getFloat(
                    PREF_Y,
                    -1f
            );

            if (originX < 0 || originY < 0) {
                return;
            }

            Float angle = WhiteLineDetector.detectAngle(
                    image,
                    originX,
                    originY
            );

            if (angle != null) {

                Intent result = new Intent(ACTION_DIRECTION);

                result.setPackage(getPackageName());

                result.putExtra(EXTRA_ANGLE, angle);

                sendBroadcast(result);
            }

        } catch (Exception e) {

            android.util.Log.e(
                    "DrawLines",
                    "Error analizando imagen",
                    e
            );

        } finally {

            if (image != null) {
                image.close();
            }
        }
    }

    private void releaseDisplay() {

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

        releaseDisplay();

        if (mediaProjection != null) {

            MediaProjection projection = mediaProjection;
            mediaProjection = null;

            projection.stop();
        }

        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
