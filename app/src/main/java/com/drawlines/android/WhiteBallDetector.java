package com.drawlines.android;

import android.media.Image;
import java.nio.ByteBuffer;
import java.util.List;

public final class WhiteBallDetector {

    private WhiteBallDetector() {}

    public static BallDetector.Ball detect(
            Image image,
            List<BallDetector.Ball> balls) {

        if (image == null || balls == null || balls.isEmpty()
                || image.getPlanes() == null
                || image.getPlanes().length == 0) {
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
            int cx = Math.round(ball.x);
            int cy = Math.round(ball.y);
            int radius = Math.max(2, Math.round(ball.radius * 0.25f));

            int red = 0, green = 0, blue = 0, samples = 0;

            for (int oy = -radius; oy <= radius; oy += 2) {
                for (int ox = -radius; ox <= radius; ox += 2) {
                    int x = cx + ox;
                    int y = cy + oy;

                    if (x < 0 || y < 0 || x >= width || y >= height) {
                        continue;
                    }

                    int index = y * rowStride + x * pixelStride;

                    if (index < 0 || index + 2 >= buffer.limit()) {
                        continue;
                    }

                    red += buffer.get(index) & 255;
                    green += buffer.get(index + 1) & 255;
                    blue += buffer.get(index + 2) & 255;
                    samples++;
                }
            }

            if (samples == 0) continue;

            red /= samples;
            green /= samples;
            blue /= samples;

            int min = Math.min(red, Math.min(green, blue));
            int max = Math.max(red, Math.max(green, blue));
            int brightness = (red + green + blue) / 3;
            int difference = max - min;

            if (brightness < 175 || difference > 48) continue;

            float score = brightness - difference * 2.5f;

            if (score > bestScore) {
                bestScore = score;
                bestBall = ball;
            }
        }

        return bestBall;
    }

    public static BallDetector.Ball detect(Image image) {
        if (image == null) return null;
        return detect(image, BallDetector.detectBalls(image));
    }
}
