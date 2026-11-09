package com.drawlines.android;

import android.media.Image;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class WhiteLineDetector {

    // Límites originales de la mesa.
    private static final float TABLE_LEFT = 0.195f;
    private static final float TABLE_RIGHT = 0.805f;
    private static final float TABLE_TOP = 0.236f;
    private static final float TABLE_BOTTOM = 0.916f;

    private static final int PIXEL_STEP = 2;
    private static final int ANGLE_STEP = 3;
    private static final int RHO_STEP = 3;

    private static final int MIN_VOTES = 18;
    private static final int MAX_LINES = 2;

    private WhiteLineDetector() {
    }

    public static class DetectedLine {
        public final float angle;
        public final float rho;
        public final int votes;

        public DetectedLine(float angle, float rho, int votes) {
            this.angle = angle;
            this.rho = rho;
            this.votes = votes;
        }
    }

    private static class WhitePoint {
        int x;
        int y;

        WhitePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static List<DetectedLine> detectLines(Image image) {

        List<DetectedLine> result = new ArrayList<>();

        if (image == null) return result;

        Image.Plane[] planes = image.getPlanes();

        if (planes == null || planes.length == 0) {
            return result;
        }

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

        List<WhitePoint> points = new ArrayList<>();

        // Recopila píxeles blancos dentro de la mesa.
        for (int y = top; y < bottom; y += PIXEL_STEP) {
            for (int x = left; x < right; x += PIXEL_STEP) {

                if (isWhite(
                        buffer,
                        x,
                        y,
                        width,
                        height,
                        rowStride,
                        pixelStride)) {

                    points.add(new WhitePoint(x, y));
                }
            }
        }

        if (points.size() < MIN_VOTES) {
            return result;
        }

        int diagonal = (int) Math.sqrt(
                (right - left) * (right - left)
                + (bottom - top) * (bottom - top)
        );

        int rhoCount = (diagonal * 2) / RHO_STEP + 1;
        int angleCount = 180 / ANGLE_STEP;

        int[][] accumulator = new int[angleCount][rhoCount];

        // Transformada de Hough: busca líneas blancas largas.
        for (WhitePoint point : points) {

            for (int a = 0; a < angleCount; a++) {

                double theta = Math.toRadians(a * ANGLE_STEP);

                double rho =
                        point.x * Math.cos(theta)
                        + point.y * Math.sin(theta);

                int rhoIndex = (int) Math.round(
                        (rho + diagonal) / RHO_STEP
                );

                if (rhoIndex >= 0 && rhoIndex < rhoCount) {
                    accumulator[a][rhoIndex]++;
                }
            }
        }

        List<DetectedLine> candidates = new ArrayList<>();

        // Busca los máximos del acumulador.
        for (int a = 0; a < angleCount; a++) {
            for (int r = 2; r < rhoCount - 2; r++) {

                int votes = accumulator[a][r];

                if (votes < MIN_VOTES) {
                    continue;
                }

                boolean maximum = true;

                for (int da = -1; da <= 1 && maximum; da++) {
                    for (int dr = -2; dr <= 2; dr++) {

                        if (da == 0 && dr == 0) {
                            continue;
                        }

                        int neighborAngle = a + da;

                        if (neighborAngle < 0 ||
                            neighborAngle >= angleCount) {
                            continue;
                        }

                        if (accumulator[neighborAngle][r + dr] > votes) {
                            maximum = false;
                            break;
                        }
                    }
                }

                if (maximum) {

                    float angle = a * ANGLE_STEP;

                    float rho =
                            r * RHO_STEP - diagonal;

                    candidates.add(
                            new DetectedLine(angle, rho, votes)
                    );
                }
            }
        }

        Collections.sort(
                candidates,
                new Comparator<DetectedLine>() {
                    @Override
                    public int compare(
                            DetectedLine first,
                            DetectedLine second) {

                        return Integer.compare(
                                second.votes,
                                first.votes
                        );
                    }
                }
        );

        // Conserva líneas diferentes y evita duplicados.
        for (DetectedLine candidate : candidates) {

            boolean duplicate = false;

            for (DetectedLine existing : result) {

                float angleDifference = Math.abs(
                        candidate.angle - existing.angle
                );

                angleDifference = Math.min(
                        angleDifference,
                        180f - angleDifference
                );

                float rhoDifference = Math.abs(
                        candidate.rho - existing.rho
                );

                if (angleDifference < 8f &&
                    rhoDifference < 24f) {

                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                result.add(candidate);
            }

            if (result.size() >= MAX_LINES) {
                break;
            }
        }

        return result;
    }

    // Se conserva para que el código anterior siga compilando.
    public static Float detectAngle(
            Image image,
            float originX,
            float originY) {

        List<DetectedLine> lines = detectLines(image);

        if (lines.isEmpty()) {
            return null;
        }

        // El ángulo representa la dirección de la línea.
        return (lines.get(0).angle + 90f) % 180f;
    }

    private static boolean isWhite(
            ByteBuffer buffer,
            int x,
            int y,
            int width,
            int height,
            int rowStride,
            int pixelStride) {

        if (x < 0 || y < 0 ||
            x >= width || y >= height) {
            return false;
        }

        int index = y * rowStride + x * pixelStride;

        if (index < 0 || index + 2 >= buffer.limit()) {
            return false;
        }

        int red = buffer.get(index) & 0xFF;
        int green = buffer.get(index + 1) & 0xFF;
        int blue = buffer.get(index + 2) & 0xFF;

        int minimum = Math.min(
                red,
                Math.min(green, blue)
        );

        int maximum = Math.max(
                red,
                Math.max(green, blue)
        );

        return minimum >= 190 &&
                maximum - minimum <= 55;
    }
}
