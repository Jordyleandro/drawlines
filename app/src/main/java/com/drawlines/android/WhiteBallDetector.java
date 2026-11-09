package com.drawlines.android;

import android.media.Image;

import java.nio.ByteBuffer;
import java.util.List;

public final class WhiteBallDetector {

    private WhiteBallDetector() {
    }

    public static BallDetector.Ball detect(Image image) {

        if (image == null || image.getPlanes() == null
                || image.getPlanes().length == 0) {
            return null;
        }

        List<BallDetector.Ball> balls =
                BallDetector.detectBalls(image);

        if (balls.isEmpty()) {
            return null;
        }

        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer().duplicate();

        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        BallDetector.Ball bestBall = null;
        float bestScore = -1f;

        for (BallDetector.Ball ball : balls) {

            int centerX = Math.round(ball.x);
            int centerY = Math.round(ball.y);

            int red = 0;
            int green = 0;
            int blue = 0;
            int samples = 0;

            int radius = Math.max(
                    2,
                    Math.round(ball.radius * 0.25f)
            );

            for (int oy = -radius; oy <= radius; oy += 2) {
                for (int ox = -radius; ox <= radius; ox += 2) {

                    int x = centerX + ox;
                    int y = centerY + oy;

                    if (x < 0 || y < 0
                            || x >= width || y >= height) {
                        continue;
                    }

                    int index = y * rowStride + x * pixelStride;

                    if (index < 0 || index + 2 >= buffer.limit()) {
                        continue;
                    }

                    red += buffer.get(index) & 0xFF;
                    green += buffer.get(index + 1) & 0xFF;
                    blue += buffer.get(index + 2) & 0xFF;

                    samples++;
                }
            }

            if (samples == 0) {
                continue;
            }

            red /= samples;
            green /= samples;
            blue /= samples;

            int minimum = Math.min(red, Math.min(green, blue));
            int maximum = Math.max(red, Math.max(green, blue));

            int brightness = (red + green + blue) / 3;
            int difference = maximum - minimum;

            if (brightness < 165 || difference > 65) {
                continue;
            }

            float score = brightness - difference * 2f;

            if (score > bestScore) {
                bestScore = score;
                bestBall = ball;
            }
        }

        return bestBall;
    }
}
