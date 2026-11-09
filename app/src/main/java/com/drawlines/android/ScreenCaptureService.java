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

import java.util.ArrayList;
import java.util.List;

public class ScreenCaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "capture_data";

    public static final String ACTION_DIRECTION =
            "com.drawlines.android.DIRECTION_DETECTED";
    public static final String EXTRA_ANGLE = "detected_angle";

    public static final String ACTION_LINES =
            "com.drawlines.android.LINES_DETECTED";

    public static final String EXTRA_LINE_ANGLES =
            "detected_line_angles";
    public static final String EXTRA_LINE_RHOS =
            "detected_line_rhos";
    public static final String EXTRA_LINE_COUNT =
            "detected_line_count";
    public static final String EXTRA_IMAGE_WIDTH =
            "detected_image_width";
    public static final String EXTRA_IMAGE_HEIGHT =
            "detected_image_height";

    public static final String EXTRA_ORIGIN_X = "origin_auto_x";
    public static final String EXTRA_ORIGIN_Y = "origin_auto_y";

    public static final String ACTION_BALLS =
            "com.drawlines.android.BALLS_DETECTED";

    public static final String EXTRA_BALL_XS = "ball_positions_x";
    public static final String EXTRA_BALL_YS = "ball_positions_y";
    public static final String EXTRA_BALL_RADII = "ball_radii";
    public static final String EXTRA_BALL_COUNT = "ball_count";

    public static final String EXTRA_CUE_FOUND = "cue_ball_found";
    public static final String EXTRA_CUE_X = "cue_ball_x";
    public static final String EXTRA_CUE_Y = "cue_ball_y";
    public static final String EXTRA_CUE_RADIUS = "cue_ball_radius";

    private static final int NOTIFICATION_ID = 92;
    private static final String CHANNEL_ID = "drawlines_capture";
    private static final String PREFS_NAME = "drawlines_origin";
    private static final String PREF_TRACKING = "tracking_enabled";

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

        captureHandler = new Handler(captureThread.getLooper());
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
                .setContentText("Analizando líneas de la mesa")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null || captureStarted) {
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

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);

        Intent data;

        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(EXTRA_DATA, Intent.class);
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

    private void startCapture(int resultCode, Intent data) {
        try {
            MediaProjectionManager manager =
                    (MediaProjectionManager)
                            getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            mediaProjection = manager.getMediaProjection(resultCode, data);

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
                    (WindowManager) getSystemService(WINDOW_SERVICE);

            DisplayMetrics metrics = new DisplayMetrics();

            windowManager.getDefaultDisplay().getRealMetrics(metrics);

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

            virtualDisplay = mediaProjection.createVirtualDisplay(
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
            Log.e("DrawLines", "Error iniciando captura", e);
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

            if (now - lastDetectionTime < 100) {
                return;
            }

            lastDetectionTime = now;

            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();

            if (imageWidth <= 0 || imageHeight <= 0) {
                return;
            }

            detectAndBroadcastBalls(
                    image,
                    imageWidth,
                    imageHeight
            );

            SharedPreferences preferences =
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            boolean trackingEnabled = preferences.getBoolean(
                    PREF_TRACKING,
                    false
            );

            if (!trackingEnabled) {
                return;
            }

            detectAndBroadcastLines(
                    image,
                    imageWidth,
                    imageHeight
            );

        } catch (Exception e) {
            Log.e("DrawLines", "Error analizando imagen", e);

        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private void detectAndBroadcastLines(
            Image image,
            int imageWidth,
            int imageHeight) {

        try {
            List<WhiteLineDetector.DetectedLine> lines =
                    WhiteLineDetector.detectLines(image);

            int count = Math.min(lines.size(), 2);

            float[] angles = new float[count];
            float[] rhos = new float[count];

            for (int i = 0; i < count; i++) {
                WhiteLineDetector.DetectedLine line = lines.get(i);

                angles[i] = line.angle;
                rhos[i] = line.rho;
            }

            Intent result = new Intent(ACTION_LINES);
            result.setPackage(getPackageName());

            result.putExtra(EXTRA_LINE_ANGLES, angles);
            result.putExtra(EXTRA_LINE_RHOS, rhos);
            result.putExtra(EXTRA_LINE_COUNT, count);
            result.putExtra(EXTRA_IMAGE_WIDTH, imageWidth);
            result.putExtra(EXTRA_IMAGE_HEIGHT, imageHeight);

            result.putExtra(EXTRA_ORIGIN_X, -1f);
            result.putExtra(EXTRA_ORIGIN_Y, -1f);

            sendBroadcast(result);

        } catch (Exception e) {
            Log.e("DrawLines", "Error detectando líneas", e);
        }
    }

    private void detectAndBroadcastBalls(
            Image image,
            int imageWidth,
            int imageHeight) {

        try {
            List<BallDetector.Ball> balls =
                    BallDetector.detectBalls(image);

            List<Float> positionsX = new ArrayList<>();
            List<Float> positionsY = new ArrayList<>();
            List<Float> radii = new ArrayList<>();

            for (BallDetector.Ball ball : balls) {
                positionsX.add(ball.x / imageWidth);
                positionsY.add(ball.y / imageHeight);
                radii.add(ball.radius / imageWidth);
            }

            float[] xs = new float[positionsX.size()];
            float[] ys = new float[positionsY.size()];
            float[] rs = new float[radii.size()];

            for (int i = 0; i < positionsX.size(); i++) {
                xs[i] = positionsX.get(i);
                ys[i] = positionsY.get(i);
                rs[i] = radii.get(i);
            }

            // Reutiliza las bolas detectadas; no repite el análisis.
            BallDetector.Ball cue =
                    WhiteBallDetector.detect(image, balls);

            Intent result = new Intent(ACTION_BALLS);
            result.setPackage(getPackageName());

            result.putExtra(EXTRA_BALL_XS, xs);
            result.putExtra(EXTRA_BALL_YS, ys);
            result.putExtra(EXTRA_BALL_RADII, rs);
            result.putExtra(EXTRA_BALL_COUNT, balls.size());

            boolean cueFound = cue != null;

            result.putExtra(EXTRA_CUE_FOUND, cueFound);

            if (cueFound) {
                result.putExtra(EXTRA_CUE_X, cue.x / imageWidth);
                result.putExtra(EXTRA_CUE_Y, cue.y / imageHeight);
                result.putExtra(EXTRA_CUE_RADIUS, cue.radius / imageWidth);
            }

            sendBroadcast(result);

        } catch (Exception e) {
            Log.e("DrawLines", "Error detectando bolas", e);
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
