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

    private boolean editing = false;
    private boolean added = false;

    private WindowManager.LayoutParams lineParams;
    private WindowManager.LayoutParams buttonParams;

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
            .setContentText("Lápiz flotante disponible")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build();

        startForeground(1, notification);
    }

    private WindowManager.LayoutParams createParams(
        int width, int height, int gravity, int flags) {

        return new WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        );
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

        floatingButton.setOnClickListener(v -> toggleEditing());

        floatingButton.setOnTouchListener(new View.OnTouchListener() {

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
                        floatingButton, buttonParams);

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

        windowManager.addView(floatingButton, buttonParams);
        added = true;
    }

    private void toggleEditing() {

        editing = !editing;

        if (editing) {
            lineParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

            floatingButton.setText("✓");
        } else {
            lineParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

            floatingButton.setText("✎");
        }

        windowManager.updateViewLayout(lineView, lineParams);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        if (windowManager != null) {
            if (lineView != null && lineView.isAttachedToWindow()) {
                windowManager.removeView(lineView);
            }

            if (floatingButton != null &&
                floatingButton.isAttachedToWindow()) {
                windowManager.removeView(floatingButton);
            }
        }

        added = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class LineView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float startX = 200;
        private float startY = 500;
        private float endX = 700;
        private float endY = 500;

        private float lastX;
        private float lastY;

        LineView() {
            super(FloatingLineService.this);

            paint.setColor(Color.YELLOW);
            paint.setStrokeWidth(5f);
            paint.setStyle(Paint.Style.STROKE);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (!editing) {
                canvas.drawLine(startX, startY, endX, endY, paint);
                return;
            }

            canvas.drawLine(startX, startY, endX, endY, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(startX, startY, 12, paint);
            canvas.drawCircle(endX, endY, 12, paint);
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {

            if (!editing) return false;

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();

                    if (Math.hypot(lastX - endX, lastY - endY) < 55) {
                        movingEnd = true;
                    } else if (Math.hypot(
                        lastX - startX, lastY - startY) < 55) {
                        movingEnd = false;
                    } else {
                        startX = lastX;
                        startY = lastY;
                        endX = lastX;
                        endY = lastY;
                        movingEnd = true;
                    }

                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:

                    float x = event.getX();
                    float y = event.getY();

                    if (movingEnd) {
                        endX = x;
                        endY = y;
                    } else {
                        startX = x;
                        startY = y;
                    }

                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                    return true;
            }

            return true;
        }

        private boolean movingEnd = true;
    }
}
