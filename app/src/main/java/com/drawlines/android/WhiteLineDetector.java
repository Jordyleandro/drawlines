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

    private static final int MIN_BRIGHTNESS = 195;
    private static final int MAX_COLOR_DIFFERENCE = 48;

    private static final int SAMPLE_STEP = 3;
    private static final int ANGLE_STEP = 2;
    private static final int RHO_STEP = 3;

    private static final int MIN_VOTES = 12;
    private static final float MIN_LINE_LENGTH = 0.12f;
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

    private static final class Point {
        final float x;
        final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Candidate {
        final float angle;
        final float rho;
        final int votes;
        final float span;

        Candidate(float angle, float rho, int votes, float span) {
            this.angle = angle;
            this.rho = rho;
            this.votes = votes;
            this.span = span;
        }
    }

    public static List<DetectedLine> detectLines(Image image) {
        return detectLines(image, -1f, -1f);
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

        if (width <= 0 || height <= 0) {
            return result;
        }

        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer().duplicate();

        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        if (pixelStride < 3 || rowStride <= 0) {
            return result;
        }

        int left = Math.max(0, (int) (width * TABLE_LEFT));
        int right = Math.min(width, (int) (width * TABLE_RIGHT));
        int top = Math.max(0, (int) (height * TABLE_TOP));
        int bottom = Math.min(height, (int) (height * TABLE_BOTTOM));

        if (right <= left || bottom <= top) {
            return result;
        }

        List<Point> points = new ArrayList<>();

        for (int y = top; y < bottom; y += SAMPLE_STEP) {
            for (int x = left; x < right; x += SAMPLE_STEP) {

                if (isWhite(
                        buffer,
                        x,
                        y,
                        rowStride,
                        pixelStride)) {

                    points.add(new Point(x, y));
                }
            }
        }

        if (points.size() < MIN_VOTES) {
            return result;
        }

        float diagonal = (float) Math.sqrt(
                width * width + height * height
        );

        int rhoCount = (int) (diagonal * 2 / RHO_STEP) + 1;
        int angleCount = 180 / ANGLE_STEP;

        int[][] accumulator =
                new int[angleCount][rhoCount];

        float[] cosTable = new float[angleCount];
        float[] sinTable = new float[angleCount];

        for (int a = 0; a < angleCount; a++) {

            double radians = Math.toRadians(a * ANGLE_STEP);

            cosTable[a] = (float) Math.cos(radians);
            sinTable[a] = (float) Math.sin(radians);
        }

        for (Point point : points) {

            for (int a = 0; a < angleCount; a++) {

                float rho =
                        point.x * cosTable[a]
                        + point.y * sinTable[a];

                int index = Math.round(
                        (rho + diagonal) / RHO_STEP
                );

                if (index >= 0 && index < rhoCount) {
                    accumulator[a][index]++;
                }
            }
        }

        List<Candidate> candidates = new ArrayList<>();

        float minimumLength =
                Math.min(right - left, bottom - top)
                * MIN_LINE_LENGTH;

        for (int a = 0; a < angleCount; a++) {

            for (int r = 0; r < rhoCount; r++) {

                int votes = accumulator[a][r];

                if (votes < MIN_VOTES) {
                    continue;
                }

                float angle = a * ANGLE_STEP;

                float rho =
                        r * RHO_STEP - diagonal;

                float radians = (float) Math.toRadians(angle);

                float nx = (float) Math.cos(radians);
                float ny = (float) Math.sin(radians);

                float dx = -ny;
                float dy = nx;

                float minProjection = Float.MAX_VALUE;
                float maxProjection = -Float.MAX_VALUE;

                int support = 0;

                for (Point point : points) {

                    float distance = Math.abs(
                            point.x * nx
                            + point.y * ny
                            - rho
                    );

                    if (distance <= RHO_STEP * 1.5f) {

                        float projection =
                                point.x * dx + point.y * dy;

                        minProjection = Math.min(
                                minProjection,
                                projection
                        );

                        maxProjection = Math.max(
                                maxProjection,
                                projection
                        );

                        support++;
                    }
                }

                float span = maxProjection - minProjection;

                if (support >= MIN_VOTES
                        && span >= minimumLength) {

                    candidates.add(
                            new Candidate(
                                    angle,
                                    rho,
                                    support,
                                    span
                            )
                    );
                }
            }
        }

        if (candidates.isEmpty()) {
            return result;
        }

        candidates.sort(
                new Comparator<Candidate>() {
                    @Override
                    public int compare(
                            Candidate first,
                            Candidate second) {

                        int votesCompare = Integer.compare(
                                second.votes,
                                first.votes
                        );

                        if (votesCompare != 0) {
                            return votesCompare;
                        }

                        return Float.compare(
                                second.span,
                                first.span
                        );
                    }
                }
        );

        List<Candidate> selected = new ArrayList<>();

        for (Candidate candidate : candidates) {

            boolean different = true;

            for (Candidate previous : selected) {

                float difference = Math.abs(
                        candidate.angle - previous.angle
                );

                difference = Math.min(
                        difference,
                        180f - difference
                );

                if (difference < MIN_ANGLE_DIFFERENCE) {
                    different = false;
                    break;
                }
            }

            if (different) {
                selected.add(candidate);
            }

            if (selected.size() >= 2) {
                break;
            }
        }

        for (Candidate candidate : selected) {

            result.add(
                    new DetectedLine(
                            candidate.angle,
                            candidate.rho
                    )
            );
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
            int rowStride,
            int pixelStride) {

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

        return minimum >= MIN_BRIGHTNESS
                && maximum - minimum <= MAX_COLOR_DIFFERENCE;
    }
}
