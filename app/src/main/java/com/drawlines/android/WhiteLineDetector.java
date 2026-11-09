package com.drawlines.android;

import android.media.Image;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WhiteLineDetector {

    private static final float TABLE_LEFT = 0.195f;
    private static final float TABLE_RIGHT = 0.805f;
    private static final float TABLE_TOP = 0.236f;
    private static final float TABLE_BOTTOM = 0.916f;

    private static final int MIN_WHITE = 175;
    private static final int MAX_COLOR_DIFFERENCE = 70;

    private static final int START_DISTANCE = 22;
    private static final int DISTANCE_STEP = 3;

    private static final int ANGLE_STEP = 1;
    private static final int MIN_VOTES = 5;
    private static final int RADIAL_BIN_SIZE = 8;

    private static final float LINE_TOLERANCE = 4.5f;
    private static final float MIN_ANGLE_DIFFERENCE = 12f;

    private WhiteLineDetector() {
    }

    public static final class DetectedLine {

        public final float angle;
        public final float rho;

        public DetectedLine(float angle, float rho) {
            this.angle = angle;
            this.rho = rho;
        }
    }

    private static final class Candidate {

        float direction;
        int votes;

        Candidate(float direction, int votes) {
            this.direction = direction;
            this.votes = votes;
        }
    }

    public static List<DetectedLine> detectLines(Image image) {
        return Collections.emptyList();
    }

    public static List<DetectedLine> detectLines(
            Image image,
            float originX,
            float originY) {

        List<DetectedLine> result = new ArrayList<>();

        if (image == null || image.getPlanes() == null
                || image.getPlanes().length == 0) {
            return result;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= 0 || height <= 0
                || originX < 0 || originY < 0
                || originX >= width || originY >= height) {
            return result;
        }

        Image.Plane plane = image.getPlanes()[0];

        ByteBuffer buffer = plane.getBuffer().duplicate();

        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        if (pixelStride < 3 || rowStride <= 0) {
            return result;
        }

        // Busca hasta los límites de la captura,
        // sin el antiguo límite de 500 píxeles.
        int maxDistance = Math.max(width, height);

        List<Candidate> candidates = new ArrayList<>();

        for (int angle = 0; angle < 360; angle += ANGLE_STEP) {

            double radians = Math.toRadians(angle);

            float dx = (float) Math.cos(radians);
            float dy = (float) Math.sin(radians);

            boolean[] radialBins =
                    new boolean[maxDistance / RADIAL_BIN_SIZE + 2];

            int votes = 0;

            for (int distance = START_DISTANCE;
                 distance <= maxDistance;
                 distance += DISTANCE_STEP) {

                float centerX = originX + dx * distance;
                float centerY = originY + dy * distance;

                if (centerX < 0 || centerY < 0
                        || centerX >= width || centerY >= height) {
                    break;
                }

                boolean foundWhite = false;

                for (int offset = -4; offset <= 4; offset += 2) {

                    int x = Math.round(centerX - dy * offset);
                    int y = Math.round(centerY + dx * offset);

                    if (x < 0 || y < 0
                            || x >= width || y >= height) {
                        continue;
                    }

                    if (isWhite(
                            buffer,
                            x,
                            y,
                            width,
                            height,
                            rowStride,
                            pixelStride)) {

                        foundWhite = true;
                        break;
                    }
                }

                if (foundWhite) {

                    int bin = distance / RADIAL_BIN_SIZE;

                    if (bin >= 0 && bin < radialBins.length
                            && !radialBins[bin]) {

                        radialBins[bin] = true;
                        votes++;
                    }
                }
            }

            if (votes >= MIN_VOTES) {
                candidates.add(new Candidate(angle, votes));
            }
        }

        candidates.sort(
                (a, b) -> Integer.compare(b.votes, a.votes)
        );

        List<Candidate> selected = new ArrayList<>();

        for (Candidate candidate : candidates) {

            boolean different = true;

            for (Candidate previous : selected) {

                float difference = Math.abs(
                        candidate.direction - previous.direction
                );

                difference = Math.min(
                        difference,
                        360f - difference
                );

                difference = Math.min(
                        difference,
                        Math.abs(180f - difference)
                );

                if (difference < MIN_ANGLE_DIFFERENCE) {
                    different = false;
                    break;
                }
            }

            if (different) {
                selected.add(candidate);
            }

            if (selected.size() == 2) {
                break;
            }
        }

        for (Candidate candidate : selected) {

            float normalAngle =
                    (candidate.direction - 90f + 360f) % 180f;

            double theta = Math.toRadians(normalAngle);

            float rho =
                    originX * (float) Math.cos(theta)
                    + originY * (float) Math.sin(theta);

            result.add(new DetectedLine(normalAngle, rho));
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

        return lines.get(0).angle;
    }

    private static boolean isWhite(
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

        int c1 = buffer.get(index) & 0xFF;
        int c2 = buffer.get(index + 1) & 0xFF;
        int c3 = buffer.get(index + 2) & 0xFF;

        int minimum = Math.min(c1, Math.min(c2, c3));
        int maximum = Math.max(c1, Math.max(c2, c3));

        return minimum >= MIN_WHITE
                && maximum - minimum <= MAX_COLOR_DIFFERENCE;
    }
}
