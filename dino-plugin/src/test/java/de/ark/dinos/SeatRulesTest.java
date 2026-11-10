package de.ark.dinos;

/** Seats must stay behind their driver and sweep the short turn through the world. */
public final class SeatRulesTest {
    public static void main(String[] args) {
        for (String species : new String[]{"trex", "triceratops", "stegosaurus", "brachiosaurus"})
            check(SeatRules.supports(species), "Large adult species needs a second seat: " + species);
        for (String species : new String[]{"pteranodon", "velociraptor", "dilophosaurus", "mosasaurus", "unknown", ""})
            check(!SeatRules.supports(species), "Species without a large saddle must keep one seat: " + species);
        check(!SeatRules.supports(null), "Unknown species must fail closed");
        offset(0, 0, -1.2);
        offset(90, 1.2, 0);
        offset(180, 0, 1.2);
        offset(270, -1.2, 0);
        offset(450, 1.2, 0);
        offset(-90, -1.2, 0);
        var nativeOffset = SeatRules.rearOffset(0, 0, 0, -.0625);
        close(nativeOffset.z(), -1.2625, "Guest stays 1.2 behind the actual native driver seat");
        var turnedOffset = SeatRules.rearOffset(0, 90, 0, -.0625);
        close(turnedOffset.x(), 1.2625, "A proposed turn rotates the native attachment too");
        close(turnedOffset.z(), 0, "A proposed turn must not retain the old world-axis offset");
        var scaledOffset = SeatRules.rearOffset(90, 180, .125, 0);
        close(scaledOffset.z(), 1.325, "Native scale changes are measured, not hardcoded");
        close(SeatRules.effectiveRear(0, 0, -.0625), 1.2625, "Bridge uses the actual carrier-relative radius");
        close(SeatRules.effectiveRear(90, .125, 0), 1.325, "Bridge radius follows scaled driver attachments");
        close(SeatRules.markerY(7.5), 8.1, "Marker height uses native rider feet exactly once");
        close(SeatRules.markerY(-62.75), -62.15, "Seat calibration works below zero");
        close(SeatRules.yawDelta(179, -179), 2, "Crossing north uses the short positive turn");
        close(SeatRules.yawDelta(-179, 179), -2, "Crossing north uses the short negative turn");
        close(SeatRules.yawDelta(10, 730), 0, "Whole turns do not enlarge the collision sweep");
        check(SeatRules.rotationSteps(0, 180) >= 18, "Half turns sample the intermediate arc, not only its endpoints");
        check(SeatRules.rotationSteps(179, -179) == 1, "The short wraparound turn needs only one sweep");
        check(SeatRules.rotationSteps(0, 0) == 1, "Translation without rotation still checks a swept volume");
        reject(() -> SeatRules.rearOffset(Float.NaN));
        reject(() -> SeatRules.yawDelta(Float.POSITIVE_INFINITY, 0));
        reject(() -> SeatRules.markerY(Double.NaN));
        System.out.println("PASS: second-seat species, native height, rear placement and rotation sweeps");
    }

    private static void offset(float yaw, double x, double z) {
        var result = SeatRules.rearOffset(yaw);
        close(result.x(), x, "Seat X behind yaw " + yaw);
        close(result.z(), z, "Seat Z behind yaw " + yaw);
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < .000001, message + ": expected " + expected + ", got " + actual);
    }

    private static void reject(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Non-finite seat coordinates must fail closed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
