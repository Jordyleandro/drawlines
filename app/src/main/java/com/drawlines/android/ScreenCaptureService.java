package com.drawlines.android;

import android.app.Activity;
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
import android.util.Log;
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
    private static final String PREF_TRACKING = "tracking_enabled";
    private static final String PREF_VIEW_WIDTH = "view_width";
    private static final String PREF_VIEW_HEIGHT = "view_height";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private HandlerThread captureThread;
    private Handler captureHandler;

    private boolean captureStarted = false;
    private boolean projectionStopped = false;

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
                .setContentText("Captura de pantalla preparada")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (captureStarted) {
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

        if (resultCode != Activity.RESULT_OK || data == null) {
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

                            projectionStopped = true;
                            mediaProjection = null;

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

            Log.e(
                    "DrawLines",
                    "Error iniciando captura",
                    e
            );

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

            SharedPreferences preferences =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );

            boolean trackingEnabled = preferences.getBoolean(
                    PREF_TRACKING,
                    false
            );

            if (!trackingEnabled) {
                return;
            }

            long now = System.currentTimeMillis();

            if (now - lastDetectionTime < 100) {
                return;
            }

            lastDetectionTime = now;

            boolean selected = preferences.getBoolean(
                    PREF_SELECTED,
                    false
            );

            if (!selected) {
                return;
            }

            float savedX = preferences.getFloat(
                    PREF_X,
                    -1f
            );

            float savedY = preferences.getFloat(
                    PREF_Y,
                    -1f
            );

            int savedWidth = preferences.getInt(
                    PREF_VIEW_WIDTH,
                    0
            );

            int savedHeight = preferences.getInt(
                    PREF_VIEW_HEIGHT,
                    0
            );

            if (savedX < 0
                    || savedY < 0
                    || savedWidth <= 0
                    || savedHeight <= 0) {
                return;
            }

            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();

            if (imageWidth <= 0 || imageHeight <= 0) {
                return;
            }

            // Convertir las coordenadas de la ventana
            // flotante a las dimensiones de la captura.
            float scaleX = (float) imageWidth / savedWidth;
            float scaleY = (float) imageHeight / savedHeight;

            float originX = savedX * scaleX;
            float originY = savedY * scaleY;

            if (originX >= imageWidth || originY >= imageHeight) {
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

            Log.e(
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

        VirtualDisplay display = virtualDisplay;
        virtualDisplay = null;

        if (display != null) {
            display.release();
        }

        ImageReader reader = imageReader;
        imageReader = null;

        if (reader != null) {
            reader.setOnImageAvailableListener(null, null);
            reader.close();
        }
    }

    @Override
    public void onDestroy() {

        releaseDisplay();

        MediaProjection projection = mediaProjection;
        mediaProjection = null;

        if (projection != null && !projectionStopped) {

            projectionStopped = true;
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
