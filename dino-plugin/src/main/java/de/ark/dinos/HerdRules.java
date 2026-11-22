package de.ark.dinos;

import java.util.List;

/** Local steering has no birthplace, biome boundary, teleport or removal rule. */
final class HerdRules {
    record Point(double x, double z) {
        boolean finite() { return Double.isFinite(x) && Double.isFinite(z); }
    }

    private HerdRules() { }

    static boolean eligible(boolean owned, boolean passengers, boolean staying, boolean leashed, boolean valid) {
        return valid && !owned && !passengers && !staying && !leashed;
    }

    static boolean graze(long ticks, int identity, boolean grass, boolean threatened) {
        return grass && !threatened && Math.floorMod(ticks / 20 + identity, 32) < 8;
    }

    static Point threat(List<Point> herd, List<Point> predators) {
        Point result = null;
        double nearest = 56 * 56;
        for (Point predator : predators) {
            if (!predator.finite()) continue;
            for (Point member : herd) {
                double distance = square(member.x() - predator.x(), member.z() - predator.z());
                if (distance < nearest) { nearest = distance; result = predator; }
            }
        }
        return result;
    }

    static Point steer(Point self, List<Point> neighbors, Point threat, Point wander) {
        if (!self.finite()) return new Point(0, 0);
        Point drift = normalized(wander.finite() ? wander.x() : 0, wander.finite() ? wander.z() : 0);
        double x = drift.x() * .35, z = drift.z() * .35;
        double sumX = 0, sumZ = 0;
        int count = 0;
        for (Point neighbor : neighbors) {
            if (!neighbor.finite()) continue;
            double dx = self.x() - neighbor.x(), dz = self.z() - neighbor.z();
            double distance = square(dx, dz);
            if (!Double.isFinite(distance) || distance > 32 * 32) continue;
            if (distance < .01) { x += drift.x() * 3; z += drift.z() * 3; }
            else if (distance < 5 * 5) { x += dx * 5 / distance; z += dz * 5 / distance; }
            sumX -= dx; sumZ -= dz; count++;
        }
        if (count != 0) { x += sumX / count * .03; z += sumZ / count * .03; }
        if (threat != null && threat.finite()) {
            Point away = normalized(self.x() - threat.x(), self.z() - threat.z());
            x += away.x() * 3; z += away.z() * 3;
        }
        return normalized(x, z);
    }

    private static double square(double x, double z) { return x * x + z * z; }

    private static Point normalized(double x, double z) {
        double length = Math.hypot(x, z);
        if (!Double.isFinite(length)) return new Point(0, 0);
        return length > 1 ? new Point(x / length, z / length) : new Point(x, z);
    }
}
