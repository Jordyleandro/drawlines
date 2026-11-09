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

    private static final int MIN_VOTES = 5;

    private static final float BALL_EXCLUSION_RADIUS = 18f;

    private static final float LINE_TOLERANCE = 4.5f;

    private static final float MIN_ANGLE_DIFFERENCE = 12f;

    private static final int RADIAL_BIN_SIZE = 8;

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

        final float dx;
        final float dy;

        WhitePoint(float dx, float dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    private static class Candidate {

        final float angle;
        final int votes;

        Candidate(float angle, int votes) {
            this.angle = angle;
            this.votes = votes;
        }
    }

    public static List<DetectedLine> detectLines(Image image) {
        return new ArrayList<>();
    }

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

        int maxDistance = (int) Math.sqrt(
                (right - left) * (right - left)
                        + (bottom - top) * (bottom - top)
        );

        int radialBins =
                maxDistance / RADIAL_BIN_SIZE + 2;

        List<WhitePoint> points = new ArrayList<>();

        // Recoge los píxeles blancos de la mesa.
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

                float distance = (float) Math.hypot(dx, dy);

                // No utiliza los píxeles de la bola blanca.
                if (distance <= BALL_EXCLUSION_RADIUS) {
                    continue;
                }

                points.add(new WhitePoint(dx, dy));
            }
        }

        if (points.size() < MIN_VOTES) {
            return result;
        }

        List<Candidate> candidates = new ArrayList<>();

        // Busca direcciones que pasen directamente
        // por el punto seleccionado.
        for (int angle = 0; angle < 180; angle++) {

            double radians = Math.toRadians(angle);

            float directionX = (float) Math.cos(radians);
            float directionY = (float) Math.sin(radians);

            boolean[] occupiedBins =
                    new boolean[radialBins];

            int occupiedCount = 0;

            for (WhitePoint point : points) {

                float perpendicular = Math.abs(
                        -point.dx * directionY
                                + point.dy * directionX
                );

                if (perpendicular > LINE_TOLERANCE) {
                    continue;
                }

                float along = Math.abs(
                        point.dx * directionX
                                + point.dy * directionY
                );

                if (along <= BALL_EXCLUSION_RADIUS) {
                    continue;
                }

                int bin = (int) (along / RADIAL_BIN_SIZE);

                if (bin >= 0 && bin < radialBins
                        && !occupiedBins[bin]) {

                    occupiedBins[bin] = true;
                    occupiedCount++;
                }
            }

            if (occupiedCount >= MIN_VOTES) {
                candidates.add(
                        new Candidate(angle, occupiedCount)
                );
            }
        }

        // Las direcciones con más tramos blancos
        // tienen prioridad.
        Collections.sort(
                candidates,
                new Comparator<Candidate>() {

                    @Override
                    public int compare(
                            Candidate first,
                            Candidate second) {

                        return Integer.compare(
                                second.votes,
                                first.votes
                        );
                    }
                }
        );

        // Selecciona como máximo dos direcciones distintas.
        for (Candidate candidate : candidates) {

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

            if (duplicate) {
                continue;
            }

            // Convierte la dirección de la línea
            // al ángulo normal que utiliza FloatingLineService.
            float normalAngle =
                    (candidate.angle - 90f + 180f) % 180f;

            double theta = Math.toRadians(normalAngle);

            float rho = (float) (
                    originX * Math.cos(theta)
                            + originY * Math.sin(theta)
            );

            result.add(
                    new DetectedLine(
                            normalAngle,
                            rho,
                            candidate.votes
                    )
            );

            if (result.size() == 2) {
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

        int index = buffer.position()
                + y * rowStride
                + x * pixelStride;

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

        return minimum >= 175
                && maximum - minimum <= 70;
    }
}
