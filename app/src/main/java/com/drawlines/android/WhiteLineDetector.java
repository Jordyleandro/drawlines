package com.drawlines.android;

import android.media.Image;

import java.nio.ByteBuffer;

public class WhiteLineDetector {

    private static final int START_DISTANCE = 22;
    private static final int MAX_DISTANCE = 500;
    private static final int DISTANCE_STEP = 3;
    private static final int ANGLE_STEP = 2;

    private static final int MIN_LINE_LENGTH = 10;

    private WhiteLineDetector() {
    }

    public static Float detectAngle(
            Image image,
            float originX,
            float originY) {

        if (image == null) {
            return null;
        }

        Image.Plane[] planes = image.getPlanes();

        if (planes == null || planes.length == 0) {
            return null;
        }

        Image.Plane plane = planes[0];

        ByteBuffer buffer = plane.getBuffer();

        int width = image.getWidth();
        int height = image.getHeight();

        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        int bestScore = 0;
        float bestAngle = 0;

        int maxDistance = Math.min(
                MAX_DISTANCE,
                Math.max(width, height)
        );

        for (int degrees = 0;
             degrees < 360;
             degrees += ANGLE_STEP) {

            double radians = Math.toRadians(degrees);

            float dx = (float) Math.cos(radians);
            float dy = (float) Math.sin(radians);

            int currentScore = 0;
            int bestRun = 0;
            int gaps = 0;

            for (int distance = START_DISTANCE;
                 distance <= maxDistance;
                 distance += DISTANCE_STEP) {

                float x = originX + dx * distance;
                float y = originY + dy * distance;

                float perpendicularX = -dy;
                float perpendicularY = dx;

                boolean whiteFound = false;

                for (int offset = -3;
                     offset <= 3;
                     offset += 3) {

                    int sampleX = Math.round(
                            x + perpendicularX * offset
                    );

                    int sampleY = Math.round(
                            y + perpendicularY * offset
                    );

                    if (isWhite(
                            buffer,
                            sampleX,
                            sampleY,
                            width,
                            height,
                            rowStride,
                            pixelStride)) {

                        whiteFound = true;
                        break;
                    }
                }

                if (whiteFound) {
                    currentScore++;
                    gaps = 0;

                    if (currentScore > bestRun) {
                        bestRun = currentScore;
                    }

                } else {
                    gaps++;

                    if (gaps > 2) {
                        currentScore = 0;
                    }
                }
            }

            if (bestRun > bestScore) {
                bestScore = bestRun;
                bestAngle = degrees;
            }
        }

        if (bestScore < MIN_LINE_LENGTH) {
            return null;
        }

        return bestAngle;
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

        int index =
                y * rowStride + x * pixelStride;

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
