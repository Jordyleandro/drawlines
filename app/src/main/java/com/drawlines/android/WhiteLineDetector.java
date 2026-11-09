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

    private static final int MIN_VOTES = 8;
    private static final int MAX_CANDIDATES = 80;

    // Radio aproximado de búsqueda de la bola blanca.
    private static final int BALL_SEARCH_RADIUS = 18;

    // Distancia máxima entre el centro de la bola y cada línea.
    private static final float MAX_ORIGIN_DISTANCE = 24f;

    // Las dos líneas deben tener direcciones diferentes.
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

    private static class BallCenter {
        final float x;
        final float y;
        final int score;

        BallCenter(float x, float y, int score) {
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }

    public static List<DetectedLine> detectLines(Image image) {

        List<DetectedLine> result = new ArrayList<>();

        if (image == null) {
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

        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer().duplicate();

        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        List<WhitePoint> points = new ArrayList<>();

        for (int y = top; y < bottom; y += PIXEL_STEP) {
            for (int x = left; x < right; x += PIXEL_STEP) {

                if (isWhite(
                        buffer, x, y,
                        width, height,
                        rowStride, pixelStride)) {

                    points.add(new WhitePoint(x, y));
                }
            }
        }

        if (points.size() < MIN_VOTES * 2) {
            return result;
        }

        // Primero localiza una zona compacta de blanco:
        // candidata a ser el centro de la bola blanca.
        BallCenter ball = findWhiteBallCenter(
                points, left, right, top, bottom
        );

        if (ball == null) {
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

        // Hough: acumula las líneas blancas de la mesa.
        for (WhitePoint point : points) {

            for (int a = 0; a < angleCount; a++) {

                double theta = Math.toRadians(
                        a * ANGLE_STEP
                );

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

        // Busca máximos que además pasen cerca
        // del centro de la bola blanca.
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
                        ball.x * (float) Math.cos(theta)
                        + ball.y * (float) Math.sin(theta)
                        - rho
                );

                if (distance > MAX_ORIGIN_DISTANCE) {
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

        // Conserva candidatos fuertes y diferentes.
        List<DetectedLine> selected =
                new ArrayList<>();

        for (DetectedLine candidate : candidates) {

            boolean duplicate = false;

            for (DetectedLine existing : selected) {

                float difference = angleDifference(
                        candidate.angle,
                        existing.angle
                );

                if (difference < MIN_ANGLE_DIFFERENCE) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                selected.add(candidate);
            }

            if (selected.size() >= 2) {
                break;
            }

            if (selected.size() >= MAX_CANDIDATES) {
                break;
            }
        }

        return selected;
    }

    private static BallCenter findWhiteBallCenter(
            List<WhitePoint> points,
            int left,
            int right,
            int top,
            int bottom) {

        BallCenter best = null;

        // Busca la zona con mayor concentración compacta
        // de píxeles blancos.
        for (int cy = top + BALL_SEARCH_RADIUS;
             cy < bottom - BALL_SEARCH_RADIUS;
             cy += 4) {

            for (int cx = left + BALL_SEARCH_RADIUS;
                 cx < right - BALL_SEARCH_RADIUS;
                 cx += 4) {

                int score = 0;

                for (WhitePoint point : points) {

                    int dx = point.x - cx;
                    int dy = point.y - cy;

                    if (dx * dx + dy * dy
                            <= BALL_SEARCH_RADIUS * BALL_SEARCH_RADIUS) {
                        score++;
                    }
                }

                if (best == null || score > best.score) {
                    best = new BallCenter(cx, cy, score);
                }
            }
        }

        // Evita aceptar una zona con muy pocos píxeles.
        if (best == null || best.score < 12) {
            return null;
        }

        return best;
    }

    private static float angleDifference(
            float first,
            float second) {

        float difference = Math.abs(first - second);

        return Math.min(difference, 180f - difference);
    }

    public static Float detectAngle(
            Image image,
            float originX,
            float originY) {

        List<DetectedLine> lines = detectLines(image);

        if (lines.isEmpty()) {
            return null;
        }

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

        return minimum >= 190
                && maximum - minimum <= 55;
    }
}
