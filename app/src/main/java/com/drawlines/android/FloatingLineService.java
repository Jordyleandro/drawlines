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
    private TextView floatingButton;
    private TextView coordinateLabel;

    private WindowManager.LayoutParams lineParams;
    private WindowManager.LayoutParams buttonParams;
    private WindowManager.LayoutParams coordinateParams;

    private boolean editing = false;

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

    private void createOverlay() {

        lineView = new LineView();

        lineParams = createParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Gravity.TOP | Gravity.START,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        windowManager.addView(lineView, lineParams);

        coordinateLabel = new TextView(this);
        coordinateLabel.setText("X: --   Y: --");
        coordinateLabel.setTextColor(Color.WHITE);
        coordinateLabel.setTextSize(14);
        coordinateLabel.setPadding(14, 10, 14, 10);
        coordinateLabel.setBackgroundColor(Color.rgb(35, 35, 35));

        coordinateParams = createParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        );

        coordinateParams.x = 20;
        coordinateParams.y = 100;

        windowManager.addView(
                coordinateLabel,
                coordinateParams
        );

        floatingButton = new TextView(this);
        floatingButton.setText("✎");
        floatingButton.setTextSize(25);
        floatingButton.setTextColor(Color.WHITE);
        floatingButton.setGravity(Gravity.CENTER);
        floatingButton.setBackgroundColor(Color.rgb(35, 35, 35));
        floatingButton.setPadding(18, 12, 18, 12);

        buttonParams = createParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        );

        buttonParams.x = 25;
        buttonParams.y = 250;

        floatingButton.setOnTouchListener(
                new View.OnTouchListener() {

            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX();
                    downY = event.getRawY();

                    startX = buttonParams.x;
                    startY = buttonParams.y;

                    moved = false;
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_MOVE) {

                    float dx = downX - event.getRawX();
                    float dy = event.getRawY() - downY;

                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        moved = true;
                    }

                    buttonParams.x = startX + (int) dx;
                    buttonParams.y = startY + (int) dy;

                    windowManager.updateViewLayout(
                            floatingButton,
                            buttonParams
                    );

                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_UP) {

                    if (!moved) {
                        toggleEditing();
                    }

                    return true;
                }

                return true;
            }
        });

        windowManager.addView(
                floatingButton,
                buttonParams
        );
    }

    private void toggleEditing() {

        editing = !editing;

        if (editing) {

            lineView.beginPointSelection();

            lineParams.flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

            floatingButton.setText("✓");

            coordinateLabel.setText(
                    "TOCA EL CENTRO DE LA BOLA BLANCA"
            );

        } else {

            lineParams.flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

            floatingButton.setText("✎");
        }

        windowManager.updateViewLayout(lineView, lineParams);
        lineView.invalidate();
    }

    private void updateCoordinates(float x, float y) {

        coordinateLabel.setText(
                "X: " + Math.round(x)
                        + "    Y: " + Math.round(y)
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

            if (lineView != null
                    && lineView.isAttachedToWindow()) {
                windowManager.removeView(lineView);
            }

            if (floatingButton != null
                    && floatingButton.isAttachedToWindow()) {
                windowManager.removeView(floatingButton);
            }

            if (coordinateLabel != null
                    && coordinateLabel.isAttachedToWindow()) {
                windowManager.removeView(coordinateLabel);
            }
        }

        super.onDestroy();
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

        private float startX;
        private float startY;

        private float endX;
        private float endY;

        private boolean pointSelected = false;
        private boolean selectingOrigin = true;
        private boolean settingOrigin = false;
        private boolean draggingEnd = false;

        LineView() {
            super(FloatingLineService.this);

            linePaint.setColor(Color.YELLOW);
            linePaint.setStrokeWidth(5f);
            linePaint.setStyle(Paint.Style.STROKE);

            pointPaint.setColor(Color.YELLOW);
            pointPaint.setStyle(Paint.Style.FILL);

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void beginPointSelection() {
            selectingOrigin = true;
            settingOrigin = false;
            draggingEnd = false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (!pointSelected) {
                return;
            }

            canvas.drawLine(
                    startX,
                    startY,
                    endX,
                    endY,
                    linePaint
            );

            if (editing) {
                canvas.drawCircle(
                        startX,
                        startY,
                        10,
                        pointPaint
                );

                canvas.drawCircle(
                        endX,
                        endY,
                        10,
                        pointPaint
                );
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            if (!editing) {
                return false;
            }

            float x = event.getRawX();
            float y = event.getRawY();

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:

                    if (selectingOrigin || !pointSelected) {

                        startX = x;
                        startY = y;

                        endX = x + 250;
                        endY = y;

                        pointSelected = true;
                        selectingOrigin = false;
                        settingOrigin = true;

                        updateCoordinates(startX, startY);
                        invalidate();

                        return true;
                    }

                    endX = x;
                    endY = y;

                    draggingEnd = true;
                    invalidate();

                    return true;

                case MotionEvent.ACTION_MOVE:

                    if (settingOrigin) {
                        return true;
                    }

                    if (draggingEnd) {
                        endX = x;
                        endY = y;
                        invalidate();
                    }

                    return true;

                case MotionEvent.ACTION_UP:

                    settingOrigin = false;
                    draggingEnd = false;

                    return true;
            }

            return true;
        }
    }
}
