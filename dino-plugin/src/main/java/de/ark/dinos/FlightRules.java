package de.ark.dinos;

final class FlightRules {
    record Motion(double x, double y, double z) { }

    static double autonomousSpeed(int phase, double distance) {
        if (!Double.isFinite(distance) || distance <= 0) return 0;
        double speed = switch (phase) { case 1 -> .24; case 2 -> .32; case 3 -> .16; default -> 0; };
        return Math.min(speed, distance);
    }

    static Motion movement(float yaw, float pitch, boolean forward, boolean backward, boolean left, boolean right,
                           boolean jump, boolean sprint, double altitude, double ceiling) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Double.isFinite(altitude) || !Double.isFinite(ceiling))
            return new Motion(0, 0, 0);
        double ahead = (forward ? 1 : 0) - (backward ? 1 : 0);
        double side = (left ? 1 : 0) - (right ? 1 : 0);
        double length = Math.hypot(ahead, side);
        double angle = Math.toRadians(yaw);
        double tilt = Math.toRadians(Math.clamp(pitch, -80, 80));
        double horizontal = sprint ? .9 : .6, vertical = sprint ? .4 : .3;
        double x = length == 0 ? 0 : (-Math.sin(angle) * ahead * Math.cos(tilt) + Math.cos(angle) * side) * horizontal / length;
        double z = length == 0 ? 0 : (Math.cos(angle) * ahead * Math.cos(tilt) + Math.sin(angle) * side) * horizontal / length;
        double y = jump ? vertical : -Math.sin(tilt) * ahead * vertical;
        if (y > 0) y = Math.min(y, Math.max(0, ceiling - altitude));
        return new Motion(x, y, z);
    }

    static Motion movement(float yaw, float pitch, boolean forward, boolean backward, boolean left, boolean right,
                           boolean jump, double altitude, double ceiling) {
        return movement(yaw, pitch, forward, backward, left, right, jump, false, altitude, ceiling);
    }
}
