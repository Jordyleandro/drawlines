package com.drawlines.android;

import android.media.Image;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class BallDetector {

    // La mesa conserva su posición y proporciones en pantalla.
    private static final float TABLE_LEFT = 0.195f;
    private static final float TABLE_RIGHT = 0.805f;
    private static final float TABLE_TOP = 0.236f;
    private static final float TABLE_BOTTOM = 0.916f;

    private static final int SCAN_STEP = 5;
    private static final int RING_SAMPLES = 24;

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

    private static class Candidate {
        float x;
        float y;
        float radius;
        float score;

        Candidate(float x, float y, float radius, float score) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.score = score;
        }
    }

    public static List<Ball> detectBalls(Image image) {
        List<Ball> result = new ArrayList<>();

        if (image == null) return result;

        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return result;

        int width = image.getWidth();
        int height = image.getHeight();

        int left = (int) (width * TABLE_LEFT);
        int right = (int) (width * TABLE_RIGHT);
        int top = (int) (height * TABLE_TOP);
        int bottom = (int) (height * TABLE_BOTTOM);

        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer().duplicate();

        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        // Tamaños aproximados de las bolas respecto al ancho de pantalla.
        float[] radii = {
            width * 0.0085f,
            width * 0.0095f,
            width * 0.0105f
        };

        List<Candidate> candidates = new ArrayList<>();

        for (float radius : radii) {

            int margin = (int) Math.ceil(radius * 1.25f);

            for (int y = top + margin; y < bottom - margin; y += SCAN_STEP) {
                for (int x = left + margin; x < right - margin; x += SCAN_STEP) {

                    float score = circularScore(
                        buffer,
                        x,
                        y,
                        radius,
                        width,
                        height,
                        rowStride,
                        pixelStride
                    );

                    if (score >= 0.63f) {
                        candidates.add(
                            new Candidate(x, y, radius, score)
                        );
                    }
                }
            }
        }

        // Primero se conservan las detecciones con mejor borde circular.
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Float.compare(b.score, a.score);
            }
        });

        // Elimina varias detecciones de una misma bola.
        for (Candidate candidate : candidates) {

            boolean duplicate = false;

            for (Ball existing : result) {
                float dx = candidate.x - existing.x;
                float dy = candidate.y - existing.y;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                float minimumDistance =
                    Math.min(candidate.radius, existing.radius) * 1.35f;

                if (distance < minimumDistance) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                result.add(new Ball(
                    candidate.x,
                    candidate.y,
                    candidate.radius
                ));
            }

            if (result.size() >= 30) break;
        }

        return result;
    }

    private static float circularScore(
        ByteBuffer buffer,
        int centerX,
        int centerY,
        float radius,
        int width,
        int height,
        int rowStride,
        int pixelStride
    ) {

        int[] centerColor = averageCenterColor(
            buffer,
            centerX,
            centerY,
            radius,
            width,
            height,
            rowStride,
            pixelStride
        );

        if (centerColor == null) return 0f;

        int contrastCount = 0;

        // Comprueba el contraste alrededor de toda la circunferencia.
        for (int i = 0; i < RING_SAMPLES; i++) {

            double angle =
                (Math.PI * 2.0 * i) / RING_SAMPLES;

            int x = Math.round(
                centerX + (float) Math.cos(angle) * radius * 1.08f
            );

            int y = Math.round(
                centerY + (float) Math.sin(angle) * radius * 1.08f
            );

            int[] color = getColor(
                buffer,
                x,
                y,
                width,
                height,
                rowStride,
                pixelStride
            );

            if (color == null) continue;

            int dr = color[0] - centerColor[0];
            int dg = color[1] - centerColor[1];
            int db = color[2] - centerColor[2];

            float difference = (float) Math.sqrt(
                dr * dr + dg * dg + db * db
            );

            if (difference >= 45f) {
                contrastCount++;
            }
        }

        return contrastCount / (float) RING_SAMPLES;
    }

    private static int[] averageCenterColor(
        ByteBuffer buffer,
        int centerX,
        int centerY,
        float radius,
        int width,
        int height,
        int rowStride,
        int pixelStride
    ) {

        int[][] offsets = {
            {0, 0},
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1},
            {1, 1},
            {-1, -1},
            {1, -1},
            {-1, 1}
        };

        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;

        for (int[] offset : offsets) {

            int x = centerX + Math.round(offset[0] * radius * 0.35f);
            int y = centerY + Math.round(offset[1] * radius * 0.35f);

            int[] color = getColor(
                buffer,
                x,
                y,
                width,
                height,
                rowStride,
                pixelStride
            );

            if (color == null) continue;

            red += color[0];
            green += color[1];
            blue += color[2];
            count++;
        }

        if (count == 0) return null;

        return new int[] {
            red / count,
            green / count,
            blue / count
        };
    }

    private static int[] getColor(
        ByteBuffer buffer,
        int x,
        int y,
        int width,
        int height,
        int rowStride,
        int pixelStride
    ) {

        if (x < 0 || y < 0 || x >= width || y >= height) {
            return null;
        }

        int index = y * rowStride + x * pixelStride;

        if (index < 0 || index + 2 >= buffer.limit()) {
            return null;
        }

        int red = buffer.get(index) & 0xFF;
        int green = buffer.get(index + 1) & 0xFF;
        int blue = buffer.get(index + 2) & 0xFF;

        return new int[] { red, green, blue };
    }
}
