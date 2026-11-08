package com.drawlines.android;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.nio.ByteBuffer;

public class FloatingLineService extends Service {

    private WindowManager windowManager;
    private LineView lineView;
    private TextView floatingButton;

    private WindowManager.LayoutParams lineParams;
    private WindowManager.LayoutParams buttonParams;

    private boolean editing = false;

    private MediaProjection mediaProjection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private HandlerThread captureThread;
    private Handler captureHandler;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private long lastDetectionTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotification();

        windowManager = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

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
                .setContentText("Buscando la bola blanca")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    1,
                    notification,
                    android.content.pm.ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(1, notification);
        }
    }

    private WindowManager.LayoutParams createParams(
            int width, int height, int gravity, int flags) {

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
                            floatingButton, buttonParams
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

        windowManager.addView(floatingButton, buttonParams);
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
        lineView.invalidate();
    }

    private void startCapture(Intent intent) {
        int resultCode = intent.getIntExtra("resultCode", 0);

        Intent data;
        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(
                    "projectionData", Intent.class
            );
        } else {
            data = intent.getParcelableExtra("projectionData");
        }

        if (data == null) {
            stopSelf();
            return;
        }

        MediaProjectionManager manager =
                (MediaProjectionManager)
                        getSystemService(MEDIA_PROJECTION_SERVICE);

        mediaProjection = manager.getMediaProjection(resultCode, data);

        if (mediaProjection == null) {
            stopSelf();
            return;
        }

        projectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        };

        mediaProjection.registerCallback(
                projectionCallback,
                new Handler(getMainLooper())
        );

        captureThread = new HandlerThread("DrawLinesCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
        );

        imageReader.setOnImageAvailableListener(
                this::detectBall,
                captureHandler
        );

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "DrawLinesScreen",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                captureHandler
        );
    }

    private void detectBall(ImageReader reader) {
        Image image = null;

        try {
            image = reader.acquireLatestImage();

            if (image == null) return;

            long now = System.currentTimeMillis();
            if (now - lastDetectionTime < 150) return;
            lastDetectionTime = now;

            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();

            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int paddedWidth = rowStride / pixelStride;

            Bitmap full = Bitmap.createBitmap(
                    paddedWidth,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );

            buffer.rewind();
            full.copyPixelsFromBuffer(buffer);

            Bitmap cropped = Bitmap.createBitmap(
                    full, 0, 0, screenWidth, screenHeight
            );
            full.recycle();

            int scale = 4;
            int smallWidth = Math.max(1, screenWidth / scale);
            int smallHeight = Math.max(1, screenHeight / scale);

            Bitmap small = Bitmap.createScaledBitmap(
                    cropped, smallWidth, smallHeight, false
            );
            cropped.recycle();

            int[] pixels = new int[smallWidth * smallHeight];

            small.getPixels(
                    pixels, 0, smallWidth,
                    0, 0, smallWidth, smallHeight
            );
            small.recycle();

            int[] result = findWhiteBall(
                    pixels, smallWidth, smallHeight
            );

            if (result != null) {
                float detectedX = result[0] * scale;
                float detectedY = result[1] * scale;

                lineView.post(() ->
                        lineView.setBallPosition(
                                detectedX, detectedY
                        )
                );
            }

        } catch (Exception e) {
            android.util.Log.e(
                    "DrawLines",
                    "Error detectando bola",
                    e
            );
        } finally {
            if (image != null) image.close();
        }
    }

    private int[] findWhiteBall(
            int[] pixels, int width, int height) {

        // Zona de búsqueda de la bola blanca.
        int left = (int) (width * 0.29f);
        int right = (int) (width * 0.41f);
        int top = (int) (height * 0.50f);
        int bottom = (int) (height * 0.66f);

        boolean[] visited = new boolean[pixels.length];
        int[] queue = new int[pixels.length];

        int bestX = -1;
        int bestY = -1;
        double bestScore = Double.MAX_VALUE;

        float expectedX = width * 0.35f;
        float expectedY = height * 0.577f;

        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {

                int index = y * width + x;

                if (visited[index] || !isWhite(pixels[index])) {
                    continue;
                }

                int head = 0;
                int tail = 0;

                queue[tail++] = index;
                visited[index] = true;

                int minX = x;
                int maxX = x;
                int minY = y;
                int maxY = y;

                int count = 0;
                long sumX = 0;
                long sumY = 0;

                while (head < tail) {
                    int current = queue[head++];

                    int cx = current % width;
                    int cy = current / width;

                    count++;
                    sumX += cx;
                    sumY += cy;

                    minX = Math.min(minX, cx);
                    maxX = Math.max(maxX, cx);
                    minY = Math.min(minY, cy);
                    maxY = Math.max(maxY, cy);

                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {

                            int nx = cx + dx;
                            int ny = cy + dy;

                            if (nx < left || nx >= right
                                    || ny < top || ny >= bottom) {
                                continue;
                            }

                            int next = ny * width + nx;

                            if (!visited[next]
                                    && isWhite(pixels[next])) {
                                visited[next] = true;
                                queue[tail++] = next;
                            }
                        }
                    }
                }

                int boxWidth = maxX - minX + 1;
                int boxHeight = maxY - minY + 1;

                if (count < 12 || count > 180) continue;

                if (boxWidth < 4 || boxHeight < 4
                        || boxWidth > 15 || boxHeight > 15) {
                    continue;
                }

                float ratio = (float) boxWidth / boxHeight;

                if (ratio < 0.60f || ratio > 1.50f) {
                    continue;
                }

                float fill = (float) count /
                        (boxWidth * boxHeight);

                if (fill < 0.25f) continue;

                float centerX = (float) sumX / count;
                float centerY = (float) sumY / count;

                double distance = Math.hypot(
                        centerX - expectedX,
                        centerY - expectedY
                );

                double score =
                        distance
                        + Math.abs(boxWidth - boxHeight) * 2
                        + (1.0 - fill) * 10;

                if (score < bestScore) {
                    bestScore = score;
                    bestX = Math.round(centerX);
                    bestY = Math.round(centerY);
                }
            }
        }

        if (bestX < 0) return null;

        return new int[]{bestX, bestY};
    }

    private boolean isWhite(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        return red > 185
                && green > 185
                && blue > 185
                && Math.abs(red - green) < 45
                && Math.abs(green - blue) < 45;
    }

    @Override
    public int onStartCommand(
            Intent intent, int flags, int startId) {

        if (intent != null && mediaProjection == null) {
            startCapture(intent);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }

        if (imageReader != null) {
            imageReader.close();
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
        }

        if (captureThread != null) {
            captureThread.quitSafely();
        }

        if (windowManager != null) {
            if (lineView != null
                    && lineView.isAttachedToWindow()) {
                windowManager.removeView(lineView);
            }

            if (floatingButton != null
                    && floatingButton.isAttachedToWindow()) {
                windowManager.removeView(floatingButton);
            }
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class LineView extends View {

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private float startX = 200;
        private float startY = 500;
        private float endX = 700;
        private float endY = 500;

        private boolean ballDetected = false;
        private boolean movingEnd = true;

        LineView() {
            super(FloatingLineService.this);

            paint.setColor(Color.YELLOW);
            paint.setStrokeWidth(5f);
            paint.setStyle(Paint.Style.STROKE);

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setBallPosition(float x, float y) {
            startX = x;
            startY = y;
            ballDetected = true;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);

            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(
                    startX, startY, endX, endY, paint
            );

            if (editing) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(startX, startY, 12, paint);
                canvas.drawCircle(endX, endY, 12, paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!editing) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    float x = event.getX();
                    float y = event.getY();

                    if (Math.hypot(x - endX, y - endY) < 55) {
                        movingEnd = true;
                    } else if (!ballDetected
                            && Math.hypot(
                                    x - startX, y - startY
                            ) < 55) {
                        movingEnd = false;
                    } else if (!ballDetected) {
                        startX = x;
                        startY = y;
                        endX = x;
                        endY = y;
                        movingEnd = true;
                    } else {
                        movingEnd = true;
                    }

                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float moveX = event.getX();
                    float moveY = event.getY();

                    if (movingEnd) {
                        endX = moveX;
                        endY = moveY;
                    } else {
                        startX = moveX;
                        startY = moveY;
                    }

                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                    return true;
            }

            return true;
        }
    }
}
