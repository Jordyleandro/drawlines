package com.drawlines.android;

import android.media.Image;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class WhiteLineDetector {

    private static final float TABLE_LEFT = 0.195f;
    private static final float TABLE_RIGHT = 0.805f;
    private static final float TABLE_TOP = 0.236f;
    private static final float TABLE_BOTTOM = 0.916f;

    private static final int PIXEL_STEP = 2;
    private static final int ANGLE_STEP = 2;
    private static final int RHO_STEP = 2;

    private static final int MIN_VOTES = 10;

    // Tolerancia para que una línea pase por el punto seleccionado.
    private static final float ORIGIN_TOLERANCE = 10f;

    // Excluye la propia bola blanca del cálculo de las líneas.
    private static final float BALL_EXCLUSION_RADIUS = 13f;

    private static final float MIN_ANGLE_DIFFERENCE = 12f;

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
        final int x;
        final int y;

        WhitePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // Compatibilidad temporal con la llamada antigua.
    // No busca líneas sin una coordenada seleccionada.
    public static List<DetectedLine> detectLines(Image image) {
        return new ArrayList<>();
    }

    // Nuevo detector: utiliza obligatoriamente el punto seleccionado.
    public static List<DetectedLine> detectLines(
            Image image,
            float originX,
            float originY) {

        List<DetectedLine> result = new ArrayList<>();

        if (image == null
                || originX < 0
                || originY < 0
                || originX >= image.getWidth()
                || originY >= image.getHeight()) {
            return result;
        }

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

        if (originX < left || originX > right
                || originY < top || originY > bottom) {
            return result;
        }

        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer().duplicate();

        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        List<WhitePoint> points = new ArrayList<>();

        // Solo recoge píxeles blancos dentro de la mesa.
        for (int y = top; y < bottom; y += PIXEL_STEP) {
            for (int x = left; x < right; x += PIXEL_STEP) {

                if (!isWhite(
                        buffer,
                        x,
                        y,
                        width,
                        height,
                        rowStride,
                        pixelStride)) {
                    continue;
                }

                float dx = x - originX;
                float dy = y - originY;

                // Ignora los píxeles de la propia bola blanca.
                if (dx * dx + dy * dy
                        <= BALL_EXCLUSION_RADIUS
                        * BALL_EXCLUSION_RADIUS) {
                    continue;
                }

                points.add(new WhitePoint(x, y));
            }
        }

        if (points.size() < MIN_VOTES) {
            return result;
        }

        int diagonal = (int) Math.sqrt(
                (right - left) * (right - left)
                        + (bottom - top) * (bottom - top)
        );

        int angleCount = 180 / ANGLE_STEP;
        int rhoCount = (diagonal * 2) / RHO_STEP + 1;

        int[][] accumulator =
                new int[angleCount][rhoCount];

        // Acumula líneas blancas de la imagen.
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

        List<DetectedLine> candidates =
                new ArrayList<>();

        // Solo acepta líneas que pasan cerca
        // de la coordenada que seleccionó el usuario.
        for (int a = 0; a < angleCount; a++) {

            for (int r = 2; r < rhoCount - 2; r++) {

                int votes = accumulator[a][r];

                if (votes < MIN_VOTES) {
                    continue;
                }

                float angle = a * ANGLE_STEP;
                float rho = r * RHO_STEP - diagonal;

                double theta = Math.toRadians(angle);

                float distance = Math.abs(
                        originX * (float) Math.cos(theta)
                                + originY * (float) Math.sin(theta)
                                - rho
                );

                if (distance > ORIGIN_TOLERANCE) {
                    continue;
                }

                boolean maximum = true;

                for (int da = -1; da <= 1 && maximum; da++) {

                    for (int dr = -2; dr <= 2; dr++) {

                        if (da == 0 && dr == 0) {
                            continue;
                        }

                        int neighborAngle = a + da;

                        if (neighborAngle < 0
                                || neighborAngle >= angleCount) {
                            continue;
                        }

                        if (accumulator[neighborAngle][r + dr]
                                > votes) {
                            maximum = false;
                            break;
                        }
                    }
                }

                if (maximum) {
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

        // Conserva como máximo dos direcciones diferentes.
        for (DetectedLine candidate : candidates) {

            boolean duplicate = false;

            for (DetectedLine existing : result) {

                if (angleDifference(
                        candidate.angle,
                        existing.angle
                ) < MIN_ANGLE_DIFFERENCE) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                result.add(candidate);
            }

            if (result.size() >= 2) {
                break;
            }
        }

        return result;
    }

    public static Float detectAngle(
            Image image,
            float originX,
            float originY) {

        List<DetectedLine> lines =
                detectLines(image, originX, originY);

        if (lines.isEmpty()) {
            return null;
        }

        return (lines.get(0).angle + 90f) % 180f;
    }

    private static float angleDifference(
            float first,
            float second) {

        float difference = Math.abs(first - second);

        return Math.min(difference, 180f - difference);
    }

    private static boolean isWhite(
            ByteBuffer buffer,
            int x,
            int y,
            int width,
            int height,
            int rowStride,
            int pixelStride) {

        if (x < 0 || y < 0
                || x >= width || y >= height) {
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

        return minimum >= 190
                && maximum - minimum <= 55;
    }
}
