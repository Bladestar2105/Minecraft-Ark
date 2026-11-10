package de.ark.dinos;

/** Model-independent, adult second-seat geometry. */
final class SeatRules {
    static final double REAR_DISTANCE = 1.2;
    static final float BEDROCK_SEAT_Y = 1.02001f;

    private SeatRules() { }

    static boolean supports(String species) {
        return switch (species) {
            case "trex", "triceratops", "stegosaurus", "brachiosaurus" -> true;
            case null, default -> false;
        };
    }

    record Offset(double x, double z) { }

    static Offset rearOffset(float yaw) {
        finite(yaw);
        double angle = Math.toRadians(yaw);
        return new Offset(Math.sin(angle) * REAR_DISTANCE, -Math.cos(angle) * REAR_DISTANCE);
    }

    /** Rotate the measured native driver attachment, then place the guest behind that seat. */
    static Offset rearOffset(float currentYaw, float proposedYaw, double driverX, double driverZ) {
        finite(driverX);
        finite(driverZ);
        double angle = Math.toRadians(yawDelta(currentYaw, proposedYaw));
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        Offset rear = rearOffset(proposedYaw);
        return new Offset(driverX * cosine - driverZ * sine + rear.x(),
                driverX * sine + driverZ * cosine + rear.z());
    }

    /** Bridge coordinates are relative to the carrier, including its scaled native attachment. */
    static double effectiveRear(float yaw, double driverX, double driverZ) {
        finite(yaw);
        finite(driverX);
        finite(driverZ);
        double angle = Math.toRadians(yaw);
        return REAR_DISTANCE + driverX * Math.sin(angle) - driverZ * Math.cos(angle);
    }

    static double markerY(double driverFeetY) {
        finite(driverFeetY);
        return driverFeetY + .6;
    }

    static double yawDelta(float from, float to) {
        finite(from);
        finite(to);
        double delta = ((double) to - from) % 360;
        if (delta > 180) delta -= 360;
        if (delta < -180) delta += 360;
        return delta;
    }

    static int rotationSteps(float from, float to) {
        return Math.max(1, (int) Math.ceil(Math.abs(yawDelta(from, to)) / 10));
    }

    private static void finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite dinosaur seat coordinate");
    }
}
