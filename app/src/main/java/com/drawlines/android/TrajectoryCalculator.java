package com.drawlines.android;

import java.util.ArrayList;
import java.util.List;

public final class TrajectoryCalculator {

    private static final float BALL_RADIUS = 0.010f;
    private static final float MIN_DISTANCE = 0.0001f;

    private TrajectoryCalculator() {}

    public static class Point {
        public final float x;
        public final float y;

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Shot {
        public final Point cueBall;
        public final Point targetBall;
        public final Point contactPoint;
        public final Point pocket;
        public final Point ghostBall;
        public final float score;

        public Shot(
                Point cueBall,
                Point targetBall,
                Point contactPoint,
                Point pocket,
                Point ghostBall,
                float score) {

            this.cueBall = cueBall;
            this.targetBall = targetBall;
            this.contactPoint = contactPoint;
            this.pocket = pocket;
            this.ghostBall = ghostBall;
            this.score = score;
        }
    }

    public static List<Point> getPockets() {
        List<Point> pockets = new ArrayList<>();

        pockets.add(new Point(0.195f, 0.236f));
        pockets.add(new Point(0.500f, 0.236f));
        pockets.add(new Point(0.805f, 0.236f));

        pockets.add(new Point(0.195f, 0.916f));
        pockets.add(new Point(0.500f, 0.916f));
        pockets.add(new Point(0.805f, 0.916f));

        return pockets;
    }

    public static List<Shot> calculate(
            Point cueBall,
            List<Point> objectBalls) {

        List<Shot> shots = new ArrayList<>();

        if (cueBall == null || objectBalls == null) {
            return shots;
        }

        for (Point target : objectBalls) {
            if (target == null) continue;

            for (Point pocket : getPockets()) {

                Shot shot = calculateShot(
                        cueBall,
                        target,
                        pocket
                );

                if (shot != null) {
                    shots.add(shot);
                }
            }
        }

        shots.sort((a, b) ->
                Float.compare(b.score, a.score));

        return shots;
    }

    private static Shot calculateShot(
            Point cue,
            Point target,
            Point pocket) {

        float dx = pocket.x - target.x;
        float dy = pocket.y - target.y;

        float distance = length(dx, dy);

        if (distance < MIN_DISTANCE) {
            return null;
        }

        float ux = dx / distance;
        float uy = dy / distance;

        // Posición ideal de la bola blanca
        // para producir el contacto correcto.
        Point ghost = new Point(
                target.x - ux * BALL_RADIUS * 2f,
                target.y - uy * BALL_RADIUS * 2f
        );

        // Punto de contacto sobre la bola objetivo.
        Point contact = new Point(
                target.x - ux * BALL_RADIUS,
                target.y - uy * BALL_RADIUS
        );

        float cueDistance = length(
                ghost.x - cue.x,
                ghost.y - cue.y
        );

        float pocketDistance = distance;

        float score = 1f / (
                1f + cueDistance * 2f
                + pocketDistance
        );

        return new Shot(
                cue,
                target,
                contact,
                pocket,
                ghost,
                score
        );
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }
}
