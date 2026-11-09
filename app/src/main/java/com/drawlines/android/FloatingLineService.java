package com.drawlines.android;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingLineService extends Service {

    private WindowManager windowManager;
    private LineView lineView;

    private TextView pointButton;
    private TextView adjustButton;
    private TextView hideButton;
    private TextView trackingButton;
    private TextView coordinateLabel;

    private WindowManager.LayoutParams lineParams;

    private static final int MODE_IDLE = 0;
    private static final int MODE_POINT = 1;
    private static final int MODE_ADJUST = 2;

    private static final String PREFS_NAME = "drawlines_origin";
    private static final String PREF_X = "origin_x";
    private static final String PREF_Y = "origin_y";
    private static final String PREF_SELECTED = "origin_selected";
    private static final String PREF_TRACKING = "tracking_enabled";
    private static final String PREF_VIEW_WIDTH = "view_width";
    private static final String PREF_VIEW_HEIGHT = "view_height";

    private boolean trackingEnabled = false;
    private boolean receiverRegistered = false;
    private boolean panelVisible = true;

    private final BroadcastReceiver directionReceiver =
            new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null || lineView == null) {
                return;
            }

            String action = intent.getAction();

            if (ScreenCaptureService.ACTION_DIRECTION.equals(action)) {

                if (!trackingEnabled) {
                    return;
                }

                float angle = intent.getFloatExtra(
                        ScreenCaptureService.EXTRA_ANGLE,
                        Float.NaN
                );

                if (!Float.isNaN(angle)) {
                    lineView.updateAutomaticDirection(angle);
                }

                return;
            }

            if (ScreenCaptureService.ACTION_BALLS.equals(action)) {

                float[] xs = intent.getFloatArrayExtra(
                        ScreenCaptureService.EXTRA_BALL_XS
                );

                float[] ys = intent.getFloatArrayExtra(
                        ScreenCaptureService.EXTRA_BALL_YS
                );

                float[] radii = intent.getFloatArrayExtra(
                        ScreenCaptureService.EXTRA_BALL_RADII
                );

                if (xs != null && ys != null && radii != null) {
                    lineView.updateDetectedBalls(xs, ys, radii);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        createNotification();

        windowManager = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        SharedPreferences preferences =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        trackingEnabled = preferences.getBoolean(
                PREF_TRACKING,
                false
        );

        createOverlay();
        registerDirectionReceiver();
    }

    private void createNotification() {

        String channelId = "drawlines_channel";

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            "DrawLines",
                            NotificationManager.IMPORTANCE_LOW
                    );

            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("DrawLines activo")
                .setContentText("Guía de trayectoria")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(true)
                .build();

        startForeground(1, notification);
    }

    private WindowManager.LayoutParams createParams(
            int width,
            int height,
            int gravity,
            int flags) {

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        width,
                        height,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        flags,
                        PixelFormat.TRANSLUCENT
                );

        params.gravity = gravity;
        return params;
    }

    private TextView createButton(String text) {

        TextView button = new TextView(this);

        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setPadding(12, 12, 12, 12);
        button.setBackgroundColor(Color.rgb(30, 30, 30));

        return button;
    }

    private void createOverlay() {

        lineView = new LineView();

        lineParams = createParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Gravity.TOP | Gravity.START,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        );

        windowManager.addView(lineView, lineParams);

        coordinateLabel = createButton("X: --   Y: --");

        WindowManager.LayoutParams coordinateParams =
                createParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.START,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                );

        coordinateParams.x = 15;
        coordinateParams.y = 90;

        windowManager.addView(
                coordinateLabel,
                coordinateParams
        );

        pointButton = createButton("PUNTO");

        WindowManager.LayoutParams pointParams =
                createParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                );

        pointParams.x = 20;
        pointParams.y = 180;

        pointButton.setOnClickListener(
                v -> activatePointMode()
        );

        windowManager.addView(pointButton, pointParams);

        adjustButton = createButton("AJUSTAR");

        WindowManager.LayoutParams adjustParams =
                createParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                );

        adjustParams.x = 20;
        adjustParams.y = 240;

        adjustButton.setOnClickListener(
                v -> activateAdjustMode()
        );

        windowManager.addView(adjustButton, adjustParams);

        trackingButton = createButton(
                trackingEnabled
                        ? "SEGUIMIENTO ACTIVADO"
                        : "SEGUIMIENTO DESACTIVADO"
        );

        WindowManager.LayoutParams trackingParams =
                createParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                );

        trackingParams.x = 20;
        trackingParams.y = 300;

        trackingButton.setOnClickListener(
                v -> toggleTracking()
        );

        windowManager.addView(trackingButton, trackingParams);

        hideButton = createButton("OCULTAR");

        WindowManager.LayoutParams hideParams =
                createParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                );

        hideParams.x = 20;
        hideParams.y = 360;

        hideButton.setOnClickListener(
                v -> toggleVisibility()
        );

        windowManager.addView(hideButton, hideParams);
    }

    private void registerDirectionReceiver() {

        IntentFilter filter = new IntentFilter();

        filter.addAction(ScreenCaptureService.ACTION_DIRECTION);
        filter.addAction(ScreenCaptureService.ACTION_BALLS);

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                    directionReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(directionReceiver, filter);
        }

        receiverRegistered = true;
    }

    private void toggleTracking() {

        SharedPreferences preferences =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (!lineView.hasSelectedPoint()) {

            trackingEnabled = false;

            preferences.edit()
                    .putBoolean(PREF_TRACKING, false)
                    .apply();

            trackingButton.setText("SELECCIONA PRIMERO EL PUNTO");
            return;
        }

        trackingEnabled = !trackingEnabled;

        preferences.edit()
                .putBoolean(PREF_TRACKING, trackingEnabled)
                .apply();

        trackingButton.setText(
                trackingEnabled
                        ? "SEGUIMIENTO ACTIVADO"
                        : "SEGUIMIENTO DESACTIVADO"
        );
    }

    private void enableLineTouch() {

        lineParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        windowManager.updateViewLayout(lineView, lineParams);
    }

    private void disableLineTouch() {

        if (lineView == null || lineParams == null) {
            return;
        }

        lineView.setTouchMode(MODE_IDLE);

        lineParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        windowManager.updateViewLayout(lineView, lineParams);

        if (pointButton != null) {
            pointButton.setText("PUNTO");
        }

        if (adjustButton != null) {
            adjustButton.setText("AJUSTAR");
        }
    }

    private void activatePointMode() {

        trackingEnabled = false;

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_TRACKING, false)
                .apply();

        trackingButton.setText("SEGUIMIENTO DESACTIVADO");

        lineView.setTouchMode(MODE_POINT);
        enableLineTouch();

        pointButton.setText("TOCA LA BOLA");
        adjustButton.setText("AJUSTAR");
    }

    private void activateAdjustMode() {

        lineView.setTouchMode(MODE_ADJUST);
        enableLineTouch();

        pointButton.setText("PUNTO");
        adjustButton.setText("AJUSTANDO");
    }

    private void toggleVisibility() {

        if (panelVisible) {

            disableLineTouch();
            panelVisible = false;

            pointButton.setVisibility(View.GONE);
            adjustButton.setVisibility(View.GONE);
            trackingButton.setVisibility(View.GONE);
            coordinateLabel.setVisibility(View.GONE);

            hideButton.setText("MOSTRAR");

        } else {

            panelVisible = true;

            pointButton.setVisibility(View.VISIBLE);
            adjustButton.setVisibility(View.VISIBLE);
            trackingButton.setVisibility(View.VISIBLE);
            coordinateLabel.setVisibility(View.VISIBLE);

            hideButton.setText("OCULTAR");
        }

        lineView.invalidate();
    }

    private void updateCoordinates(float x, float y) {

        if (coordinateLabel != null) {
            coordinateLabel.setText(
                    "X: " + Math.round(x)
                            + "   Y: " + Math.round(y)
            );
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {

        if (receiverRegistered) {
            unregisterReceiver(directionReceiver);
            receiverRegistered = false;
        }

        if (windowManager != null) {
            removeView(lineView);
            removeView(pointButton);
            removeView(adjustButton);
            removeView(trackingButton);
            removeView(hideButton);
            removeView(coordinateLabel);
        }

        super.onDestroy();
    }

    private void removeView(View view) {

        if (view != null && view.isAttachedToWindow()) {
            windowManager.removeView(view);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class LineView extends View {

        private final Paint linePaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint pointPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint ballPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private float startX = 600;
        private float startY = 450;

        private float endX = 850;
        private float endY = 450;

        private boolean pointSelected = false;

        private int touchMode = MODE_IDLE;

        private float[] ballXs = new float[0];
        private float[] ballYs = new float[0];
        private float[] ballRadii = new float[0];

        LineView() {
            super(FloatingLineService.this);

            linePaint.setColor(Color.YELLOW);
            linePaint.setStrokeWidth(5f);
            linePaint.setStyle(Paint.Style.STROKE);

            pointPaint.setColor(Color.YELLOW);
            pointPaint.setStyle(Paint.Style.FILL);

            ballPaint.setColor(Color.CYAN);
            ballPaint.setStrokeWidth(3f);
            ballPaint.setStyle(Paint.Style.STROKE);

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            restoreSelectedPoint();
        }

        private void restoreSelectedPoint() {

            SharedPreferences preferences =
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            pointSelected = preferences.getBoolean(
                    PREF_SELECTED,
                    false
            );

            if (pointSelected) {

                startX = preferences.getFloat(PREF_X, 600f);
                startY = preferences.getFloat(PREF_Y, 450f);

                endX = startX + 250f;
                endY = startY;

                post(() -> {
                    updateCoordinates(startX, startY);
                    invalidate();
                });
            }
        }

        boolean hasSelectedPoint() {
            return pointSelected;
        }

        void setTouchMode(int mode) {
            touchMode = mode;
        }

        void updateDetectedBalls(
                float[] xs,
                float[] ys,
                float[] radii) {

            if (xs == null || ys == null || radii == null) {
                return;
            }

            int count = Math.min(
                    xs.length,
                    Math.min(ys.length, radii.length)
            );

            float width = getWidth();
            float height = getHeight();

            if (width <= 0 || height <= 0) {
                return;
            }

            ballXs = new float[count];
            ballYs = new float[count];
            ballRadii = new float[count];

            for (int i = 0; i < count; i++) {

                ballXs[i] = xs[i] * width;
                ballYs[i] = ys[i] * height;
                ballRadii[i] = radii[i] * width;
            }

            invalidate();
        }

        // Ajusta el origen al centro de la bola detectada más cercana.
        private void snapToNearestBall() {

            if (ballXs == null || ballYs == null || ballRadii == null) {
                return;
            }

            float nearestDistance = Float.MAX_VALUE;
            int nearestIndex = -1;

            for (int i = 0; i < ballXs.length; i++) {

                float dx = startX - ballXs[i];
                float dy = startY - ballYs[i];

                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestIndex = i;
                }
            }

            if (nearestIndex >= 0) {

                float radius = ballRadii[nearestIndex];

                if (nearestDistance <= radius * 2.5f) {
                    startX = ballXs[nearestIndex];
                    startY = ballYs[nearestIndex];
                }
            }
        }

        void updateAutomaticDirection(float angle) {

            if (!trackingEnabled || !pointSelected) {
                return;
            }

            double radians = Math.toRadians(angle);

            float length = 250f;

            endX = startX + (float) Math.cos(radians) * length;
            endY = startY + (float) Math.sin(radians) * length;

            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            drawDetectedBalls(canvas);

            if (!pointSelected) {
                return;
            }

            drawReflections(canvas);

            canvas.drawCircle(
                    startX, startY, 10, pointPaint
            );

            canvas.drawCircle(
                    endX, endY, 9, pointPaint
            );
        }

        private void drawDetectedBalls(Canvas canvas) {

            for (int i = 0; i < ballXs.length; i++) {

                float x = ballXs[i];
                float y = ballYs[i];
                float radius = ballRadii[i];

                if (radius <= 0) {
                    continue;
                }

                canvas.drawCircle(
                        x,
                        y,
                        radius,
                        ballPaint
                );
            }
        }

        private void drawReflections(Canvas canvas) {

            float width = getWidth();
            float height = getHeight();

            if (width <= 0 || height <= 0) {
                return;
            }

            float left = width * 0.195f;
            float right = width * 0.805f;
            float top = height * 0.236f;
            float bottom = height * 0.916f;

            float dx = endX - startX;
            float dy = endY - startY;

            float length = (float) Math.hypot(dx, dy);

            if (length < 1f) {
                return;
            }

            dx /= length;
            dy /= length;

            float x = startX;
            float y = startY;

            for (int i = 0; i < 8; i++) {

                float tx = Float.POSITIVE_INFINITY;
                float ty = Float.POSITIVE_INFINITY;

                if (dx > 0.00001f) {
                    tx = (right - x) / dx;
                } else if (dx < -0.00001f) {
                    tx = (left - x) / dx;
                }

                if (dy > 0.00001f) {
                    ty = (bottom - y) / dy;
                } else if (dy < -0.00001f) {
                    ty = (top - y) / dy;
                }

                float distance = Math.min(tx, ty);

                if (!Float.isFinite(distance) || distance <= 0) {
                    break;
                }

                float nextX = x + dx * distance;
                float nextY = y + dy * distance;

                canvas.drawLine(
                        x, y, nextX, nextY, linePaint
                );

                boolean hitLeftOrRight =
                        Math.abs(tx - distance) < 0.01f;

                boolean hitTopOrBottom =
                        Math.abs(ty - distance) < 0.01f;

                x = nextX;
                y = nextY;

                if (hitLeftOrRight) {
                    dx = -dx;
                }

                if (hitTopOrBottom) {
                    dy = -dy;
                }

                if (hitLeftOrRight) {
                    x += dx * 0.1f;
                }

                if (hitTopOrBottom) {
                    y += dy * 0.1f;
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            if (touchMode == MODE_IDLE) {
                return false;
            }

            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:

                    if (touchMode == MODE_POINT) {

                        startX = x;
                        startY = y;

                        // Busca el centro de la bola más cercana.
                        snapToNearestBall();

                        endX = startX + 250f;
                        endY = startY;

                        pointSelected = true;

                        SharedPreferences preferences =
                                getSharedPreferences(
                                        PREFS_NAME,
                                        MODE_PRIVATE
                                );

                        preferences.edit()
                                .putFloat(PREF_X, startX)
                                .putFloat(PREF_Y, startY)
                                .putInt(PREF_VIEW_WIDTH, getWidth())
                                .putInt(PREF_VIEW_HEIGHT, getHeight())
                                .putBoolean(PREF_SELECTED, true)
                                .apply();

                        updateCoordinates(startX, startY);
                        invalidate();

                        post(() -> disableLineTouch());

                        return true;
                    }

                    if (touchMode == MODE_ADJUST && pointSelected) {

                        endX = x;
                        endY = y;

                        invalidate();
                        return true;
                    }

                    return true;

                case MotionEvent.ACTION_MOVE:

                    if (touchMode == MODE_ADJUST && pointSelected) {

                        endX = x;
                        endY = y;

                        invalidate();
                    }

                    return true;

                case MotionEvent.ACTION_UP:

                    if (touchMode == MODE_ADJUST) {
                        post(() -> disableLineTouch());
                    }

                    return true;

                case MotionEvent.ACTION_CANCEL:

                    post(() -> disableLineTouch());
                    return true;
            }

            return true;
        }
    }
}
