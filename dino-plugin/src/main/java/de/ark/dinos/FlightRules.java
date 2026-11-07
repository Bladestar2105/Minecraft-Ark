package de.ark.dinos;

final class FlightRules {
    record Motion(double x, double y, double z) { }

    static Motion movement(float yaw, float pitch, boolean forward, boolean backward, boolean left, boolean right,
                           boolean jump, double altitude, double ceiling) {
        double ahead = (forward ? 1 : 0) - (backward ? 1 : 0);
        double side = (left ? 1 : 0) - (right ? 1 : 0);
        double length = Math.hypot(ahead, side);
        double angle = Math.toRadians(yaw);
        double tilt = Math.toRadians(Math.clamp(pitch, -80, 80));
        double x = length == 0 ? 0 : (-Math.sin(angle) * ahead * Math.cos(tilt) + Math.cos(angle) * side) * .35 / length;
        double z = length == 0 ? 0 : (Math.cos(angle) * ahead * Math.cos(tilt) + Math.sin(angle) * side) * .35 / length;
        double y = jump ? .25 : -Math.sin(tilt) * ahead * .25;
        if (y > 0) y = Math.min(y, Math.max(0, ceiling - altitude));
        return new Motion(x, y, z);
    }
}
