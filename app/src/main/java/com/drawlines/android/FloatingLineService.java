package com.drawlines.android;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
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
    private TextView coordinateLabel;

    private WindowManager.LayoutParams lineParams;
    private WindowManager.LayoutParams pointParams;
    private WindowManager.LayoutParams adjustParams;
    private WindowManager.LayoutParams hideParams;
    private WindowManager.LayoutParams coordinateParams;

    private static final int MODE_IDLE = 0;
    private static final int MODE_POINT = 1;
    private static final int MODE_ADJUST = 2;

    private int mode = MODE_IDLE;

    private boolean lineVisible = true;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotification();

        windowManager = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        createOverlay();
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

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("DrawLines activo")
                .setContentText("Selector de coordenadas disponible")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
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
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setPadding(16, 12, 16, 12);
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

        coordinateParams = createParams(
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

        pointParams = createParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        );

        pointParams.x = 20;
        pointParams.y = 180;

        pointButton.setOnClickListener(v -> activatePointMode());

        windowManager.addView(pointButton, pointParams);

        adjustButton = createButton("AJUSTAR");

        adjustParams = createParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        );

        adjustParams.x = 20;
        adjustParams.y = 240;

        adjustButton.setOnClickListener(v -> activateAdjustMode());

        windowManager.addView(adjustButton, adjustParams);

        hideButton = createButton("OCULTAR");

        hideParams = createParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        );

        hideParams.x = 20;
        hideParams.y = 300;

        hideButton.setOnClickListener(v -> toggleLineVisibility());

        windowManager.addView(hideButton, hideParams);
    }

    private void activatePointMode() {

        mode = MODE_POINT;

        lineView.setTouchMode(MODE_POINT);

        lineParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        windowManager.updateViewLayout(lineView, lineParams);

        pointButton.setText("TOCA LA BOLA");
        adjustButton.setText("AJUSTAR");
    }

    private void activateAdjustMode() {

        mode = MODE_ADJUST;

        lineView.setTouchMode(MODE_ADJUST);

        lineParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        windowManager.updateViewLayout(lineView, lineParams);

        pointButton.setText("PUNTO");
        adjustButton.setText("ARRASTRA EXTREMO");
    }

    private void deactivateTouch() {

        mode = MODE_IDLE;

        lineView.setTouchMode(MODE_IDLE);

        lineParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        windowManager.updateViewLayout(lineView, lineParams);

        pointButton.setText("PUNTO");
        adjustButton.setText("AJUSTAR");
    }

    private void toggleLineVisibility() {

        lineVisible = !lineVisible;

        lineView.setLineVisible(lineVisible);

        hideButton.setText(
                lineVisible ? "OCULTAR" : "MOSTRAR"
        );

        if (!lineVisible) {
            deactivateTouch();
        }

        lineView.invalidate();
    }

    private void updateCoordinates(float x, float y) {

        coordinateLabel.setText(
                "X: " + Math.round(x)
                        + "   Y: " + Math.round(y)
        );
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

        if (windowManager != null) {

            removeView(lineView);
            removeView(pointButton);
            removeView(adjustButton);
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

        private float startX = 200;
        private float startY = 400;

        private float endX = 450;
        private float endY = 400;

        private boolean pointSelected = false;
        private boolean visible = true;

        private int touchMode = MODE_IDLE;

        LineView() {
            super(FloatingLineService.this);

            linePaint.setColor(Color.YELLOW);
            linePaint.setStrokeWidth(5f);
            linePaint.setStyle(Paint.Style.STROKE);

            pointPaint.setColor(Color.YELLOW);
            pointPaint.setStyle(Paint.Style.FILL);

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setTouchMode(int newMode) {
            touchMode = newMode;
        }

        void setLineVisible(boolean value) {
            visible = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            if (!visible || !pointSelected) {
                return;
            }

            canvas.drawLine(
                    startX,
                    startY,
                    endX,
                    endY,
                    linePaint
            );

            canvas.drawCircle(
                    startX,
                    startY,
                    9,
                    pointPaint
            );

            canvas.drawCircle(
                    endX,
                    endY,
                    9,
                    pointPaint
            );
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

                        endX = x + 250;
                        endY = y;

                        pointSelected = true;

                        updateCoordinates(startX, startY);

                        invalidate();

                        return true;
                    }

                    if (touchMode == MODE_ADJUST
                            && pointSelected) {

                        endX = x;
                        endY = y;

                        invalidate();

                        return true;
                    }

                    return true;

                case MotionEvent.ACTION_MOVE:

                    if (touchMode == MODE_ADJUST
                            && pointSelected) {

                        endX = x;
                        endY = y;

                        invalidate();
                    }

                    return true;

                case MotionEvent.ACTION_UP:

                    return true;
            }

            return true;
        }
    }
}
