package com.drawlines.android;

import android.media.Image;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class BallDetector {

    // Límites de la mesa: se mantienen fijos.
    private static final float TABLE_LEFT = 0.195f;
    private static final float TABLE_RIGHT = 0.805f;
    private static final float TABLE_TOP = 0.236f;
    private static final float TABLE_BOTTOM = 0.916f;

    // Muestreo para reducir el trabajo de análisis.
    private static final int STEP = 2;

    private BallDetector() {}

    public static class Ball {
        public final float x;
        public final float y;
        public final float radius;

        public Ball(float x, float y, float radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    public static List<Ball> detectBalls(Image image) {

        List<Ball> balls = new ArrayList<>();

        if (image == null) return balls;

        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return balls;

        int width = image.getWidth();
        int height = image.getHeight();

        int left = (int) (width * TABLE_LEFT);
        int right = (int) (width * TABLE_RIGHT);
        int top = (int) (height * TABLE_TOP);
        int bottom = (int) (height * TABLE_BOTTOM);

        int gridWidth = (right - left + STEP - 1) / STEP;
        int gridHeight = (bottom - top + STEP - 1) / STEP;

        if (gridWidth <= 0 || gridHeight <= 0) return balls;

        boolean[] visited = new boolean[gridWidth * gridHeight];
        int[] queue = new int[gridWidth * gridHeight];

        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer().duplicate();

        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        float minDiameter = width * 0.012f;
        float maxDiameter = width * 0.055f;

        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {

                int index = gy * gridWidth + gx;

                if (visited[index]) continue;

                int px = left + gx * STEP;
                int py = top + gy * STEP;

                if (!isBallColor(
                        buffer, px, py,
                        width, height,
                        rowStride, pixelStride)) {

                    visited[index] = true;
                    continue;
                }

                int head = 0;
                int tail = 0;

                queue[tail++] = index;
                visited[index] = true;

                int minX = gx;
                int maxX = gx;
                int minY = gy;
                int maxY = gy;

                while (head < tail) {

                    int current = queue[head++];

                    int cx = current % gridWidth;
                    int cy = current / gridWidth;

                    minX = Math.min(minX, cx);
                    maxX = Math.max(maxX, cx);
                    minY = Math.min(minY, cy);
                    maxY = Math.max(maxY, cy);

                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {

                            if (ox == 0 && oy == 0) continue;

                            int nx = cx + ox;
                            int ny = cy + oy;

                            if (nx < 0 || ny < 0 ||
                                    nx >= gridWidth ||
                                    ny >= gridHeight) {
                                continue;
                            }

                            int next = ny * gridWidth + nx;

                            if (visited[next]) continue;

                            visited[next] = true;

                            int sampleX = left + nx * STEP;
                            int sampleY = top + ny * STEP;

                            if (isBallColor(
                                    buffer, sampleX, sampleY,
                                    width, height,
                                    rowStride, pixelStride)) {

                                if (tail < queue.length) {
                                    queue[tail++] = next;
                                }
                            }
                        }
                    }
                }

                float boxWidth = (maxX - minX + 1) * STEP;
                float boxHeight = (maxY - minY + 1) * STEP;

                float diameter = (boxWidth + boxHeight) / 2f;

                if (diameter < minDiameter ||
                        diameter > maxDiameter) {
                    continue;
                }

                float ratio = boxWidth / boxHeight;

                if (ratio < 0.70f || ratio > 1.30f) {
                    continue;
                }

                float boxArea = boxWidth * boxHeight;
                float sampledArea = tail * STEP * STEP;
                float fillRatio = sampledArea / boxArea;

                if (fillRatio < 0.35f || fillRatio > 1.25f) {
                    continue;
                }

                float centerX = left +
                        ((minX + maxX) / 2f) * STEP;

                float centerY = top +
                        ((minY + maxY) / 2f) * STEP;

                balls.add(new Ball(
                        centerX,
                        centerY,
                        diameter / 2f
                ));
            }
        }

        return balls;
    }

    private static boolean isBallColor(
            ByteBuffer buffer,
            int x,
            int y,
            int width,
            int height,
            int rowStride,
            int pixelStride) {

        if (x < 0 || y < 0 || x >= width || y >= height) {
            return false;
        }

        int index = y * rowStride + x * pixelStride;

        if (index < 0 || index + 2 >= buffer.limit()) {
            return false;
        }

        int red = buffer.get(index) & 0xFF;
        int green = buffer.get(index + 1) & 0xFF;
        int blue = buffer.get(index + 2) & 0xFF;

        int maximum = Math.max(red, Math.max(green, blue));
        int minimum = Math.min(red, Math.min(green, blue));

        float saturation = maximum == 0
                ? 0f
                : (maximum - minimum) / (float) maximum;

        float brightness = maximum / 255f;

        // Bolas de colores.
        boolean colored = saturation >= 0.22f
                && brightness >= 0.18f
                && brightness <= 0.98f;

        // Bola blanca.
        boolean white = saturation <= 0.18f
                && brightness >= 0.72f;

        // Bola negra u oscura.
        boolean dark = brightness <= 0.16f;

        return colored || white || dark;
    }
}
