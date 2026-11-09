package com.drawlines.android;

import java.util.ArrayList;
import java.util.List;

public final class ShotGuideEngine {

    private static final float BALL_DIAMETER_FACTOR = 2.0f;
    private static final float BLOCK_MARGIN = 1.15f;

    private ShotGuideEngine() {}

    public static class Guide {
        public final BallDetector.Ball target;
        public final float pocketX;
        public final float pocketY;
        public final float contactX;
        public final float contactY;
        public final float ghostX;
        public final float ghostY;
        public final float score;

        Guide(
                BallDetector.Ball target,
                float pocketX,
                float pocketY,
                float contactX,
                float contactY,
                float ghostX,
                float ghostY,
                float score) {

            this.target = target;
            this.pocketX = pocketX;
            this.pocketY = pocketY;
            this.contactX = contactX;
            this.contactY = contactY;
            this.ghostX = ghostX;
            this.ghostY = ghostY;
            this.score = score;
        }
    }

    private static class Pocket {
        final float x;
        final float y;

        Pocket(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static Guide calculateBest(
            BallDetector.Ball cue,
            List<BallDetector.Ball> balls,
            int imageWidth,
            int imageHeight) {

        if (cue == null || balls == null
                || imageWidth <= 0 || imageHeight <= 0) {
            return null;
        }

        List<Pocket> pockets = getPockets(
                imageWidth,
                imageHeight
        );

        Guide best = null;

        for (BallDetector.Ball target : balls) {

            if (target == null || target == cue) {
                continue;
            }

            float dx = target.x - cue.x;
            float dy = target.y - cue.y;

            if (distance(dx, dy) < 1f) {
                continue;
            }

            for (Pocket pocket : pockets) {

                Guide candidate = evaluate(
                        cue,
                        target,
                        pocket,
                        balls
                );

                if (candidate == null) {
                    continue;
                }

                if (best == null
                        || candidate.score > best.score) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private static Guide evaluate(
            BallDetector.Ball cue,
            BallDetector.Ball target,
            Pocket pocket,
            List<BallDetector.Ball> balls) {

        float dx = pocket.x - target.x;
        float dy = pocket.y - target.y;

        float targetToPocket = distance(dx, dy);

        if (targetToPocket < 1f) {
            return null;
        }

        float ux = dx / targetToPocket;
        float uy = dy / targetToPocket;

        float radius = Math.max(
                cue.radius,
                target.radius
        );

        float diameter = radius * BALL_DIAMETER_FACTOR;

        float ghostX = target.x - ux * diameter;
        float ghostY = target.y - uy * diameter;

        float contactX = target.x - ux * radius;
        float contactY = target.y - uy * radius;

        // Comprueba que el recorrido de la bola blanca
        // hasta la posición ideal no esté bloqueado.
        for (BallDetector.Ball other : balls) {

            if (other == null
                    || other == cue
                    || other == target) {
                continue;
            }

            float clearance = other.radius
                    + radius * BLOCK_MARGIN;

            if (distanceToSegment(
                    other.x,
                    other.y,
                    cue.x,
                    cue.y,
                    ghostX,
                    ghostY
            ) < clearance) {
                return null;
            }

            // Comprueba también el recorrido de la
            // bola objetivo hacia la tronera.
            if (distanceToSegment(
                    other.x,
                    other.y,
                    target.x,
                    target.y,
                    pocket.x,
                    pocket.y
            ) < clearance) {
                return null;
            }
        }

        float cueDistance = distance(
                ghostX - cue.x,
                ghostY - cue.y
        );

        float totalDistance =
                cueDistance + targetToPocket;

        float score = 1000f / (100f + totalDistance);

        return new Guide(
                target,
                pocket.x,
                pocket.y,
                contactX,
                contactY,
                ghostX,
                ghostY,
                score
        );
    }

    private static List<Pocket> getPockets(
            int width,
            int height) {

        List<Pocket> result = new ArrayList<>();

        float left = width * 0.195f;
        float right = width * 0.805f;
        float top = height * 0.236f;
        float bottom = height * 0.916f;
        float middle = (left + right) / 2f;

        result.add(new Pocket(left, top));
        result.add(new Pocket(middle, top));
        result.add(new Pocket(right, top));

        result.add(new Pocket(left, bottom));
        result.add(new Pocket(middle, bottom));
        result.add(new Pocket(right, bottom));

        return result;
    }

    private static float distance(
            float dx,
            float dy) {

        return (float) Math.sqrt(
                dx * dx + dy * dy
        );
    }

    private static float distanceToSegment(
            float px,
            float py,
            float ax,
            float ay,
            float bx,
            float by) {

        float dx = bx - ax;
        float dy = by - ay;

        float lengthSquared = dx * dx + dy * dy;

        if (lengthSquared <= 0.0001f) {
            return distance(px - ax, py - ay);
        }

        float t = (
                (px - ax) * dx
                + (py - ay) * dy
        ) / lengthSquared;

        t = Math.max(0f, Math.min(1f, t));

        float nearestX = ax + t * dx;
        float nearestY = ay + t * dy;

        return distance(
                px - nearestX,
                py - nearestY
        );
    }
}
